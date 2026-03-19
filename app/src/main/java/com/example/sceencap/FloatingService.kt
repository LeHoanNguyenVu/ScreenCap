package com.example.sceencap

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    private var screenCaptureResultCode: Int = 0
    private var screenCaptureResultData: Intent? = null

    // Bộ 3 quyền lực chạy ngầm xuyên suốt
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // --- BIẾN TOÀN CỤC CHỨA ẢNH ĐỂ TRUYỀN SANG CROP ACTIVITY ---
    companion object {
        var capturedBitmap: Bitmap? = null
    }

    // --- MA THUẬT SỬA LỖI LẶP ĐÚP TOAST ---
    // Khóa 1: Lời yêu cầu chụp (Bình thường hạ xuống)
    private var takePictureFlag = false
    // Khóa 2: Trạng thái đang đúc ảnh (Chặn khung hình tràn vào)
    private var isCaptureProcessing = false
    // ----------------------------------------

    override fun onBind(intent: Intent?): IBinder? { return null }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        val viewCollapsed = floatingView.findViewById<View>(R.id.view_collapsed)
        val viewExpanded = floatingView.findViewById<View>(R.id.view_expanded)
        val btnCapture = floatingView.findViewById<View>(R.id.btn_capture)
        val btnClose = floatingView.findViewById<View>(R.id.btn_close)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        windowManager.addView(floatingView, params)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        viewCollapsed.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX > 10 || diffY > 10) {
                        isMoved = true
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoved) {
                        viewCollapsed.visibility = View.GONE
                        viewExpanded.visibility = View.VISIBLE
                    } else {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val halfScreenWidth = screenWidth / 2
                        val targetX = if (params.x + (floatingView.width / 2) < halfScreenWidth) 0 else screenWidth - floatingView.width
                        val animator = android.animation.ValueAnimator.ofInt(params.x, targetX)
                        animator.duration = 250
                        animator.addUpdateListener { animation ->
                            params.x = animation.animatedValue as Int
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        animator.start()
                    }
                    true
                }
                else -> false
            }
        }

        btnClose.setOnClickListener { stopSelf() }

        btnCapture.setOnClickListener {
            viewExpanded.visibility = View.GONE
            viewCollapsed.visibility = View.VISIBLE

            if (mediaProjection == null) {
                val intent = Intent(this, CaptureActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                captureScreen()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_SAVE_TOKEN_AND_CAPTURE") {
            try {
                screenCaptureResultCode = intent.getIntExtra("RESULT_CODE", 0)
                @Suppress("DEPRECATION")
                screenCaptureResultData = intent.getParcelableExtra("RESULT_DATA")

                // 1. Mặc giáp chạy ngầm
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel("sceencap_channel", "SceenCap", NotificationManager.IMPORTANCE_LOW)
                    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
                    val notification = Notification.Builder(this, "sceencap_channel")
                        .setContentTitle("SceenCap")
                        .setContentText("Đang sẵn sàng chụp")
                        .setSmallIcon(android.R.drawable.ic_menu_camera)
                        .build()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    } else {
                        startForeground(1, notification)
                    }
                }

                // 2. Lắp đặt Camera An Ninh
                Handler(Looper.getMainLooper()).postDelayed({
                    setupCameraStandby()
                }, 300)

            } catch (e: Throwable) {
                Toast.makeText(this, "❌ Lỗi hệ thống: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun setupCameraStandby() {
        if (mediaProjection != null) return

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(screenCaptureResultCode, screenCaptureResultData!!)

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    mediaProjection = null
                }
            }, null)

            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val metrics = android.util.DisplayMetrics()
            display.getRealMetrics(metrics)

            var width = metrics.widthPixels
            var height = metrics.heightPixels
            val density = metrics.densityDpi

            if (width % 2 != 0) width -= 1
            if (height % 2 != 0) height -= 1

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            handlerThread = HandlerThread("ScreenCapture")
            handlerThread?.start()
            backgroundHandler = Handler(handlerThread!!.looper)

            imageReader?.setOnImageAvailableListener({ reader ->
                // Dùng acquireLatestImage để luôn lấy khung hình mới nhất, vứt bỏ các khung cũ
                val image = reader.acquireLatestImage()
                if (image != null) {
                    // --- NÂNG CẤP KHÓA AN TOÀN ---
                    // Phải có Lời yêu cầu CHÙA có người đang đúc ảnh, mới cho vào
                    if (takePictureFlag && !isCaptureProcessing) {
                        isCaptureProcessing = true // KHÓA CỬA LẠI
                        takePictureFlag = false // Hạ cờ xuống ngay lập tức

                        try {
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * width

                            val bitmapWidth = width + rowPadding / pixelStride
                            val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)

                            buffer.position(0)
                            bitmap.copyPixelsFromBuffer(buffer)

                            // Tạo bức ảnh hoàn chỉnh cắt bỏ phần viền đen thừa
                            val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                            // 1. CẤT ẢNH VÀO BIẾN COMPANION OBJECT
                            capturedBitmap = finalBitmap

                            // 2. CHUYỂN LUỒNG & GỌI MÀN HÌNH CROP MỞ LÊN
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(this@FloatingService, "🎉 Đã chụp! Đang mở chế độ cắt...", Toast.LENGTH_SHORT).show()

                                // Khởi chạy CropActivity từ Service
                                val intent = Intent(this@FloatingService, CropActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)

                                // MỞ KHÓA LẠI SAU KHI ĐÃ ĐIỀU HƯỚNG XONG (đảm bảo RAM ổn định)
                                isCaptureProcessing = false
                            }
                        } catch (e: Throwable) {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(this@FloatingService, "❌ Lỗi điểm ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                                isCaptureProcessing = false // Lỗi cũng phải mở khóa
                            }
                        }
                    }
                    // Dọn rác
                    image.close()
                }
            }, backgroundHandler)

            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                flags,
                imageReader?.surface, null, backgroundHandler
            )

            captureScreen()

        } catch (e: Throwable) {
            Toast.makeText(this, "❌ Lỗi lắp Camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun captureScreen() {
        if (mediaProjection == null) {
            Toast.makeText(this, "❌ Máy quay bị sập, vui lòng bật lại app!", Toast.LENGTH_SHORT).show()
            return
        }

        // Nếu đang đúc ảnh dở thì không cho xin thêm
        if (isCaptureProcessing) return


        // Trì hoãn 300ms để giao diện có thời gian thu cái Menu lại thành Ngôi sao
        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(this, "📸 Đang nháy máy...", Toast.LENGTH_SHORT).show()
            takePictureFlag = true // Bắt đầu phất cờ chụp
        }, 300)
        // ----------------------------------------
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        virtualDisplay?.release()
        imageReader?.close()
        handlerThread?.quitSafely()
        mediaProjection?.stop()
    }
}
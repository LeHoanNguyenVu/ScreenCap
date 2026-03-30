package com.example.sceencap

import android.annotation.SuppressLint
import android.app.AlertDialog
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
import android.view.ContextThemeWrapper
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
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var viewCollapsed: View
    private lateinit var viewExpanded: View

    private var screenCaptureResultCode: Int = 0
    private var screenCaptureResultData: Intent? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    companion object {
        var capturedBitmap: Bitmap? = null
    }

    private var takePictureFlag = false
    private var isCaptureProcessing = false

    override fun onBind(intent: Intent?): IBinder? { return null }

    override fun onCreate() {
        super.onCreate()

        // Khởi động Foreground Service ở chế độ specialUse để duy trì ngôi sao
        createNotificationChannel()
        val notification = createNotification("ScreenApp đang sẵn sàng")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ yêu cầu specialUse nếu không phải mediaProjection ngay lập tức
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            // Fallback nếu có lỗi quyền
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification)
            }
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        viewCollapsed = floatingView.findViewById(R.id.view_collapsed)
        viewExpanded = floatingView.findViewById(R.id.view_expanded)
        val btnCapture = floatingView.findViewById<View>(R.id.btn_capture)
        val btnQr = floatingView.findViewById<View>(R.id.btn_menu_qr)
        val btnClose = floatingView.findViewById<View>(R.id.btn_close)

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowParams.gravity = Gravity.TOP or Gravity.START
        windowParams.x = 0
        windowParams.y = 200

        windowManager.addView(floatingView, windowParams)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        viewCollapsed.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams.x
                    initialY = windowParams.y
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
                        windowParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        windowParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, windowParams)
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
                        val targetX = if (windowParams.x + (floatingView.width / 2) < halfScreenWidth) 0 else screenWidth - floatingView.width
                        val animator = android.animation.ValueAnimator.ofInt(windowParams.x, targetX)
                        animator.duration = 250
                        animator.addUpdateListener { animation ->
                            windowParams.x = animation.animatedValue as Int
                            windowManager.updateViewLayout(floatingView, windowParams)
                        }
                        animator.start()
                    }
                    true
                }
                else -> false
            }
        }

        btnClose.setOnClickListener {
            showExitConfirmationDialog()
        }

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

        btnQr.setOnClickListener {
            viewExpanded.visibility = View.GONE
            viewCollapsed.visibility = View.VISIBLE
            floatingView.visibility = View.GONE // Ẩn ngôi sao trực tiếp
            
            val intent = Intent(this, ScannerActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("sceencap_channel", "SceenCap Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "sceencap_channel")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("SceenCap")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    private fun showExitConfirmationDialog() {
        val builder = AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert))
        builder.setTitle("Xác nhận")
        builder.setMessage("Bạn có chắc muốn tắt ScreenApp?")
        
        builder.setPositiveButton("Tắt") { _, _ ->
            stopSelf()
        }
        
        builder.setNegativeButton("Hủy") { dialog, _ ->
            viewExpanded.visibility = View.GONE
            viewCollapsed.visibility = View.VISIBLE
            dialog.dismiss()
        }

        val dialog = builder.create()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_HIDE_STAR" -> {
                if (::floatingView.isInitialized) floatingView.visibility = View.GONE
            }
            "ACTION_SHOW_STAR" -> {
                if (::floatingView.isInitialized) {
                    floatingView.visibility = View.VISIBLE
                    viewCollapsed.visibility = View.VISIBLE
                    viewExpanded.visibility = View.GONE
                }
            }
            "ACTION_SAVE_TOKEN_AND_CAPTURE" -> {
                try {
                    screenCaptureResultCode = intent.getIntExtra("RESULT_CODE", 0)
                    @Suppress("DEPRECATION")
                    screenCaptureResultData = intent.getParcelableExtra("RESULT_DATA")

                    // Chuyển sang chế độ MediaProjection khi đã có quyền
                    val notification = createNotification("Đang sẵn sàng chụp")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    }

                    Handler(Looper.getMainLooper()).postDelayed({
                        setupCameraStandby()
                    }, 300)

                } catch (e: Throwable) {
                    Toast.makeText(this, "❌ Lỗi hệ thống: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        return START_STICKY
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            tearDownAll()
        }
    }

    @SuppressLint("WrongConstant")
    private fun setupCameraStandby() {
        if (mediaProjection != null) return

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(screenCaptureResultCode, screenCaptureResultData!!)
            mediaProjection?.registerCallback(projectionCallback, null)

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
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        if (takePictureFlag && !isCaptureProcessing) {
                            isCaptureProcessing = true
                            takePictureFlag = false

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

                                val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                                capturedBitmap = finalBitmap

                                Handler(Looper.getMainLooper()).post {
                                    val intent = Intent(this@FloatingService, CropActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(intent)
                                    isCaptureProcessing = false
                                }
                            } catch (e: Throwable) {
                                Handler(Looper.getMainLooper()).post {
                                    isCaptureProcessing = false
                                }
                            }
                        }
                        image.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, backgroundHandler)

            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture", width, height, density, flags,
                imageReader?.surface, null, backgroundHandler
            )

            captureScreen()

        } catch (e: Throwable) {
            tearDownAll()
        }
    }

    private fun captureScreen() {
        if (mediaProjection == null) return
        if (isCaptureProcessing) return

        Handler(Looper.getMainLooper()).postDelayed({
            takePictureFlag = true
            val animator = android.animation.ValueAnimator.ofFloat(1f, 0.99f, 1f)
            animator.duration = 400
            animator.addUpdateListener {
                floatingView.alpha = it.animatedValue as Float
            }
            animator.start()

            Handler(Looper.getMainLooper()).postDelayed({
                if (takePictureFlag) {
                    takePictureFlag = false
                    isCaptureProcessing = false
                }
            }, 3000)

        }, 300)
    }

    private fun tearDownAll() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        tearDownAll()
    }
}
package com.example.sceencap

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
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

    // 2 Biến lưu giữ "Thẻ bài" chụp màn hình
    private var screenCaptureResultCode: Int = 0
    private var screenCaptureResultData: Intent? = null

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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        windowManager.addView(floatingView, params)

        // --- LOGIC KÉO THẢ, HÍT LỀ & MỞ MENU BONG BÓNG ---
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
                        // NẾU CLICK: Mở menu bong bóng
                        viewCollapsed.visibility = View.GONE
                        viewExpanded.visibility = View.VISIBLE
                    } else {
                        // NẾU KÉO XONG: Hít vào lề màn hình
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

        // --- SỰ KIỆN MENU BONG BÓNG ---
        btnClose.setOnClickListener {
            stopSelf() // Tắt hoàn toàn Service và bong bóng
        }

        btnCapture.setOnClickListener {
            // Thu menu lại thành ngôi sao
            viewExpanded.visibility = View.GONE
            viewCollapsed.visibility = View.VISIBLE

            // KIỂM TRA THẺ BÀI
            if (screenCaptureResultData == null) {
                // Lần đầu bấm: Chưa có thẻ -> Mở CaptureActivity xin quyền
                val intent = Intent(this, CaptureActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                // Những lần sau: Đã có thẻ -> Chụp luôn!
                captureScreen()
            }
        }
    }

    // --- NHẬN THẺ BÀI TỪ CAPTURE ACTIVITY GỬI VỀ ---
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_SAVE_TOKEN_AND_CAPTURE") {
            screenCaptureResultCode = intent.getIntExtra("RESULT_CODE", 0)

            // Xử lý lấy Intent data an toàn cho nhiều phiên bản Android
            screenCaptureResultData = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("RESULT_DATA", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("RESULT_DATA")
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "sceencap_channel", "SceenCap Service", android.app.NotificationManager.IMPORTANCE_LOW
                )
                getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)

                val notification = android.app.Notification.Builder(this, "sceencap_channel")
                    .setContentTitle("SceenCap")
                    .setContentText("Đang chạy ngầm để chụp ảnh")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build()

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(1, notification)
                }
            }

            // Lấy thẻ xong thì chụp luôn
            captureScreen()
        }
        return START_NOT_STICKY
    }

    // --- HÀM XỬ LÝ CHỤP (Tạm thời là Demo) ---
    private fun captureScreen() {
        Toast.makeText(this, "CHỤP CÁI RỤP! (Đã dùng thẻ bài)", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
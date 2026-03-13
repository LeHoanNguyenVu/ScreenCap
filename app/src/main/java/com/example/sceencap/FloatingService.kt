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

    override fun onBind(intent: Intent?): IBinder? { return null }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        // Ánh xạ các thành phần giao diện mới
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

        // --- BIẾN DÙNG ĐỂ TÍNH TOÁN KÉO & CLICK ---
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false // Cờ hiệu nhận biết Kéo hay Click

        viewCollapsed.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false // Vừa chạm vào thì chưa di chuyển
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Tính khoảng cách ngón tay đã trượt đi
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)

                    // Nếu trượt xa hơn 10 pixel -> Đánh dấu là hành động KÉO (Drag)
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
                        // NẾU LÀ CLICK (Không kéo di chuyển): Ẩn sao, Mở Menu
                        viewCollapsed.visibility = View.GONE
                        viewExpanded.visibility = View.VISIBLE
                    } else {
                        // NẾU LÀ DRAG (Có kéo đi): Logic hít lề cũ
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

        // --- XỬ LÝ SỰ KIỆN KHI BẤM NÚT TRONG MENU ---

        // 1. Nút Tắt: Tiêu diệt Service -> Ngôi sao biến mất hoàn toàn
        btnClose.setOnClickListener {
            stopSelf()
        }

        // 2. Nút Chụp: Tạm thời hiện thông báo, sau đó tự thu menu lại thành ngôi sao
        btnCapture.setOnClickListener {
            viewExpanded.visibility = View.GONE
            viewCollapsed.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
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

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

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

        // --- ĐOẠN CODE MỚI: XỬ LÝ KÉO THẢ (DRAG & DROP) ---

        // Tạo các biến để lưu trữ vị trí ban đầu của ngôi sao và ngón tay
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        // Lắng nghe sự kiện ngón tay chạm vào ngôi sao
        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                // Trạng thái 1: Vừa đặt ngón tay xuống (ACTION_DOWN)
                MotionEvent.ACTION_DOWN -> {
                    // Ghi nhớ lại vị trí của ngôi sao lúc này
                    initialX = params.x
                    initialY = params.y
                    // Ghi nhớ tọa độ tuyệt đối của ngón tay trên màn hình
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                // Trạng thái 2: Giữ ngón tay và di chuyển (ACTION_MOVE)
                MotionEvent.ACTION_MOVE -> {
                    // Tính toán xem ngón tay đã trượt đi bao nhiêu xa so với lúc đầu
                    // Rồi cộng dồn vào vị trí ban đầu của ngôi sao
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()

                    // Cập nhật lại vị trí hiển thị liên tục
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }

                // Trạng thái 3: Nhấc ngón tay lên (ACTION_UP)
                MotionEvent.ACTION_UP -> {
                    // Tạm thời chưa làm gì. (Sau này mình sẽ code vụ click mở menu ở đây)
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
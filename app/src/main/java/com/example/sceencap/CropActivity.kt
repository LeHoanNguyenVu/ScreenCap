package com.example.sceencap

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class CropActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        // Phóng to toàn màn hình
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        val imgBg = findViewById<ImageView>(R.id.img_screenshot_bg)

        // --- 1. TÌM ĐẾN CUSTOM VIEW CỦA CHÚNG TA ---
        val viewCropOverlay = findViewById<CropOverlayView>(R.id.view_crop_overlay)

        // LẤY TẤM ẢNH VỪA CHỤP
        val bitmap = FloatingService.capturedBitmap
        if (bitmap != null) {
            imgBg.setImageBitmap(bitmap)

            // --- 2. TRUYỀN TẤM ẢNH NÀY VÀO CUSTOM VIEW ĐỂ NÓ CẮT ---
            viewCropOverlay.setOriginalBitmap(bitmap)

        } else {
            finish()
        }
    }

    // --- HÀM onResume ĐƯỢC ĐẶT Ở ĐÂY (Ngang hàng với onCreate) ---
    // Hàm này sẽ tự động chạy mỗi khi bạn ấn nút Back để quay lại màn hình này
    override fun onResume() {
        super.onResume()
        // Tìm Custom View và gọi lệnh reset để xóa sạch khung vẽ cũ
        val viewCropOverlay = findViewById<CropOverlayView>(R.id.view_crop_overlay)
        viewCropOverlay?.reset()
    }
}
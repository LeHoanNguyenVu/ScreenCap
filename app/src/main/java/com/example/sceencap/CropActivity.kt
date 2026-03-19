package com.example.sceencap

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class CropActivity : AppCompatActivity() {

    // --- MA THUẬT: Tạo biến toàn cục để điều khiển màn hình này từ xa ---
    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: CropActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        // Ghi nhận màn hình đang mở
        instance = this

        // Phóng to toàn màn hình
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        val imgBg = findViewById<ImageView>(R.id.img_screenshot_bg)
        val viewCropOverlay = findViewById<CropOverlayView>(R.id.view_crop_overlay)

        val bitmap = FloatingService.capturedBitmap
        if (bitmap != null) {
            imgBg.setImageBitmap(bitmap)
            viewCropOverlay.setOriginalBitmap(bitmap)
        } else {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val viewCropOverlay = findViewById<CropOverlayView>(R.id.view_crop_overlay)
        viewCropOverlay?.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dọn dẹp biến khi màn hình bị đóng để chống tràn RAM
        instance = null
    }
}
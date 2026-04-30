package com.example.sceencap

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class CropActivity : AppCompatActivity() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: CropActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        instance = this

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
        // Ẩn ngôi sao mỗi khi màn hình này active (kể cả khi quay lại từ CropPreviewActivity)
        startService(Intent(this, FloatingService::class.java).apply {
            action = "ACTION_HIDE_STAR"
        })
        val viewCropOverlay = findViewById<CropOverlayView>(R.id.view_crop_overlay)
        viewCropOverlay?.reset()
    }

    override fun onPause() {
        super.onPause()
        // Hiện lại ngôi sao khi rời màn hình này (sang CropPreviewActivity hoặc bất kỳ đâu)
        startService(Intent(this, FloatingService::class.java).apply {
            action = "ACTION_SHOW_STAR"
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
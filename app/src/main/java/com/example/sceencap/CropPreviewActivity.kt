package com.example.sceencap

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class CropPreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_preview)

        val imgPreview = findViewById<ImageView>(R.id.img_cropped_preview)

        // Lấy tấm ảnh cắt từ biến static
        val croppedBitmap = CropOverlayView.croppedBitmap
        if (croppedBitmap != null) {
            imgPreview.setImageBitmap(croppedBitmap)
        } else {
            // Không có ảnh thì đóng đi
            finish()
        }
    }
}
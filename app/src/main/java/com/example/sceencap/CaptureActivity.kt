package com.example.sceencap

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity // Đã đổi import ở đây
import androidx.activity.result.contract.ActivityResultContracts

// Đổi AppCompatActivity thành ComponentActivity
class CaptureActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // NẾU CHO PHÉP: Đóng gói "thẻ bài" và gửi về lại cho FloatingService
            val serviceIntent = Intent(this, FloatingService::class.java).apply {
                action = "ACTION_SAVE_TOKEN_AND_CAPTURE"
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("RESULT_DATA", result.data)
            }
            startService(serviceIntent)
        }
        // Dù cho phép hay từ chối, cũng tự động tắt màn hình tàng hình này đi
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // LÁ CHẮN CHỐNG LOOP
        if (savedInstanceState != null) {
            finish()
            return
        }

        // Gọi bảng thông báo xin quyền của hệ thống lên
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager.createScreenCaptureIntent()
        captureLauncher.launch(captureIntent)
    }
}
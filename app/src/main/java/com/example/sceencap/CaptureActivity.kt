package com.example.sceencap

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class CaptureActivity : ComponentActivity() {

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // CỜ SỐ 1: BÁO CÁO LẤY THẺ BÀI THÀNH CÔNG
            Toast.makeText(this, "🚩 Cờ 1: Lấy thẻ bài thành công!", Toast.LENGTH_SHORT).show()

            val serviceIntent = Intent(this, FloatingService::class.java).apply {
                action = "ACTION_SAVE_TOKEN_AND_CAPTURE"
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("RESULT_DATA", result.data)
            }
            startService(serviceIntent)
        } else {
            Toast.makeText(this, "❌ Lỗi: Bạn chưa cấp quyền hoặc thẻ bài bị rỗng", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            captureLauncher.launch(projectionManager.createScreenCaptureIntent())
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi mở bảng xin quyền: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
package com.example.sceencap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sceencap.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStartService = findViewById<Button>(R.id.btn_start_service)

        btnStartService.setOnClickListener {
            // Kiểm tra xem app đã được cấp quyền vẽ bậy lên màn hình chưa?
            if (!Settings.canDrawOverlays(this)) {
                // Nếu chưa: Mở trang cài đặt của hệ thống để người dùng cấp quyền
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "Vui lòng cấp quyền Hiển thị trên ứng dụng khác!", Toast.LENGTH_LONG).show()
            } else {
                // Nếu có quyền rồi: Khởi động FloatingService
                val serviceIntent = Intent(this, FloatingService::class.java)
                startService(serviceIntent)

                Toast.makeText(this, "Đã bật ngôi sao!", Toast.LENGTH_SHORT).show()

                // Tiện tay cho app thu nhỏ về màn hình chính (Home) để nhìn ngôi sao cho rõ
                finish()
            }
        }
    }
}
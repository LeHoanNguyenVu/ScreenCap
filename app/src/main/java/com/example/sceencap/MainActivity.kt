package com.example.sceencap

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- BƯỚC 1: XIN QUYỀN THÔNG BÁO CHO ANDROID 13+ (GIẢI MÃ XIAOMI) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Bung bảng hỏi: "SceenCap muốn gửi thông báo cho bạn"
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // --- BƯỚC 2: XIN QUYỀN HIỂN THỊ TRÊN ỨNG DỤNG KHÁC (ĐỂ GỌI NGÔI SAO) ---
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            // Mở trang cài đặt để người dùng cấp quyền
            startActivityForResult(intent, 102)
            Toast.makeText(this, "Vui lòng cấp quyền Hiển thị trên các ứng dụng khác", Toast.LENGTH_LONG).show()
        } else {
            // Nếu đã có đủ quyền rồi -> Bật ngôi sao luôn
            startFloatingService()
        }
    }

    // Lắng nghe kết quả sau khi người dùng đi từ trang Cài đặt về
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                Toast.makeText(this, "Bạn phải cấp quyền để ứng dụng hoạt động!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFloatingService() {
        val serviceIntent = Intent(this, FloatingService::class.java)
        // Kích hoạt Service chứa ngôi sao
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Tắt màn hình trắng này đi để chừa không gian cho ngôi sao lơ lửng
        finish()
    }
}
package com.example.sceencap

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class CropPreviewActivity : AppCompatActivity() {

    private var currentBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_preview)

        val imgPreview = findViewById<ImageView>(R.id.img_cropped_preview)
        val btnCancel = findViewById<Button>(R.id.btn_cancel)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnShare = findViewById<Button>(R.id.btn_share)

        currentBitmap = CropOverlayView.croppedBitmap

        if (currentBitmap != null) {
            imgPreview.setImageBitmap(currentBitmap)
        } else {
            finish()
            return
        }

        // NÚT HỦY: Hủy toàn bộ tiến trình chụp, quay về màn hình ứng dụng đang dùng dở
        btnCancel.setOnClickListener {
            finishHome()
        }

        // NÚT LƯU: Lưu thẳng vào Bộ sưu tập của máy
        btnSave.setOnClickListener {
            saveImageToGallery(currentBitmap!!)
        }

        // NÚT CHIA SẺ: Gửi ảnh qua Zalo, Messenger...
        btnShare.setOnClickListener {
            shareImage(currentBitmap!!)
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        try {
            val filename = "SceenCap_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                // Lưu vào thư mục Pictures/SceenCap (Chuẩn Android 10+)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SceenCap")
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Toast.makeText(this, "💾 Đã lưu vào Bộ sưu tập!", Toast.LENGTH_LONG).show()
                // Lưu xong thì tắt màn hình Preview, đưa người dùng về màn hình Home
                finishHome()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi khi lưu ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage(bitmap: Bitmap) {
        try {
            // Lưu tạm vào cache để share
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "share_image.png")
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()

            // Dùng FileProvider để tạo link an toàn (Phần này cần setup thêm xíu, tạm thời cứ để code ở đây)
            Toast.makeText(this, "Tính năng share cần setup FileProvider (Sẽ làm ở bước sau)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Hàm đóng toàn bộ tiến trình cắt và quay về màn hình nền của máy
    private fun finishHome() {
        // Đóng sạch mọi Activity của app SceenCap
        finishAffinity()
    }
}
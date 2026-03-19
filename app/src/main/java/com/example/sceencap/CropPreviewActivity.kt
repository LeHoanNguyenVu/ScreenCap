package com.example.sceencap

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream

class CropPreviewActivity : AppCompatActivity() {

    private var currentCroppedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_preview)

        val viewCropAdjust = findViewById<CropAdjustView>(R.id.view_crop_adjust)
        val btnCancel = findViewById<Button>(R.id.btn_cancel)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnShare = findViewById<Button>(R.id.btn_share)

        val btnScanText = findViewById<Button>(R.id.btn_scan_text)
        val layoutTextResult = findViewById<LinearLayout>(R.id.layout_text_result)
        val tvScannedText = findViewById<TextView>(R.id.tv_scanned_text)
        val btnCopyText = findViewById<Button>(R.id.btn_copy_text)

        // 2 Nhóm nút công cụ
        val layoutNormalActions = findViewById<LinearLayout>(R.id.layout_normal_actions)
        val layoutEditActions = findViewById<LinearLayout>(R.id.layout_edit_actions)
        val btnConfirmCrop = findViewById<Button>(R.id.btn_confirm_crop)

        // Lấy ảnh từ bước trước
        currentCroppedBitmap = CropOverlayView.croppedBitmap

        if (currentCroppedBitmap != null) {
            // Nạp ảnh vào Cỗ máy cắt
            viewCropAdjust.setBitmap(currentCroppedBitmap!!)
            startOcrFromBitmap(currentCroppedBitmap!!, layoutTextResult, tvScannedText, btnScanText)
        } else {
            finish()
            return
        }

        btnCancel.setOnClickListener { finishHome() }
        btnShare.setOnClickListener { shareImage(currentCroppedBitmap!!) }
        btnSave.setOnClickListener { saveImageToGallery(currentCroppedBitmap!!) }
        btnScanText.setOnClickListener { startOcrFromBitmap(currentCroppedBitmap!!, layoutTextResult, tvScannedText, btnScanText) }

        btnCopyText.setOnClickListener {
            val textToCopy = tvScannedText.text.toString()
            if (textToCopy.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("SceenCap_Text", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "📋 Đã copy!", Toast.LENGTH_SHORT).show()
                finishHome()
            }
        }

        // --- LUỒNG LOGIC QUAN TRỌNG CHỖ NÀY ---

        // 1. Lắng nghe Custom View: Khi người dùng CHẠM vào góc để edit
        viewCropAdjust.onCropAreaReleased = { _ ->
            // null mang ý nghĩa "đang chạm/kéo" -> Tụi mình chỉ quan tâm đến logic ẩn/hiện nút thôi
            runOnUiThread {
                layoutNormalActions.visibility = View.GONE
                layoutEditActions.visibility = View.VISIBLE
            }
        }

        // 2. KHI BẠN BẤM NÚT "XÁC NHẬN CẮT"
        btnConfirmCrop.setOnClickListener {
            // Gọi Custom View cắt thật lấy ảnh về
            val newBmp = viewCropAdjust.getCroppedBitmap()
            if (newBmp != null) {
                currentCroppedBitmap = newBmp

                // Ép khung nhìn tải lại bức ảnh mới (Nó sẽ tự phóng to lên y như app xịn)
                viewCropAdjust.setBitmap(newBmp)

                // Gọi AI quét lại chữ ngay
                startOcrFromBitmap(newBmp, layoutTextResult, tvScannedText, btnScanText)

                // Hiện lại các nút bình thường để user Copy hoặc Lưu
                layoutNormalActions.visibility = View.VISIBLE
                layoutEditActions.visibility = View.GONE
            }
        }
    }

    private fun startOcrFromBitmap(bitmap: Bitmap, layoutResult: LinearLayout, tvResult: TextView, btnScan: Button) {
        btnScan.isEnabled = false
        btnScan.text = "⏳..."

        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                btnScan.isEnabled = true
                btnScan.text = "📝 QUÉT"

                val resultText = visionText.text
                if (resultText.trim().isEmpty()) {
                    tvResult.text = ""
                    layoutResult.visibility = View.GONE
                } else {
                    layoutResult.visibility = View.VISIBLE
                    tvResult.text = resultText
                }
            }
            .addOnFailureListener { e ->
                btnScan.isEnabled = true
                btnScan.text = "📝 QUÉT"
                Log.e("SceenCap_OCR", "Failure: ${e.message}")
            }
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        try {
            val filename = "SceenCap_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SceenCap")
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Toast.makeText(this, "💾 Đã lưu!", Toast.LENGTH_SHORT).show()
                finishHome()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi lưu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage(bitmap: Bitmap) {
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "share_image.png")
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()
            Toast.makeText(this, "Tính năng share cần setup FileProvider", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun finishHome() {
        // 1. Đóng màn hình Cắt (CropActivity) đang nằm ẩn ở dưới
        CropActivity.instance?.finish()

        // 2. Tự đóng chính màn hình Preview này lại
        finish()
    }
}
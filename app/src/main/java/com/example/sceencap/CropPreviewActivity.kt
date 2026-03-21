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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream

class CropPreviewActivity : AppCompatActivity() {

    private var currentCroppedBitmap: Bitmap? = null

    private var rawOriginalText: String = ""
    private var rawTranslatedText: String = ""

    private lateinit var btnTranslate: Button
    private lateinit var btnCopyOriginal: Button
    private lateinit var btnCopyTranslated: Button
    private lateinit var tvScannedText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_preview)

        val viewCropAdjust = findViewById<CropAdjustView>(R.id.view_crop_adjust)
        val btnCancel = findViewById<Button>(R.id.btn_cancel)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnShare = findViewById<Button>(R.id.btn_share)

        val btnScanText = findViewById<Button>(R.id.btn_scan_text)
        val layoutTextResult = findViewById<LinearLayout>(R.id.layout_text_result)
        tvScannedText = findViewById(R.id.tv_scanned_text)

        btnTranslate = findViewById(R.id.btn_translate)
        btnCopyOriginal = findViewById(R.id.btn_copy_original)
        btnCopyTranslated = findViewById(R.id.btn_copy_translated)

        val layoutNormalActions = findViewById<LinearLayout>(R.id.layout_normal_actions)
        val layoutEditActions = findViewById<LinearLayout>(R.id.layout_edit_actions)
        val btnConfirmCrop = findViewById<Button>(R.id.btn_confirm_crop)

        currentCroppedBitmap = CropOverlayView.croppedBitmap

        if (currentCroppedBitmap != null) {
            viewCropAdjust.setBitmap(currentCroppedBitmap!!)
            // ĐÃ XÓA: Lệnh tự động quét chữ ở đây
        } else {
            finish()
            return
        }

        btnCancel.setOnClickListener { finishHome() }
        btnShare.setOnClickListener { shareImage(currentCroppedBitmap!!) }
        btnSave.setOnClickListener { saveImageToGallery(currentCroppedBitmap!!) }

        // --- CHỈ QUÉT KHI NÀO NGƯỜI DÙNG BẤM NÚT NÀY ---
        btnScanText.setOnClickListener {
            startOcrFromBitmap(currentCroppedBitmap!!, layoutTextResult, tvScannedText, btnScanText)
        }

        btnCopyOriginal.setOnClickListener {
            if (rawOriginalText.isNotEmpty()) {
                copyToClipboard(rawOriginalText, "📋 Đã copy bản gốc!")
            }
        }

        btnCopyTranslated.setOnClickListener {
            if (rawTranslatedText.isNotEmpty()) {
                copyToClipboard(rawTranslatedText, "🇻🇳 Đã copy bản dịch!")
            }
        }

        btnTranslate.setOnClickListener {
            if (rawOriginalText.isNotEmpty()) {
                translateText(rawOriginalText)
            }
        }

        // --- SỰ KIỆN CHẠM VÀO 4 GÓC ĐỂ CẮT LẠI ---
        viewCropAdjust.onCropAreaReleased = { _ ->
            runOnUiThread {
                layoutNormalActions.visibility = View.GONE
                layoutEditActions.visibility = View.VISIBLE
                // Giấu bảng chữ đi để màn hình thoáng đãng cho việc cắt ảnh
                layoutTextResult.visibility = View.GONE
            }
        }

        // --- SỰ KIỆN XÁC NHẬN CẮT LẠI ---
        btnConfirmCrop.setOnClickListener {
            val newBmp = viewCropAdjust.getCroppedBitmap()
            if (newBmp != null) {
                currentCroppedBitmap = newBmp
                viewCropAdjust.setBitmap(newBmp)

                // ĐÃ XÓA: Lệnh tự động quét chữ sau khi cắt lại
                // Màn hình sẽ chỉ hiện ảnh mới cắt và 4 nút menu

                layoutNormalActions.visibility = View.VISIBLE
                layoutEditActions.visibility = View.GONE
            }
        }
    }

    private fun copyToClipboard(text: String, toastMessage: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SceenCap_Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
        finishHome()
    }

    private fun startOcrFromBitmap(bitmap: Bitmap, layoutResult: LinearLayout, tvResult: TextView, btnScan: Button) {
        btnScan.isEnabled = false
        btnScan.text = "⏳..."

        btnTranslate.visibility = View.VISIBLE
        btnTranslate.text = "🌐 DỊCH (VI)"
        btnTranslate.isEnabled = true
        btnCopyTranslated.visibility = View.GONE

        rawOriginalText = ""
        rawTranslatedText = ""

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
                    Toast.makeText(this, "Không tìm thấy chữ nào trong ảnh!", Toast.LENGTH_SHORT).show()
                } else {
                    layoutResult.visibility = View.VISIBLE
                    rawOriginalText = resultText
                    tvResult.text = rawOriginalText
                }
            }
            .addOnFailureListener { e ->
                btnScan.isEnabled = true
                btnScan.text = "📝 QUÉT"
                Log.e("SceenCap_OCR", "Failure: ${e.message}")
            }
    }

    private fun translateText(textToTranslate: String) {
        btnTranslate.isEnabled = false
        btnTranslate.text = "⏳..."

        val languageIdentifier = LanguageIdentification.getClient()

        // Quét ra toàn bộ các ngôn ngữ có khả năng xuất hiện trong đoạn chữ
        languageIdentifier.identifyPossibleLanguages(textToTranslate)
            .addOnSuccessListener { identifiedLanguages ->

                // 1. KIỂM TRA NGẶT NGHÈO: Có dính dáng đến Tiếng Việt không?
                val hasVietnamese = identifiedLanguages.any { it.languageTag == TranslateLanguage.VIETNAMESE }

                if (hasVietnamese) {
                    // Nếu có Tiếng Việt -> Chặn ngay lập tức để bảo vệ UX
                    Toast.makeText(this, "Văn bản có lẫn Tiếng Việt, Hãy cắt sát vào vùng chữ ngoại ngữ nhé.", Toast.LENGTH_LONG).show()
                    resetTranslateButton()
                } else {
                    // 2. NẾU SẠCH SẼ (Không có Tiếng Việt) -> Tìm ngôn ngữ gốc để dịch
                    var sourceLangCode = identifiedLanguages.firstOrNull {
                        it.languageTag != "und"
                    }?.languageTag

                    if (sourceLangCode == null) {
                        sourceLangCode = TranslateLanguage.ENGLISH
                    }

                    // Chuyển hàng vào lò dịch
                    downloadModelAndTranslate(sourceLangCode, textToTranslate)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi nhận diện ngôn ngữ!", Toast.LENGTH_SHORT).show()
                resetTranslateButton()
            }
    }

    private fun downloadModelAndTranslate(sourceLangCode: String, textToTranslate: String) {
        btnTranslate.text = "⏳ Tải Data..."

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangCode)
            .setTargetLanguage(TranslateLanguage.VIETNAMESE)
            .build()
        val translator = Translation.getClient(options)

        val conditions = DownloadConditions.Builder().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                btnTranslate.text = "⏳ Đang dịch..."
                translator.translate(textToTranslate)
                    .addOnSuccessListener { translatedText ->
                        rawTranslatedText = translatedText
                        tvScannedText.append("\n\n--- Bản dịch (VI) ---\n$rawTranslatedText")

                        btnTranslate.visibility = View.GONE
                        btnCopyTranslated.visibility = View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Lỗi dịch thuật: ${e.message}", Toast.LENGTH_SHORT).show()
                        resetTranslateButton()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi tải bộ dịch. Vui lòng bật Mạng!: ${e.message}", Toast.LENGTH_LONG).show()
                resetTranslateButton()
            }
    }

    private fun resetTranslateButton() {
        btnTranslate.isEnabled = true
        btnTranslate.text = "🌐 DỊCH (VI)"
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

            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ ảnh qua..."))
            finishHome()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi chia sẻ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishHome() {
        CropActivity.instance?.finish()
        finish()
    }
}
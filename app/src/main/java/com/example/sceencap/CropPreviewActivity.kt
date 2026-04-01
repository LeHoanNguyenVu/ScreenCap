package com.example.sceencap

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import android.net.Uri
import android.app.AlertDialog

class CropPreviewActivity : AppCompatActivity() {

    private var currentCroppedBitmap: Bitmap? = null

    private var rawOriginalText: String = ""
    private var rawTranslatedText: String = ""

    private lateinit var btnTranslate: Button
    private lateinit var btnCopyOriginal: Button
    private lateinit var btnCopyTranslated: Button
    private lateinit var tvScannedText: TextView
    private lateinit var edtAiPrompt: EditText
    private lateinit var tvFuriganaWarning: TextView

    // ĐIỂM NHẤN UX: TẤM BẢNG HELP DESCRIPTION
    private lateinit var tvHelpDescription: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_preview)

        val viewCropAdjust = findViewById<CropAdjustView>(R.id.view_crop_adjust)
        val btnCancel = findViewById<Button>(R.id.btn_cancel)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnShare = findViewById<Button>(R.id.btn_share)
        val btnSearchImage = findViewById<Button>(R.id.btn_search_image)
        val btnScanQr = findViewById<Button>(R.id.btn_scan_qr)
        btnScanQr.setOnClickListener { scanQrBarcode(currentCroppedBitmap!!) }

        val btnAiEdit = findViewById<Button>(R.id.btn_ai_edit)
        val layoutAiEditDialog = findViewById<LinearLayout>(R.id.layout_ai_edit_dialog)
        edtAiPrompt = findViewById(R.id.edt_ai_prompt)
        val btnCancelAi = findViewById<Button>(R.id.btn_cancel_ai)
        val btnGoToAi = findViewById<Button>(R.id.btn_go_to_ai)

        val btnScanText = findViewById<Button>(R.id.btn_scan_text)
        val layoutTextResult = findViewById<LinearLayout>(R.id.layout_text_result)
        tvScannedText = findViewById(R.id.tv_scanned_text)
        tvFuriganaWarning = findViewById(R.id.tv_furigana_warning)

        btnTranslate = findViewById(R.id.btn_translate)
        btnCopyOriginal = findViewById(R.id.btn_copy_original)
        btnCopyTranslated = findViewById(R.id.btn_copy_translated)

        val layoutNormalActions = findViewById<LinearLayout>(R.id.layout_normal_actions)
        val layoutEditActions = findViewById<LinearLayout>(R.id.layout_edit_actions)
        val btnConfirmCrop = findViewById<Button>(R.id.btn_confirm_crop)

        // --- UX HELP PROMPT: ÁNH XẠ NÚT & BẢNG DESCRIPTION ---
        tvHelpDescription = findViewById(R.id.tv_help_description)
        val ibHelpCancel = findViewById<ImageButton>(R.id.ib_help_cancel)
        val ibHelpShare = findViewById<ImageButton>(R.id.ib_help_share)
        val ibHelpSearch = findViewById<ImageButton>(R.id.ib_help_search)
        val ibHelpAiEdit = findViewById<ImageButton>(R.id.ib_help_ai_edit)
        val ibHelpScan = findViewById<ImageButton>(R.id.ib_help_scan)
        val ibHelpSave = findViewById<ImageButton>(R.id.ib_help_save)

        currentCroppedBitmap = CropOverlayView.croppedBitmap

        if (currentCroppedBitmap != null) {
            viewCropAdjust.setBitmap(currentCroppedBitmap!!)
        } else {
            finish()
            return
        }

        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                edtAiPrompt.clearFocus()
                hideKeyboard(edtAiPrompt)
            }
            false
        }

        btnCancel.setOnClickListener { finishHome() }
        btnShare.setOnClickListener { shareImage(currentCroppedBitmap!!) }
        btnSave.setOnClickListener { saveImageToGallery(currentCroppedBitmap!!) }
        btnSearchImage.setOnClickListener { searchImageOnGoogle(currentCroppedBitmap!!) }

        btnAiEdit.setOnClickListener {
            layoutTextResult.visibility = View.GONE
            layoutAiEditDialog.visibility = View.VISIBLE
            tvHelpDescription.visibility = View.GONE
            edtAiPrompt.requestFocus()
            showKeyboard(edtAiPrompt)
        }

        btnCancelAi.setOnClickListener {
            layoutAiEditDialog.visibility = View.GONE
            hideKeyboard(edtAiPrompt)
            edtAiPrompt.clearFocus()
            edtAiPrompt.setText("")
        }

        btnGoToAi.setOnClickListener {
            val prompt = edtAiPrompt.text.toString().trim()
            if (prompt.isEmpty()) {
                Toast.makeText(this, "Hãy nhập ý tưởng của bạn trước nhé!", Toast.LENGTH_SHORT).show()
            } else {
                hideKeyboard(edtAiPrompt)
                edtAiPrompt.clearFocus()
                startAiWorkflow(currentCroppedBitmap!!, prompt)
            }
        }

        edtAiPrompt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(edtAiPrompt)
                edtAiPrompt.clearFocus()
                true
            } else {
                false
            }
        }

        fun toggleHelpDescription(buttonName: String, descriptionText: String) {
            val currentDesc = tvHelpDescription.text.toString()
            if (tvHelpDescription.visibility == View.VISIBLE && currentDesc == descriptionText) {
                tvHelpDescription.visibility = View.GONE
                tvHelpDescription.text = ""
            } else {
                tvHelpDescription.text = descriptionText
                tvHelpDescription.visibility = View.VISIBLE
            }
        }

        ibHelpCancel.setOnClickListener { toggleHelpDescription("Hủy", "❌ Hủy bỏ thay đổi và quay lại màn hình chụp ảnh.") }
        ibHelpShare.setOnClickListener { toggleHelpDescription("Share", "📤 Chia sẻ tấm ảnh này cho bạn bè qua các ứng dụng khác (Zalo, Facebook...).") }
        ibHelpSearch.setOnClickListener { toggleHelpDescription("Tìm", "🔍 Tìm kiếm thông tin, sản phẩm, địa điểm... có trong bức ảnh này qua Google Lens.") }
        ibHelpAiEdit.setOnClickListener { toggleHelpDescription("Sửa AI", "✨ Dùng sức mạnh AI (Gemini) để chỉnh sửa, biến đổi bức ảnh theo ý thích của bạn.") }
        ibHelpScan.setOnClickListener { toggleHelpDescription("Quét", "📝 Nhận diện chữ viết trong ảnh. Chữ sẽ được hiển thị ở bảng phía trên.") }
        ibHelpSave.setOnClickListener { toggleHelpDescription("Lưu", "💾 Lưu bức ảnh đã cắt này vào bộ sưu tập điện thoại của bạn.") }

        btnScanText.setOnClickListener {
            // NÂNG CẤP THÔNG MINH: Tự động quét không cần hỏi ngôn ngữ
            startSmartOcr(currentCroppedBitmap!!, layoutTextResult, tvScannedText, btnScanText)
        }

        btnCopyOriginal.setOnClickListener {
            if (rawOriginalText.isNotEmpty()) { copyToClipboard(rawOriginalText, "📋 Đã copy bản gốc!") }
        }

        btnCopyTranslated.setOnClickListener {
            if (rawTranslatedText.isNotEmpty()) { copyToClipboard(rawTranslatedText, "📋 Đã copy bản dịch!") }
        }

        btnTranslate.setOnClickListener {
            if (rawOriginalText.isNotEmpty()) {
                showTargetLanguageDialog(rawOriginalText)
            }
        }

        viewCropAdjust.onCropAreaReleased = { _ ->
            runOnUiThread {
                layoutNormalActions.visibility = View.GONE
                layoutEditActions.visibility = View.VISIBLE
                layoutTextResult.visibility = View.GONE
                layoutAiEditDialog.visibility = View.GONE
                tvHelpDescription.visibility = View.GONE
                hideKeyboard(edtAiPrompt)
                edtAiPrompt.clearFocus()
            }
        }

        btnConfirmCrop.setOnClickListener {
            val newBmp = viewCropAdjust.getCroppedBitmap()
            if (newBmp != null) {
                currentCroppedBitmap = newBmp
                viewCropAdjust.setBitmap(newBmp)
                layoutNormalActions.visibility = View.VISIBLE
                layoutEditActions.visibility = View.GONE
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    hideKeyboard(v)
                    v.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun startAiWorkflow(bitmap: Bitmap, prompt: CharSequence) {
        Toast.makeText(this, "✨ Đang chuyển dữ liệu sang Gemini...", Toast.LENGTH_SHORT).show()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SceenCap_Prompt", prompt)
        clipboard.setPrimaryClip(clip)
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val uniqueFileName = "ai_edit_input_${System.currentTimeMillis()}.jpg"
            val file = File(cachePath, uniqueFileName)
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
            fileOutputStream.close()
            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)
            val aiIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.google.android.apps.bard")
            }
            startActivity(aiIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Không tìm thấy App Gemini!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi AI: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String, toastMessage: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SceenCap_Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
    }

    private fun startSmartOcr(bitmap: Bitmap, layoutResult: LinearLayout, tvResult: TextView, btnScan: Button) {
        btnScan.isEnabled = false
        btnScan.text = "⏳..."
        btnTranslate.visibility = View.VISIBLE
        btnTranslate.text = "🌐 DỊCH"
        btnTranslate.isEnabled = true
        btnCopyTranslated.visibility = View.GONE
        rawOriginalText = ""
        rawTranslatedText = ""
        tvFuriganaWarning.visibility = View.GONE

        val image = InputImage.fromBitmap(bitmap, 0)
        
        // Chạy song song các bộ quét để lấy kết quả tốt nhất
        val recognizers = listOf(
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        )

        var completedCount = 0
        var bestText = ""

        recognizers.forEach { recognizer ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (visionText.text.length > bestText.length) {
                        bestText = visionText.text
                    }
                }
                .addOnCompleteListener {
                    completedCount++
                    if (completedCount == recognizers.size) {
                        runOnUiThread {
                            btnScan.isEnabled = true
                            btnScan.text = "📝 QUÉT"
                            if (bestText.trim().isEmpty()) {
                                layoutResult.visibility = View.GONE
                                Toast.makeText(this, "Không tìm thấy chữ nào trong ảnh!", Toast.LENGTH_SHORT).show()
                            } else {
                                layoutResult.visibility = View.VISIBLE
                                rawOriginalText = bestText
                                tvResult.text = rawOriginalText
                                
                                // Nếu có chữ tiếng Nhật, hiện cảnh báo Furigana cho chắc chắn
                                if (rawOriginalText.any { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF }) {
                                    tvFuriganaWarning.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun showTargetLanguageDialog(textToTranslate: String) {
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(textToTranslate)
            .addOnSuccessListener { sourceLangCode ->
                val finalSourceLang = if (sourceLangCode == "und") "en" else sourceLangCode
                
                val langMap = mapOf(
                    "en" to "🇺🇸 Tiếng Anh",
                    "vi" to "🇻🇳 Tiếng Việt",
                    "ja" to "🇯🇵 Tiếng Nhật",
                    "zh" to "🇨🇳 Tiếng Trung",
                    "ko" to "🇰🇷 Tiếng Hàn"
                )

                val targetLangs = langMap.filter { it.key != finalSourceLang }
                val displayNames = targetLangs.values.toTypedArray()
                val langCodes = targetLangs.keys.toTypedArray()

                AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Dịch sang ngôn ngữ nào?")
                    .setItems(displayNames) { _, which ->
                        val targetCode = langCodes[which]
                        downloadModelAndTranslate(finalSourceLang, targetCode, textToTranslate, displayNames[which])
                    }
                    .show()
            }
    }

    private fun downloadModelAndTranslate(sourceLang: String, targetLang: String, text: String, targetName: String) {
        btnTranslate.isEnabled = false
        btnTranslate.text = "⏳..."
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        rawTranslatedText = translatedText
                        tvScannedText.append("\n\n--- Bản dịch ($targetName) ---\n$rawTranslatedText")
                        btnTranslate.visibility = View.GONE
                        btnCopyTranslated.visibility = View.VISIBLE
                        btnTranslate.isEnabled = true
                        btnTranslate.text = "🌐 DỊCH"
                    }
                    .addOnFailureListener { resetTranslateButton() }
            }
            .addOnFailureListener { resetTranslateButton() }
    }

    private fun resetTranslateButton() {
        btnTranslate.isEnabled = true
        btnTranslate.text = "🌐 DỊCH"
        Toast.makeText(this, "Lỗi dịch thuật!", Toast.LENGTH_SHORT).show()
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
                contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                Toast.makeText(this, "💾 Đã lưu vào Thư viện!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi lưu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage(bitmap: Bitmap) {
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "share_${System.currentTimeMillis()}.png")
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()
            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Chia sẻ ảnh qua...")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi chia sẻ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchImageOnGoogle(bitmap: Bitmap) {
        Toast.makeText(this, "🔍 Đang chuyển sang Google...", Toast.LENGTH_SHORT).show()
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "search_${System.currentTimeMillis()}.png")
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()
            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)
            val searchIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.google.android.googlequicksearchbox")
            }
            startActivity(searchIntent)
        } catch (e: ActivityNotFoundException) {
            shareImage(bitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi tìm kiếm: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishHome() {
        CropActivity.instance?.finish()
        finish()
    }

    private fun showKeyboard(editText: EditText) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View? = this.currentFocus) {
        view?.let {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun scanQrBarcode(bitmap: Bitmap) {
        Toast.makeText(this, "🔍 Đang phân tích mã...", Toast.LENGTH_SHORT).show()
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isEmpty()) {
                    Toast.makeText(this, "Không tìm thấy mã QR nào trong ảnh!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                handleBarcodeResult(barcodes[0])
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi quét mã: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleBarcodeResult(barcode: Barcode) {
        val rawValue = barcode.rawValue ?: "Không đọc được dữ liệu"
        val valueType = barcode.valueType
        val dialogBuilder = AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
        dialogBuilder.setTitle("✨ Đã tìm thấy mã!")
        when (valueType) {
            Barcode.TYPE_URL -> {
                val url = barcode.url?.url ?: rawValue
                dialogBuilder.setMessage("🔗 Liên kết Web:\n$url")
                dialogBuilder.setPositiveButton("MỞ TRÌNH DUYỆT") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            Barcode.TYPE_WIFI -> {
                val ssid = barcode.wifi?.ssid ?: "Không rõ"
                val password = barcode.wifi?.password ?: "Không có pass"
                dialogBuilder.setMessage("📶 Mạng Wi-Fi: $ssid\n🔑 Mật khẩu: $password")
                dialogBuilder.setPositiveButton("COPY PASS") { _, _ ->
                    copyToClipboard(password, "Đã copy mật khẩu Wi-Fi!")
                }
            }
            else -> {
                dialogBuilder.setMessage("📄 Nội dung:\n$rawValue")
                dialogBuilder.setPositiveButton("TÌM GOOGLE") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$rawValue")))
                }
            }
        }
        dialogBuilder.setNeutralButton("COPY TẤT CẢ") { _, _ ->
            copyToClipboard(rawValue, "Đã copy toàn bộ nội dung mã!")
        }
        dialogBuilder.setNegativeButton("ĐÓNG", null)
        dialogBuilder.show()
    }
}
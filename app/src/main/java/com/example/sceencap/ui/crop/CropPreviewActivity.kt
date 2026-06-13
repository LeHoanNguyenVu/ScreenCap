package com.example.sceencap.ui.crop

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
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.example.sceencap.ui.scanner.BankTransferActivity
import com.example.sceencap.R
import com.example.sceencap.core.engine.TranslationEngine
import com.example.sceencap.ui.floating.FloatingService
import com.example.sceencap.ui.translation.LanguageBottomSheetFragment
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import android.net.Uri
import android.app.AlertDialog
import java.io.File
import java.io.FileOutputStream

class CropPreviewActivity : AppCompatActivity() {

    private var currentCroppedBitmap: Bitmap? = null

    private var rawOriginalText: String = ""
    private var rawTranslatedText: String = ""
    private var detectedSourceLangCode: String = "und"

    private lateinit var btnScanText: View
    private lateinit var btnCopyOriginal: Button
    private lateinit var btnCopyTranslated: Button
    private lateinit var btnRetranslate: Button
    private lateinit var tvScannedText: TextView
    private lateinit var tvFuriganaWarning: TextView
    private lateinit var tvHelpDescription: TextView
    private lateinit var layoutTextResult: LinearLayout

    private lateinit var translationEngine: TranslationEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_preview)

        sendServiceAction("ACTION_HIDE_STAR")

        translationEngine = TranslationEngine(this)
        cleanOldCaches()

        val viewCropAdjust = findViewById<CropAdjustView>(R.id.view_crop_adjust)
        val btnCancel = findViewById<View>(R.id.btn_cancel)
        val btnSave = findViewById<View>(R.id.btn_save)
        val btnShare = findViewById<View>(R.id.btn_share)
        val btnSearchImage = findViewById<View>(R.id.btn_search_image)
        val btnScanQr = findViewById<View>(R.id.btn_scan_qr)
        btnScanQr.setOnClickListener { scanQrBarcode(currentCroppedBitmap!!) }

        val btnCopyImage = findViewById<View>(R.id.btn_copy_image)

        val btnAiEdit = findViewById<View>(R.id.btn_ai_edit)
        val layoutAiEditDialog = findViewById<LinearLayout>(R.id.layout_ai_edit_dialog)
        val edtAiPrompt = findViewById<EditText>(R.id.edt_ai_prompt)
        val btnCancelAi = findViewById<Button>(R.id.btn_cancel_ai)
        val btnGoToAi = findViewById<Button>(R.id.btn_go_to_ai)

        btnScanText = findViewById(R.id.btn_scan_text)
        layoutTextResult = findViewById(R.id.layout_text_result)
        tvScannedText = findViewById(R.id.tv_scanned_text)
        tvFuriganaWarning = findViewById(R.id.tv_furigana_warning)

        btnCopyOriginal = findViewById(R.id.btn_copy_original)
        btnCopyTranslated = findViewById(R.id.btn_copy_translated)
        btnRetranslate = findViewById(R.id.btn_retranslate)

        val layoutNormalActions = findViewById<android.widget.GridLayout>(R.id.layout_normal_actions)
        val layoutEditActions = findViewById<LinearLayout>(R.id.layout_edit_actions)
        val btnConfirmCrop = findViewById<Button>(R.id.btn_confirm_crop)

        tvHelpDescription = findViewById(R.id.tv_help_description)
        // Các biến ibHelp không cần thiết nữa (đã dùng long-press trên tile)

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
        btnCopyImage.setOnClickListener { copyImageToClipboard(currentCroppedBitmap!!) }

        btnAiEdit.setOnClickListener {
            if (!isPackageInstalled("com.google.android.apps.bard", packageManager)) {
                AlertDialog.Builder(this)
                    .setTitle("Cần ứng dụng Gemini")
                    .setMessage("Để sử dụng tính năng sửa ảnh bằng AI miễn phí, bạn cần cài đặt ứng dụng Google Gemini. Bạn có muốn tải về từ CH Play không?")
                    .setPositiveButton("Tải về") { _, _ ->
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.bard")))
                        } catch (e: ActivityNotFoundException) {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.bard")))
                        }
                    }
                    .setNegativeButton("Bỏ qua", null)
                    .show()
                return@setOnClickListener
            }

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

        fun toggleHelpDescription(descriptionText: String) {
            val currentDesc = tvHelpDescription.text.toString()
            if (tvHelpDescription.visibility == View.VISIBLE && currentDesc == descriptionText) {
                tvHelpDescription.visibility = View.GONE
                tvHelpDescription.text = ""
            } else {
                tvHelpDescription.text = descriptionText
                tvHelpDescription.visibility = View.VISIBLE
            }
        }

        // Trợ giúp: giữ lâu vào tile để xem mô tả
        btnCancel.setOnLongClickListener { toggleHelpDescription("Hủy bỏ thay đổi và quay về màn hình chụp ảnh."); true }
        btnShare.setOnLongClickListener { toggleHelpDescription("Chia sẻ tấm ảnh này cho bạn bè qua Zalo, Facebook..."); true }
        btnSearchImage.setOnLongClickListener { toggleHelpDescription("Tìm kiếm sản phẩm, địa điểm... trong bức ảnh qua Google Lens."); true }
        btnAiEdit.setOnLongClickListener { toggleHelpDescription("Dùng AI (Gemini) để chỉnh sửa, biến đổi bức ảnh theo ý thích."); true }
        btnScanText.setOnLongClickListener { toggleHelpDescription("Nhận diện chữ và dịch sang ngôn ngữ bạn chọn (Gemini AI + Offline)."); true }
        btnSave.setOnLongClickListener { toggleHelpDescription("Lưu bức ảnh đã cắt vào bộ sưu tập điện thoại."); true }
        btnCopyImage.setOnLongClickListener { toggleHelpDescription("Sao chép ảnh đã cắt vào bộ nhớ tạm để có thể dán (paste) trực tiếp."); true }

        // ---- NÚT DỊCH TỔNG HỢP (OCR + Chọn ngôn ngữ + Dịch) ----
        btnScanText.setOnClickListener {
            showLanguagePickerAndTranslate()
        }

        // ---- NÚT ĐỔI NGÔN NGỮ (dịch lại với ngôn ngữ khác) ----
        btnRetranslate.setOnClickListener {
            showLanguagePickerAndTranslate()
        }

        btnCopyOriginal.setOnClickListener {
            if (rawOriginalText.isNotEmpty()) copyToClipboard(rawOriginalText, "📋 Đã copy bản gốc!")
        }

        btnCopyTranslated.setOnClickListener {
            if (rawTranslatedText.isNotEmpty()) copyToClipboard(rawTranslatedText, "📋 Đã copy bản dịch!")
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
                // Reset lại kết quả dịch khi cắt ảnh mới
                resetTranslationResult()
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

    // =========================================================================
    // LUỒNG CHÍNH: Hiện bảng chọn ngôn ngữ → OCR → Dịch
    // =========================================================================

    private fun showLanguagePickerAndTranslate() {
        val bottomSheet = LanguageBottomSheetFragment()
        bottomSheet.onLanguageSelected = { targetCode, targetName ->
            // User đã chọn ngôn ngữ đích → bắt đầu xử lý
            performOcrThenTranslate(currentCroppedBitmap!!, targetCode, targetName)
        }
        bottomSheet.show(supportFragmentManager, "LanguagePicker")
    }

    /**
     * Bước 1: Chạy OCR thông minh (song song 4 recognizer)
     * Bước 2: Nhận diện ngôn ngữ nguồn
     * Bước 3: Gọi TranslationEngine (Gemini hoặc ML Kit fallback)
     */
    private fun performOcrThenTranslate(bitmap: Bitmap, targetCode: String, targetName: String) {
        // Hiện loading
        btnScanText.isEnabled = false
        val tvLabel = btnScanText.findViewById<TextView>(R.id.tv_label_scan)
        if (tvLabel != null) tvLabel.text = "Đang quét..."
        btnRetranslate.visibility = View.GONE
        btnCopyTranslated.visibility = View.GONE
        rawOriginalText = ""
        rawTranslatedText = ""
        tvFuriganaWarning.visibility = View.GONE
        layoutTextResult.visibility = View.GONE

        val image = InputImage.fromBitmap(bitmap, 0)

        // Chạy song song 4 bộ recognizer để lấy kết quả tốt nhất
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
                            if (bestText.trim().isEmpty()) {
                                // Không tìm thấy chữ trong ảnh
                                resetScanButton()
                                Toast.makeText(this, "Không tìm thấy chữ nào trong ảnh!", Toast.LENGTH_SHORT).show()
                            } else {
                                rawOriginalText = bestText
                                // Bước 2: Nhận diện ngôn ngữ nguồn rồi dịch
                                detectLanguageAndTranslate(rawOriginalText, targetCode, targetName)
                            }
                        }
                    }
                }
        }
    }

    private fun detectLanguageAndTranslate(text: String, targetCode: String, targetName: String) {
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { sourceLangCode ->
                detectedSourceLangCode = if (sourceLangCode == "und") "en" else sourceLangCode
                Log.d("CropPreview", "Ngôn ngữ nguồn phát hiện: $detectedSourceLangCode")
                // Bước 3: Gọi engine dịch
                startTranslation(text, detectedSourceLangCode, targetCode, targetName)
            }
            .addOnFailureListener {
                // Nếu không nhận diện được, mặc định là tiếng Anh
                detectedSourceLangCode = "en"
                startTranslation(text, detectedSourceLangCode, targetCode, targetName)
            }
    }

    private fun startTranslation(text: String, sourceLang: String, targetCode: String, targetName: String) {
        val tvLabel = btnScanText.findViewById<TextView>(R.id.tv_label_scan)
        if (tvLabel != null) tvLabel.text = "Đang dịch..."

        translationEngine.translate(
            text = text,
            sourceLangCode = sourceLang,
            targetLangCode = targetCode,
            targetLangName = targetName,
            onSuccess = { translatedText, usedGemini ->
                rawTranslatedText = translatedText
                displayTranslationResult(text, translatedText, targetName, usedGemini)
            },
            onError = { errorMessage ->
                resetScanButton()
                Toast.makeText(this, "❌ $errorMessage", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun displayTranslationResult(
        originalText: String,
        translatedText: String,
        targetName: String,
        usedGemini: Boolean
    ) {
        val engineLabel = if (usedGemini) "✨ Gemini AI" else "📴 Offline"
        val displayText = "📄 Bản gốc:\n$originalText\n\n🌐 Dịch sang $targetName ($engineLabel):\n$translatedText"

        tvScannedText.text = displayText
        layoutTextResult.visibility = View.VISIBLE

        // Hiện cảnh báo Furigana nếu có chữ Nhật
        if (originalText.any { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF }) {
            tvFuriganaWarning.visibility = View.VISIBLE
        }

        // Hiện các nút hành động
        btnCopyOriginal.visibility = View.VISIBLE
        btnCopyTranslated.visibility = View.VISIBLE
        btnRetranslate.visibility = View.VISIBLE

        resetScanButton()
    }

    private fun resetScanButton() {
        btnScanText.isEnabled = true
        val tvLabel = btnScanText.findViewById<TextView>(R.id.tv_label_scan)
        if (tvLabel != null) tvLabel.text = "Dịch"
    }

    private fun resetTranslationResult() {
        rawOriginalText = ""
        rawTranslatedText = ""
        detectedSourceLangCode = "und"
        layoutTextResult.visibility = View.GONE
        btnCopyTranslated.visibility = View.GONE
        btnRetranslate.visibility = View.GONE
        tvFuriganaWarning.visibility = View.GONE
        resetScanButton()
    }

    // =========================================================================
    // Các hàm tiện ích
    // =========================================================================

    private fun startAiWorkflow(bitmap: Bitmap, prompt: CharSequence) {
        Toast.makeText(this, "✨ Đang chuyển dữ liệu sang Gemini...", Toast.LENGTH_SHORT).show()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ScreenCap_Prompt", prompt)
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
        val clip = ClipData.newPlainText("ScreenCap_Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
    }

    private fun copyImageToClipboard(bitmap: Bitmap) {
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "copy_${System.currentTimeMillis()}.png")
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()
            
            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)
            
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newUri(contentResolver, "ScreenCap_Image", uri)
            clipboard.setPrimaryClip(clipData)
            
            Toast.makeText(this, "📋 Đã copy ảnh vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi copy ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, FloatingService::class.java)
        intent.action = action
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Hiện lại floating widget khi rời màn hình nghiệp vụ
        sendServiceAction("ACTION_SHOW_STAR")
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

        // Kiểm tra xem có phải mã VietQR chuyển khoản hay không
        val isVietQR = rawValue.startsWith("000201") && rawValue.contains("38")
        if (isVietQR) {
            val intent = Intent(this, BankTransferActivity::class.java)
            intent.putExtra("RAW_QR", rawValue)
            startActivity(intent)
            return
        }

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_qr_result, null)
        dialog.setContentView(view)

        // Make BottomSheet background transparent to show rounded corners
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val tvTitle = view.findViewById<TextView>(R.id.tv_qr_title)
        val tvContent = view.findViewById<TextView>(R.id.tv_qr_content)
        val btnPositive = view.findViewById<Button>(R.id.btn_positive)
        val btnNeutral = view.findViewById<Button>(R.id.btn_neutral)
        val btnNegative = view.findViewById<Button>(R.id.btn_negative)

        val vietQRDesc = getVietQRDescription(rawValue)

        if (vietQRDesc != null) {
            tvTitle.text = "Mã Chuyển Khoản VietQR"
            tvContent.text = vietQRDesc + "\n👉 Nhấn 'SAO CHÉP THANH TOÁN' rồi mở ứng dụng Ngân hàng (hoặc Ví MoMo, ZaloPay) để tự động thanh toán."
            btnPositive.text = "SAO CHÉP THANH TOÁN"
            btnPositive.setOnClickListener {
                copyToClipboard(rawValue, "📋 Đã copy mã VietQR! Hãy mở app Ngân hàng của bạn để thanh toán.")
                dialog.dismiss()
            }
        } else if (valueType == Barcode.TYPE_URL || isUrl(rawValue)) {
            val url = if (valueType == Barcode.TYPE_URL) (barcode.url?.url ?: rawValue) else rawValue
            val formattedUrl = formatUrl(url)
            tvTitle.text = "Liên kết Web"
            tvContent.text = "🔗 URL tìm thấy:\n$formattedUrl"
            btnPositive.text = "MỞ TRÌNH DUYỆT"
            btnPositive.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Không thể mở liên kết: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        } else {
            when (valueType) {
                Barcode.TYPE_WIFI -> {
                    val ssid = barcode.wifi?.ssid ?: "Không rõ"
                    val password = barcode.wifi?.password ?: "Không có pass"
                    tvTitle.text = "Kết nối Wi-Fi"
                    tvContent.text = "📶 Mạng Wi-Fi: $ssid\n🔑 Mật khẩu: $password"
                    btnPositive.text = "COPY MẬT KHẨU"
                    btnPositive.setOnClickListener {
                        copyToClipboard(password, "Đã copy mật khẩu Wi-Fi!")
                        dialog.dismiss()
                    }
                }
                else -> {
                    tvTitle.text = "Nội dung văn bản"
                    tvContent.text = "📄 Dữ liệu quét được:\n$rawValue"
                    btnPositive.text = "TÌM KIẾM GOOGLE"
                    btnPositive.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$rawValue")))
                        dialog.dismiss()
                    }
                }
            }
        }

        btnNeutral.text = "Sao chép tất cả"
        btnNeutral.setOnClickListener {
            copyToClipboard(rawValue, "Đã copy toàn bộ nội dung mã!")
            dialog.dismiss()
        }

        btnNegative.text = "Đóng"
        btnNegative.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun parseEMVCo(qr: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index + 4 <= qr.length) {
            val tag = qr.substring(index, index + 2)
            val lengthStr = qr.substring(index + 2, index + 4)
            val length = lengthStr.toIntOrNull() ?: break
            index += 4
            if (index + length > qr.length) break
            val value = qr.substring(index, index + length)
            result[tag] = value
            index += length
        }
        return result
    }

    private fun getBankNameByBin(bin: String): String {
        return when (bin) {
            "970436" -> "Vietcombank (VCB)"
            "970415" -> "VietinBank"
            "970418" -> "BIDV"
            "970405" -> "Agribank"
            "970407" -> "Techcombank (TCB)"
            "970416" -> "ACB"
            "970422" -> "MBBank (MB)"
            "970432" -> "VPBank"
            "970403" -> "Sacombank"
            "970423" -> "TPBank"
            "970441" -> "VIB"
            "970429" -> "SCB"
            "970443" -> "SHB"
            "970428" -> "Nam A Bank"
            "970437" -> "HDBank"
            "970454" -> "MSB"
            "970448" -> "OCB"
            "970439" -> "Shinhan Bank"
            else -> "Ngân hàng liên kết (BIN: $bin)"
        }
    }

    private fun getVietQRDescription(qr: String): String? {
        if (!qr.startsWith("000201")) return null
        try {
            val mainTags = parseEMVCo(qr)
            val merchantInfoStr = mainTags["38"] ?: return null
            val merchantTags = parseEMVCo(merchantInfoStr)

            val bankInfoStr = merchantTags["01"] ?: return null
            val bankTags = parseEMVCo(bankInfoStr)

            val bin = bankTags["00"] ?: ""
            val accountNumber = bankTags["01"] ?: ""

            if (bin.isEmpty() || accountNumber.isEmpty()) return null

            val bankName = getBankNameByBin(bin)

            val amount = mainTags["54"]?.toDoubleOrNull()
            val formattedAmount = if (amount != null) {
                val formatter = java.text.DecimalFormat("#,###")
                formatter.format(amount) + " VND"
            } else {
                "Người nhận tự nhập"
            }

            var description = ""
            val additionalDataStr = mainTags["62"]
            if (additionalDataStr != null) {
                val additionalTags = parseEMVCo(additionalDataStr)
                description = additionalTags["08"] ?: ""
            }

            val sb = java.lang.StringBuilder()
            sb.append("🏦 Ngân hàng: $bankName\n")
            sb.append("💳 Số tài khoản: $accountNumber\n")
            sb.append("💰 Số tiền chuyển: $formattedAmount\n")
            if (description.isNotEmpty()) {
                sb.append("📝 Nội dung: $description\n")
            }
            return sb.toString()
        } catch (e: Exception) {
            return null
        }
    }

    private fun isUrl(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) return true
        return android.util.Patterns.WEB_URL.matcher(text).matches()
    }

    private fun formatUrl(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.lowercase().startsWith("http://") && !trimmed.lowercase().startsWith("https://")) {
            return "https://$trimmed"
        }
        return trimmed
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val filename = "ScreenCap_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScreenCap")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            Toast.makeText(this, "💾 Đã lưu vào Thư viện!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Lỗi lưu ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(packageName: String, packageManager: android.content.pm.PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun cleanOldCaches() {
        try {
            val cachePath = File(cacheDir, "images")
            if (cachePath.exists()) {
                val files = cachePath.listFiles()
                if (files != null) {
                    for (file in files) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CropPreviewActivity", "Error cleaning old caches: ${e.message}")
        }
    }
}

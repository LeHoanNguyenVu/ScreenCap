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

        // --- UX HELP PROMPT: ÁNH XẠ NÚT & BẢNG DESCRIPTION (MỚI) ---
        tvHelpDescription = findViewById(R.id.tv_help_description)
        val ibHelpCancel = findViewById<ImageButton>(R.id.ib_help_cancel)
        val ibHelpShare = findViewById<ImageButton>(R.id.ib_help_share)
        val ibHelpSearch = findViewById<ImageButton>(R.id.ib_help_search)
        val ibHelpAiEdit = findViewById<ImageButton>(R.id.ib_help_ai_edit)
        val ibHelpScan = findViewById<ImageButton>(R.id.ib_help_scan)
        val ibHelpSave = findViewById<ImageButton>(R.id.ib_help_save)
        // --------------------------------------------------------

        currentCroppedBitmap = CropOverlayView.croppedBitmap

        if (currentCroppedBitmap != null) {
            viewCropAdjust.setBitmap(currentCroppedBitmap!!)
        } else {
            finish()
            return
        }

        // Bắt sự kiện chạm vào màn hình gốc để tắt bàn phím
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
            // Ẩn bảng Help Description khi vào AI
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

        // --- UX HELP PROMPT: LOGIC THU PHÁT THANH ĐIỀU KHIỂN BẢNG (MỚI) ---
        // Hàm Quản lý việc ẩn/hiện và thay đổi nội dung của tấm bảng description
        fun toggleHelpDescription(buttonName: String, descriptionText: String) {
            val currentDesc = tvHelpDescription.text.toString()
            if (tvHelpDescription.visibility == View.VISIBLE && currentDesc == descriptionText) {
                // Nếu đang nói đúng cái đó -> Im lặng
                tvHelpDescription.visibility = View.GONE
                tvHelpDescription.text = ""
            } else {
                // Nếu đang im lặng hoặc đang nói cái khác -> Chuyển đài phát thanh sang cái mới
                tvHelpDescription.text = descriptionText
                tvHelpDescription.visibility = View.VISIBLE
            }
        }

        // SỰ KIỆN: User bấm vào các icon chấm hỏi nhỏ xinh
        ibHelpCancel.setOnClickListener { toggleHelpDescription("Hủy", "❌ Hủy bỏ thay đổi và quay lại màn hình chụp ảnh.") }
        ibHelpShare.setOnClickListener { toggleHelpDescription("Share", "📤 Chia sẻ tấm ảnh này cho bạn bè qua các ứng dụng khác (Zalo, Facebook...).") }
        ibHelpSearch.setOnClickListener { toggleHelpDescription("Tìm", "🔍 Tìm kiếm thông tin, sản phẩm, địa điểm... có trong bức ảnh này qua Google Lens.") }
        ibHelpAiEdit.setOnClickListener { toggleHelpDescription("Sửa AI", "✨ Dùng sức mạnh AI (Gemini) để chỉnh sửa, biến đổi bức ảnh theo ý thích của bạn.") }
        ibHelpScan.setOnClickListener { toggleHelpDescription("Quét", "📝 Nhận diện chữ viết trong ảnh. Chữ sẽ được hiển thị ở bảng phía trên.") }
        ibHelpSave.setOnClickListener { toggleHelpDescription("Lưu", "💾 Lưu bức ảnh đã cắt này vào bộ sưu tập điện thoại của bạn.") }
        // ----------------------------------------------------------------

        btnScanText.setOnClickListener {
            val languages = arrayOf("🇺🇸 Tiếng Anh / Latinh", "🇯🇵 Tiếng Nhật", "🇨🇳 Tiếng Trung", "🇰🇷 Tiếng Hàn")
            android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Chọn ngôn ngữ văn bản trong ảnh:")
                .setItems(languages) { _, which ->
                    startOcrFromBitmap(currentCroppedBitmap!!, layoutTextResult, tvScannedText, btnScanText, which)
                }
                .show()
        }

        btnCopyOriginal.setOnClickListener {
            if (rawOriginalText.isNotEmpty()) { copyToClipboard(rawOriginalText, "📋 Đã copy bản gốc!") }
        }

        btnCopyTranslated.setOnClickListener {
            if (rawTranslatedText.isNotEmpty()) { copyToClipboard(rawTranslatedText, "🇻🇳 Đã copy bản dịch!") }
        }

        btnTranslate.setOnClickListener {
            if (rawOriginalText.isNotEmpty()) { translateText(rawOriginalText) }
        }

        viewCropAdjust.onCropAreaReleased = { _ ->
            runOnUiThread {
                layoutNormalActions.visibility = View.GONE
                layoutEditActions.visibility = View.VISIBLE
                layoutTextResult.visibility = View.GONE
                layoutAiEditDialog.visibility = View.GONE
                tvHelpDescription.visibility = View.GONE // Ẩn Help Description khi đang cắt lại
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

            cachePath.listFiles()?.forEach { file ->
                if (file.name.startsWith("ai_edit_input")) {
                    file.delete()
                }
            }

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
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.google.android.apps.bard")
            }

            startActivity(aiIntent)
            finishHome()

        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Không tìm thấy App Gemini! Vui lòng cài Gemini nhé.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi AI: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String, toastMessage: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SceenCap_Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
        finishHome()
    }

    private fun startOcrFromBitmap(bitmap: Bitmap, layoutResult: LinearLayout, tvResult: TextView, btnScan: Button, languageMode: Int) {
        btnScan.isEnabled = false
        btnScan.text = "⏳..."

        btnTranslate.visibility = View.VISIBLE
        btnTranslate.text = "🌐 DỊCH (VI)"
        btnTranslate.isEnabled = true
        btnCopyTranslated.visibility = View.GONE

        rawOriginalText = ""
        rawTranslatedText = ""

        if (languageMode == 1) {
            tvFuriganaWarning.visibility = View.VISIBLE
        } else {
            tvFuriganaWarning.visibility = View.GONE
        }

        val image = InputImage.fromBitmap(bitmap, 0)

        val recognizerOptions = when (languageMode) {
            1 -> JapaneseTextRecognizerOptions.Builder().build()
            2 -> ChineseTextRecognizerOptions.Builder().build()
            3 -> KoreanTextRecognizerOptions.Builder().build()
            else -> TextRecognizerOptions.DEFAULT_OPTIONS // Mặc định là Tiếng Anh/Latinh
        }
        val recognizer = TextRecognition.getClient(recognizerOptions)

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
                Toast.makeText(this, "Lỗi quét chữ: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("SceenCap_OCR", "Failure: ${e.message}")
            }
    }

    private fun translateText(textToTranslate: String) {
        btnTranslate.isEnabled = false
        btnTranslate.text = "⏳..."
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyPossibleLanguages(textToTranslate)
            .addOnSuccessListener { identifiedLanguages ->
                val hasVietnamese = identifiedLanguages.any { it.languageTag == TranslateLanguage.VIETNAMESE }
                if (hasVietnamese) {
                    Toast.makeText(this, "Văn bản có lẫn Tiếng Việt, dịch thất bại! Hãy cắt sát vào vùng chữ ngoại ngữ nhé.", Toast.LENGTH_LONG).show()
                    resetTranslateButton()
                } else {
                    var sourceLangCode = identifiedLanguages.firstOrNull { it.languageTag != "und" }?.languageTag
                    if (sourceLangCode == null) { sourceLangCode = TranslateLanguage.ENGLISH }
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
        val options = TranslatorOptions.Builder().setSourceLanguage(sourceLangCode).setTargetLanguage(TranslateLanguage.VIETNAMESE).build()
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
                    .addOnFailureListener { resetTranslateButton() }
            }
            .addOnFailureListener { resetTranslateButton() }
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
                contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                Toast.makeText(this, "💾 Đã lưu!", Toast.LENGTH_SHORT).show()
                finishHome()
            }
        } catch (e: Exception) {
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
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ ảnh qua..."))
            finishHome()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi chia sẻ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchImageOnGoogle(bitmap: Bitmap) {
        Toast.makeText(this, "🔍 Đang chuyển sang Google...", Toast.LENGTH_SHORT).show()
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "search_image.png")
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()
            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)
            val searchIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.google.android.googlequicksearchbox")
            }
            startActivity(searchIntent)
            finishHome()
        } catch (e: ActivityNotFoundException) {
            shareImage(bitmap)
        } catch (e: Exception) {}
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

        // Cấu hình quét tất cả các định dạng mã trên đời
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()

        val scanner = BarcodeScanning.getClient(options)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isEmpty()) {
                    Toast.makeText(this, "Không tìm thấy mã QR/Barcode nào trong ảnh!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                // Chỉ lấy mã đầu tiên tìm thấy để xử lý
                val barcode = barcodes[0]
                handleBarcodeResult(barcode)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi quét mã: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleBarcodeResult(barcode: Barcode) {
        val rawValue = barcode.rawValue ?: "Không đọc được dữ liệu"
        val valueType = barcode.valueType

        val dialogBuilder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        dialogBuilder.setTitle("Kết quả quét mã:")

        // Xử lý Smart Actions dựa trên loại dữ liệu
        when (valueType) {
            Barcode.TYPE_URL -> {
                val url = barcode.url?.url ?: rawValue
                dialogBuilder.setMessage("🔗 Liên kết Web:\n$url")
                dialogBuilder.setPositiveButton("MỞ TRÌNH DUYỆT") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
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
                // Mã vạch sản phẩm (EAN/UPC) hoặc đoạn text bình thường chứa trong QR
                dialogBuilder.setMessage("📄 Nội dung:\n$rawValue")
                dialogBuilder.setPositiveButton("TÌM GOOGLE") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$rawValue"))
                    startActivity(intent)
                }
            }
        }

        // Nút chung cho mọi trường hợp
        dialogBuilder.setNeutralButton("COPY TẤT CẢ") { _, _ ->
            copyToClipboard(rawValue, "Đã copy toàn bộ nội dung mã!")
        }
        dialogBuilder.setNegativeButton("ĐÓNG", null)
        dialogBuilder.show()
    }
}
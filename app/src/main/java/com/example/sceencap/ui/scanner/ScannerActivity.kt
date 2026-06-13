package com.example.sceencap.ui.scanner

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.Toast
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sceencap.R
import com.example.sceencap.ui.floating.FloatingService
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scanLine: View
    private lateinit var btnFlash: ImageButton

    private var camera: Camera? = null
    private var isFlashOn = false
    private var isScanning = true

    // Tạo 1 lần, reuse mỗi frame — tránh tạo mới mỗi processImageProxy
    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        viewFinder = findViewById(R.id.viewFinder)
        scanLine = findViewById(R.id.scan_line)
        btnFlash = findViewById(R.id.btn_flash)
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<ImageButton>(R.id.btn_close_camera).setOnClickListener {
            finish()
        }

        btnFlash.setOnClickListener {
            toggleFlash()
        }

        startScanAnimation()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    private fun toggleFlash() {
        if (camera != null && camera!!.cameraInfo.hasFlashUnit()) {
            isFlashOn = !isFlashOn
            camera!!.cameraControl.enableTorch(isFlashOn)

            if (isFlashOn) {
                btnFlash.setImageResource(android.R.drawable.ic_lock_power_off)
                btnFlash.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            } else {
                btnFlash.setImageResource(android.R.drawable.ic_lock_power_off)
                btnFlash.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        } else {
            Toast.makeText(this, "Thiết bị không hỗ trợ đèn Flash!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startScanAnimation() {
        val animation = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, 650f)
        animation.duration = 2000
        animation.repeatCount = ValueAnimator.INFINITE
        animation.repeatMode = ValueAnimator.REVERSE
        animation.interpolator = LinearInterpolator()
        animation.start()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Toast.makeText(this, "Lỗi mở Camera!", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (!isScanning) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty() && isScanning) {
                        val barcode = barcodes[0]
                        val boundingBox = barcode.boundingBox
                        if (boundingBox != null && isInsideTarget(boundingBox, imageProxy.width, imageProxy.height)) {
                            isScanning = false
                            runOnUiThread { handleBarcodeResult(barcode) }
                        }
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun isInsideTarget(rect: Rect, imgWidth: Int, imgHeight: Int): Boolean {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val toleranceX = imgWidth * 0.25
        val toleranceY = imgHeight * 0.25
        val imgCenterX = imgWidth / 2
        val imgCenterY = imgHeight / 2
        return Math.abs(centerX - imgCenterX) < toleranceX && Math.abs(centerY - imgCenterY) < toleranceY
    }

    override fun onResume() {
        super.onResume()
        // Đợi 1 giây khi quay lại màn hình quét để camera ổn định
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isScanning = true
        }, 1000)
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
                    val ssid = barcode.wifi?.ssid ?: ""
                    val pass = barcode.wifi?.password ?: ""
                    tvTitle.text = "Kết nối Wi-Fi"
                    tvContent.text = "📶 Mạng Wi-Fi: $ssid\n🔑 Mật khẩu: $pass"
                    btnPositive.text = "COPY MẬT KHẨU"
                    btnPositive.setOnClickListener {
                        copyToClipboard(pass, "Đã copy mật khẩu Wi-Fi!")
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

        var immediateResume = false

        btnNeutral.text = "Sao chép tất cả"
        btnNeutral.setOnClickListener {
            copyToClipboard(rawValue, "Đã copy toàn bộ nội dung mã!")
            dialog.dismiss()
        }

        btnNegative.text = "Quét lại"
        btnNegative.setOnClickListener {
            immediateResume = true
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (immediateResume) {
                isScanning = true
            } else {
                // Đợi 2 giây trước khi quét lại để tránh việc quét lặp lại liên tục mã QR cũ
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isScanning = true
                }, 2000)
            }
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

    private fun copyToClipboard(text: String, msg: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ScreenCap_QR", text))
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && allPermissionsGranted()) startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (isFlashOn) {
            camera?.cameraControl?.enableTorch(false)
        }
        val intent = Intent(this, FloatingService::class.java)
        intent.action = "ACTION_SHOW_STAR"
        startService(intent)
    }
}

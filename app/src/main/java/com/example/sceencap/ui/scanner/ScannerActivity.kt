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
            val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build())

            scanner.process(image)
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

    private fun handleBarcodeResult(barcode: Barcode) {
        val rawValue = barcode.rawValue ?: "Không đọc được dữ liệu"
        val valueType = barcode.valueType

        val dialogBuilder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        dialogBuilder.setTitle("✨ Đã tìm thấy mã!")
        dialogBuilder.setCancelable(false)

        when (valueType) {
            Barcode.TYPE_URL -> {
                val url = barcode.url?.url ?: rawValue
                dialogBuilder.setMessage("🔗 Liên kết Web:\n$url")
                dialogBuilder.setPositiveButton("MỞ TRÌNH DUYỆT") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    isScanning = true
                }
            }
            Barcode.TYPE_WIFI -> {
                val ssid = barcode.wifi?.ssid ?: ""
                val pass = barcode.wifi?.password ?: ""
                dialogBuilder.setMessage("📶 Wi-Fi: $ssid\n🔑 Pass: $pass")
                dialogBuilder.setPositiveButton("COPY PASS") { _, _ ->
                    copyToClipboard(pass, "Đã copy mật khẩu!")
                    isScanning = true
                }
            }
            else -> {
                dialogBuilder.setMessage("📄 Nội dung:\n$rawValue")
                dialogBuilder.setPositiveButton("TÌM GOOGLE") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$rawValue")))
                    isScanning = true
                }
            }
        }

        dialogBuilder.setNeutralButton("COPY TẤT CẢ") { _, _ ->
            copyToClipboard(rawValue, "Đã copy!")
            isScanning = true
        }
        dialogBuilder.setNegativeButton("QUÉT LẠI") { _, _ ->
            isScanning = true
        }

        val dialog = dialogBuilder.create()
        dialog.show()
    }

    private fun copyToClipboard(text: String, msg: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SceenCap_QR", text))
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

package com.example.sceencap.ui.crop

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.example.sceencap.ui.floating.FloatingService

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- BIẾN TOÀN CỤC CHỨA ẢNH CẮT ĐỂ TRUYỀN SANG PREVIEW ACTIVITY ---
    companion object {
        var croppedBitmap: Bitmap? = null
        // Biến static để chứa tọa độ Rect đã kéo trên màn hình
        var currentRect = RectF()
    }

    // Biến chứa tấm ảnh nền gốc để cắt
    private var originalBitmap: Bitmap? = null

    // Tọa độ ngón tay
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f

    // Các "Cọ vẽ" (Paint)
    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) // Cọ tẩy: Đục thủng lỗ
    }

    private val borderPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val cornerPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.SQUARE
    }

    private val cornerFillPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#FF385C") // Airbnb red
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.SQUARE
    }

    // Hình chữ nhật đại diện cho vùng cắt
    private val cropRect = RectF()

    init {
        // Ép View vẽ bằng Software để lệnh "Cọ tẩy" (CLEAR) hoạt động mượt mà 100%
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // --- THÊM HÀM RESET NÀY ĐỂ XÓA TỌA ĐỘ CŨ KHI QUAY LẠI ---
    fun reset() {
        startX = 0f
        startY = 0f
        endX = 0f
        endY = 0f
        invalidate() // Vẽ lại màn hình sạch sẽ
    }

    // Hàm nhận tấm ảnh nền gốc từ CropActivity
    fun setOriginalBitmap(bitmap: Bitmap) {
        this.originalBitmap = bitmap
    }

    // HÀM LẮNG NGHE NGÓN TAY CHẠM
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (originalBitmap == null) return false // Không có ảnh nền thì không làm gì hết

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 1. Đặt ngón tay xuống: Ghi lại điểm đầu
                startX = event.x
                startY = event.y
                endX = event.x
                endY = event.x // Đặt điểm cuối trùng điểm đầu để khung không bị méo lúc mới vẽ

                // Ẩn ngôi sao để không bị chụp vào ảnh
                val hideIntent = Intent(context, FloatingService::class.java)
                hideIntent.action = "ACTION_HIDE_STAR"
                context.startService(hideIntent)

                invalidate() // Yêu cầu vẽ lại
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // 2. Kéo ngón tay đi: Ghi lại điểm cuối
                endX = event.x
                endY = event.y
                invalidate() // Yêu cầu vẽ lại liên tục để co giãn khung
            }
            MotionEvent.ACTION_UP -> {
                // 3. THẢ NGÓN TAY RA: BẮT ĐẦU CẮT ẢNH THẬT!

                // Tính toán tỷ lệ và độ lệch của ảnh nền do ImageView (fitCenter)
                val bw = originalBitmap!!.width.toFloat()
                val bh = originalBitmap!!.height.toFloat()
                val scale = kotlin.math.min(width / bw, height / bh)
                val newW = bw * scale
                val newH = bh * scale
                val imgLeft = (width - newW) / 2f
                val imgTop = (height - newH) / 2f

                // Chuyển tọa độ khung cắt trên màn hình sang tọa độ thực của tấm ảnh
                val cropLeft = ((cropRect.left - imgLeft) / scale).toInt()
                val cropTop = ((cropRect.top - imgTop) / scale).toInt()
                val cropWidth = (cropRect.width() / scale).toInt()
                val cropHeight = (cropRect.height() / scale).toInt()

                // CỦNG CỐ TỌA ĐỘ: Đảm bảo khung không đi ra ngoài tấm ảnh nền
                val finalLeft = kotlin.math.max(0, cropLeft)
                val finalTop = kotlin.math.max(0, cropTop)
                val finalWidth = kotlin.math.min(originalBitmap!!.width - finalLeft, cropWidth)
                val finalHeight = kotlin.math.min(originalBitmap!!.height - finalTop, cropHeight)

                // Kiểm tra lại lần cuối nếu width/height âm do kéo ra ngoài màn hình
                if (finalWidth <= 0 || finalHeight <= 0) {
                    Toast.makeText(context, "Vùng cắt không hợp lệ!", Toast.LENGTH_SHORT).show()
                    invalidate()
                    return true
                }

                try {
                    val cropped = Bitmap.createBitmap(originalBitmap!!, finalLeft, finalTop, finalWidth, finalHeight)

                    croppedBitmap = cropped

                    val intent = Intent(context, CropPreviewActivity::class.java)
                    context.startActivity(intent)

                } catch (e: Throwable) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi khi cắt ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }

                invalidate()

                // Hiện lại ngôi sao sau khi đã cắt xong
                val showIntent = Intent(context, FloatingService::class.java)
                showIntent.action = "ACTION_SHOW_STAR"
                context.startService(showIntent)
            }
        }
        return super.onTouchEvent(event)
    }

    // HÀM VẼ LÊN CANVAS
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (startX == 0f && startY == 0f) return

        cropRect.set(
            kotlin.math.min(startX, endX),
            kotlin.math.min(startY, endY),
            kotlin.math.max(startX, endX),
            kotlin.math.max(startY, endY)
        )

        // 1. Vẽ lớp đen mờ phủ kín toàn bộ
        canvas.drawColor(Color.parseColor("#80000000"))

        // 2. "Cọ tẩy" đục thủng lỗ hình chữ nhật (hiệu ứng sáng bừng vùng cắt)
        canvas.drawRect(cropRect, eraserPaint)

        // 3. Viền trắng mỏng bao quanh khung
        canvas.drawRect(cropRect, borderPaint)

        // 4. Vẽ 4 góc nổi bật (bracket style) - trắng viền ngoài to và nổi bật hơn
        val cornerLen = 100f
        val l = cropRect.left
        val t = cropRect.top
        val r = cropRect.right
        val b = cropRect.bottom

        // Góc trên-trái
        canvas.drawLine(l, t, l + cornerLen, t, cornerPaint)
        canvas.drawLine(l, t, l, t + cornerLen, cornerPaint)
        // Góc trên-phải
        canvas.drawLine(r, t, r - cornerLen, t, cornerPaint)
        canvas.drawLine(r, t, r, t + cornerLen, cornerPaint)
        // Góc dưới-trái
        canvas.drawLine(l, b, l + cornerLen, b, cornerPaint)
        canvas.drawLine(l, b, l, b - cornerLen, cornerPaint)
        // Góc dưới-phải
        canvas.drawLine(r, b, r - cornerLen, b, cornerPaint)
        canvas.drawLine(r, b, r, b - cornerLen, cornerPaint)

        // 5. Vẽ lớp màu đỏ Airbnb bên trong góc (layered effect)
        val off = 0f
        canvas.drawLine(l + off, t + off, l + cornerLen - off, t + off, cornerFillPaint)
        canvas.drawLine(l + off, t + off, l + off, t + cornerLen - off, cornerFillPaint)
        canvas.drawLine(r - off, t + off, r - cornerLen + off, t + off, cornerFillPaint)
        canvas.drawLine(r - off, t + off, r - off, t + cornerLen - off, cornerFillPaint)
        canvas.drawLine(l + off, b - off, l + cornerLen - off, b - off, cornerFillPaint)
        canvas.drawLine(l + off, b - off, l + off, b - cornerLen + off, cornerFillPaint)
        canvas.drawLine(r - off, b - off, r - cornerLen + off, b - off, cornerFillPaint)
        canvas.drawLine(r - off, b - off, r - off, b - cornerLen + off, cornerFillPaint)
    }
}

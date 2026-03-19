package com.example.sceencap

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- BIẾN TOÀN CỤC CHỨA ẢNH CẮT ĐỂ TRUYỀN SANG PREVIEW ACTIVITY ---
    companion object {
        var croppedBitmap: Bitmap? = null
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
        color = Color.WHITE // Màu khung
        style = Paint.Style.STROKE
        strokeWidth = 5f // Độ dày khung
    }

    // Hình chữ nhật đại diện cho vùng cắt
    private val cropRect = RectF()

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

                // Lấy tọa độ cái khung chữ nhật đã vẽ
                // Tọa độ này là tọa độ trên màn hình (X, Y)
                val left = cropRect.left.toInt()
                val top = cropRect.top.toInt()
                val width = cropRect.width().toInt()
                val height = cropRect.height().toInt()

                // Kiểm tra điều kiện:
                // Nếu khung quá nhỏ (ví dụ nhỏ hơn 5 pixel mỗi cạnh), ta lờ đi
                if (width < 5 || height < 5) {
                    Toast.makeText(context, "Khung cắt quá nhỏ, vui lòng vẽ lại!", Toast.LENGTH_SHORT).show()
                    invalidate() // Vẽ lại màn hình tối để người dùng vẽ lại
                    return true
                }

                // CỦNG CỐ CỘA ĐỘ: Đảm bảo khung không đi ra ngoài tấm ảnh nền
                val finalLeft = kotlin.math.max(0, left)
                val finalTop = kotlin.math.max(0, top)
                // Chiều rộng và cao tối đa là chiều rộng, cao tối đa của tấm ảnh nền
                val finalWidth = kotlin.math.min(originalBitmap!!.width - finalLeft, width)
                val finalHeight = kotlin.math.min(originalBitmap!!.height - finalTop, height)

                // --- MA THUẬT CẮT ẢNH THẬT LÀ ĐÂY!!! ---
                try {
                    // Cắt lấy điểm ảnh từ tấm ảnh nền theo đúng tọa độ khung vẽ
                    val cropped = Bitmap.createBitmap(originalBitmap!!, finalLeft, finalTop, finalWidth, finalHeight)

                    // 1. CẤT TẤM ẢNH CẮT VÀO BIẾN STATIC
                    croppedBitmap = cropped

                    // 2. MỞ MÀN HÌNH XEM TRƯỚC (PREVIEW ACTIVITY) LÊN
                    val intent = Intent(context, CropPreviewActivity::class.java)
                    // Vì chúng ta đang gọi Activity từ bên trong 1 custom View, ta cần lấy Context của Activity
                    context.startActivity(intent)

                } catch (e: Throwable) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi khi cắt ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }

                invalidate() // Vẽ lại màn hình về trạng thái tối đen để người dùng có thể vẽ lại nếu muốn
            }
        }
        return super.onTouchEvent(event)
    }

    // HÀM VẼ LÊN CANVAS (Giữ nguyên)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (startX == 0f && startY == 0f) return

        // Dùng min/max để đảm bảo dù bạn kéo ngược hay xuôi thì khung vẫn vẽ đúng
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

        // 3. Vẽ thêm đường viền trắng xung quanh
        canvas.drawRect(cropRect, borderPaint)
    }
}
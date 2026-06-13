package com.example.sceencap.ui.crop

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CropAdjustView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private val imageRect = RectF() // Khung chứa toàn bộ ảnh gốc (Fit Center)
    private val cropRect = RectF()  // Vùng đang được chọn để cắt

    private var activeCorner: Int = 0 // 1:TL, 2:TR, 3:BL, 4:BR

    private val paintDim = Paint().apply { color = Color.parseColor("#99000000") }

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

    var onCropAreaReleased: ((Bitmap?) -> Unit)? = null

    // Nạp ảnh vào máy
    fun setBitmap(bmp: Bitmap) {
        this.bitmap = bmp
        activeCorner = 0

        // Tính toán lại tọa độ 4 góc ôm sát vào ảnh mới ngay lập tức
        setupRects()

        requestLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupRects()
    }

    private fun setupRects() {
        if (bitmap == null || width == 0 || height == 0) return
        val bw = bitmap!!.width.toFloat()
        val bh = bitmap!!.height.toFloat()
        val scale = kotlin.math.min(width / bw, height / bh)
        val newW = bw * scale
        val newH = bh * scale
        val left = (width - newW) / 2f
        val top = (height - newH) / 2f

        imageRect.set(left, top, left + newW, top + newH)
        cropRect.set(imageRect) // Vùng cắt bao trọn tấm ảnh
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bitmap == null) return

        canvas.drawBitmap(bitmap!!, null, imageRect, null)

        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, paintDim)
        canvas.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, paintDim)
        canvas.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, paintDim)
        canvas.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, paintDim)

        canvas.drawRect(cropRect, borderPaint)

        // Vẽ 4 góc nổi bật (bracket style) - trắng viền ngoài, to và nổi bật hơn
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

        // Vẽ lớp màu đỏ Airbnb bên trong góc (layered effect)
        // offset 0f vì stroke sẽ tự canh giữa, hoặc offset nhỏ hơn
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val touchRadius = 110f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeCorner = 0
                if (isNear(x, y, cropRect.left, cropRect.top, touchRadius)) activeCorner = 1
                else if (isNear(x, y, cropRect.right, cropRect.top, touchRadius)) activeCorner = 2
                else if (isNear(x, y, cropRect.left, cropRect.bottom, touchRadius)) activeCorner = 3
                else if (isNear(x, y, cropRect.right, cropRect.bottom, touchRadius)) activeCorner = 4

                if (activeCorner != 0) {
                    onCropAreaReleased?.invoke(null)
                }
                return activeCorner != 0
            }
            MotionEvent.ACTION_MOVE -> {
                val minSize = 100f
                when (activeCorner) {
                    1 -> {
                        cropRect.left = kotlin.math.max(imageRect.left, kotlin.math.min(x, cropRect.right - minSize))
                        cropRect.top = kotlin.math.max(imageRect.top, kotlin.math.min(y, cropRect.bottom - minSize))
                    }
                    2 -> {
                        cropRect.right = kotlin.math.min(imageRect.right, kotlin.math.max(x, cropRect.left + minSize))
                        cropRect.top = kotlin.math.max(imageRect.top, kotlin.math.min(y, cropRect.bottom - minSize))
                    }
                    3 -> {
                        cropRect.left = kotlin.math.max(imageRect.left, kotlin.math.min(x, cropRect.right - minSize))
                        cropRect.bottom = kotlin.math.min(imageRect.bottom, kotlin.math.max(y, cropRect.top + minSize))
                    }
                    4 -> {
                        cropRect.right = kotlin.math.min(imageRect.right, kotlin.math.max(x, cropRect.left + minSize))
                        cropRect.bottom = kotlin.math.min(imageRect.bottom, kotlin.math.max(y, cropRect.top + minSize))
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                activeCorner = 0
            }
        }
        return true
    }

    private fun isNear(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float): Boolean {
        return (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2) <= radius * radius
    }

    fun getCroppedBitmap(): Bitmap? {
        if (bitmap == null) return null

        val scaleX = bitmap!!.width / imageRect.width()
        val scaleY = bitmap!!.height / imageRect.height()

        val cropLeft = ((cropRect.left - imageRect.left) * scaleX).toInt()
        val cropTop = ((cropRect.top - imageRect.top) * scaleY).toInt()
        val cropWidth = (cropRect.width() * scaleX).toInt()
        val cropHeight = (cropRect.height() * scaleY).toInt()

        return try {
            Bitmap.createBitmap(bitmap!!, cropLeft, cropTop, cropWidth, cropHeight)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

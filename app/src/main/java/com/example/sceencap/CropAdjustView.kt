package com.example.sceencap

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

    private val paintBracket = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.SQUARE
    }

    var onCropAreaReleased: ((Bitmap?) -> Unit)? = null

    // Nạp ảnh vào máy
    fun setBitmap(bmp: Bitmap) {
        this.bitmap = bmp
        activeCorner = 0

        // --- MA THUẬT SỬA LỖI Ở ĐÂY ---
        // Tính toán lại tọa độ 4 góc ôm sát vào ảnh mới ngay lập tức!
        setupRects()
        // ------------------------------

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

        val len = 70f
        val offset = paintBracket.strokeWidth / 2f

        canvas.drawLine(cropRect.left - offset, cropRect.top - offset, cropRect.left + len, cropRect.top - offset, paintBracket)
        canvas.drawLine(cropRect.left - offset, cropRect.top - offset, cropRect.left - offset, cropRect.top + len, paintBracket)

        canvas.drawLine(cropRect.right + offset, cropRect.top - offset, cropRect.right - len, cropRect.top - offset, paintBracket)
        canvas.drawLine(cropRect.right + offset, cropRect.top - offset, cropRect.right + offset, cropRect.top + len, paintBracket)

        canvas.drawLine(cropRect.left - offset, cropRect.bottom + offset, cropRect.left + len, cropRect.bottom + offset, paintBracket)
        canvas.drawLine(cropRect.left - offset, cropRect.bottom + offset, cropRect.left - offset, cropRect.bottom - len, paintBracket)

        canvas.drawLine(cropRect.right + offset, cropRect.bottom + offset, cropRect.right - len, cropRect.bottom + offset, paintBracket)
        canvas.drawLine(cropRect.right + offset, cropRect.bottom + offset, cropRect.right + offset, cropRect.bottom - len, paintBracket)
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
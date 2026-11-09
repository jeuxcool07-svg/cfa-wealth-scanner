package com.adtech.cfawealth

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import java.util.Locale
import kotlin.math.max

class OverlayView(context: Context) : View(context) {

    @Volatile
    private var frame: ScanFrame? = null
    var mirror = false

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC000000")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#00E5FF")
    }

    fun update(f: ScanFrame) {
        frame = f
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawFrame(canvas, width, height)
    }

    fun drawFrame(canvas: Canvas, w: Int, h: Int) {
        val f = frame ?: return
        if (f.imageWidth == 0 || f.imageHeight == 0) return
        val scale = max(w.toFloat() / f.imageWidth, h.toFloat() / f.imageHeight)
        val dx = (w - f.imageWidth * scale) / 2f
        val dy = (h - f.imageHeight * scale) / 2f

        for (p in f.people) {
            var left = p.box.left * scale + dx
            var right = p.box.right * scale + dx
            if (mirror) {
                val l = w - right
                val r = w - left
                left = l
                right = r
            }
            val top = p.box.top * scale + dy
            val bottom = p.box.bottom * scale + dy

            canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, borderPaint)

            val value = p.fcfa
            val label = if (value != null) {
                String.format(Locale.US, "%,d", value).replace(',', ' ') + " FCFA"
            } else {
                "SCAN... " + p.progress + "%"
            }
            textPaint.color = when {
                value == null -> Color.parseColor("#00E5FF")
                value >= 150_000_000L -> Color.parseColor("#FFD700")
                else -> Color.WHITE
            }
            textPaint.textSize = ((right - left) * 0.22f).coerceIn(30f, 80f)

            val textW = textPaint.measureText(label)
            val pad = textPaint.textSize * 0.4f
            val cx = (left + right) / 2f
            var boxBottom = top - 12f
            if (boxBottom < textPaint.textSize + pad * 2) {
                boxBottom = textPaint.textSize + pad * 2
            }
            val boxTop = boxBottom - textPaint.textSize - pad * 2
            canvas.drawRoundRect(
                RectF(cx - textW / 2 - pad, boxTop, cx + textW / 2 + pad, boxBottom),
                20f, 20f, bgPaint
            )
            canvas.drawText(label, cx, boxBottom - pad - textPaint.textSize * 0.15f, textPaint)
        }
    }
}

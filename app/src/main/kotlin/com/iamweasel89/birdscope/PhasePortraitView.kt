package com.iamweasel89.birdscope

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

// n203: phase portrait via delay embedding
class PhasePortraitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val tau = 8
    private var samples: ShortArray? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22000000")
        strokeWidth = 1f
    }

    fun setSamples(buf: ShortArray) {
        samples = buf.copyOf()
        postInvalidate()
    }

    fun clear() {
        samples = null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // axes through center
        canvas.drawLine(0f, h / 2f, w, h / 2f, axisPaint)
        canvas.drawLine(w / 2f, 0f, w / 2f, h, axisPaint)

        val s = samples ?: return
        if (s.size <= tau + 1) return

        val cx = w / 2f
        val cy = h / 2f
        val scale = (kotlin.math.min(w, h) / 2f) / 32768f

        var prevX = cx + s[0].toInt() * scale
        var prevY = cy - s[tau].toInt() * scale

        var i = 1
        val end = s.size - tau
        while (i < end) {
            val x = cx + s[i].toInt() * scale
            val y = cy - s[i + tau].toInt() * scale
            canvas.drawLine(prevX, prevY, x, y, linePaint)
            prevX = x
            prevY = y
            i++
        }
    }
}
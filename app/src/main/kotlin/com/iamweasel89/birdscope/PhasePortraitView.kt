package com.iamweasel89.birdscope

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

// n203: phase portrait via delay embedding
class PhasePortraitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val tau = 8
    private var samples: ShortArray? = null

    // n203: smoothed peak amplitude across windows; floor 1500 to avoid
    // stretching pure noise to full screen during silence
    private var smoothedPeak: Float = 1500f
    private val peakFloor = 1500f
    private val peakSmoothing = 0.85f

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#0a0a0a")
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7CFC00")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        strokeWidth = 1f
    }

    fun setSamples(buf: ShortArray) {
        samples = buf.copyOf()

        // update smoothed peak
        var peak = 0
        for (s in buf) {
            val v = if (s < 0) -s.toInt() else s.toInt()
            if (v > peak) peak = v
        }
        val effectivePeak = max(peak.toFloat(), peakFloor)
        smoothedPeak = peakSmoothing * smoothedPeak + (1f - peakSmoothing) * effectivePeak

        postInvalidate()
    }

    fun clear() {
        samples = null
        smoothedPeak = peakFloor
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // axes through center
        canvas.drawLine(0f, h / 2f, w, h / 2f, axisPaint)
        canvas.drawLine(w / 2f, 0f, w / 2f, h, axisPaint)

        val s = samples ?: return
        if (s.size <= tau + 1) return

        val cx = w / 2f
        val cy = h / 2f
        // scale so smoothedPeak reaches 90% of half-extent
        val halfExtent = kotlin.math.min(w, h) / 2f
        val scale = (halfExtent * 0.9f) / smoothedPeak

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
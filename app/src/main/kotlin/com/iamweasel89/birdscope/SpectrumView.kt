package com.iamweasel89.birdscope

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.ln

class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    @Volatile private var mags: FloatArray? = null
    @Volatile private var sampleRate: Int = 44100

    private val bg = Color.parseColor("#0a0a0a")
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7CFC00")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 24f
    }
    private val path = Path()

    private val minDb = -90f
    private val maxDb = 0f
    private val minHz = 50.0
    private val maxHz = 12000.0
    private val logMin = ln(minHz)
    private val logMax = ln(maxHz)

    fun setSpectrum(mags: FloatArray, sampleRate: Int) {
        this.mags = mags
        this.sampleRate = sampleRate
        postInvalidateOnAnimation()
    }

    fun clear() {
        mags = null
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(bg)

        for (db in -80..0 step 20) {
            val y = dbToY(db.toFloat(), h)
            canvas.drawLine(0f, y, w, y, gridPaint)
            canvas.drawText("$db dB", 4f, y - 4f, labelPaint)
        }

        val freqLabels = intArrayOf(100, 250, 500, 1000, 2000, 4000, 8000)
        for (f in freqLabels) {
            val x = hzToX(f.toDouble(), w)
            canvas.drawLine(x, 0f, x, h, gridPaint)
            val label = if (f >= 1000) "${f / 1000}k" else "$f"
            canvas.drawText(label, x + 4f, h - 4f, labelPaint)
        }

        val m = mags ?: return
        val sr = sampleRate
        val fftSize = m.size * 2
        path.reset()
        var moved = false
        for (bin in 1 until m.size) {
            val freq = bin.toDouble() * sr / fftSize
            if (freq < minHz) continue
            if (freq > maxHz) break
            val x = hzToX(freq, w)
            val y = dbToY(m[bin], h)
            if (!moved) { path.moveTo(x, y); moved = true }
            else path.lineTo(x, y)
        }
        canvas.drawPath(path, curvePaint)
    }

    private fun hzToX(hz: Double, w: Float): Float {
        val v = hz.coerceIn(minHz, maxHz)
        return (((ln(v) - logMin) / (logMax - logMin)) * w).toFloat()
    }

    private fun dbToY(db: Float, h: Float): Float {
        val clamped = db.coerceIn(minDb, maxDb)
        return ((maxDb - clamped) / (maxDb - minDb)) * h
    }
}

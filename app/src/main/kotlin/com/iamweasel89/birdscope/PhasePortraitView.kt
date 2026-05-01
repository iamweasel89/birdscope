package com.iamweasel89.birdscope

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class PhasePortraitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgColor = Color.parseColor("#0a0a0a")
    private val fadePaint = Paint().apply {
        color = Color.argb(20, 10, 10, 10)
    }
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7CFC00")
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        alpha = 220
    }

    private var backing: Bitmap? = null
    private var backingCanvas: Canvas? = null
    private val path = Path()

    @Volatile private var pendingSamples: ShortArray? = null

    fun feedSamples(samples: ShortArray, length: Int) {
        if (length < 2) return
        val copy = ShortArray(length)
        System.arraycopy(samples, 0, copy, 0, length)
        pendingSamples = copy
        postInvalidateOnAnimation()
    }

    fun clear() {
        backingCanvas?.drawColor(bgColor, PorterDuff.Mode.SRC)
        pendingSamples = null
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        backing = bm
        backingCanvas = Canvas(bm).apply { drawColor(bgColor, PorterDuff.Mode.SRC) }
    }

    override fun onDraw(canvas: Canvas) {
        val bm = backing ?: return
        val bc = backingCanvas ?: return

        bc.drawRect(0f, 0f, bm.width.toFloat(), bm.height.toFloat(), fadePaint)

        val samples = pendingSamples
        if (samples != null && samples.size >= 2) {
            val w = bm.width.toFloat()
            val h = bm.height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val halfMin = min(w, h) / 2f
            val gain = halfMin / 16384f

            path.reset()
            var moved = false
            for (i in 1 until samples.size) {
                val x = cx + samples[i - 1] * gain
                val y = cy - samples[i] * gain
                if (!moved) { path.moveTo(x, y); moved = true }
                else path.lineTo(x, y)
            }
            bc.drawPath(path, tracePaint)
        }

        canvas.drawBitmap(bm, 0f, 0f, null)
    }
}

package com.iamweasel89.birdscope

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class FftAnalyzer(val fftSize: Int) {

    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) {
            "fftSize must be a positive power of two"
        }
    }

    private val window = DoubleArray(fftSize) { i ->
        0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
    }
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)

    fun analyze(samples: ShortArray): FloatArray {
        for (i in 0 until fftSize) {
            re[i] = samples[i].toDouble() * window[i] / 32768.0
            im[i] = 0.0
        }
        fft(re, im)
        val out = FloatArray(fftSize / 2)
        val n = fftSize.toDouble()
        for (i in 0 until fftSize / 2) {
            val mag = 2.0 * sqrt(re[i] * re[i] + im[i] * im[i]) / n
            out[i] = if (mag <= 1e-12) -120f
                     else (20.0 * log10(mag)).toFloat().coerceAtLeast(-120f)
        }
        return out
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (bit != 0 && (j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wlenRe = cos(angle)
            val wlenIm = sin(angle)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val uRe = real[i + k]
                    val uIm = imag[i + k]
                    val vRe = real[i + k + half] * wRe - imag[i + k + half] * wIm
                    val vIm = real[i + k + half] * wIm + imag[i + k + half] * wRe
                    real[i + k] = uRe + vRe
                    imag[i + k] = uIm + vIm
                    real[i + k + half] = uRe - vRe
                    imag[i + k + half] = uIm - vIm
                    val newWRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = newWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}

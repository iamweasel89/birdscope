package com.iamweasel89.birdscope

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.iamweasel89.birdscope.databinding.ActivityMainBinding
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    companion object {
        // n201: recording parameters
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2
        private const val CHANNELS = 1
        // n202: FFT window size
        private const val FFT_SIZE = 2048
        // n203: phase portrait window size
        private const val PHASE_SIZE = 4096
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var updater: Updater

    // n201: recording state
    private val isRecording = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var recorder: AudioRecord? = null
    private var startTimeMs: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    // n202: FFT + stats
    private val fftAnalyzer = FftAnalyzer(FFT_SIZE)
    private val fftAccum = ShortArray(FFT_SIZE)
    private var fftAccumLen = 0
    private val maxPeakSample = AtomicInteger(0)
    private val minPeakSample = AtomicInteger(Int.MAX_VALUE)

    // n203: phase portrait accumulator
    private val phaseAccum = ShortArray(PHASE_SIZE)
    private var phaseAccumLen = 0

    // n203: which view is shown
    private var phaseMode = false

    private val timeTicker = object : Runnable {
        override fun run() {
            if (isRecording.get()) {
                val elapsed = System.currentTimeMillis() - startTimeMs
                binding.elapsed.text = formatElapsed(elapsed)
                updateStats() // n202
                mainHandler.postDelayed(this, 100L)
            }
        }
    }

    // n202: dBFS readouts
    private fun updateStats() {
        val mx = maxPeakSample.get()
        val mn = minPeakSample.get()
        binding.maxDbfs.text = if (mx <= 0) getString(R.string.max_init) else
            String.format(Locale.US, "max %.1f dBFS", 20.0 * log10(mx.toDouble() / 32767.0))
        binding.minDbfs.text = if (mn == Int.MAX_VALUE) getString(R.string.min_init) else
            String.format(Locale.US, "min %.1f dBFS", 20.0 * log10(mn.toDouble() / 32767.0))
    }

    // n202 + n203: reset before each recording
    private fun resetStats() {
        maxPeakSample.set(0)
        minPeakSample.set(Int.MAX_VALUE)
        binding.maxDbfs.text = getString(R.string.max_init)
        binding.minDbfs.text = getString(R.string.min_init)
        fftAccumLen = 0
        phaseAccumLen = 0
        binding.spectrum.clear()
        binding.phasePortrait.clear()
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else binding.micName.text = "Mic permission denied"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pi = packageManager.getPackageInfo(packageName, 0)
        binding.buildInfo.text = "Build " + pi.longVersionCode + " - " + pi.versionName

        // n201: record button
        binding.recordButton.setOnClickListener {
            if (isRecording.get()) stopRecording() else requestMicAndStart()
        }

        // n203: toggle between spectrum and phase portrait
        binding.togglePhaseButton.setOnClickListener {
            phaseMode = !phaseMode
            applyVisualizationMode()
        }
        applyVisualizationMode()

        // updater
        updater = Updater(
            ctx = this,
            onStatus = { status -> runOnUiThread { binding.updateStatus.text = status } },
            onUpdateAvailable = { latest -> runOnUiThread { confirmUpdate(latest) } }
        )
        binding.checkUpdateButton.setOnClickListener { updater.check() }
    }

    // n203
    private fun applyVisualizationMode() {
        if (phaseMode) {
            binding.spectrum.visibility = View.GONE
            binding.phasePortrait.visibility = View.VISIBLE
            binding.togglePhaseButton.text = getString(R.string.show_spectrum)
        } else {
            binding.spectrum.visibility = View.VISIBLE
            binding.phasePortrait.visibility = View.GONE
            binding.togglePhaseButton.text = getString(R.string.show_phase)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording.get()) stopRecording()
    }

    private fun confirmUpdate(latestBuild: Long) {
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage("Build " + latestBuild + " is available. Download and install?")
            .setPositiveButton("Download") { _, _ -> updater.confirmDownload() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // n201: permission gate
    private fun requestMicAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecording() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // n201: start recording
    private fun startRecording() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            binding.micName.text = "AudioRecord init failed (bad params)"
            return
        }
        val bufSize = minBuf * 4

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize
            )
        } catch (e: SecurityException) {
            binding.micName.text = "Permission error: " + e.message
            return
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            binding.micName.text = "AudioRecord not initialised"
            rec.release()
            return
        }

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outDir = java.io.File(filesDir, "recordings").apply { mkdirs() }
        val file = java.io.File(outDir, "bird_" + ts + ".wav")

        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            binding.micName.text = "startRecording failed: " + e.message
            rec.release()
            return
        }

        resetStats()

        binding.micName.text = "Mic: " + micDisplayName(rec)
        binding.fileInfo.text = "Recording -> " + file.name
        binding.recordButton.text = getString(R.string.stop)
        startTimeMs = System.currentTimeMillis()
        mainHandler.post(timeTicker)

        recorder = rec
        isRecording.set(true)

        recordThread = Thread {
            writePcmToWav(file, rec, bufSize)
        }.apply { name = "BirdscopeRecorder"; start() }
    }

    // n201: mic name
    private fun micDisplayName(rec: AudioRecord): String {
        val dev = rec.routedDevice ?: return "default"
        return dev.productName?.toString() ?: "default"
    }

    // n201 + n202 + n203: WAV writer with peak tracking, FFT, phase portrait
    private fun writePcmToWav(file: java.io.File, rec: AudioRecord, bufSize: Int) {
        val raf = RandomAccessFile(file, "rw")
        try {
            raf.setLength(0)
            raf.write(ByteArray(44))

            val buffer = ShortArray(bufSize / 2)
            var totalSamples = 0L

            while (isRecording.get()) {
                val read = rec.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                // n202: peak tracking
                var peak = 0
                for (i in 0 until read) {
                    val v = abs(buffer[i].toInt())
                    if (v > peak) peak = v
                }
                val curMax = maxPeakSample.get()
                if (peak > curMax) maxPeakSample.set(peak)
                if (peak > 0) {
                    val curMin = minPeakSample.get()
                    if (peak < curMin) minPeakSample.set(peak)
                }

                // n202: FFT accumulator
                var off = 0
                while (off < read) {
                    val needed = fftAccum.size - fftAccumLen
                    val take = min(needed, read - off)
                    System.arraycopy(buffer, off, fftAccum, fftAccumLen, take)
                    fftAccumLen += take
                    off += take
                    if (fftAccumLen == fftAccum.size) {
                        val mags = fftAnalyzer.analyze(fftAccum)
                        binding.spectrum.setSpectrum(mags, SAMPLE_RATE)
                        fftAccumLen = 0
                    }
                }

                // n203: phase accumulator
                var poff = 0
                while (poff < read) {
                    val pneeded = phaseAccum.size - phaseAccumLen
                    val ptake = min(pneeded, read - poff)
                    System.arraycopy(buffer, poff, phaseAccum, phaseAccumLen, ptake)
                    phaseAccumLen += ptake
                    poff += ptake
                    if (phaseAccumLen == phaseAccum.size) {
                        binding.phasePortrait.setSamples(phaseAccum)
                        phaseAccumLen = 0
                    }
                }

                // n201: write samples to WAV
                val bytes = ByteArray(read * 2)
                for (i in 0 until read) {
                    val s = buffer[i].toInt()
                    bytes[i * 2] = (s and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }
                raf.write(bytes)
                totalSamples += read
            }

            val dataBytes = (totalSamples * BYTES_PER_SAMPLE).toInt()
            raf.seek(0)
            raf.write(buildWavHeader(dataBytes))
        } finally {
            raf.close()
        }

        mainHandler.post {
            val sizeKb = file.length() / 1024
            binding.fileInfo.text = "Saved: " + file.name + " - " + sizeKb + " KB"
            updateStats()
        }
    }

    // n201: WAV header
    private fun buildWavHeader(dataBytes: Int): ByteArray {
        val byteRate = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE
        val blockAlign = CHANNELS * BYTES_PER_SAMPLE
        val totalDataLen = dataBytes + 36
        val h = ByteArray(44)
        h[0] = 'R'.code.toByte(); h[1] = 'I'.code.toByte(); h[2] = 'F'.code.toByte(); h[3] = 'F'.code.toByte()
        writeIntLE(h, 4, totalDataLen)
        h[8] = 'W'.code.toByte(); h[9] = 'A'.code.toByte(); h[10] = 'V'.code.toByte(); h[11] = 'E'.code.toByte()
        h[12] = 'f'.code.toByte(); h[13] = 'm'.code.toByte(); h[14] = 't'.code.toByte(); h[15] = ' '.code.toByte()
        writeIntLE(h, 16, 16)
        writeShortLE(h, 20, 1)
        writeShortLE(h, 22, CHANNELS)
        writeIntLE(h, 24, SAMPLE_RATE)
        writeIntLE(h, 28, byteRate)
        writeShortLE(h, 32, blockAlign)
        writeShortLE(h, 34, 16)
        h[36] = 'd'.code.toByte(); h[37] = 'a'.code.toByte(); h[38] = 't'.code.toByte(); h[39] = 'a'.code.toByte()
        writeIntLE(h, 40, dataBytes)
        return h
    }

    private fun writeIntLE(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xFF).toByte()
        b[offset + 1] = ((value shr 8) and 0xFF).toByte()
        b[offset + 2] = ((value shr 16) and 0xFF).toByte()
        b[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xFF).toByte()
        b[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    // n201: stop recording
    private fun stopRecording() {
        if (!isRecording.compareAndSet(true, false)) return
        mainHandler.removeCallbacks(timeTicker)
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {}
        recorder?.release()
        recorder = null

        recordThread?.join(2000)
        recordThread = null

        binding.recordButton.text = getString(R.string.start)
    }

    private fun formatElapsed(ms: Long): String {
        val totalTenths = ms / 100
        val tenths = (totalTenths % 10).toInt()
        val totalSec = totalTenths / 10
        val sec = (totalSec % 60).toInt()
        val min = (totalSec / 60).toInt()
        return String.format(Locale.US, "%02d:%02d.%d", min, sec, tenths)
    }
}
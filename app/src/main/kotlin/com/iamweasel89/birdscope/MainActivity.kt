package com.iamweasel89.birdscope

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2
        private const val CHANNELS = 1
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var updater: Updater

    private val isRecording = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var recorder: AudioRecord? = null
    private var currentFile: java.io.File? = null
    private var startTimeMs: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeTicker = object : Runnable {
        override fun run() {
            if (isRecording.get()) {
                val elapsed = System.currentTimeMillis() - startTimeMs
                binding.elapsed.text = formatElapsed(elapsed)
                mainHandler.postDelayed(this, 100L)
            }
        }
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
        binding.buildInfo.text = "Build ${pi.longVersionCode} — ${pi.versionName}"

        binding.recordButton.setOnClickListener {
            if (isRecording.get()) stopRecording() else requestMicAndStart()
        }

        updater = Updater(
            ctx = this,
            onStatus = { status -> runOnUiThread { binding.updateStatus.text = status } },
            onUpdateAvailable = { latest -> runOnUiThread { confirmUpdate(latest) } }
        )

        binding.updateButton.setOnClickListener { updater.check() }

        binding.deleteAllButton.setOnClickListener { confirmDeleteAll() }
    }

    private fun confirmUpdate(latestBuild: Long) {
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage("Build $latestBuild is available. Download and install?")
            .setPositiveButton("Download") { _, _ -> updater.confirmDownload() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAll() {
        if (isRecording.get()) {
            binding.fileInfo.text = "Stop recording first"
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.delete_yes) { _, _ -> deleteAllRecordings() }
            .setNegativeButton(R.string.delete_no, null)
            .show()
    }

    private fun deleteAllRecordings() {
        val dir = java.io.File(filesDir, "recordings")
        val files = dir.listFiles().orEmpty()
        var deleted = 0
        for (f in files) if (f.delete()) deleted++
        binding.fileInfo.text = "Deleted $deleted file(s)"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording.get()) stopRecording()
    }

    private fun requestMicAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecording() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

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
            binding.micName.text = "Permission error: ${e.message}"
            return
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            binding.micName.text = "AudioRecord not initialised"
            rec.release()
            return
        }

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outDir = java.io.File(filesDir, "recordings").apply { mkdirs() }
        val file = java.io.File(outDir, "bird_$ts.wav")
        currentFile = file

        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            binding.micName.text = "startRecording failed: ${e.message}"
            rec.release()
            return
        }

        binding.micName.text = "Mic: ${micDisplayName(rec)}"
        binding.fileInfo.text = "Recording → ${file.name}"
        binding.recordButton.text = getString(R.string.stop)
        startTimeMs = System.currentTimeMillis()
        mainHandler.post(timeTicker)

        recorder = rec
        isRecording.set(true)

        recordThread = Thread {
            writePcmToWav(file, rec, bufSize)
        }.apply { name = "BirdscopeRecorder"; start() }
    }

    private fun micDisplayName(rec: AudioRecord): String {
        val dev = rec.routedDevice ?: return "default"
        return dev.productName?.toString() ?: "default"
    }

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

                var peak = 0
                for (i in 0 until read) {
                    val v = abs(buffer[i].toInt())
                    if (v > peak) peak = v
                }
                val pct = (peak * 100 / 32767).coerceIn(0, 100)
                mainHandler.post { binding.levelBar.progress = pct }

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
            binding.fileInfo.text = "Saved: ${file.name} — ${sizeKb} KB"
            binding.levelBar.progress = 0
        }
    }

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

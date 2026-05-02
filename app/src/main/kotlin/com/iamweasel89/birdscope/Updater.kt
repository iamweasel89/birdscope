package com.iamweasel89.birdscope

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val APK_FILENAME = "birdscope_update.apk"
private const val RELEASES_LATEST =
    "https://github.com/iamweasel89/birdscope/releases/latest"
private const val RELEASES_BASE =
    "https://github.com/iamweasel89/birdscope/releases/download"

const val ACTION_INSTALL_STATUS = "com.iamweasel89.birdscope.INSTALL_STATUS"

class Updater(
    private val ctx: Context,
    private val onStatus: (String) -> Unit,
    private val onUpdateAvailable: (latestBuild: Long) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadId: Long? = null
    private var pollJob: Job? = null
    private var pendingDownloadUrl: String? = null

    private fun apkFile(): File {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(dir, APK_FILENAME)
    }

    private fun canInstallPackages(): Boolean =
        ctx.packageManager.canRequestPackageInstalls()

    fun check() {
        scope.launch {
            try {
                onStatus("Checking…")
                val installed = ctx.packageManager
                    .getPackageInfo(ctx.packageName, 0).longVersionCode
                val tag = fetchLatestTag() ?: run {
                    onStatus("No releases yet (build $installed)")
                    return@launch
                }
                val latest = tag.removePrefix("build-").toLongOrNull() ?: 0L
                if (latest <= installed) {
                    onStatus("Up to date (build $installed)")
                    return@launch
                }
                pendingDownloadUrl = "$RELEASES_BASE/$tag/birdscope.apk"
                onStatus("Update available: build $latest")
                onUpdateAvailable(latest)
            } catch (e: Exception) {
                onStatus("Update error: ${e.message}")
            }
        }
    }

    fun confirmDownload() {
        val url = pendingDownloadUrl ?: run {
            onStatus("No update prepared — tap Check first")
            return
        }
        onStatus("Downloading…")
        startDownload(url)
    }

    private suspend fun fetchLatestTag(): String? = withContext(Dispatchers.IO) {
        val conn = (URL(RELEASES_LATEST).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
        }
        try {
            val code = conn.responseCode
            if (code == 404) return@withContext null
            if (code != 301 && code != 302) error("releases/latest returned $code")
            val loc = conn.getHeaderField("Location") ?: error("No Location header")
            // location ends with /releases/tag/build-N
            loc.substringAfterLast('/')
        } finally {
            conn.disconnect()
        }
    }

    private fun startDownload(url: String) {
        apkFile().delete()
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("birdscope update")
            .setDescription("Downloading APK…")
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, APK_FILENAME
            )
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
        downloadId = dm.enqueue(req)
        pollJob = scope.launch { poll(dm, downloadId!!) }
    }

    private suspend fun poll(dm: DownloadManager, id: Long) {
        while (true) {
            delay(700)
            val q = DownloadManager.Query().setFilterById(id)
            val cursor = dm.query(q)
            if (!cursor.moveToFirst()) {
                cursor.close()
                onStatus("Download lost")
                return
            }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            cursor.close()

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    onStatus("Downloaded — installing…")
                    install()
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    onStatus("Download failed")
                    return
                }
                else -> {
                    if (total > 0) {
                        val pct = (downloaded * 100 / total).toInt()
                        onStatus("Downloading… $pct%")
                    } else {
                        onStatus("Downloading…")
                    }
                }
            }
        }
    }

    private fun install() {
        if (!canInstallPackages()) {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            onStatus("Allow install from this app, then tap Check again")
            return
        }
        val apk = apkFile()
        if (!apk.exists()) {
            onStatus("APK not found")
            return
        }
        Thread {
            try {
                val pi = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                val sessionId = pi.createSession(params)
                pi.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = Intent(ACTION_INSTALL_STATUS).setPackage(ctx.packageName)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            PendingIntent.FLAG_MUTABLE
                        else 0
                    val pending = PendingIntent.getBroadcast(
                        ctx.applicationContext, sessionId, intent, flags
                    )
                    session.commit(pending.intentSender)
                }
            } catch (e: Exception) {
                onStatus("Install error: ${e.message}")
            }
        }.start()
    }
}

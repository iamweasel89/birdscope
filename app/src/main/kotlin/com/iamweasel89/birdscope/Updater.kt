package com.iamweasel89.birdscope

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private var pendingDownloadUrl: String? = null

    // store APK in app-private cacheDir; no scoped-storage permission issues
    private fun apkFile(): File = File(ctx.cacheDir, APK_FILENAME)

    private fun canInstallPackages(): Boolean =
        ctx.packageManager.canRequestPackageInstalls()

    fun check() {
        scope.launch {
            try {
                onStatus("Checking...")
                val installed = ctx.packageManager
                    .getPackageInfo(ctx.packageName, 0).longVersionCode
                val tag = fetchLatestTag() ?: run {
                    onStatus("No releases yet (build " + installed + ")")
                    return@launch
                }
                val latest = tag.removePrefix("build-").toLongOrNull() ?: 0L
                if (latest <= installed) {
                    onStatus("Up to date (build " + installed + ")")
                    return@launch
                }
                pendingDownloadUrl = RELEASES_BASE + "/" + tag + "/birdscope.apk"
                onStatus("Update available: build " + latest)
                onUpdateAvailable(latest)
            } catch (e: Exception) {
                onStatus("Update error: " + e.message)
            }
        }
    }

    fun confirmDownload() {
        val url = pendingDownloadUrl ?: run {
            onStatus("No update prepared - tap Check first")
            return
        }
        onStatus("Downloading...")
        scope.launch {
            try {
                downloadToCache(url)
                onStatus("Downloaded - installing...")
                install()
            } catch (e: Exception) {
                onStatus("Download error: " + e.message)
            }
        }
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
            if (code != 301 && code != 302) error("releases/latest returned " + code)
            val loc = conn.getHeaderField("Location") ?: error("No Location header")
            loc.substringAfterLast('/')
        } finally {
            conn.disconnect()
        }
    }

    // n201/updater: download APK directly into app-private cacheDir
    private suspend fun downloadToCache(url: String) = withContext(Dispatchers.IO) {
        val target = apkFile()
        target.delete()

        var connection: HttpURLConnection? = null
        try {
            // follow redirects manually so we can keep showing progress
            var current = url
            var redirects = 0
            while (redirects < 5) {
                connection = (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                val code = connection.responseCode
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val next = connection.getHeaderField("Location") ?: error("redirect without Location")
                    connection.disconnect()
                    current = next
                    redirects++
                    continue
                }
                if (code !in 200..299) error("HTTP " + code + " for APK")
                break
            }

            val conn = connection ?: error("no connection")
            val total = conn.contentLengthLong
            var downloaded = 0L
            var lastReportedPct = -1

            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) {
                            val pct = ((downloaded * 100) / total).toInt()
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                onStatus("Downloading... " + pct + "%")
                            }
                        }
                    }
                }
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun install() {
        if (!canInstallPackages()) {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + ctx.packageName)
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
                onStatus("Install error: " + e.message)
            }
        }.start()
    }
}
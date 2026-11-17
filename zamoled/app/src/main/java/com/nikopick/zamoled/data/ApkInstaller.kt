package com.nikopick.zamoled.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app updates: downloads the new APK from this app's GitHub release, checks its SHA-256, and hands it to
 * Android's PackageInstaller, which asks the person to confirm. Android itself refuses an APK signed with a different key.
 */
object ApkInstaller {
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Downloads into the app's private cache and verifies the checksum when the release publishes one. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        require(info.apkUrl.startsWith("https://github.com/VK-VfX/")) { "Unexpected download address" }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "zamoled-${info.version}.apk")
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            Diag.log("update", "download ${info.version} HTTP ${conn.responseCode}")
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var last = -1
            conn.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(16384)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (done > 120L * 1024 * 1024) throw IOException("File too large")
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) {
                                last = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256.isNotEmpty() && !hex.equals(info.sha256, ignoreCase = true)) {
                out.delete()
                Diag.log("update", "checksum mismatch: got ${hex.take(12)}, wanted ${info.sha256.take(12)}")
                throw IOException("The download didn't match its checksum. Try again in a few minutes.")
            }
            Diag.log("update", "downloaded ${out.length()} bytes, sha256 ${hex.take(12)} ok")
        } finally {
            conn.disconnect()
        }
        out
    }

    /** Streams the APK into a PackageInstaller session; Android then asks the person to confirm. */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            session.openWrite("update.apk", 0, apk.length()).use { os ->
                apk.inputStream().use { it.copyTo(os) }
                session.fsync(os)
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val callback = Intent(context, InstallReceiver::class.java).setPackage(context.packageName)
            Diag.log("update", "install session $id committed")
            session.commit(PendingIntent.getBroadcast(context, id, callback, flags).intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }
}

/** Receives the installer's progress; shows Android's confirmation screen when it asks for it. */
class InstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_INTENT)

    override fun onReceive(context: Context, intent: Intent) {
        Diag.log("installer", "status ${intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)} ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                confirmIntent(intent)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(context, "Install didn't finish" + (if (msg != null) ": $msg" else ""), Toast.LENGTH_LONG).show()
            }
        }
    }
}

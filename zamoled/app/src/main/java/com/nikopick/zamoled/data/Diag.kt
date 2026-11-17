package com.nikopick.zamoled.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The log catcher: a small diagnostic log kept on the phone and shared only when you tap Share in About.
 * It records starts, crashes and update checks/downloads/installs. Never places, searches or anything typed:
 * [clean] strips decimals that look like coordinates and URL query strings before anything is written.
 */
object Diag {
    private const val MAX = 48_000
    private var file: File? = null
    private var app: Context? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        app = context.applicationContext
        file = File(context.applicationContext.filesDir, "diag.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            error("CRASH ${t.name}", e)
            previous?.uncaughtException(t, e)
        }
    }

    fun clean(s: String?): String =
        (s ?: "").replace(Regex("-?\\d{1,3}\\.\\d{4,}"), "#").replace(Regex("\\?[^\\s\"']+"), "?...")

    @Synchronized
    fun log(tag: String, msg: String) {
        val f = file ?: return
        runCatching {
            if (f.length() > MAX) {
                val all = f.readBytes()
                var from = all.size / 2
                while (from < all.size && all[from] != '\n'.code.toByte()) from++
                f.writeBytes(all.copyOfRange(minOf(from + 1, all.size), all.size))
            }
            f.appendText(SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date()) + " $tag ${clean(msg)}\n")
        }
    }

    fun error(tag: String, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        log(tag, sw.toString().take(2500))
    }

    /** The text that gets shared: header, why the app last stopped, then the log. */
    @Synchronized
    fun report(context: Context): String {
        val b = StringBuilder()
        b.append("Zamoled ${Updates.installedVersion} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n")
        b.append("Purrcast ${Trio.installedVersion(context, Trio.PURRCAST)} · Maps and Cats ${Trio.installedVersion(context, Trio.MAPS)}\n")
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                for (x in am.getHistoricalProcessExitReasons(null, 0, 5)) {
                    b.append("exit ${SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(x.timestamp))} reason=${x.reason} status=${x.status} ${clean(x.description)}\n")
                }
            }
        }
        b.append("---\n")
        runCatching { file?.takeIf { it.exists() }?.readText()?.let { b.append(it) } }
        return b.toString()
    }

    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Zamoled log").putExtra(Intent.EXTRA_TEXT, report(context))
        context.startActivity(Intent.createChooser(send, "Share Zamoled log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Synchronized
    fun clear() {
        file?.delete()
    }
}

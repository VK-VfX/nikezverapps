package com.nikopick.zamoled.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** What one of the three apps reports about itself for the shared Cat Club, e.g. "12" and "cats caught". */
data class CatStat(val value: Int, val label: String)

/**
 * How Zamoled, Purrcast and Maps and Cats know about each other. Every call is safe when the other app is missing:
 * it just returns null, and the screen hides that line.
 */
object Trio {
    const val PURRCAST = "app.skypix.weather"
    const val MAPS = "app.mapsandcats"

    private const val RELEASES = "https://api.github.com/repos/VK-VfX/nikezverapps/releases?per_page=30"
    private const val PURRCAST_UPDATE = "https://raw.githubusercontent.com/VK-VfX/nikezverapps/main/purrcast/dist/update.json"

    fun installedVersion(context: Context, pkg: String): String? =
        runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()

    /** Reads another app's public Cat Club numbers. Null when it is not installed or has no provider yet. */
    fun stat(context: Context, authority: String): CatStat? = runCatching {
        context.contentResolver.query(Uri.parse("content://$authority/stats"), null, null, null, null)?.use { c ->
            if (c.moveToFirst()) CatStat(c.getInt(c.getColumnIndexOrThrow("value")), c.getString(c.getColumnIndexOrThrow("label"))) else null
        }
    }.getOrNull()

    /** Newest published version of each app, keyed "purrcast", "zamoled", "mapsandcats". Empty when offline. */
    suspend fun latestVersions(): Map<String, String> = withContext(Dispatchers.IO) {
        val out = HashMap<String, String>()
        runCatching {
            val all = JSONArray(get(RELEASES))
            for (i in 0 until all.length()) {
                val r = all.getJSONObject(i)
                if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
                val tag = r.optString("tag_name")
                for (app in listOf("zamoled", "mapsandcats")) {
                    if (tag.startsWith("$app-v") && app !in out) out[app] = tag.removePrefix("$app-v")
                }
            }
        }
        runCatching { out["purrcast"] = JSONObject(get(PURRCAST_UPDATE)).getString("versionName") }
        out
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun isNewer(candidate: String, current: String): Boolean = Updates.isNewer(candidate, current)
}

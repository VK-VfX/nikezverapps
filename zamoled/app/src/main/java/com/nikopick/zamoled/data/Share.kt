package com.nikopick.zamoled.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nikopick.zamoled.gen.WallpaperSpec

/** Sharing a wallpaper is sharing its recipe: the receiving phone draws the same picture from it. */
object ShareLink {
    private const val SCHEME = "zamoled"
    private const val HOST = "w"

    fun linkFor(spec: WallpaperSpec): String =
        Uri.Builder().scheme(SCHEME).authority(HOST).appendQueryParameter("k", spec.key).build().toString()

    /** Reads a wallpaper out of a link, or out of a bare code that was pasted. */
    fun parse(text: String?): WallpaperSpec? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val start = raw.indexOf("$SCHEME://")
        if (start >= 0) {
            val link = raw.substring(start).takeWhile { !it.isWhitespace() }
            val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return null
            // zamoled://weather?mood=rain: a wallpaper for the weather, sent by Purrcast
            if (uri.host == "weather") return weatherSpec(uri.getQueryParameter("mood"))
            val key = uri.getQueryParameter("k")
            return key?.let { WallpaperSpec.fromKey(it) }?.takeIf { known(it) }
        }
        return WallpaperSpec.fromKey(raw)?.takeIf { known(it) }
    }

    /** A wallpaper that suits the weather, with quiet zones left for Purrcast's widgets. Unknown moods give null. */
    fun weatherSpec(mood: String?): WallpaperSpec? {
        val (style, palette) = when (mood) {
            "rain" -> "wet_street" to "ocean"
            "storm" -> "storm_strike" to "storm"
            "cloudy" -> "storm_cell" to "storm"
            "clear" -> "pixel_sunset" to "sunset"
            "night" -> "moon_fisher" to "violet"
            "snow" -> "contours" to "mono"
            "fog" -> "orbits" to "ocean"
            else -> return null
        }
        return WallpaperSpec(style, System.currentTimeMillis() and 0xFFFFFFL, palette, black = 0.25f, zone = 2).takeIf { known(it) }
    }

    private fun known(spec: WallpaperSpec): Boolean = spec.style.id == spec.styleId

    fun share(context: Context, spec: WallpaperSpec) {
        val text = "${spec.style.name}, a Zamoled wallpaper. Open this link on a phone with Zamoled, " +
            "or paste it into the app's search:\n${linkFor(spec)}"
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(send, "Share wallpaper").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

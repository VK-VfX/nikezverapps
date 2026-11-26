package com.nikopick.depthlock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import java.io.File

/** Everything the user can change about the clock. */
data class ClockSettings(
    val style: Int = 1,           // 0 one line, 1 stacked (hours over minutes)
    val font: Int = 0,
    val weight: Int = 300,        // 100..900, for the built-in fonts
    val aodMode: Int = 0,         // always-on look: 0 wallpaper, 1 subject only, 2 clock only
    val aodDim: Float = 0.4f,
    val size: Float = 0.9f,       // 0.5..1.1 of screen width
    val xOffset: Float = 0f,      // -0.3..0.3 sideways, fraction of screen width
    val y: Float = 0.36f,         // vertical centre of the clock, fraction of screen height
    val stretch: Float = 1f,      // 0.8..2.6 taller digits
    val color: Int = 0,
    val alpha: Float = 0.92f,
    val h24: Boolean = false,
    val date: Boolean = true,
    val tilt: Boolean = true,
    val clockOnHome: Boolean = false,
)

object Looks {
    val fontNames = listOf("Pixel Clock", "Google Sans", "Sans", "Condensed", "Serif", "Mono", "Casual", "My font")
    private val families = listOf("google-sans-clock", "google-sans", "sans-serif", "sans-serif-condensed", "serif", "monospace", "casual")
    const val CUSTOM = 7

    private var custom: Typeface? = null
    private var customRev = -1L
    private val cache = HashMap<Int, Typeface>()

    /** Built-in fonts follow the weight slider. A family a phone does not have falls back to the system font. */
    fun typeface(font: Int, weight: Int): Typeface {
        if (font == CUSTOM) return custom ?: Typeface.DEFAULT
        val w = weight.coerceIn(100, 900)
        return cache.getOrPut(font * 1000 + w) { Typeface.create(Typeface.create(families[font], Typeface.NORMAL), w, false) }
    }

    fun hasCustom() = custom != null

    @Synchronized
    fun ensureCustom(c: Context, rev: Long) {
        if (rev == customRev) return
        customRev = rev
        val f = File(c.filesDir, "clock_font.ttf")
        custom = if (f.exists()) runCatching { Typeface.createFromFile(f) }.getOrNull() else null
    }

    /** Copies a .ttf or .otf into the app and checks it really is a font. */
    fun importFont(c: Context, uri: android.net.Uri): Result<Unit> = runCatching {
        val tmp = File(c.filesDir, "clock_font.tmp")
        c.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        try {
            android.graphics.fonts.Font.Builder(tmp).build()
        } catch (e: Exception) {
            tmp.delete(); throw e
        }
        tmp.copyTo(File(c.filesDir, "clock_font.ttf"), overwrite = true)
        tmp.delete()
        Prefs.bumpFontRev(c)
        ensureCustom(c, Prefs.fontRev(c))
    }

    /** Name, top colour (hours), bottom colour (minutes). One-line style uses the top colour only. */
    class Palette(val name: String, val top: Int, val bottom: Int)
    val palettes: List<Palette> = listOf(
        Palette("Teal", 0xFF3DF5D0.toInt(), 0xFFFFFFFF.toInt()),
        Palette("Pastel", 0xFFA5EDF0.toInt(), 0xFFF5C2D9.toInt()),
        Palette("Sunset", 0xFF9CEFF2.toInt(), 0xFFFAD3A0.toInt()),
        Palette("Forest", 0xFF1F5A3E.toInt(), 0xFFFBD3BC.toInt()),
        Palette("Gold", 0xFFFFD27A.toInt(), 0xFFFFFFFF.toInt()),
        Palette("Coral", 0xFFFF7A6B.toInt(), 0xFFFFE1DA.toInt()),
        Palette("Mono", 0xFFFFFFFF.toInt(), 0xFFD0D0D0.toInt()),
    )
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("depthlock", Context.MODE_PRIVATE)

    fun load(c: Context): ClockSettings {
        val p = sp(c)
        Looks.ensureCustom(c, p.getLong("fontRev", 0L))
        val d = ClockSettings()
        return ClockSettings(
            style = p.getInt("style", d.style).coerceIn(0, 1),
            font = p.getInt("font", d.font).coerceIn(0, Looks.fontNames.lastIndex),
            weight = p.getInt("weight", d.weight).coerceIn(100, 900),
            aodMode = p.getInt("aodMode", d.aodMode).coerceIn(0, 2),
            aodDim = p.getFloat("aodDim", d.aodDim).coerceIn(0.1f, 0.9f),
            size = p.getFloat("size", d.size),
            xOffset = p.getFloat("xOffset", d.xOffset),
            y = p.getFloat("y", d.y),
            stretch = p.getFloat("stretch", d.stretch),
            color = p.getInt("color", d.color).coerceIn(0, Looks.palettes.lastIndex),
            alpha = p.getFloat("alpha", d.alpha),
            h24 = p.getBoolean("h24", d.h24),
            date = p.getBoolean("date", d.date),
            tilt = p.getBoolean("tilt", d.tilt),
            clockOnHome = p.getBoolean("clockOnHome", d.clockOnHome),
        )
    }

    fun save(c: Context, s: ClockSettings) {
        sp(c).edit()
            .putInt("style", s.style).putInt("font", s.font).putInt("weight", s.weight).putInt("aodMode", s.aodMode).putFloat("aodDim", s.aodDim).putFloat("size", s.size).putFloat("y", s.y).putFloat("xOffset", s.xOffset).putFloat("stretch", s.stretch)
            .putInt("color", s.color).putFloat("alpha", s.alpha)
            .putBoolean("h24", s.h24).putBoolean("date", s.date)
            .putBoolean("tilt", s.tilt).putBoolean("clockOnHome", s.clockOnHome)
            .apply()
    }

    /** Bumped whenever a new photo is cut out, so running wallpapers know to reload. */
    fun rev(c: Context): Long = sp(c).getLong("rev", 0L)
    fun fontRev(c: Context): Long = sp(c).getLong("fontRev", 0L)
    fun bumpFontRev(c: Context) = sp(c).edit().putLong("fontRev", System.currentTimeMillis()).apply()
    fun bumpRev(c: Context) = sp(c).edit().putLong("rev", System.currentTimeMillis()).apply()
}

/** The two layers: the full photo, and the same photo with only the subject left. */
class DepthAssets(val bg: Bitmap?, val subject: Bitmap?) {
    /** What the always-on look keeps: the wallpaper, only the subject on black, or just the clock. */
    fun forAod(mode: Int) = when (mode) {
        0 -> this
        1 -> DepthAssets(null, subject)
        else -> DepthAssets(null, null)
    }
}

object Store {
    private fun bgFile(c: Context) = File(c.filesDir, "bg.jpg")
    private fun subjectFile(c: Context) = File(c.filesDir, "subject.png")

    fun load(c: Context): DepthAssets {
        val bg = bgFile(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        val subject = subjectFile(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        return DepthAssets(bg, subject)
    }

    fun save(c: Context, bg: Bitmap, subject: Bitmap) {
        writeBg(c, bg)
        writeSubject(c, subject)
        Prefs.bumpRev(c)
    }

    fun saveBg(c: Context, bg: Bitmap) { writeBg(c, bg); Prefs.bumpRev(c) }
    fun saveSubject(c: Context, subject: Bitmap) { writeSubject(c, subject); Prefs.bumpRev(c) }
    fun clearSubject(c: Context) { subjectFile(c).delete(); Prefs.bumpRev(c) }

    private fun writeBg(c: Context, bg: Bitmap) =
        bgFile(c).outputStream().use { bg.compress(Bitmap.CompressFormat.JPEG, 93, it) }

    private fun writeSubject(c: Context, subject: Bitmap) =
        subjectFile(c).outputStream().use { subject.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

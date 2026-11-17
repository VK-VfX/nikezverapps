package com.nikopick.zamoled.gen

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Night Photos: six photographs, already pushed to pure black (everything below a brightness threshold is
 * #000000), shipped inside the app so nothing is downloaded. The Detail slider is the exposure, and the Black level
 * slider crushes what is left, like every other wallpaper.
 *
 * The images live in assets/photos. Where there are no assets (the Windows app) the category is simply not shown.
 */
object PhotoPack {
    @Volatile
    private var opener: ((String) -> java.io.InputStream?)? = null

    @Volatile
    private var present: Set<String> = emptySet()

    private val cache = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 2
    }

    fun init(am: AssetManager) {
        if (opener != null) return
        present = runCatching { am.list("photos")?.filter { it.endsWith(".webp") }?.toSet() }.getOrNull().orEmpty()
        opener = { n -> runCatching { am.open("photos/$n") }.getOrNull() }
    }

    /** For desktop previews: read the same files straight from a folder. */
    fun init(dir: java.io.File) {
        present = dir.listFiles { f -> f.name.endsWith(".webp") }?.map { it.name }?.toSet().orEmpty()
        opener = { n -> runCatching { java.io.File(dir, n).inputStream() }.getOrNull() }
    }

    /** True when the photos are bundled, so the category can be shown. */
    val ready: Boolean get() = present.isNotEmpty()

    /** The photo [file], decoded no larger than needed for a picture [targetWidth] pixels wide. */
    fun load(file: String, targetWidth: Int): Bitmap? {
        val open = opener ?: return null
        if (file !in present) return null
        var sample = 1
        while (SOURCE_WIDTH / (sample * 2) >= targetWidth && sample < 8) sample *= 2
        val key = "$file@$sample"
        synchronized(cache) { cache[key]?.let { if (!it.isRecycled) return it } }
        val bmp = runCatching {
            open(file)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }.getOrNull() ?: return null
        synchronized(cache) { cache[key] = bmp }
        return bmp
    }

    private const val SOURCE_WIDTH = 1080
}

/** One bundled photograph, centre-cropped to the screen. */
class PhotoStyle(id: String, name: String, private val file: String) : Style(id, name, Category.PHOTO) {
    override val usesPalette: Boolean get() = false

    // WebP and scaling leave faint noise in the blacks; this clears it to true #000000 so the screen switches off.
    override val minBlack: Float get() = 0.3f

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    override fun draw(s: Scene) {
        val bmp = PhotoPack.load(file, s.canvas.width) ?: return
        // Centre-crop the photo to the scene's shape.
        val want = s.w / s.h
        val have = bmp.width.toFloat() / bmp.height
        val src = if (have > want) {
            val cw = (bmp.height * want).toInt()
            Rect((bmp.width - cw) / 2, 0, (bmp.width + cw) / 2, bmp.height)
        } else {
            val ch = (bmp.width / want).toInt()
            Rect(0, (bmp.height - ch) / 2, bmp.width, (bmp.height + ch) / 2)
        }
        // Detail is the exposure: 1.0 is the photo as shipped, lower is dimmer, higher is brighter.
        val g = s.density.coerceIn(0.4f, 1.8f)
        paint.colorFilter = ColorMatrixColorFilter(
            ColorMatrix(floatArrayOf(g, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
        )
        s.canvas.drawBitmap(bmp, src, RectF(0f, 0f, s.w, s.h), paint)
    }
}

object PhotoWorld {
    val styles: List<Style> = listOf(
        PhotoStyle("photo_milkyway", "Milky Way", "milkyway.webp"),
        PhotoStyle("photo_aurora", "Aurora", "aurora.webp"),
        PhotoStyle("photo_skyline", "Skyline", "city.webp"),
        PhotoStyle("photo_moon", "Moon", "moon.webp"),
        PhotoStyle("photo_neon", "Neon", "neon.webp"),
        PhotoStyle("photo_jellyfish", "Jellyfish", "jelly.webp"),
    )
}

package com.nikopick.zamoled.gen

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hero Club: wallpapers of the masked hero cat. They exist only while Purrcast, Zamoled and Maps and Cats are all installed
 * and the "Show hero cat" switch is on. The art is bundled in assets/hero (the Windows app simply does not show the style).
 * The big cats show the mood Purrcast chose for the weather; the rest come from the calm set (never angry or sad).
 */
object HeroPack {
    @Volatile private var opener: ((String) -> java.io.InputStream?)? = null
    @Volatile private var present: Set<String> = emptySet()

    /** All three apps are installed and the switch is on. Set by the app. */
    @Volatile var unlocked: Boolean = false

    /** The mood of the big cats (Purrcast's weather mood), or "smile". */
    @Volatile var lead: String = "smile"

    private val cache = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 12
    }

    fun init(am: AssetManager) {
        if (opener != null) return
        present = runCatching { am.list("hero")?.filter { it.endsWith(".png") }?.toSet() }.getOrNull().orEmpty()
        opener = { n -> runCatching { am.open("hero/$n") }.getOrNull() }
    }

    /** For desktop previews: read the same files from a folder. */
    fun init(dir: java.io.File) {
        present = dir.listFiles { f -> f.name.endsWith(".png") }?.map { it.name }?.toSet().orEmpty()
        opener = { n -> runCatching { java.io.File(dir, n).inputStream() }.getOrNull() }
    }

    val ready: Boolean get() = present.isNotEmpty()

    /** The style is browsable only when the art is bundled and the hero is unlocked. */
    val available: Boolean get() = ready && unlocked

    val CALM = listOf("smile", "grin", "smirk", "confident", "shocked", "loaf")
    val ALL = listOf("smile", "grin", "smirk", "confident", "angry", "shocked", "sad", "scared", "loaf")

    const val LOOKS = 5

    /** The big 768 px portrait for the supercat wallpaper (assets/hero/portrait), or the normal picture when it is missing. */
    fun portrait(mood: String, look: Int): Bitmap? {
        val open = opener ?: return null
        val name = "hero_${mood}_${Math.floorMod(look, LOOKS)}.png"
        val key = "portrait/$name"
        synchronized(cache) { cache[key]?.let { if (!it.isRecycled) return it } }
        val bmp = runCatching { open("portrait/$name")?.use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return load(mood, look, 512)
        synchronized(cache) { cache[key] = bmp }
        return bmp
    }

    /** The picture of the hero cat in [mood] and mask look [look] (0 to 4; the locked silhouette has no look), decoded no larger than needed for [targetWidth] pixels. */
    fun load(mood: String, look: Int, targetWidth: Int): Bitmap? {
        val open = opener ?: return null
        val file = if (mood == "locked") "hero_locked.png" else "hero_${mood}_${Math.floorMod(look, LOOKS)}.png"
        if (file !in present) return null
        var sample = 1
        while (512 / (sample * 2) >= targetWidth && sample < 8) sample *= 2
        val key = "$file@$sample"
        synchronized(cache) { cache[key]?.let { if (!it.isRecycled) return it } }
        val bmp = runCatching { open(file)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } }.getOrNull() ?: return null
        synchronized(cache) { cache[key] = bmp }
        return bmp
    }
}

object HeroClub : Style("hero_club", "Hero Club", Category.DOODLE) {
    override val usesPalette: Boolean get() = false
    override val minBlack: Float get() = 0.3f

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(s: Scene) {
        val pxScale = s.canvas.width / Renderer.VIRTUAL_WIDTH
        val cell = 250f / kotlin.math.sqrt(s.density)
        val fit = s.fit()
        var n = 0
        for (p in s.spots(s.layout, 250f, size = 0.45f)) {
            if (p.kind == Spot.FILL) {
                // tiny sparkles in the gaps
                val r = cell * s.f(0.03f, 0.055f)
                if ((p.x < r * 2 || p.x > s.w - r * 2) && (p.y < r * 2 || p.y > s.h - r * 2)) continue
                mark.color = (0x55 shl 24) or 0xFFFFFF
                s.canvas.save()
                s.canvas.translate(p.x, p.y)
                s.canvas.rotate(45f)
                s.canvas.drawRect(-r * 0.22f, -r, r * 0.22f, r, mark)
                s.canvas.drawRect(-r, -r * 0.22f, r, r * 0.22f, mark)
                s.canvas.restore()
                continue
            }
            val mood = if (p.kind == Spot.HERO) HeroPack.lead else HeroPack.CALM[(n++ + s.i(0, HeroPack.CALM.size)) % HeroPack.CALM.size]
            val size = cell * s.f(0.40f, 0.48f) * (p.scale / fit) * if (p.kind == Spot.HERO) 1.45f else 1f
            // keep the four corners pure black: nothing is drawn where a cat would reach one
            val m = size * 0.75f
            if ((p.x < m || p.x > s.w - m) && (p.y < m || p.y > s.h - m)) continue
            val bmp = HeroPack.load(mood, s.i(0, HeroPack.LOOKS), (size * pxScale).toInt().coerceAtLeast(64)) ?: continue
            s.canvas.save()
            s.canvas.translate(p.x, p.y)
            s.canvas.rotate(p.rot + s.f(-6f, 6f))
            s.canvas.drawBitmap(bmp, null, RectF(-size / 2, -size / 2, size / 2, size / 2), paint)
            s.canvas.restore()
        }
    }
}

package com.nikopick.zamoled.gen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Supercat wallpaper, earned in Purrcast at every 10 streak days (zamoled://reward?day=N). A big portrait of the hero cat
 * in a halo of light, rays and stars on pure black. It grows richer with the streak: more rays and rings every 10 days.
 * Past 20 days it gets living effects (turning rays, orbiting sparkles, twinkling stars, shooting stars, a breathing glow):
 * [drawBase] is the still picture, [drawEffects] is the moving layer on top, drawn by the live wallpaper and the animated preview.
 */
object SuperCatArt {
    private fun a(color: Int, alpha: Float) = ColorUtils.setAlphaComponent(color, (alpha * 255f).toInt().coerceIn(0, 255))
    private fun tier(day: Int) = (day / 10).coerceIn(1, 8)
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Where the cat sits: x centre, y centre, size in virtual units (1000 wide). */
    fun catBox(w: Float, h: Float): RectF {
        val size = w * 0.74f
        val cy = h * 0.50f
        return RectF(w / 2f - size / 2f, cy - size / 2f, w / 2f + size / 2f, cy + size / 2f)
    }

    private fun star4(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float) {
        fillP.shader = null
        fillP.color = a(color, alpha)
        val p = Path().apply {
            moveTo(x, y - r); quadTo(x, y, x + r * 0.28f, y); quadTo(x, y, x, y + r); quadTo(x, y, x - r * 0.28f, y); quadTo(x, y, x, y - r)
            moveTo(x - r * 0.6f, y); quadTo(x, y, x, y - r * 0.28f); quadTo(x, y, x + r * 0.6f, y); quadTo(x, y, x, y + r * 0.28f); quadTo(x, y, x - r * 0.6f, y)
        }
        c.drawPath(p, fillP)
        fillP.color = a(Color.WHITE, alpha)
        c.drawCircle(x, y, r * 0.16f, fillP)
    }

    private fun ray(c: Canvas, cx: Float, cy: Float, ang: Float, len: Float, half: Float, color: Int, alpha: Float) {
        val rad = Math.toRadians(ang.toDouble())
        val dx = cos(rad).toFloat(); val dy = sin(rad).toFloat()
        val ex = cx + dx * len; val ey = cy + dy * len
        val hw = Math.toRadians(half.toDouble())
        val p = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + (cos(rad - hw) * len).toFloat(), cy + (sin(rad - hw) * len).toFloat())
            lineTo(cx + (cos(rad + hw) * len).toFloat(), cy + (sin(rad + hw) * len).toFloat())
            close()
        }
        fillP.shader = LinearGradient(cx, cy, ex, ey, a(color, alpha), a(color, 0f), Shader.TileMode.CLAMP)
        c.drawPath(p, fillP)
        fillP.shader = null
    }

    /** The still picture: aura, rays, rings, stars, the cat portrait and the day label. */
    fun drawBase(c: Canvas, w: Float, h: Float, day: Int, mood: String, look: Int, c0: Int, c1: Int, c2: Int, seed: Long) {
        val box = catBox(w, h)
        val cx = box.centerX(); val cy = box.centerY()
        val t = tier(day)
        val rnd = Random(seed)
        // deep aura, pure black at the corners
        fillP.shader = RadialGradient(cx, cy, w * 0.92f, intArrayOf(a(c0, 0.42f), a(c1, 0.20f), a(c2, 0.07f), 0), floatArrayOf(0f, 0.38f, 0.72f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, w * 0.92f, fillP)
        fillP.shader = null
        // rays: more every 10 days
        val rays = 14 + t * 4
        val off = rnd.nextFloat() * 360f
        for (i in 0 until rays) ray(c, cx, cy, off + i * 360f / rays, w * 1.05f, if (i % 2 == 0) 2.2f else 1.1f, if (i % 3 == 0) c1 else c0, if (i % 2 == 0) 0.30f else 0.18f)
        // a calm dark disc behind the cat so it pops, with a bright rim
        fillP.shader = RadialGradient(cx, cy, box.width() * 0.52f, intArrayOf(a(Color.BLACK, 0.92f), a(Color.BLACK, 0.75f), a(c0, 0.18f)), floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, box.width() * 0.52f, fillP)
        fillP.shader = null
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = a(c1, 0.95f) }
        val rimGlow = Paint(rim).apply { strokeWidth = 14f; color = a(c0, 0.55f); maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
        c.drawCircle(cx, cy, box.width() * 0.52f, rimGlow)
        c.drawCircle(cx, cy, box.width() * 0.52f, rim)
        // rings: one more every 10 days (up to five), alternate solid and dashed
        val ringCount = (1 + t).coerceAtMost(5)
        for (k in 0 until ringCount) {
            val r = box.width() * 0.52f + 46f + k * 62f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = if (k == 0) 3f else 2f
                color = a(if (k % 2 == 0) c1 else c0, 0.55f - k * 0.08f)
                if (k % 2 == 1) pathEffect = DashPathEffect(floatArrayOf(14f, 18f), 0f)
            }
            c.drawCircle(cx, cy, r, paint)
        }
        // stars
        val corner = w * 0.16f
        val star = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(170) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            if ((x < corner || x > w - corner) && (y < corner || y > h - corner)) return@repeat
            val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()
            if (d < box.width() * 0.52f) return@repeat
            star.color = a(Color.WHITE, 0.15f + rnd.nextFloat() * rnd.nextFloat() * 0.6f)
            c.drawCircle(x, y, 0.7f + rnd.nextFloat() * rnd.nextFloat() * 2.0f, star)
        }
        repeat(10 + t * 2) {
            val x = rnd.nextFloat() * (w - 2 * corner) + corner; val y = rnd.nextFloat() * h
            val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()
            if (d < box.width() * 0.62f || (y < corner && (x < corner * 1.4f || x > w - corner * 1.4f)) || (y > h - corner && (x < corner * 1.4f || x > w - corner * 1.4f))) return@repeat
            star4(c, x, y, 10f + rnd.nextFloat() * 22f, if (rnd.nextBoolean()) c0 else c1, 0.55f + rnd.nextFloat() * 0.4f)
        }
        // the hero cat in its big portrait
        val bmp = HeroPack.portrait(mood, look)
        if (bmp != null) {
            val ph = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            c.drawBitmap(bmp, null, box, ph)
        }
        // day label
        val ly = box.bottom + w * 0.21f
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD); textSize = w * 0.21f; textAlign = Paint.Align.CENTER; color = c1; letterSpacing = 0.02f }
        val glow = Paint(big).apply { color = a(c0, 0.8f); maskFilter = android.graphics.BlurMaskFilter(26f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
        c.drawText("$day", cx, ly, glow)
        c.drawText("$day", cx, ly, big)
        val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textSize = w * 0.032f; textAlign = Paint.Align.CENTER; color = a(Color.WHITE, 0.78f); letterSpacing = 0.34f }
        c.drawText("DAY STREAK  ·  SUPERCAT", cx, ly + w * 0.075f, cap)
    }

    /** The moving layer for streaks over 20 days. [t] is seconds. Cheap shapes only, so a live wallpaper can draw it every frame. */
    fun drawEffects(c: Canvas, w: Float, h: Float, day: Int, t: Float, c0: Int, c1: Int, c2: Int, seed: Long) {
        val box = catBox(w, h)
        val cx = box.centerX(); val cy = box.centerY()
        val tr = tier(day)
        val rnd = Random(seed xor 0x5DEECE66DL)
        // breathing light behind the cat
        val breath = 0.5f + 0.5f * sin(t * 1.4f)
        fillP.shader = RadialGradient(cx, cy, box.width() * (0.62f + 0.05f * breath), intArrayOf(a(c1, 0.0f), a(c1, 0.10f + 0.10f * breath), a(c0, 0f)), floatArrayOf(0.70f, 0.85f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, box.width() * 0.7f, fillP)
        fillP.shader = null
        // a slowly turning fan of light
        val spin = t * 5f
        val rays = 8 + tr * 2
        for (i in 0 until rays) ray(c, cx, cy, spin + i * 360f / rays, w * 1.0f, 1.6f, if (i % 2 == 0) c1 else c0, 0.16f + 0.08f * sin(t * 1.1f + i))
        // twinkling stars
        val sp = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(46) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            val ph = rnd.nextFloat() * 6.28f; val sp2 = 0.8f + rnd.nextFloat() * 2.2f
            val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()
            val ok = d > box.width() * 0.56f && !((x < w * 0.12f || x > w * 0.88f) && (y < w * 0.12f || y > h - w * 0.12f))
            if (ok) {
                val al = (0.5f + 0.5f * sin(t * sp2 + ph)).let { it * it }
                if (al > 0.05f) star4(c, x, y, 7f + 12f * al, if (it % 2 == 0) Color.WHITE else c1, 0.25f + 0.7f * al)
            }
        }
        // sparkles orbiting the rings
        val orbit = 4 + tr * 2
        for (i in 0 until orbit) {
            val ring = i % (1 + tr).coerceAtMost(5)
            val r = box.width() * 0.52f + 46f + ring * 62f
            val dir = if (ring % 2 == 0) 1f else -1f
            val ang = dir * (t * (0.45f + 0.12f * ring)) + i * 6.2832f / orbit * 1.618f
            val x = cx + cos(ang) * r; val y = cy + sin(ang) * r
            sp.color = a(c0, 0.35f); c.drawCircle(x, y, 13f, sp)
            star4(c, x, y, 16f, if (i % 2 == 0) c1 else Color.WHITE, 0.95f)
        }
        // a shooting star every few seconds
        val period = 4.2f
        val ph = ((t + 1.1f) % period) / 1.0f
        if (ph in 0f..1f) {
            val k = (((t + 1.1f) / period).toInt()) % 5
            val sx = w * (0.15f + 0.17f * k); val sy = h * (0.04f + 0.03f * k)
            val x = sx + ph * w * 0.55f; val y = sy + ph * h * 0.16f
            val tail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(x, y, x - w * 0.16f, y - h * 0.045f, a(Color.WHITE, 0.95f * (1f - ph)), 0, Shader.TileMode.CLAMP)
            }
            c.drawLine(x, y, x - w * 0.16f, y - h * 0.045f, tail)
        }
        // from 30 days: ribbons of colour sweeping the lower sky
        if (day >= 30) {
            val rb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 6f }
            for (n in 0 until 3) {
                val p = Path()
                val baseY = h * (0.80f + 0.045f * n)
                var x = -20f
                p.moveTo(x, baseY)
                while (x <= w + 20f) { x += 20f; p.lineTo(x, baseY + 26f * sin(x / 150f + t * (0.9f + 0.3f * n) + n * 2f)) }
                rb.color = a(if (n == 1) c1 else c0, 0.35f - n * 0.07f)
                c.drawPath(p, rb)
            }
        }
    }
}

/** The Supercat reward style: the still picture, with the effects frozen mid-motion past 20 days. Hidden from browsing; rewards open it by link. */
object SuperCatStyle : Style("supercat", "Supercat", Category.DOODLE) {
    override val usesPalette: Boolean get() = true
    override val usesDetail: Boolean get() = false
    override val minBlack: Float get() = 0.4f

    /** Which mask look and mood the portrait uses for a seed (the same reward always shows the same cat). */
    fun lookFor(seed: Long): Int = Math.floorMod(seed.toInt(), HeroPack.LOOKS)

    override fun draw(s: Scene) {
        val day = s.text.toIntOrNull() ?: 10
        val mood = HeroPack.lead.takeIf { it in HeroPack.ALL } ?: "smile"
        val look = lookFor(s.i(0, 1000).toLong())
        val seed = day * 7919L
        SuperCatArt.drawBase(s.canvas, s.w, s.h, day, heroEmotion(mood, day), look, s.color(0), s.color(1), s.color(2), seed)
        if (heroAnimated(day)) SuperCatArt.drawEffects(s.canvas, s.w, s.h, day, 1.7f, s.color(0), s.color(1), s.color(2), seed)
    }
}

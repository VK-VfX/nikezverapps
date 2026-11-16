package com.nikopick.zamoled.gen

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Space: a star field, one small subject and a thin edge of light. Everything else is pure black,
 * so well over 95% of the pixels are off.
 */
object SpaceWorld {
    val styles: List<Style> = listOf(Ascent, LoneSatellite, Comet)
}

private val SPACE_PALETTES = listOf("mono", "ocean", "gold", "sakura", "violet")

/** Scatter of stars: mostly faint, a few bright. [maxY] keeps them above the planet. */
private fun Scene.spaceStars(n: Int, maxY: Float = h) {
    repeat(count(n)) {
        val b = f()
        val r = if (b > 0.94f) 1.9f else if (b > 0.72f) 1.2f else 0.75f
        dot(f(0f, w), f(0f, maxY), r, Color.WHITE, (60 + b * 195).toInt())
    }
}

/**
 * A thin, bright planet edge: a crescent hugging the top of a huge circle that sits below the screen.
 * Built as a polygon, so blurring it only touches a strip of pixels.
 */
private fun Scene.limb(yTop: Float, thick: Float, tint: Int) {
    val bigR = w * 3.4f
    val n = 90
    val x0 = -30f
    val x1 = w + 30f
    fun outer(x: Float): Float = yTop + bigR - sqrt(bigR * bigR - (x - cx) * (x - cx))
    fun taper(x: Float): Float {
        val t = (x - cx) / (w * 0.62f)
        return (1f - t * t).coerceAtLeast(0f)
    }
    val path = Path()
    for (k in 0..n) {
        val x = x0 + (x1 - x0) * k / n
        if (k == 0) path.moveTo(x, outer(x)) else path.lineTo(x, outer(x))
    }
    for (k in n downTo 0) {
        val x = x0 + (x1 - x0) * k / n
        path.lineTo(x, outer(x) + thick * taper(x))
    }
    path.close()
    canvas.drawPath(path, fill(tint, 55, thick * 5f))
    canvas.drawPath(path, fill(tint, 120, thick * 1.8f))
    canvas.drawPath(path, fill(lighten(tint, 0.6f), 255))
}

/** The shuttle silhouette (nose up) in unit coordinates, scaled by [u]. */
private fun shuttlePath(x: Float, y: Float, u: Float): Path = Path().apply {
    moveTo(x, y - u)
    cubicTo(x + 0.10f * u, y - 0.80f * u, x + 0.12f * u, y - 0.30f * u, x + 0.12f * u, y + 0.20f * u)
    lineTo(x + 0.40f * u, y + 0.62f * u)
    lineTo(x + 0.12f * u, y + 0.55f * u)
    lineTo(x + 0.10f * u, y + 0.72f * u)
    lineTo(x - 0.10f * u, y + 0.72f * u)
    lineTo(x - 0.12f * u, y + 0.55f * u)
    lineTo(x - 0.40f * u, y + 0.62f * u)
    lineTo(x - 0.12f * u, y + 0.20f * u)
    cubicTo(x - 0.12f * u, y - 0.30f * u, x - 0.10f * u, y - 0.80f * u, x, y - u)
    close()
}

/** A shuttle climbing away from the planet's edge on a hairline trail. */
object Ascent : Style("space_ascent", "Ascent", Category.SPACE) {
    override val autoPalettes = SPACE_PALETTES
    override val usesDetail: Boolean get() = true

    override fun draw(s: Scene) {
        val limbY = s.h * s.f(0.5f, 0.62f)
        val tint = lighten(s.color(0), 0.5f)
        s.spaceStars(85, limbY)
        val x = s.w * s.f(0.34f, 0.66f)
        val y = limbY * s.f(0.28f, 0.5f)
        val u = s.w * 0.021f
        val trail = limbY * s.f(0.34f, 0.52f)
        val shader = LinearGradient(x, y, x, y + trail, Color.argb(235, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            style = Paint.Style.STROKE
            strokeWidth = 6f
            alpha = 90
            maskFilter = android.graphics.BlurMaskFilter(7f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        s.canvas.drawLine(x, y + u * 0.7f, x, y + trail, glowPaint)
        s.canvas.drawLine(x, y + u * 0.7f, x, y + trail, line)
        s.canvas.drawPath(shuttlePath(x, y, u), s.fill(Color.WHITE))
        s.limb(limbY, s.w * 0.007f, tint)
    }
}

/** A satellite drifting above the limb. */
object LoneSatellite : Style("space_satellite", "Lone Satellite", Category.SPACE) {
    override val autoPalettes = SPACE_PALETTES

    override fun draw(s: Scene) {
        val limbY = s.h * s.f(0.52f, 0.66f)
        val tint = lighten(s.color(0), 0.45f)
        s.spaceStars(80, limbY)
        val x = s.w * s.f(0.3f, 0.7f)
        val y = limbY * s.f(0.32f, 0.62f)
        val tilt = s.f(-28f, 28f)
        val k = s.w * 0.0036f
        val c = s.canvas
        c.save()
        c.translate(x, y)
        c.rotate(tilt)
        c.scale(k, k)
        val white = s.fill(Color.WHITE)
        c.drawRect(-7f, -5f, 7f, 5f, white)
        c.drawRect(-42f, -3f, -11f, 3f, white)
        c.drawRect(11f, -3f, 42f, 3f, white)
        val gap = s.fill(Color.BLACK)
        for (i in 0 until 4) {
            c.drawRect(-40f + i * 7.5f, -3f, -39f + i * 7.5f, 3f, gap)
            c.drawRect(13f + i * 7.5f, -3f, 14f + i * 7.5f, 3f, gap)
        }
        val ink = s.stroke(Color.WHITE, 1.4f)
        c.drawLine(0f, -5f, 0f, -16f, ink)
        c.drawArc(-5f, -24f, 5f, -14f, 20f, 140f, false, ink)
        c.restore()
        s.limb(limbY, s.w * 0.006f, tint)
    }
}

/** A single comet with a long, thin tail. */
object Comet : Style("space_comet", "Comet", Category.SPACE) {
    override val autoPalettes = listOf("mono", "ocean", "violet", "aurora")

    override fun draw(s: Scene) {
        s.spaceStars(115)
        val tint = lighten(s.color(0), 0.55f)
        val fromRight = s.chance(0.5f)
        val hx = s.w * (if (fromRight) s.f(0.58f, 0.8f) else s.f(0.2f, 0.42f))
        val hy = s.h * s.f(0.18f, 0.4f)
        val dir = (if (fromRight) PI.toFloat() * 0.78f else PI.toFloat() * 0.22f) + s.f(-0.08f, 0.08f)
        for (k in 0 until s.count(22)) {
            val a = dir + s.f(-0.05f, 0.05f)
            val len = s.h * s.f(0.14f, 0.36f)
            val ex = hx + cos(a) * len
            val ey = hy + sin(a) * len
            val sh = LinearGradient(hx, hy, ex, ey, Color.argb(95, Color.red(tint), Color.green(tint), Color.blue(tint)), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = sh
                style = Paint.Style.STROKE
                strokeWidth = s.f(0.8f, 3.2f)
                strokeCap = Paint.Cap.ROUND
            }
            s.canvas.drawLine(hx, hy, ex, ey, p)
        }
        s.glowDot(hx, hy, 3.2f, Color.WHITE, glow = 26f)
    }
}

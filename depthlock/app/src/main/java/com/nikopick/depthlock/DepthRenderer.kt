package com.nikopick.depthlock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Draws the three layers back to front: photo, clock, subject cut-out.
 * The subject sits on top of the clock, which is the whole trick.
 * Used by the live wallpaper, the screen saver, the always-on screen and the in-app preview.
 */
class DepthRenderer {
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val matrix = Matrix()
    private val bounds = Rect()

    private class Line(val text: String, val baseOff: Float, val second: Boolean)

    /** The clock before stretch: lines and extents measured from the clock's centre. */
    private class Block(val lines: List<Line>, val top: Float, val bottom: Float, val halfW: Float, val size: Float)

    /**
     * @param tiltX,tiltY -1..1, phone tilt. Layers shift by different amounts, which gives the depth.
     * @param dim 1 = full brightness, lower values darken the whole frame (always-on screen).
     * @param shiftX,shiftY pixel offset for the always-on screen, so no pixel stays lit for hours.
     */
    fun draw(
        c: Canvas, w: Int, h: Int, assets: DepthAssets, s: ClockSettings,
        tiltX: Float = 0f, tiltY: Float = 0f, dim: Float = 1f, showClock: Boolean = true,
        shiftX: Float = 0f, shiftY: Float = 0f, nowMs: Long = System.currentTimeMillis(),
    ) {
        c.drawColor(Color.BLACK)
        val zoom = if (s.tilt) 1.05f else 1f
        val tx = if (s.tilt) tiltX else 0f
        val ty = if (s.tilt) tiltY else 0f

        assets.bg?.let { drawLayer(c, it, w, h, zoom, shiftX + layerDx(tx, w, 0.5f), shiftY + layerDy(ty, h, 0.5f)) }
        if (showClock) {
            drawClock(c, w, h, s, shiftX + layerDx(tx, w, 1f), shiftY + layerDy(ty, h, 1f), nowMs)
        }
        assets.subject?.let { drawLayer(c, it, w, h, zoom, shiftX + layerDx(tx, w, 1.6f), shiftY + layerDy(ty, h, 1.6f)) }

        if (dim < 1f) c.drawColor(Color.argb(((1f - dim) * 255).toInt().coerceIn(0, 255), 0, 0, 0))
    }

    /** Where the clock sits on a w x h screen, after stretch. Used to place it behind the subject. */
    fun clockRect(w: Int, h: Int, s: ClockSettings): RectF {
        val b = block(w, s, Date(0), sample = true)
        val cy = h * s.y
        val cx = w / 2f + w * s.xOffset
        return RectF(cx - b.halfW, cy + b.top * s.stretch, cx + b.halfW, cy + b.bottom * s.stretch)
    }

    fun lineCount(s: ClockSettings) = if (s.style == 1) 2 else 1

    /** One line of the clock in solid white, for measuring what the subject would hide. */
    fun drawClockLine(c: Canvas, w: Int, h: Int, s: ClockSettings, line: Int) {
        val b = block(w, s, Date(0), sample = true)
        timePaint.textSize = b.size
        timePaint.color = Color.WHITE
        timePaint.alpha = 255
        val cx = w / 2f + w * s.xOffset
        val cy = h * s.y
        c.save()
        c.scale(1f, s.stretch, cx, cy)
        val l = b.lines[line]
        c.drawText(l.text, cx, cy + l.baseOff, timePaint)
        c.restore()
    }

    private fun layerDx(t: Float, w: Int, depth: Float) = -t * w * 0.018f * depth
    private fun layerDy(t: Float, h: Int, depth: Float) = t * h * 0.010f * depth

    private fun drawLayer(c: Canvas, bmp: Bitmap, w: Int, h: Int, zoom: Float, dx: Float, dy: Float) {
        val k = max(w / bmp.width.toFloat(), h / bmp.height.toFloat()) * zoom
        matrix.reset()
        matrix.postScale(k, k)
        matrix.postTranslate((w - bmp.width * k) / 2f + dx, (h - bmp.height * k) / 2f + dy)
        c.drawBitmap(bmp, matrix, bmpPaint)
    }

    private fun fmt(pattern: String, d: Date) = SimpleDateFormat(pattern, Locale.getDefault()).format(d)

    /** [sample] uses wide digits (08:08) so layout measuring does not depend on the time of day. */
    private fun block(w: Int, s: ClockSettings, now: Date, sample: Boolean = false): Block {
        timePaint.typeface = Looks.typeface(s.font, s.weight)
        timePaint.textSize = 200f
        if (s.style == 1) {
            // Hours over minutes, like the Pixel stacked clocks.
            val hh = if (sample) "08" else fmt(if (s.h24) "HH" else "hh", now)
            val mm = if (sample) "08" else fmt("mm", now)
            val targetW = w * s.size * 0.55f
            timePaint.textSize = 200f * targetW / timePaint.measureText("00")
            timePaint.getTextBounds("0", 0, 1, bounds)
            val d = (bounds.bottom - bounds.top).toFloat()
            val gap = d * 0.12f
            val mid = -(bounds.top + bounds.bottom) / 2f
            val c1 = -(d + gap) / 2f
            val c2 = (d + gap) / 2f
            return Block(listOf(Line(hh, c1 + mid, false), Line(mm, c2 + mid, true)), c1 - d / 2, c2 + d / 2, targetW / 2, timePaint.textSize)
        }
        val time = if (sample) "8:08" else fmt(if (s.h24) "HH:mm" else "h:mm", now)
        val fit = (w * s.size) / timePaint.measureText(time)
        timePaint.textSize = 200f * fit
        timePaint.getTextBounds(time, 0, time.length, bounds)
        val d = (bounds.bottom - bounds.top).toFloat()
        return Block(listOf(Line(time, -(bounds.top + bounds.bottom) / 2f, false)), -d / 2, d / 2, w * s.size / 2, timePaint.textSize)
    }

    private fun drawClock(c: Canvas, w: Int, h: Int, s: ClockSettings, dx: Float, dy: Float, nowMs: Long) {
        val now = Date(nowMs)
        val pal = Looks.palettes[s.color]
        val a = (s.alpha.coerceIn(0f, 1f) * 255).toInt()
        val b = block(w, s, now)
        timePaint.textSize = b.size

        val cx = w / 2f + w * s.xOffset + dx
        val cy = h * s.y + dy
        c.save()
        c.scale(1f, s.stretch, cx, cy)
        for (l in b.lines) {
            timePaint.color = if (l.second) pal.bottom else pal.top
            timePaint.alpha = a
            c.drawText(l.text, cx, cy + l.baseOff, timePaint)
        }
        c.restore()

        if (s.date) {
            datePaint.typeface = Looks.typeface(s.font, s.weight)
            datePaint.color = pal.top
            datePaint.alpha = (a * 0.9f).toInt()
            datePaint.textSize = w * 0.05f
            c.drawText(fmt("EEE d MMM", now), cx, cy + b.top * s.stretch - w * 0.035f, datePaint)
        }
    }
}

/**
 * Builds the clock layout around the subject. It draws the real clock glyphs at many sizes, heights and
 * positions against the subject's silhouette, and keeps the layout where
 *  - every line of the time stays readable (most of each line is NOT hidden), and
 *  - enough of the clock is behind the subject to give the depth effect.
 * It then checks the clock colours against the photo behind them.
 */
object SmartFit {
    class Fit(val settings: ClockSettings, val covered: Float, val minVisible: Float, val recoloured: Boolean)

    private class Cfg(val style: Int, val size: Float, val stretch: Float)

    fun fit(bg: Bitmap?, subject: Bitmap, base: ClockSettings): Fit {
        val cols = 120
        val rows = (cols * subject.height / subject.width.toFloat()).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(subject, cols, rows, true)
        val px = IntArray(cols * rows)
        small.getPixels(px, 0, cols, 0, 0, cols, rows)
        val solid = BooleanArray(px.size) { (px[it] ushr 24) > 110 }

        val renderer = DepthRenderer()
        val bmp = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val buf = IntArray(cols * rows)

        val cfgs = ArrayList<Cfg>()
        for (sz in listOf(1.1f, 1.0f, 0.9f, 0.8f, 0.7f)) cfgs.add(Cfg(1, sz, 1f))
        for (sz in listOf(1.0f, 0.9f, 0.8f)) for (st in listOf(1f, 1.4f, 1.8f, 2.2f)) cfgs.add(Cfg(0, sz, st))
        val xs = listOf(-0.2f, -0.1f, 0f, 0.1f, 0.2f)
        val topLimit = (rows * (if (base.date) 0.13f else 0.05f)).toInt()

        var best: Fit? = null
        var bestScore = -1e9f
        for (cfg in cfgs) {
            val s0 = base.copy(style = cfg.style, size = cfg.size, stretch = cfg.stretch, y = 0.5f, xOffset = 0f)
            val glyphs = (0 until renderer.lineCount(s0)).map { line ->
                bmp.eraseColor(0)
                renderer.drawClockLine(canvas, cols, rows, s0, line)
                bmp.getPixels(buf, 0, cols, 0, 0, cols, rows)
                val cells = ArrayList<Int>()
                for (i in buf.indices) if ((buf[i] ushr 24) > 128) cells.add(i)
                cells.toIntArray()
            }
            if (glyphs.any { it.isEmpty() }) continue

            for (yi in 8..64) {
                val y = yi / 100f
                val dy = ((y - 0.5f) * rows).roundToInt()
                for (x in xs) {
                    val dx = (x * cols).roundToInt()
                    var ok = true
                    var covered = 0
                    var total = 0
                    var minVis = 1f
                    for (g in glyphs) {
                        var c = 0
                        for (cell in g) {
                            val gx = cell % cols + dx
                            val gy = cell / cols + dy
                            if (gx < 0 || gx >= cols || gy < topLimit || gy >= rows) { ok = false; break }
                            if (solid[gy * cols + gx]) c++
                        }
                        if (!ok) break
                        covered += c
                        total += g.size
                        minVis = min(minVis, 1f - c / g.size.toFloat())
                    }
                    if (!ok || total == 0) continue

                    val cov = covered / total.toFloat()
                    // Readable: every line at least 60% visible. Below 40% the line is as good as gone.
                    val legible = ((minVis - 0.4f) / 0.2f).coerceIn(0f, 1f)
                    // Depth: best when about a quarter of the clock sits behind the subject.
                    val depth = max(0f, 1f - abs(cov - 0.25f) / 0.25f)
                    val sizeN = if (cfg.style == 1) cfg.size / 1.1f else cfg.size
                    val score = legible * (0.7f * depth + 0.3f * sizeN) + 0.1f * minVis - 0.4f * abs(x)
                    if (score > bestScore) {
                        bestScore = score
                        best = Fit(base.copy(style = cfg.style, size = cfg.size, stretch = cfg.stretch, y = y, xOffset = x), cov, minVis, false)
                    }
                }
            }
        }
        val chosen = best ?: return Fit(base, 0f, 1f, false)
        return recolour(chosen, bg, renderer, cols, rows)
    }

    /** Keeps the user's palette when it reads on the photo behind the clock, otherwise picks the one that does. */
    private fun recolour(f: Fit, bg: Bitmap?, renderer: DepthRenderer, cols: Int, rows: Int): Fit {
        if (bg == null) return f
        val small = Bitmap.createScaledBitmap(bg, cols, rows, true)
        val r = renderer.clockRect(cols, rows, f.settings)
        val x0 = r.left.toInt().coerceIn(0, cols - 1)
        val x1 = r.right.toInt().coerceIn(x0 + 1, cols)
        val y0 = r.top.toInt().coerceIn(0, rows - 1)
        val y1 = r.bottom.toInt().coerceIn(y0 + 1, rows)
        var sum = 0f
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) { sum += luminance(small.getPixel(x, y)); n++ }
        val l = sum / n

        fun score(p: Looks.Palette): Float {
            val top = contrast(luminance(p.top), l)
            return if (f.settings.style == 1) min(top, contrast(luminance(p.bottom), l)) else top
        }
        if (score(Looks.palettes[f.settings.color]) >= 3f) return f
        val idx = Looks.palettes.indices.maxByOrNull { score(Looks.palettes[it]) } ?: return f
        return Fit(f.settings.copy(color = idx), f.covered, f.minVisible, idx != f.settings.color)
    }

    private fun luminance(c: Int): Float {
        fun ch(v: Int): Float {
            val f = v / 255f
            return if (f <= 0.03928f) f / 12.92f else ((f + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * ch(Color.red(c)) + 0.7152f * ch(Color.green(c)) + 0.0722f * ch(Color.blue(c))
    }

    private fun contrast(a: Float, b: Float) = (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

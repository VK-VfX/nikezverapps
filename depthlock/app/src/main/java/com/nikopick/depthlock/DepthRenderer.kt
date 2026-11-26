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
        val b = block(w, s, Date(0))
        val cy = h * s.y
        return RectF(w / 2f - b.halfW, cy + b.top * s.stretch, w / 2f + b.halfW, cy + b.bottom * s.stretch)
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

    private fun block(w: Int, s: ClockSettings, now: Date): Block {
        timePaint.typeface = Looks.typeface(s.font, s.weight)
        timePaint.textSize = 200f
        if (s.style == 1) {
            // Hours over minutes, like the Pixel stacked clocks.
            val hh = fmt(if (s.h24) "HH" else "hh", now)
            val mm = fmt("mm", now)
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
        val time = fmt(if (s.h24) "HH:mm" else "h:mm", now)
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

        val cx = w / 2f + dx
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

/** Finds the clock height where the subject covers the most of it, so the depth effect shows on any photo. */
object AutoPlace {
    /** @return best height (fraction of screen) and how much of the clock the subject covers (0..1). */
    fun best(subject: Bitmap, s: ClockSettings): Pair<Float, Float> {
        val cols = 96
        val rows = (cols * subject.height / subject.width.toFloat()).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(subject, cols, rows, true)
        val px = IntArray(cols * rows)
        small.getPixels(px, 0, cols, 0, 0, cols, rows)
        // Running totals, so any rectangle is counted in four lookups.
        val sum = Array(rows + 1) { IntArray(cols + 1) }
        for (y in 0 until rows) for (x in 0 until cols) {
            val solid = if ((px[y * cols + x] ushr 24) > 100) 1 else 0
            sum[y + 1][x + 1] = solid + sum[y][x + 1] + sum[y + 1][x] - sum[y][x]
        }
        val refW = 1000
        val refH = (refW * subject.height / subject.width.toFloat()).toInt()
        val renderer = DepthRenderer()
        var bestY = s.y
        var bestScore = -1f
        var bestCov = 0f
        var yy = 0.10f
        while (yy <= 0.62f) {
            val r = renderer.clockRect(refW, refH, s.copy(y = yy))
            val x0 = (r.left / refW * cols).toInt().coerceIn(0, cols - 1)
            val x1 = (r.right / refW * cols).toInt().coerceIn(x0 + 1, cols)
            val y0 = (r.top / refH * rows).toInt().coerceIn(0, rows - 1)
            val y1 = (r.bottom / refH * rows).toInt().coerceIn(y0 + 1, rows)
            val area = (x1 - x0) * (y1 - y0)
            val cov = (sum[y1][x1] - sum[y0][x1] - sum[y1][x0] + sum[y0][x0]).toFloat() / area
            // A third of the clock hidden reads best; past that, prefer the spot nearest the usual height.
            val score = min(cov, 0.35f) - abs(yy - 0.36f) * 0.02f
            if (score > bestScore) { bestScore = score; bestY = yy; bestCov = cov }
            yy += 0.01f
        }
        return bestY to bestCov
    }
}

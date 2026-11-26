package com.nikopick.depthlock

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.service.dreams.DreamService
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import java.util.Calendar

/**
 * A dimmed copy of the lock screen that moves a few pixels every minute (OLED burn-in).
 * Shared by the screen saver and the always-on screen.
 */
class DepthView(context: Context) : View(context) {
    private val renderer = DepthRenderer()
    private var assets = DepthAssets(null, null)
    private var rev = -1L

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(tick, f, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(tick, f)
    }

    override fun onDetachedFromWindow() {
        runCatching { context.unregisterReceiver(tick) }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val r = Prefs.rev(context)
        if (r != rev) { assets = Store.load(context); rev = r }
        val minute = Calendar.getInstance().get(Calendar.MINUTE)
        val step = resources.displayMetrics.density * 4f
        val sx = ((minute % 5) - 2) * step
        val sy = (((minute / 5) % 5) - 2) * step
        val s = Prefs.load(context)
        renderer.draw(canvas, width, height, assets.forAod(s.aodMode), s, dim = s.aodDim, shiftX = sx, shiftY = sy)
    }
}

/** Shows while the phone charges or sits on a dock, once chosen under Settings, Display, Screen saver. */
class DepthDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        setContentView(DepthView(this))
    }
}

/** Keeps the screen on, dim, over the lock screen. Tap anywhere to leave. It uses battery. */
class AodActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0.02f }
        val v = DepthView(this)
        v.setOnClickListener { finish() }
        v.contentDescription = "Always-on screen. Tap to close."
        setContentView(v)
        window.insetsController?.hide(WindowInsets.Type.systemBars())
    }
}

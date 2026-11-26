package com.nikopick.depthlock

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.abs

/**
 * The live wallpaper. Set it for the lock screen: the clock is drawn between the photo and the subject.
 * The phone's own lock-screen clock is drawn by the system above every wallpaper, see the README.
 */
class DepthWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = DepthEngine()

    inner class DepthEngine : Engine(), SensorEventListener {
        private val handler = Handler(Looper.getMainLooper())
        private val renderer = DepthRenderer()
        private var assets = DepthAssets(null, null)
        private var rev = -1L
        private var settings = ClockSettings()
        private var w = 0
        private var h = 0
        private var visible = false
        private var ambient = false
        private var receiversOn = false

        private var targetX = 0f
        private var targetY = 0f
        private var tiltX = 0f
        private var tiltY = 0f
        private var baseY = Float.NaN
        private var lastSig = ""

        private val sensors by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }

        private val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = draw(force = true)
        }
        private val frame = object : Runnable {
            override fun run() {
                tiltX += (targetX - tiltX) * 0.15f
                tiltY += (targetY - tiltY) * 0.15f
                draw()
                if (visible && settings.tilt && !ambient) handler.postDelayed(this, 33)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            w = width; h = height
            draw(force = true)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) = draw(force = true)

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(frame)
            if (visible) {
                refresh()
                registerTime()
                if (settings.tilt && !ambient) {
                    sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                        sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                    }
                }
                draw(force = true)
                if (settings.tilt && !ambient) handler.post(frame)
            } else {
                unregisterTime()
                sensors.unregisterListener(this)
                baseY = Float.NaN
            }
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            unregisterTime()
            sensors.unregisterListener(this)
        }

        /**
         * Called by the system on devices that dim the wallpaper for an always-on display.
         * It is not part of the public SDK, so this works only where the system calls it.
         */
        @Suppress("unused")
        fun onAmbientModeChanged(inAmbientMode: Boolean, animationDuration: Long) {
            ambient = inAmbientMode
            if (inAmbientMode) { handler.removeCallbacks(frame); sensors.unregisterListener(this) }
            else if (visible) onVisibilityChanged(true)
            draw(force = true)
        }

        override fun onSensorChanged(e: SensorEvent) {
            // Tilt relative to how the phone is usually held: the baseline follows slowly.
            val x = (-e.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            val y = (e.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            baseY = if (baseY.isNaN()) y else baseY + (y - baseY) * 0.01f
            targetX = (x * 2.5f).coerceIn(-1f, 1f)
            targetY = ((y - baseY) * 2.5f).coerceIn(-1f, 1f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun registerTime() {
            if (receiversOn) return
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(timeReceiver, f, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(timeReceiver, f)
            }
            receiversOn = true
        }

        private fun unregisterTime() {
            if (!receiversOn) return
            runCatching { unregisterReceiver(timeReceiver) }
            receiversOn = false
        }

        private fun refresh() {
            settings = Prefs.load(this@DepthWallpaperService)
            val r = Prefs.rev(this@DepthWallpaperService)
            if (r != rev) {
                assets = Store.load(this@DepthWallpaperService)
                rev = r
            }
        }

        private fun showClock(): Boolean {
            if (isPreview || Build.VERSION.SDK_INT < 34) return true
            val flags = wallpaperFlags
            val homeOnly = (flags and WallpaperManager.FLAG_SYSTEM) != 0 && (flags and WallpaperManager.FLAG_LOCK) == 0
            return !homeOnly || settings.clockOnHome
        }

        private fun draw(force: Boolean = false) {
            if (!visible || w == 0 || h == 0) return
            refresh()
            val sig = "${System.currentTimeMillis() / 60000}|${(tiltX * 500).toInt()}|${(tiltY * 500).toInt()}|$ambient|$rev|$settings"
            if (!force && sig == lastSig) return
            lastSig = sig

            val holder = surfaceHolder
            val c = try { holder.lockHardwareCanvas() } catch (_: Exception) { null } ?: return
            try {
                renderer.draw(
                    c, w, h, if (ambient) assets.forAod(settings.aodMode) else assets, settings, tiltX, tiltY,
                    dim = if (ambient) settings.aodDim else 1f, showClock = showClock(),
                )
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }
    }
}

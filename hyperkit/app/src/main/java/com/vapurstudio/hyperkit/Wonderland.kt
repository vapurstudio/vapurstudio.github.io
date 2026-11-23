package com.vapurstudio.hyperkit

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder

/** Wonderland: a live wallpaper that tilts your own photo with the phone (parallax depth). */
class Wonderland : WallpaperService() {
    override fun onCreateEngine(): Engine = ParallaxEngine()

    inner class ParallaxEngine : Engine(), SensorEventListener, Choreographer.FrameCallback {
        private val sensors = getSystemService(SensorManager::class.java)
        private var bitmap: Bitmap? = null
        private var loadedUri: String? = null
        private var w = 0
        private var h = 0
        private var tx = 0f; private var ty = 0f      // target offset -1..1
        private var cx = 0f; private var cy = 0f      // current offset
        private var baseP = Float.NaN; private var baseR = 0f
        private var visible = false
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val dst = RectF()
        private val rot = FloatArray(9)
        private val ori = FloatArray(3)

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            w = width; h = height; load(true); draw()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                load(false)
                baseP = Float.NaN
                val s = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                if (s != null) sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                sensors.unregisterListener(this)
                Choreographer.getInstance().removeFrameCallback(this)
            }
        }

        override fun onDestroy() {
            sensors.unregisterListener(this)
            Choreographer.getInstance().removeFrameCallback(this)
            super.onDestroy()
        }

        private fun load(force: Boolean) {
            val uri = Prefs.get(this@Wonderland).getString(Prefs.WL_URI, null)
            if (!force && uri == loadedUri && bitmap != null) return
            loadedUri = uri
            bitmap = if (uri == null || w == 0) null else runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, Uri.parse(uri))) { dec, info, _ ->
                    val s = maxOf(w * 1.3f / info.size.width, h * 1.3f / info.size.height).coerceAtMost(1f)
                    dec.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                }
            }.getOrNull()
        }

        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rot, e.values)
            SensorManager.getOrientation(rot, ori)
            val pitch = ori[1]; val roll = ori[2]
            if (baseP.isNaN()) { baseP = pitch; baseR = roll }
            // slowly re-centre so holding the phone at any angle looks neutral
            baseP += (pitch - baseP) * 0.01f; baseR += (roll - baseR) * 0.01f
            tx = ((roll - baseR) / 0.5f).coerceIn(-1f, 1f)
            ty = ((pitch - baseP) / 0.5f).coerceIn(-1f, 1f)
        }

        override fun onAccuracyChanged(s: Sensor?, a: Int) {}

        override fun doFrame(frameTimeNanos: Long) {
            cx += (tx - cx) * 0.12f; cy += (ty - cy) * 0.12f
            draw()
            if (visible) Choreographer.getInstance().postFrameCallback(this)
        }

        private fun draw() {
            val holder = surfaceHolder
            val c = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                c.drawColor(Color.BLACK)
                val b = bitmap
                val p = Prefs.get(this@Wonderland)
                if (b != null) {
                    val depth = p.getInt(Prefs.WL_DEPTH, 8) / 100f
                    val scale = maxOf(w * (1 + depth * 2) / b.width, h * (1 + depth * 2) / b.height)
                    val bw = b.width * scale; val bh = b.height * scale
                    val ox = (w - bw) / 2 + cx * w * depth
                    val oy = (h - bh) / 2 + cy * h * depth
                    dst.set(ox, oy, ox + bw, oy + bh)
                    c.drawBitmap(b, null, dst, paint)
                }
                val dim = p.getInt(Prefs.WL_DIM, 0)
                if (dim > 0) c.drawColor(Color.argb((dim * 2.55f).toInt(), 0, 0, 0))
            } finally {
                runCatching { holder.unlockCanvasAndPost(c) }
            }
        }
    }
}

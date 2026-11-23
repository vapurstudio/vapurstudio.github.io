package com.vapurstudio.hyperkit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.Display
import android.view.RoundedCorner
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator

/** Edge Lighting+: a glowing, rotating border around the screen when a notification arrives. */
class EdgeLight(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var current: View? = null

    fun flash(appColor: Int?) {
        val p = Prefs.get(ctx)
        val mode = p.getString(Prefs.LIGHT_COLOR, "app")
        val accent = ctx.getColor(R.color.icon_bg)
        val base = when (mode) {
            "accent" -> accent
            "app" -> appColor?.takeIf { it != 0 && Color.alpha(it) > 0 } ?: accent
            else -> null
        }
        val colors = if (base == null) intArrayOf(0xFFFF6B6B.toInt(), 0xFFFFD43B.toInt(), 0xFF69DB7C.toInt(),
            0xFF4DABF7.toInt(), 0xFFDA77F2.toInt(), 0xFFFF6B6B.toInt())
        else intArrayOf(base, Color.WHITE, base, adjust(base), base)
        val width = p.getInt(Prefs.LIGHT_WIDTH, 6) * ctx.resources.displayMetrics.density
        val dur = p.getInt(Prefs.LIGHT_TIME, 3) * 1000L

        current?.let { runCatching { wm.removeView(it) } }
        val radius = runCatching {
            ctx.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY).getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
        }.getOrNull() ?: (36 * ctx.resources.displayMetrics.density)
        val v = GlowView(ctx, colors, width, radius)
        val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            title = "HyperKit edge light"
        }
        if (runCatching { wm.addView(v, lp) }.isFailure) return
        current = v
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur
            interpolator = LinearInterpolator()
            addUpdateListener { v.progress = it.animatedValue as Float }
            doOnEnd { if (current === v) { runCatching { wm.removeView(v) }; current = null } }
            start()
        }
    }

    private fun adjust(c: Int): Int {
        val hsv = FloatArray(3); Color.colorToHSV(c, hsv); hsv[0] = (hsv[0] + 40) % 360; return Color.HSVToColor(hsv)
    }

    private fun ValueAnimator.doOnEnd(f: () -> Unit) = addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = f()
    })

    private class GlowView(ctx: Context, private val colors: IntArray, private val w: Float, private val radius: Float) : View(ctx) {
        var progress = 0f
            set(v) { field = v; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rect = RectF()
        private val m = Matrix()

        override fun onDraw(c: Canvas) {
            val sg = SweepGradient(width / 2f, height / 2f, colors, null)
            m.setRotate(progress * 720f, width / 2f, height / 2f)
            sg.setLocalMatrix(m)
            // fade in fast, out slowly
            val a = when {
                progress < 0.1f -> progress / 0.1f
                progress > 0.7f -> (1f - progress) / 0.3f
                else -> 1f
            }
            paint.shader = sg; paint.strokeWidth = w; paint.alpha = (255 * a).toInt()
            glow.shader = sg; glow.strokeWidth = w * 3; glow.alpha = (70 * a).toInt()
            rect.set(w / 2, w / 2, width - w / 2, height - w / 2)
            c.drawRoundRect(rect, radius, radius, glow)
            c.drawRoundRect(rect, radius, radius, paint)
        }
    }
}

package com.vapurstudio.hyperkit.lock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.View
import com.vapurstudio.hyperkit.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class ClockStyle(val label: Int) {
    DIGITAL(R.string.cs_digital),
    BOLD(R.string.cs_bold),
    STACKED(R.string.cs_stacked),
    OUTLINE(R.string.cs_outline),
    ANALOG(R.string.cs_analog),
    MINIMAL(R.string.cs_minimal);

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: DIGITAL
    }
}

/** Self-updating clock face used by the lock screen and the ClockFace screensaver. */
class ClockView(context: Context) : View(context) {
    var style = ClockStyle.DIGITAL
        set(v) { field = v; requestLayout(); invalidate() }
    var color = Color.WHITE
        set(v) { field = v; invalidate() }
    var scale = 1f
        set(v) { field = v; requestLayout(); invalidate() }
    var showDate = true

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            val now = System.currentTimeMillis()
            val step = if (style == ClockStyle.ANALOG) 1000L else 60_000L
            postDelayed(this, step - now % step + 5)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); removeCallbacks(tick); post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick); super.onDetachedFromWindow()
    }

    private val timeSize get() = when (style) {
        ClockStyle.DIGITAL -> 92f
        ClockStyle.BOLD, ClockStyle.OUTLINE -> 96f
        ClockStyle.STACKED -> 120f
        ClockStyle.MINIMAL -> 60f
        ClockStyle.ANALOG -> 0f
    } * sp * scale

    private val dateSize get() = 17f * sp * min(1.2f, scale)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val dateH = if (showDate) dateSize * 2.2f else 0f
        val h = when (style) {
            ClockStyle.ANALOG -> 230 * d * scale + dateH
            ClockStyle.STACKED -> timeSize * 1.9f + dateH
            else -> timeSize * 1.15f + dateH
        }
        setMeasuredDimension(w, h.toInt())
    }

    private fun pattern(): Pair<String, String> =
        if (DateFormat.is24HourFormat(context)) "HH" to "mm" else "h" to "mm"

    override fun onDraw(c: Canvas) {
        val cal = Calendar.getInstance()
        val (hp, mp) = pattern()
        val loc = Locale.getDefault()
        val hh = SimpleDateFormat(hp, loc).format(cal.time)
        val mm = SimpleDateFormat(mp, loc).format(cal.time)
        val cx = width / 2f
        p.color = color
        p.style = Paint.Style.FILL
        p.strokeWidth = 0f
        p.textAlign = Paint.Align.CENTER
        var bottom: Float

        when (style) {
            ClockStyle.DIGITAL, ClockStyle.BOLD, ClockStyle.OUTLINE -> {
                p.typeface = when (style) {
                    ClockStyle.DIGITAL -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
                    else -> Typeface.create("sans-serif-black", Typeface.NORMAL)
                }
                if (style == ClockStyle.OUTLINE) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d }
                p.textSize = timeSize
                val base = timeSize * 0.95f
                c.drawText("$hh:$mm", cx, base, p)
                bottom = timeSize * 1.15f
            }
            ClockStyle.STACKED -> {
                p.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                p.textSize = timeSize
                c.drawText(hh, cx, timeSize * 0.9f, p)
                p.alpha = 170
                c.drawText(mm, cx, timeSize * 1.78f, p)
                p.alpha = 255
                bottom = timeSize * 1.9f
            }
            ClockStyle.MINIMAL -> {
                p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                p.textAlign = Paint.Align.LEFT
                p.textSize = timeSize
                c.drawText("$hh:$mm", 24 * d, timeSize * 0.95f, p)
                bottom = timeSize * 1.15f
            }
            ClockStyle.ANALOG -> {
                val r = 110 * d * scale
                val cy = r + 5 * d
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2 * d
                p.alpha = 90
                c.drawCircle(cx, cy, r, p)
                p.alpha = 255
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0)
                    val inner = if (i % 3 == 0) r * 0.80f else r * 0.88f
                    p.strokeWidth = if (i % 3 == 0) 4 * d else 2 * d
                    c.drawLine(cx + (sin(a) * inner).toFloat(), cy - (cos(a) * inner).toFloat(),
                        cx + (sin(a) * r * 0.95f).toFloat(), cy - (cos(a) * r * 0.95f).toFloat(), p)
                }
                p.strokeCap = Paint.Cap.ROUND
                val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
                val h = cal.get(Calendar.HOUR) + m / 60f
                hand(c, cx, cy, h * 30f, r * 0.5f, 7 * d)
                hand(c, cx, cy, m * 6f, r * 0.75f, 4.5f * d)
                p.alpha = 200
                hand(c, cx, cy, cal.get(Calendar.SECOND) * 6f, r * 0.85f, 1.5f * d)
                p.alpha = 255
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, 6 * d, p)
                p.strokeCap = Paint.Cap.BUTT
                bottom = 2 * r + 10 * d
            }
        }

        if (showDate) {
            val pat = DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM")
            datePaint.color = color
            datePaint.textSize = dateSize
            datePaint.textAlign = if (style == ClockStyle.MINIMAL) Paint.Align.LEFT else Paint.Align.CENTER
            val x = if (style == ClockStyle.MINIMAL) 28 * d else cx
            c.drawText(SimpleDateFormat(pat, loc).format(cal.time), x, bottom + dateSize * 1.3f, datePaint)
        }
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, w: Float) {
        val a = Math.toRadians(deg.toDouble())
        p.strokeWidth = w
        p.style = Paint.Style.STROKE
        c.drawLine(cx, cy, cx + (sin(a) * len).toFloat(), cy - (cos(a) * len).toFloat(), p)
    }
}

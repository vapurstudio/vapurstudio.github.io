package com.vapurstudio.hyperkit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A thin strip on a screen edge. Recognises four gestures that start on it:
 * swipe straight in, swipe in diagonally up / down, and swipe in then hold still.
 */
@SuppressLint("ViewConstructor")
class EdgeView(
    context: Context,
    private val left: Boolean,
    private val onGesture: (Gesture) -> Unit,
) : View(context) {

    var distancePx = 0f
    var color = 0
        set(value) { field = value; paint.color = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density = resources.displayMetrics.density
    private val holdSlop = 12 * density
    private val holdDelay = 420L

    private var downX = 0f
    private var downY = 0f
    private var anchorX = 0f
    private var anchorY = 0f
    private var armed = false
    private var consumed = false

    private val holdRunnable = Runnable {
        if (armed && !consumed) {
            consumed = true
            onGesture(Gesture.HOLD)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Ask the system to leave this strip to us. Android caps this per edge, so very tall
        // handles still lose the overlapping part to the system back gesture.
        systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
    }

    override fun onDraw(canvas: Canvas) {
        if (paint.alpha == 0) return
        val bar = width * 0.45f
        if (left) rect.set(0f, 0f, bar, height.toFloat()) else rect.set(width - bar, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, bar, bar, paint)
    }

    private fun inward(x: Float) = if (left) x - downX else downX - x

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY
                armed = false; consumed = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (consumed) return true
                if (!armed && inward(e.rawX) >= distancePx) {
                    armed = true
                    anchorX = e.rawX; anchorY = e.rawY
                    postDelayed(holdRunnable, holdDelay)
                } else if (armed && hypot(e.rawX - anchorX, e.rawY - anchorY) > holdSlop) {
                    anchorX = e.rawX; anchorY = e.rawY
                    removeCallbacks(holdRunnable)
                    postDelayed(holdRunnable, holdDelay)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(holdRunnable)
                if (!consumed) {
                    val dx = inward(e.rawX)
                    val dy = e.rawY - downY
                    if (dx >= distancePx || (armed && dx > 0)) {
                        val g = when {
                            abs(dy) < dx * 0.75f -> Gesture.SWIPE_IN
                            dy < 0 -> Gesture.SWIPE_UP
                            else -> Gesture.SWIPE_DOWN
                        }
                        onGesture(g)
                    }
                }
                armed = false
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(holdRunnable)
                armed = false
            }
        }
        return true
    }
}

package com.vapurstudio.hyperkit.lock

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R
import kotlin.random.Random

/** ClockFace: a dim, burn-in safe clock screensaver (Settings → Display → Screen saver). */
class ClockDream : DreamService() {
    private lateinit var box: LinearLayout
    private lateinit var battery: TextView
    private val mover = object : Runnable {
        override fun run() {
            val parent = box.parent as? FrameLayout ?: return
            val maxX = (parent.width - box.width).coerceAtLeast(0) / 2f
            val maxY = (parent.height - box.height).coerceAtLeast(0) / 2f
            box.animate().translationX(Random.nextFloat() * 2 * maxX - maxX).translationY(Random.nextFloat() * 2 * maxY - maxY)
                .setDuration(1500).start()
            updateBattery()
            box.postDelayed(this, 60_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val p = Prefs.get(this)
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(ClockView(this).apply {
            style = ClockStyle.of(p.getString(Prefs.AOD_STYLE, ClockStyle.ANALOG.name))
            color = p.getInt(Prefs.AOD_COLOR, Color.WHITE)
            scale = 0.9f
        }, LinearLayout.LayoutParams(resources.displayMetrics.widthPixels * 9 / 10, -2))
        battery = TextView(this).apply { setTextColor(0xB3FFFFFF.toInt()); textSize = 14f; gravity = Gravity.CENTER }
        box.addView(battery)
        frame.addView(box, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(frame)
        updateBattery()
    }

    private fun updateBattery() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        battery.text = if (plugged) getString(R.string.lock_charging, level) else "%$level"
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); box.postDelayed(mover, 60_000) }
    override fun onDreamingStopped() { box.removeCallbacks(mover); super.onDreamingStopped() }
}

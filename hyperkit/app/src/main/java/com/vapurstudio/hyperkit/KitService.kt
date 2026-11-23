package com.vapurstudio.hyperkit

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.vapurstudio.hyperkit.tiles.CaffeineTile
import com.vapurstudio.hyperkit.tiles.MediaKeysTile

class KitService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        @Volatile var instance: KitService? = null
            private set
        val running get() = instance != null
        private val SIDES = listOf("l", "r")
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var audio: AudioManager
    private val edges = mutableMapOf<String, EdgeView>()
    private var caffeineView: View? = null

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (prefs.getBoolean(Prefs.CAFFEINE, false)) prefs.edit().putBoolean(Prefs.CAFFEINE, false).apply()
        }
    }

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WindowManager::class.java)
        audio = getSystemService(AudioManager::class.java)
        prefs = Prefs.get(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        Torch.init(this)
        refreshEdges()
        refreshCaffeine()
        refreshKeyFilter()
        refreshTiles()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            runCatching { unregisterReceiver(screenOff) }
            removeEdges()
            caffeineView?.let { runCatching { wm.removeView(it) } }
            caffeineView = null
            refreshTiles()
        }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (instance === this) refreshEdges()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences, key: String?) {
        when {
            key == null -> { refreshEdges(); refreshCaffeine(); refreshKeyFilter() }
            key == Prefs.CAFFEINE -> { refreshCaffeine(); refreshTiles() }
            key == Prefs.MEDIA_KEYS || key == Prefs.LONG_PRESS_SKIP -> { refreshKeyFilter(); refreshTiles() }
            key.startsWith("edge_") -> refreshEdges()
        }
    }

    private fun refreshTiles() {
        for (c in listOf(CaffeineTile::class.java, MediaKeysTile::class.java)) {
            runCatching { TileService.requestListeningState(this, ComponentName(this, c)) }
        }
    }

    // ---------------- Edge gestures ----------------

    private fun removeEdges() {
        edges.values.forEach { runCatching { wm.removeView(it) } }
        edges.clear()
    }

    private fun refreshEdges() {
        removeEdges()
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape && !prefs.getBoolean(Prefs.LANDSCAPE, false)) return
        val bounds = wm.currentWindowMetrics.bounds
        val screenH = bounds.height()
        val density = resources.displayMetrics.density
        val accent = getColor(R.color.icon_bg)

        for (side in SIDES) {
            if (!Prefs.edgeOn(prefs, side)) continue
            val h = (screenH * Prefs.heightOf(prefs, side) / 100f).toInt()
            val y = (screenH * Prefs.posOf(prefs, side) / 100f - h / 2f).toInt().coerceIn(0, (screenH - h).coerceAtLeast(0))
            val w = (Prefs.widthOf(prefs, side) * density).toInt()
            val view = EdgeView(this, side == "l") { g -> onGesture(side, g) }
            view.distancePx = Prefs.distance(prefs) * density
            val a = (Prefs.alphaOf(prefs, side) * 2.55f).toInt()
            view.color = Color.argb(a, Color.red(accent), Color.green(accent), Color.blue(accent))

            val lp = WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = @Suppress("RtlHardcoded") (Gravity.TOP or if (side == "l") Gravity.LEFT else Gravity.RIGHT)
                this.x = 0
                this.y = y
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
                title = "HyperKit edge $side"
            }
            runCatching { wm.addView(view, lp); edges[side] = view }
        }
    }

    private fun onGesture(side: String, g: Gesture) {
        val action = Prefs.actionFor(prefs, side, g)
        if (action == Action.NONE) return
        if (prefs.getBoolean(Prefs.HAPTIC, true)) tick()
        action.perform(this, this)
    }

    private fun tick() {
        runCatching {
            getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    // ---------------- Caffeine ----------------

    private fun refreshCaffeine() {
        val want = prefs.getBoolean(Prefs.CAFFEINE, false)
        if (want && caffeineView == null) {
            val v = View(this)
            val lp = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSPARENT,
            ).apply { gravity = Gravity.TOP or Gravity.LEFT; title = "HyperKit caffeine" }
            runCatching { wm.addView(v, lp); caffeineView = v }
        } else if (!want && caffeineView != null) {
            runCatching { wm.removeView(caffeineView) }
            caffeineView = null
        }
    }

    // ---------------- Sound assistant (volume keys) ----------------

    private fun refreshKeyFilter() {
        val info = serviceInfo ?: return
        val want = prefs.getBoolean(Prefs.MEDIA_KEYS, false) || prefs.getBoolean(Prefs.LONG_PRESS_SKIP, false)
        val flags = if (want) info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        else info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        if (flags != info.flags) {
            info.flags = flags
            serviceInfo = info
        }
    }

    private var longDir = 0
    private var longFired = false
    private var longPending = false
    private val longRunnable = Runnable {
        longPending = false
        longFired = true
        tick()
        Action.mediaKey(this, if (longDir > 0) KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val dir = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return false
        }
        val mode = audio.mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_CALL_SCREENING) return false

        val mediaKeys = prefs.getBoolean(Prefs.MEDIA_KEYS, false)
        val longSkip = prefs.getBoolean(Prefs.LONG_PRESS_SKIP, false)
        val down = event.action == KeyEvent.ACTION_DOWN

        // Long-press handling: only while music plays (or a long press is already being tracked).
        if (longSkip && (longPending || longFired || (down && event.repeatCount == 0 && audio.isMusicActive))) {
            if (down) {
                if (event.repeatCount == 0) {
                    longDir = dir; longFired = false; longPending = true
                    handler.postDelayed(longRunnable, 550)
                }
            } else {
                handler.removeCallbacks(longRunnable)
                val fired = longFired
                longPending = false; longFired = false
                if (!fired) adjust(dir, mediaKeys)
            }
            return true
        }

        if (mediaKeys) {
            if (down) adjust(dir, true)
            return true
        }
        return false
    }

    private fun adjust(dir: Int, media: Boolean) {
        if (media) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        else audio.adjustSuggestedStreamVolume(dir, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
    }
}

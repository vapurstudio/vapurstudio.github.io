package com.vapurstudio.hyperkit.tiles

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.vapurstudio.hyperkit.Action
import com.vapurstudio.hyperkit.KitService
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R
import com.vapurstudio.hyperkit.Tuner
import com.vapurstudio.hyperkit.ui.MainActivity
import com.vapurstudio.hyperkit.ui.TunerActivity

abstract class BaseTile : TileService() {
    open fun isActive(): Boolean? = null   // null = stateless button

    override fun onStartListening() = update()

    fun update() {
        val t = qsTile ?: return
        t.state = when (isActive()) {
            null -> Tile.STATE_INACTIVE
            true -> Tile.STATE_ACTIVE
            false -> Tile.STATE_INACTIVE
        }
        t.updateTile()
    }

    protected fun openApp(cls: Class<*>) {
        val i = Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(i)
        }
    }

    /** Runs a global action after the Control Center has closed. */
    protected fun runAfterCollapse(action: Action) {
        val s = KitService.instance ?: run {
            Toast.makeText(this, R.string.enable_service_first, Toast.LENGTH_SHORT).show()
            openApp(MainActivity::class.java)
            return
        }
        s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        Handler(Looper.getMainLooper()).postDelayed({ action.perform(s, s) }, 450)
    }
}

class CaffeineTile : BaseTile() {
    override fun isActive() = Prefs.get(this).getBoolean(Prefs.CAFFEINE, false) && KitService.running
    override fun onClick() {
        if (!KitService.running) {
            Toast.makeText(this, R.string.enable_service_first, Toast.LENGTH_SHORT).show()
            openApp(MainActivity::class.java)
            return
        }
        val p = Prefs.get(this)
        p.edit().putBoolean(Prefs.CAFFEINE, !p.getBoolean(Prefs.CAFFEINE, false)).apply()
        update()
    }
}

class LockTile : BaseTile() {
    override fun onClick() = runAfterCollapse(Action.LOCK)
}

class ScreenshotTile : BaseTile() {
    override fun onClick() = runAfterCollapse(Action.SCREENSHOT)
}

class PowerTile : BaseTile() {
    override fun onClick() = runAfterCollapse(Action.POWER)
}

class AnimationsTile : BaseTile() {
    override fun isActive() = !Tuner.animationsOff(this)
    override fun onClick() {
        if (!Tuner.toggleAnimations(this)) {
            openApp(TunerActivity::class.java)
            return
        }
        update()
    }
}

class MediaKeysTile : BaseTile() {
    override fun isActive() = Prefs.get(this).getBoolean(Prefs.MEDIA_KEYS, false)
    override fun onClick() {
        val p = Prefs.get(this)
        p.edit().putBoolean(Prefs.MEDIA_KEYS, !p.getBoolean(Prefs.MEDIA_KEYS, false)).apply()
        if (!KitService.running) Toast.makeText(this, R.string.enable_service_first, Toast.LENGTH_SHORT).show()
        update()
    }
}

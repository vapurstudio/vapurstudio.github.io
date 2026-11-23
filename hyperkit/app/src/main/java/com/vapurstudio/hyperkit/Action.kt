package com.vapurstudio.hyperkit

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast

enum class Action(val label: Int) {
    NONE(R.string.act_none),
    BACK(R.string.act_back),
    HOME(R.string.act_home),
    RECENTS(R.string.act_recents),
    LAST_APP(R.string.act_last_app),
    NOTIFICATIONS(R.string.act_notifications),
    QUICK_SETTINGS(R.string.act_quick_settings),
    LOCK(R.string.act_lock),
    SCREENSHOT(R.string.act_screenshot),
    POWER(R.string.act_power),
    SPLIT(R.string.act_split),
    PLAY_PAUSE(R.string.act_play_pause),
    NEXT(R.string.act_next),
    PREV(R.string.act_prev),
    VOLUME_PANEL(R.string.act_volume_panel),
    FLASHLIGHT(R.string.act_flashlight),
    CAFFEINE(R.string.act_caffeine),
    KILL_APP(R.string.act_kill);

    /** Runs the action. Global actions need the accessibility service; the rest work anywhere. */
    fun perform(ctx: Context, service: AccessibilityService?): Boolean {
        fun global(a: Int): Boolean {
            val s = service ?: run {
                Toast.makeText(ctx, R.string.enable_service_first, Toast.LENGTH_SHORT).show()
                return false
            }
            return s.performGlobalAction(a)
        }
        return when (this) {
            NONE -> false
            BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME)
            RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            LAST_APP -> {
                // Double "recents" switches to the previous app on AOSP-derived launchers, HyperOS included.
                val ok = global(AccessibilityService.GLOBAL_ACTION_RECENTS)
                if (ok) Handler(Looper.getMainLooper()).postDelayed({
                    service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                }, 280)
                ok
            }
            NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            LOCK -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            SCREENSHOT -> global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            POWER -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            SPLIT -> global(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            PLAY_PAUSE -> mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            NEXT -> mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
            PREV -> mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            VOLUME_PANEL -> {
                ctx.getSystemService(AudioManager::class.java).adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                true
            }
            FLASHLIGHT -> Torch.toggle(ctx)
            CAFFEINE -> {
                val p = Prefs.get(ctx)
                p.edit().putBoolean(Prefs.CAFFEINE, !p.getBoolean(Prefs.CAFFEINE, false)).apply()
                if (service == null) Toast.makeText(ctx, R.string.enable_service_first, Toast.LENGTH_SHORT).show()
                true
            }
            KILL_APP -> {
                val pkg = (service as? KitService)?.currentPkg
                if (!Prefs.isRoot(ctx)) {
                    Toast.makeText(ctx, R.string.needs_root, Toast.LENGTH_SHORT).show(); false
                } else if (pkg == null || pkg == ctx.packageName || isHome(ctx, pkg)) false
                else {
                    service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    Root.run("am force-stop $pkg")
                    true
                }
            }
        }
    }

    companion object {
        fun isHome(ctx: Context, pkg: String): Boolean {
            val i = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
            return ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName == pkg
        }

        fun fromName(name: String?): Action? = entries.firstOrNull { it.name == name }

        fun mediaKey(ctx: Context, code: Int): Boolean {
            val am = ctx.getSystemService(AudioManager::class.java)
            val t = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
            return true
        }
    }
}

object Torch {
    @Volatile private var on = false
    private var registered = false

    private fun cameraId(cm: CameraManager): String? = cm.cameraIdList.firstOrNull {
        cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    /** Starts tracking the torch state so the first toggle is right even if the system turned it on. */
    fun init(ctx: Context) {
        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            cameraId(cm)?.let { track(cm, it) }
        } catch (_: Exception) {
        }
    }

    private fun track(cm: CameraManager, id: String) {
        if (registered) return
        registered = true
        cm.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) on = enabled
            }
        }, Handler(Looper.getMainLooper()))
    }

    fun toggle(ctx: Context): Boolean {
        val cm = ctx.getSystemService(CameraManager::class.java)
        return try {
            val id = cameraId(cm) ?: throw IllegalStateException()
            track(cm, id)
            cm.setTorchMode(id, !on)
            true
        } catch (e: Exception) {
            Toast.makeText(ctx, R.string.flash_unavailable, Toast.LENGTH_SHORT).show()
            false
        }
    }
}

package com.vapurstudio.hyperkit

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.Surface
import org.json.JSONObject

/** Per-app profile (Display Assistant + Sound Assistant "app volume" from Good Lock). */
data class Profile(
    var rotation: Int = -1,    // -1 keep, 0 auto, 1 portrait, 2 landscape
    var keepOn: Boolean = false,
    var brightness: Int = -1,  // -1 keep, else 0..100 %
    var volume: Int = -1,      // -1 keep, else 0..100 % media volume
) {
    fun toJson() = JSONObject().put("r", rotation).put("k", keepOn).put("b", brightness).put("v", volume)

    companion object {
        fun from(o: JSONObject) = Profile(o.optInt("r", -1), o.optBoolean("k"), o.optInt("b", -1), o.optInt("v", -1))
    }
}

object Profiles {
    private val IGNORED = setOf("com.android.systemui", "android", "com.miui.securitycenter", "com.lbe.security.miui")

    fun all(ctx: Context): MutableMap<String, Profile> {
        val o = runCatching { JSONObject(Prefs.get(ctx).getString(Prefs.PROFILES, "{}")!!) }.getOrDefault(JSONObject())
        val map = linkedMapOf<String, Profile>()
        o.keys().forEach { k -> map[k] = Profile.from(o.getJSONObject(k)) }
        return map
    }

    fun save(ctx: Context, map: Map<String, Profile>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v.toJson()) }
        Prefs.get(ctx).edit().putString(Prefs.PROFILES, o.toString()).apply()
    }

    private var active: String? = null

    /** Called on every foreground app change. Returns whether the app wants the screen kept on. */
    fun onForeground(ctx: Context, pkg: String, isIme: Boolean): Boolean? {
        if (pkg in IGNORED || isIme || pkg == ctx.packageName) return null
        val map = all(ctx)
        val prof = map[pkg]
        if (pkg == active) return prof?.keepOn
        if (active != null) restore(ctx)
        active = null
        if (prof == null) return false
        saveBase(ctx)
        apply(ctx, prof)
        active = pkg
        return prof.keepOn
    }

    private fun audio(ctx: Context) = ctx.getSystemService(AudioManager::class.java)

    private fun saveBase(ctx: Context) {
        val cr = ctx.contentResolver
        val o = JSONObject()
            .put("acc", Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1))
            .put("usr", Settings.System.getInt(cr, Settings.System.USER_ROTATION, 0))
            .put("bm", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 1))
            .put("b", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128))
            .put("v", audio(ctx).getStreamVolume(AudioManager.STREAM_MUSIC))
        Prefs.get(ctx).edit().putString(Prefs.PROFILE_BASE, o.toString()).apply()
    }

    private fun apply(ctx: Context, p: Profile) {
        val cr = ctx.contentResolver
        val canWrite = Settings.System.canWrite(ctx)
        if (canWrite && p.rotation >= 0) runCatching {
            if (p.rotation == 0) Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1)
            else {
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(cr, Settings.System.USER_ROTATION, if (p.rotation == 1) Surface.ROTATION_0 else Surface.ROTATION_90)
            }
        }
        if (canWrite && p.brightness >= 0) runCatching {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (p.brightness * 2.55f).toInt().coerceIn(1, 255))
        }
        if (p.volume >= 0) runCatching {
            val am = audio(ctx)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * p.volume / 100, 0)
        }
    }

    private fun restore(ctx: Context) {
        val prof = all(ctx)[active] ?: return
        val base = runCatching { JSONObject(Prefs.get(ctx).getString(Prefs.PROFILE_BASE, null)!!) }.getOrNull() ?: return
        val cr = ctx.contentResolver
        if (Settings.System.canWrite(ctx)) runCatching {
            if (prof.rotation >= 0) {
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, base.getInt("acc"))
                Settings.System.putInt(cr, Settings.System.USER_ROTATION, base.getInt("usr"))
            }
            if (prof.brightness >= 0) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, base.getInt("b"))
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, base.getInt("bm"))
            }
        }
        if (prof.volume >= 0) runCatching { audio(ctx).setStreamVolume(AudioManager.STREAM_MUSIC, base.getInt("v"), 0) }
    }
}

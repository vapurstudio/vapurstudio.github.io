package com.vapurstudio.hyperkit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/** Settings that need WRITE_SECURE_SETTINGS, which the user grants once with ADB. */
object Tuner {
    val ANIM_KEYS = listOf(
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.ANIMATOR_DURATION_SCALE,
    )
    const val ICON_BLACKLIST = "icon_blacklist"

    fun hasSecure(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun adbCommand(ctx: Context) =
        "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"

    fun getAnim(ctx: Context, key: String) = Settings.Global.getFloat(ctx.contentResolver, key, 1f)

    fun setAnim(ctx: Context, key: String, v: Float): Boolean =
        runCatching { Settings.Global.putFloat(ctx.contentResolver, key, v) }.getOrDefault(false)

    fun animationsOff(ctx: Context) = ANIM_KEYS.all { getAnim(ctx, it) == 0f }

    /** Turns all animations off, remembering the previous values; or restores them. */
    fun toggleAnimations(ctx: Context): Boolean {
        if (!hasSecure(ctx)) return false
        val p = Prefs.get(ctx)
        if (animationsOff(ctx)) {
            val saved = p.getString(Prefs.ANIM_SAVED, null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ANIM_KEYS.forEachIndexed { i, k ->
                val v = saved?.getOrNull(i)?.takeIf { it > 0f } ?: 1f
                setAnim(ctx, k, v)
            }
        } else {
            p.edit().putString(Prefs.ANIM_SAVED, ANIM_KEYS.joinToString(",") { getAnim(ctx, it).toString() }).apply()
            ANIM_KEYS.forEach { setAnim(ctx, it, 0f) }
        }
        return true
    }

    const val THEME_KEY = "theme_customization_overlay_packages"
    val MONET = listOf(0xFF3D6BFF.toInt(), 0xFF7B4DFF.toInt(), 0xFFE64980.toInt(), 0xFFFA5252.toInt(),
        0xFFFD7E14.toInt(), 0xFFFAB005.toInt(), 0xFF40C057.toInt(), 0xFF15AABF.toInt())

    fun monetColor(ctx: Context): Int? = runCatching {
        val o = org.json.JSONObject(Settings.Secure.getString(ctx.contentResolver, THEME_KEY) ?: return null)
        val hex = o.optString("android.theme.customization.system_palette", "").ifEmpty { return null }
        (0xFF000000.toInt() or hex.takeLast(6).toInt(16))
    }.getOrNull()

    /** Material You seed colour (Theme Park). null resets to wallpaper colours. */
    fun setMonet(ctx: Context, color: Int?): Boolean = runCatching {
        val cr = ctx.contentResolver
        val o = runCatching { org.json.JSONObject(Settings.Secure.getString(cr, THEME_KEY) ?: "{}") }.getOrDefault(org.json.JSONObject())
        if (color == null) {
            o.remove("android.theme.customization.system_palette"); o.remove("android.theme.customization.accent_color")
            o.put("android.theme.customization.color_source", "home_wallpaper")
        } else {
            val hex = String.format("%06X", color and 0xFFFFFF)
            o.put("android.theme.customization.system_palette", hex)
            o.put("android.theme.customization.accent_color", hex)
            o.put("android.theme.customization.color_source", "preset")
        }
        o.put("_applied_timestamp", System.currentTimeMillis())
        Settings.Secure.putString(cr, THEME_KEY, o.toString())
    }.getOrDefault(false)

    fun hiddenIcons(ctx: Context): MutableSet<String> =
        (Settings.Secure.getString(ctx.contentResolver, ICON_BLACKLIST) ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()

    fun setHiddenIcons(ctx: Context, set: Set<String>): Boolean = runCatching {
        Settings.Secure.putString(ctx.contentResolver, ICON_BLACKLIST, set.joinToString(",").ifEmpty { null })
    }.getOrDefault(false)
}

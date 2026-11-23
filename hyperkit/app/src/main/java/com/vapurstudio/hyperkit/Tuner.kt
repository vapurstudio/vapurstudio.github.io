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

    fun hiddenIcons(ctx: Context): MutableSet<String> =
        (Settings.Secure.getString(ctx.contentResolver, ICON_BLACKLIST) ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()

    fun setHiddenIcons(ctx: Context, set: Set<String>): Boolean = runCatching {
        Settings.Secure.putString(ctx.contentResolver, ICON_BLACKLIST, set.joinToString(",").ifEmpty { null })
    }.getOrDefault(false)
}

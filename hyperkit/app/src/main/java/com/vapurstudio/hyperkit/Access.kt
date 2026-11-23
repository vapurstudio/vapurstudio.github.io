package com.vapurstudio.hyperkit

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** Grants and keeps HyperKit's special accesses on, using WRITE_SECURE_SETTINGS or root. */
object Access {
    fun service(ctx: Context) = ComponentName(ctx, KitService::class.java)
    fun listener(ctx: Context) = ComponentName(ctx, NotiService::class.java)

    private fun addTo(ctx: Context, key: String, comp: ComponentName): Boolean = runCatching {
        val cr = ctx.contentResolver
        val cur = Settings.Secure.getString(cr, key) ?: ""
        val items = cur.split(':').filter { it.isNotBlank() }
        if (items.none { ComponentName.unflattenFromString(it) == comp }) {
            Settings.Secure.putString(cr, key, (items + comp.flattenToString()).joinToString(":"))
        }
        true
    }.getOrDefault(false)

    fun listenerEnabled(ctx: Context): Boolean {
        val cur = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        return cur.split(':').any { ComponentName.unflattenFromString(it) == listener(ctx) }
    }

    /** Turns the accessibility service back on if HyperOS (or the user) switched it off. */
    fun ensureService(ctx: Context): Boolean {
        if (!Tuner.hasSecure(ctx)) return false
        val ok = addTo(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, service(ctx))
        runCatching { Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1) }
        return ok
    }

    fun ensureListener(ctx: Context): Boolean =
        Tuner.hasSecure(ctx) && addTo(ctx, "enabled_notification_listeners", listener(ctx))

    /** Root mode: grant everything HyperKit can use in one go. */
    fun grantAllWithRoot(ctx: Context, cb: (Boolean) -> Unit) {
        val pkg = ctx.packageName
        val l = listener(ctx).flattenToString()
        val cmd = listOf(
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
            "appops set $pkg WRITE_SETTINGS allow",
            "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "cmd notification allow_listener $l",
            "dumpsys deviceidle whitelist +$pkg",
        ).joinToString("; ")
        Root.run(cmd) { r ->
            if (Tuner.hasSecure(ctx)) { ensureService(ctx); ensureListener(ctx) }
            cb(r.code != -1 && Tuner.hasSecure(ctx))
        }
    }
}

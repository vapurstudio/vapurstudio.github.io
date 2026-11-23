package com.vapurstudio.hyperkit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Hidden Android / HyperOS screens. Each entry lists candidate intents; the first one that opens wins. */
data class HiddenItem(val id: String, val title: Int, val desc: Int, val intents: (Context) -> List<Intent>) {
    fun available(ctx: Context) = intents(ctx).any { resolves(ctx, it) }

    /** Returns true if one of the candidates started. */
    fun launch(ctx: Context): Boolean {
        for (i in intents(ctx)) {
            if (!resolves(ctx, i)) continue
            try {
                ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                // not exported on this build, try the next one
            }
        }
        return false
    }

    companion object {
        private fun resolves(ctx: Context, i: Intent) =
            ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) != null ||
                (i.component != null && runCatching { ctx.packageManager.getActivityInfo(i.component!!, 0) }.isSuccess)

        private fun c(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
        private const val S = "com.android.settings"

        val ALL = listOf(
            HiddenItem("dev", R.string.h_dev, R.string.h_dev_d) {
                listOf(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            },
            HiddenItem("usage", R.string.h_usage, R.string.h_usage_d) {
                listOf(c(S, "$S.UsageStatsActivity"), c(S, "$S.Settings\$UsageStatsActivity"))
            },
            HiddenItem("notif_log", R.string.h_notif_log, R.string.h_notif_log_d) {
                listOf(c(S, "$S.Settings\$NotificationStationActivity"), Intent("android.settings.NOTIFICATION_HISTORY"))
            },
            HiddenItem("radio", R.string.h_radio, R.string.h_radio_d) {
                listOf(c("com.android.phone", "com.android.phone.settings.RadioInfo"), c(S, "$S.RadioInfo"))
            },
            HiddenItem("running", R.string.h_running, R.string.h_running_d) {
                listOf(c(S, "$S.Settings\$DevRunningServicesActivity"), c(S, "$S.Settings\$RunningServicesActivity"))
            },
            HiddenItem("battery", R.string.h_battery, R.string.h_battery_d) {
                listOf(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            },
            HiddenItem("autostart", R.string.h_autostart, R.string.h_autostart_d) {
                listOf(
                    c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    Intent("miui.intent.action.OP_AUTO_START"),
                )
            },
            HiddenItem("bg_limits", R.string.h_bg_limits, R.string.h_bg_limits_d) {
                listOf(
                    c("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"),
                    Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST"),
                )
            },
            HiddenItem("perm", R.string.h_perm, R.string.h_perm_d) {
                listOf(
                    c("com.miui.securitycenter", "com.miui.permcenter.MainAcitivty"), // sic: Xiaomi's class name
                    Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", it.packageName),
                )
            },
            HiddenItem("cit", R.string.h_cit, R.string.h_cit_d) {
                listOf(
                    c("com.miui.cit", "com.miui.cit.home.CitLauncherActivity"),
                    c("com.miui.cit", "com.miui.cit.CitLauncherActivity"),
                    it.packageManager.getLaunchIntentForPackage("com.miui.cit") ?: Intent(),
                ).filter { i -> i.component != null || i.action != null }
            },
            HiddenItem("special", R.string.h_special, R.string.h_special_d) {
                listOf(c(S, "$S.Settings\$SpecialAccessSettingsActivity"), Intent("android.settings.SPECIAL_APPLICATION_ACCESS_SETTINGS"))
            },
            HiddenItem("pip", R.string.h_pip, R.string.h_pip_d) {
                listOf(Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS"), c(S, "$S.Settings\$PictureInPictureSettingsActivity"))
            },
            HiddenItem("default_apps", R.string.h_default_apps, R.string.h_default_apps_d) {
                listOf(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            },
            HiddenItem("device_info", R.string.h_device_info, R.string.h_device_info_d) {
                listOf(c(S, "$S.Settings\$StatusActivity"), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            },
            HiddenItem("sysui_tuner", R.string.h_tuner, R.string.h_tuner_d) {
                listOf(c("com.android.systemui", "com.android.systemui.tuner.TunerActivity"))
            },
        )

        fun byId(id: String?) = ALL.firstOrNull { it.id == id }
    }
}

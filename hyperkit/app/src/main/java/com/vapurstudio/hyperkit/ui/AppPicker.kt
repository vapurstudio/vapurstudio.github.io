package com.vapurstudio.hyperkit.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent

data class AppEntry(val pkg: String, val label: String)

object AppPicker {
    fun launchable(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun pick(ctx: Context, title: Int, onPick: (AppEntry) -> Unit) {
        val apps = launchable(ctx)
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setItems(apps.map { it.label }.toTypedArray()) { _, i -> onPick(apps[i]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

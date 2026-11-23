package com.vapurstudio.hyperkit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs.get(ctx).getBoolean(Prefs.KEEP_ALIVE, true)) {
            Access.ensureService(ctx)
            Access.ensureListener(ctx)
        }
    }
}

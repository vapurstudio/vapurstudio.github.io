package com.vapurstudio.hyperkit.lock

import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Invisible activity that asks the system to dismiss the keyguard (shows PIN / fingerprint if the
 * lock is secure), then runs an optional follow-up such as opening a notification or an app.
 */
class DismissActivity : Activity() {
    companion object {
        @Volatile private var pending: ((Activity) -> Unit)? = null

        fun start(ctx: Context, then: ((Activity) -> Unit)? = null) {
            pending = then
            ctx.startActivity(Intent(ctx, DismissActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
        }

        fun send(a: Activity, pi: PendingIntent) {
            val opts = if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
            } else null
            runCatching { pi.send(a, 0, null, null, null, null, opts) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) { finishWith(true); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finishWith(true)
            override fun onDismissCancelled() = finishWith(false)
            override fun onDismissError() = finishWith(false)
        })
    }

    private fun finishWith(ok: Boolean) {
        val p = pending
        pending = null
        if (ok) p?.invoke(this)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}

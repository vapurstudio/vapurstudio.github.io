package com.vapurstudio.hyperkit.ui

import android.content.Intent
import com.vapurstudio.hyperkit.Access
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R
import com.vapurstudio.hyperkit.Root

/** First launch: choose Standard (no root) or Root mode. Can be reopened from the main screen. */
class OnboardingActivity : BaseActivity() {
    override val screenTitle = R.string.ob_title
    override val showBack get() = prefs.getString(Prefs.MODE, null) != null
    private var checking = false

    override fun build() {
        root.addView(text(getString(R.string.ob_body), 15f, muted).apply { setPadding(dp(4), 0, dp(4), dp(12)) })

        val std = card()
        row(std, getString(R.string.mode_standard), getString(R.string.mode_standard_desc), R.drawable.ic_swipe,
            trailing = if (prefs.getString(Prefs.MODE, null) == "standard") "✓" else null) { choose("standard") }
        note(std, getString(R.string.mode_standard_list))
        button(std, getString(R.string.ob_pick_standard)) { choose("standard") }

        val rt = card()
        row(rt, getString(R.string.mode_root), getString(R.string.mode_root_desc), R.drawable.ic_root,
            trailing = if (prefs.getString(Prefs.MODE, null) == "root") "✓" else null) { choose("root") }
        note(rt, getString(R.string.mode_root_list))
        button(rt, getString(if (checking) R.string.ob_checking else R.string.ob_pick_root)) { choose("root") }

        note(card(), getString(R.string.ob_change_later))
    }

    private fun choose(mode: String) {
        if (mode == "standard") {
            prefs.edit().putString(Prefs.MODE, "standard").apply()
            done()
            return
        }
        if (checking) return
        checking = true
        rebuild()
        Root.check { ok ->
            checking = false
            if (!ok) {
                toast(R.string.ob_no_root)
                rebuild()
                return@check
            }
            prefs.edit().putString(Prefs.MODE, "root").apply()
            Access.grantAllWithRoot(this) { granted ->
                toast(if (granted) R.string.ob_root_ok else R.string.ob_root_partial)
                done()
            }
        }
    }

    private fun done() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}

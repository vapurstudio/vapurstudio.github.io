package com.vapurstudio.hyperkit.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.vapurstudio.hyperkit.KitService
import com.vapurstudio.hyperkit.R

class MainActivity : BaseActivity() {
    override val showBack = false
    override val screenTitle = R.string.app_name

    override fun header() {
        super.header()
        root.addView(text(getString(R.string.app_tagline), 14f, muted).apply { setPadding(0, 0, 0, dp(12)) })
    }

    override fun build() {
        val on = KitService.running
        val status = card()
        row(status, getString(if (on) R.string.service_on else R.string.service_off),
            if (on) null else getString(R.string.setup_body), R.drawable.ic_power) { openAccessibility() }
        if (!on) button(status, getString(R.string.setup_open)) { openAccessibility() }
        note(status, getString(R.string.setup_autostart))
        button(status, getString(R.string.setup_autostart_btn)) { openAutostart() }

        val mods = card()
        module(mods, R.string.mod_edge, R.string.mod_edge_desc, R.drawable.ic_swipe, true, EdgeActivity::class.java)
        module(mods, R.string.mod_sound, R.string.mod_sound_desc, R.drawable.ic_volume, true, SoundActivity::class.java)
        module(mods, R.string.mod_tiles, R.string.mod_tiles_desc, R.drawable.ic_tiles, false, TilesActivity::class.java)
        module(mods, R.string.mod_hidden, R.string.mod_hidden_desc, R.drawable.ic_hidden, false, HiddenActivity::class.java)
        module(mods, R.string.mod_tuner, R.string.mod_tuner_desc, R.drawable.ic_tune, false, TunerActivity::class.java)

        root.addView(text(getString(R.string.about), 12f, muted).apply { setPadding(dp(8), dp(8), dp(8), 0) })
    }

    private fun module(parent: android.view.ViewGroup, title: Int, desc: Int, icon: Int, needsService: Boolean, cls: Class<*>) {
        val sub = if (needsService && !KitService.running) getString(desc) + "\n⚠ " + getString(R.string.needs_service) else getString(desc)
        row(parent, getString(title), sub, icon) { startActivity(Intent(this, cls)) }
    }

    private fun openAutostart() {
        tryStart(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }
}

package com.vapurstudio.hyperkit.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import com.vapurstudio.hyperkit.Access
import com.vapurstudio.hyperkit.KitService
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R

class MainActivity : BaseActivity() {
    override val showBack = false
    override val screenTitle = R.string.app_name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.getString(Prefs.MODE, null) == null) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        if (prefs.getBoolean(Prefs.KEEP_ALIVE, true)) {
            Access.ensureService(this)
            Access.ensureListener(this)
        }
        super.onResume()
    }

    override fun header() {
        super.header()
        root.addView(text(getString(R.string.app_tagline), 14f, muted).apply { setPadding(0, 0, 0, dp(12)) })
    }

    override fun build() {
        val rootMode = Prefs.isRoot(this)
        val on = KitService.running

        val status = card()
        row(status, getString(if (rootMode) R.string.mode_root else R.string.mode_standard),
            getString(R.string.mode_change), if (rootMode) R.drawable.ic_root else R.drawable.ic_swipe) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        row(status, getString(if (on) R.string.service_on else R.string.service_off),
            if (on) null else getString(R.string.setup_body), R.drawable.ic_power) { openAccessibility() }
        if (!on) button(status, getString(R.string.setup_open)) { openAccessibility() }
        if (!Access.listenerEnabled(this)) {
            row(status, getString(R.string.listener_off), getString(R.string.listener_why), R.drawable.ic_bell) {
                openListenerSettings()
            }
        }
        note(status, getString(R.string.setup_autostart))
        button(status, getString(R.string.setup_autostart_btn)) { openAutostart() }

        sectionTitle(R.string.group_makeup)
        val a = card()
        module(a, R.string.mod_lock, R.string.mod_lock_desc, R.drawable.ic_lock, LockActivity::class.java, service = true)
        module(a, R.string.mod_light, R.string.mod_light_desc, R.drawable.ic_light, EdgeLightActivity::class.java, service = true)
        module(a, R.string.mod_wonder, R.string.mod_wonder_desc, R.drawable.ic_wallpaper, WonderlandActivity::class.java)
        module(a, R.string.mod_tiles, R.string.mod_tiles_desc, R.drawable.ic_tiles, TilesActivity::class.java)
        module(a, R.string.mod_tuner, R.string.mod_tuner_desc, R.drawable.ic_tune, TunerActivity::class.java)

        sectionTitle(R.string.group_lifeup)
        val b = card()
        module(b, R.string.mod_edge, R.string.mod_edge_desc, R.drawable.ic_swipe, EdgeActivity::class.java, service = true)
        module(b, R.string.mod_sound, R.string.mod_sound_desc, R.drawable.ic_volume, SoundActivity::class.java, service = true)
        module(b, R.string.mod_noti, R.string.mod_noti_desc, R.drawable.ic_bell, NotiActivity::class.java)
        module(b, R.string.mod_profiles, R.string.mod_profiles_desc, R.drawable.ic_apps, ProfilesActivity::class.java, service = true)
        module(b, R.string.mod_hidden, R.string.mod_hidden_desc, R.drawable.ic_hidden, HiddenActivity::class.java)

        sectionTitle(R.string.group_root)
        val c = card()
        if (rootMode) {
            module(c, R.string.mod_root, R.string.mod_root_desc, R.drawable.ic_root, RootActivity::class.java)
            module(c, R.string.mod_rootapps, R.string.mod_rootapps_desc, R.drawable.ic_apps, RootAppsActivity::class.java)
        } else {
            row(c, getString(R.string.mod_root), getString(R.string.mod_root_desc) + "\n🔒 " + getString(R.string.needs_root),
                R.drawable.ic_root, enabled = false) { startActivity(Intent(this, OnboardingActivity::class.java)) }
            row(c, getString(R.string.mod_rootapps), getString(R.string.mod_rootapps_desc) + "\n🔒 " + getString(R.string.needs_root),
                R.drawable.ic_apps, enabled = false) { startActivity(Intent(this, OnboardingActivity::class.java)) }
        }

        root.addView(text(getString(R.string.about), 12f, muted).apply { setPadding(dp(8), dp(8), dp(8), 0) })
    }

    private fun module(parent: ViewGroup, title: Int, desc: Int, icon: Int, cls: Class<*>, service: Boolean = false) {
        val sub = if (service && !KitService.running) getString(desc) + "\n⚠ " + getString(R.string.needs_service) else getString(desc)
        row(parent, getString(title), sub, icon) { startActivity(Intent(this, cls)) }
    }

    private fun openAutostart() {
        tryStart(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }
}

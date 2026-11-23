package com.vapurstudio.hyperkit.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.vapurstudio.hyperkit.KitService
import com.vapurstudio.hyperkit.Profile
import com.vapurstudio.hyperkit.Profiles
import com.vapurstudio.hyperkit.R

/** Per-app rotation, brightness, media volume and keep-screen-on (Display Assistant). */
class ProfilesActivity : BaseActivity() {
    override val screenTitle = R.string.mod_profiles
    private var open: String? = null

    override fun build() {
        val top = card()
        if (!KitService.running) row(top, getString(R.string.service_off), getString(R.string.setup_body), R.drawable.ic_power) { openAccessibility() }
        if (!Settings.System.canWrite(this)) {
            note(top, getString(R.string.tuner_write_settings))
            button(top, getString(R.string.tuner_allow)) {
                tryStart(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        note(top, getString(R.string.profiles_desc))
        button(top, getString(R.string.profiles_add)) {
            AppPicker.pick(this, R.string.profiles_add) { a ->
                val map = Profiles.all(this)
                if (a.pkg !in map) map[a.pkg] = Profile()
                Profiles.save(this, map); open = a.pkg; rebuild()
            }
        }

        val map = Profiles.all(this)
        for ((pkg, p) in map) {
            val c = card()
            row(c, AppPicker.label(this, pkg), summary(p), trailing = if (open == pkg) "▾" else "▸") {
                open = if (open == pkg) null else pkg; rebuild()
            }
            if (open != pkg) continue
            fun save() { map[pkg] = p; Profiles.save(this, map) }
            val rot = listOf(getString(R.string.keep_system), getString(R.string.rot_auto), getString(R.string.rot_portrait), getString(R.string.rot_landscape))
            row(c, getString(R.string.prof_rotation), trailing = rot[p.rotation + 1]) {
                choose(getString(R.string.prof_rotation), rot, p.rotation + 1) { p.rotation = it - 1; save(); rebuild() }
            }
            switchRow(c, getString(R.string.prof_keep_on), checked = p.keepOn) { p.keepOn = it; save() }
            slider(c, getString(R.string.prof_brightness), -1, 100, p.brightness,
                label = { if (it < 0) getString(R.string.keep_system) else "$it%" }, onStop = true) { p.brightness = it; save() }
            slider(c, getString(R.string.prof_volume), -1, 100, p.volume,
                label = { if (it < 0) getString(R.string.keep_system) else "$it%" }, onStop = true) { p.volume = it; save() }
            row(c, getString(R.string.prof_delete)) { map.remove(pkg); Profiles.save(this, map); open = null; rebuild() }
        }
    }

    private fun summary(p: Profile): String {
        val parts = mutableListOf<String>()
        if (p.rotation >= 0) parts += getString(listOf(R.string.rot_auto, R.string.rot_portrait, R.string.rot_landscape)[p.rotation])
        if (p.keepOn) parts += getString(R.string.prof_keep_on)
        if (p.brightness >= 0) parts += "☀ ${p.brightness}%"
        if (p.volume >= 0) parts += "♪ ${p.volume}%"
        return parts.joinToString(" · ").ifEmpty { getString(R.string.keep_system) }
    }
}

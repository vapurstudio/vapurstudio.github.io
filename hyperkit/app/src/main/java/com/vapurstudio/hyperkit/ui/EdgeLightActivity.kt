package com.vapurstudio.hyperkit.ui

import com.vapurstudio.hyperkit.Access
import com.vapurstudio.hyperkit.KitService
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R

class EdgeLightActivity : BaseActivity() {
    override val screenTitle = R.string.mod_light

    override fun build() {
        val c = card()
        if (!KitService.running) row(c, getString(R.string.service_off), getString(R.string.setup_body), R.drawable.ic_power) { openAccessibility() }
        if (!Access.listenerEnabled(this)) row(c, getString(R.string.listener_off), getString(R.string.listener_why), R.drawable.ic_bell) { openListenerSettings() }
        switchRow(c, getString(R.string.light_enable), getString(R.string.light_enable_desc), prefs.getBoolean(Prefs.LIGHT_ON, false)) {
            prefs.edit().putBoolean(Prefs.LIGHT_ON, it).apply()
        }
        val keys = listOf("app", "accent", "rainbow")
        val labels = listOf(getString(R.string.light_app), getString(R.string.light_accent), getString(R.string.light_rainbow))
        val cur = prefs.getString(Prefs.LIGHT_COLOR, "app")
        row(c, getString(R.string.light_color), trailing = labels[keys.indexOf(cur).coerceAtLeast(0)]) {
            choose(getString(R.string.light_color), labels, keys.indexOf(cur)) { prefs.edit().putString(Prefs.LIGHT_COLOR, keys[it]).apply(); rebuild() }
        }
        slider(c, getString(R.string.light_width), 2, 16, prefs.getInt(Prefs.LIGHT_WIDTH, 6), label = { "$it dp" }) {
            prefs.edit().putInt(Prefs.LIGHT_WIDTH, it).apply()
        }
        slider(c, getString(R.string.light_time), 1, 8, prefs.getInt(Prefs.LIGHT_TIME, 3), label = { "$it s" }) {
            prefs.edit().putInt(Prefs.LIGHT_TIME, it).apply()
        }
        button(c, getString(R.string.light_test)) {
            KitService.instance?.testLight() ?: toast(R.string.enable_service_first)
        }
        note(c, getString(R.string.light_note))
    }
}

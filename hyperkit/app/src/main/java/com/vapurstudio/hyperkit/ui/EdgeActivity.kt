package com.vapurstudio.hyperkit.ui

import android.app.AlertDialog
import com.vapurstudio.hyperkit.Action
import com.vapurstudio.hyperkit.Gesture
import com.vapurstudio.hyperkit.KitService
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R

class EdgeActivity : BaseActivity() {
    override val screenTitle = R.string.mod_edge

    override fun build() {
        if (!KitService.running) {
            val c = card()
            row(c, getString(R.string.service_off), getString(R.string.setup_body), R.drawable.ic_power) { openAccessibility() }
            button(c, getString(R.string.setup_open)) { openAccessibility() }
        }
        side("l", R.string.edge_left)
        side("r", R.string.edge_right)

        sectionTitle(R.string.edge_general)
        val g = card()
        switchRow(g, getString(R.string.edge_haptic), checked = prefs.getBoolean(Prefs.HAPTIC, true)) {
            prefs.edit().putBoolean(Prefs.HAPTIC, it).apply()
        }
        switchRow(g, getString(R.string.edge_landscape), checked = prefs.getBoolean(Prefs.LANDSCAPE, false)) {
            prefs.edit().putBoolean(Prefs.LANDSCAPE, it).apply()
        }
        slider(g, getString(R.string.edge_sensitivity), 16, 96, Prefs.distance(prefs), label = { "$it dp" }) {
            prefs.edit().putInt(Prefs.DISTANCE, it).apply()
        }
        note(g, getString(R.string.edge_note))
    }

    private fun side(side: String, title: Int) {
        sectionTitle(title)
        val c = card()
        val on = Prefs.edgeOn(prefs, side)
        switchRow(c, getString(R.string.edge_enabled), checked = on) {
            prefs.edit().putBoolean(Prefs.enabled(side), it).apply()
            rebuild()
        }
        for (g in Gesture.entries) {
            val a = Prefs.actionFor(prefs, side, g)
            row(c, getString(g.label), trailing = getString(a.label), enabled = on) { if (on) pick(side, g) }
        }
        slider(c, getString(R.string.edge_position), 0, 100, Prefs.posOf(prefs, side), on, { "$it%" }) {
            prefs.edit().putInt(Prefs.pos(side), it).apply()
        }
        slider(c, getString(R.string.edge_height), 5, 60, Prefs.heightOf(prefs, side), on, { "$it%" }) {
            prefs.edit().putInt(Prefs.height(side), it).apply()
        }
        slider(c, getString(R.string.edge_width), 4, 32, Prefs.widthOf(prefs, side), on, { "$it dp" }) {
            prefs.edit().putInt(Prefs.width(side), it).apply()
        }
        slider(c, getString(R.string.edge_visibility), 0, 100, Prefs.alphaOf(prefs, side), on, { "$it%" }) {
            prefs.edit().putInt(Prefs.alpha(side), it).apply()
        }
    }

    private fun pick(side: String, g: Gesture) {
        val actions = Action.entries
        val current = actions.indexOf(Prefs.actionFor(prefs, side, g))
        AlertDialog.Builder(this)
            .setTitle(getString(g.label))
            .setSingleChoiceItems(actions.map { getString(it.label) }.toTypedArray(), current) { d, i ->
                prefs.edit().putString(Prefs.action(side, g), actions[i].name).apply()
                d.dismiss()
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

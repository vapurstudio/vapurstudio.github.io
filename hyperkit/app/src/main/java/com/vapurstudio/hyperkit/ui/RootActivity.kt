package com.vapurstudio.hyperkit.ui

import android.app.AlertDialog
import com.vapurstudio.hyperkit.Access
import com.vapurstudio.hyperkit.ChargeNode
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R
import com.vapurstudio.hyperkit.Root
import kotlin.math.roundToInt

/** Root-only tools: display, refresh rate, charge limit (Battery Guardian), gesture line, reboot menu. */
class RootActivity : BaseActivity() {
    override val screenTitle = R.string.mod_root

    companion object {
        private var revert: Runnable? = null
        private const val GESTURE_OVERLAY = "com.android.systemui.gesture.line.overlay"
    }

    private var rootOk: Boolean? = null
    private var node: String? = null
    private var nodeChecked = false

    override fun onResume() {
        super.onResume()
        if (rootOk == null) Root.check { rootOk = it; rebuild() }
        if (!nodeChecked) Root.run(ChargeNode.detectCmd()) { r ->
            nodeChecked = true
            node = r.out.lines().firstOrNull { it.startsWith("/") }
            node?.let { prefs.edit().putString(Prefs.CHG_NODE, it).apply() }
            rebuild()
        }
    }

    override fun build() {
        val st = card()
        row(st, getString(when (rootOk) { null -> R.string.ob_checking; true -> R.string.root_ok; false -> R.string.ob_no_root }),
            getString(R.string.root_grant_desc), R.drawable.ic_root)
        button(st, getString(R.string.root_grant)) {
            Access.grantAllWithRoot(this) { toast(if (it) R.string.ob_root_ok else R.string.ob_root_partial); rebuild() }
        }

        // display
        sectionTitle(R.string.tuner_display)
        val d = card()
        val rates = display?.supportedModes?.map { it.refreshRate.roundToInt() }?.distinct()?.sorted() ?: emptyList()
        val cur = prefs.getInt("root_rr", 0)
        val labels = listOf(getString(R.string.rr_auto)) + rates.map { "$it Hz" }
        row(d, getString(R.string.rr_title), getString(R.string.rr_desc), trailing = if (cur == 0) labels[0] else "$cur Hz") {
            choose(getString(R.string.rr_title), labels, if (cur == 0) 0 else rates.indexOf(cur) + 1) { i ->
                val rr = if (i == 0) 0 else rates[i - 1]
                prefs.edit().putInt("root_rr", rr).apply()
                val cmd = if (rr == 0) "settings delete system min_refresh_rate; settings delete secure user_refresh_rate; " +
                    "settings delete system miui_refresh_rate; settings put system peak_refresh_rate ${rates.maxOrNull() ?: 60}.0"
                else "settings put system peak_refresh_rate $rr.0; settings put system min_refresh_rate $rr.0; " +
                    "settings put secure user_refresh_rate $rr; settings put system miui_refresh_rate $rr"
                Root.run(cmd) { rebuild() }
            }
        }
        val dpi = resources.configuration.densityDpi
        slider(d, getString(R.string.density), 28, 64, dpi / 10, label = { "${it * 10} dpi" }, onStop = true) {
            applyDensity(dpi, it * 10)
        }
        row(d, getString(R.string.density_reset)) { Root.run("wm density reset") }

        // gesture line
        sectionTitle(R.string.nav_title)
        val n = card()
        note(n, getString(R.string.nav_gap_desc))
        row(n, getString(R.string.nav_gap_remove)) { confirm(R.string.nav_gap_remove) { Root.run("pm uninstall -k --user 0 $GESTURE_OVERLAY") { toast(R.string.done_reboot) } } }
        row(n, getString(R.string.nav_gap_restore)) { Root.run("cmd package install-existing $GESTURE_OVERLAY") { toast(R.string.done_reboot) } }

        // charge limit
        sectionTitle(R.string.chg_title)
        val c = card()
        note(c, getString(R.string.chg_desc))
        if (nodeChecked && node == null) {
            note(c, getString(R.string.chg_unsupported))
        } else {
            switchRow(c, getString(R.string.chg_enable), node ?: getString(R.string.ob_checking),
                prefs.getBoolean(Prefs.CHG_ON, false), enabled = node != null) { on ->
                prefs.edit().putBoolean(Prefs.CHG_ON, on).apply()
                if (!on) node?.let { Root.run(ChargeNode.write(it, false)) }
            }
            slider(c, getString(R.string.chg_limit), 50, 100, prefs.getInt(Prefs.CHG_LIMIT, 80), node != null,
                label = { "%$it" }, onStop = true) { prefs.edit().putInt(Prefs.CHG_LIMIT, it).apply() }
        }

        // power
        sectionTitle(R.string.power_title)
        val p = card()
        row(p, getString(R.string.pw_systemui)) { confirm(R.string.pw_systemui) { Root.run("pkill -f com.android.systemui || killall com.android.systemui") } }
        row(p, getString(R.string.pw_reboot)) { confirm(R.string.pw_reboot) { Root.run("svc power reboot || reboot") } }
        row(p, getString(R.string.pw_recovery)) { confirm(R.string.pw_recovery) { Root.run("reboot recovery") } }
        row(p, getString(R.string.pw_bootloader)) { confirm(R.string.pw_bootloader) { Root.run("reboot bootloader") } }
        row(p, getString(R.string.pw_off)) { confirm(R.string.pw_off) { Root.run("svc power shutdown || reboot -p") } }
    }

    private fun confirm(title: Int, ok: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(R.string.are_you_sure)
            .setPositiveButton(android.R.string.ok) { _, _ -> ok() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    /** Applies a new density and reverts it after 15 s unless the user confirms (like display mode changes). */
    private fun applyDensity(old: Int, new: Int) {
        if (new == old) return
        revert?.let { Root.main.removeCallbacks(it) }
        val r = Runnable { Root.run("wm density $old"); revert = null }
        revert = r
        Root.run("wm density $new") {
            Root.main.postDelayed(r, 15_000)
            AlertDialog.Builder(this).setTitle(R.string.density).setMessage(R.string.density_keep)
                .setCancelable(false)
                .setPositiveButton(R.string.keep) { _, _ -> Root.main.removeCallbacks(r); revert = null }
                .setNegativeButton(R.string.revert) { _, _ -> Root.main.removeCallbacks(r); r.run() }
                .show()
        }
    }
}

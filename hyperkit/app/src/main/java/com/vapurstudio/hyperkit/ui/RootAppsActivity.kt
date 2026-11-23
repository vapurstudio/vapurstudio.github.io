package com.vapurstudio.hyperkit.ui

import android.app.AlertDialog
import com.vapurstudio.hyperkit.R
import com.vapurstudio.hyperkit.Root

/** Root app tools: freeze / unfreeze, force stop, open in a floating window (MultiStar). */
class RootAppsActivity : BaseActivity() {
    override val screenTitle = R.string.mod_rootapps
    private var apps: List<String>? = null
    private var frozen = setOf<String>()
    private var system = false

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val flag = if (system) "-s" else "-3"
        Root.run("pm list packages $flag; echo ===; pm list packages -d") { r ->
            val (all, dis) = r.out.split("===").let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            fun parse(s: String) = s.lines().map { it.trim().removePrefix("package:") }.filter { it.isNotEmpty() }
            frozen = parse(dis).toSet()
            apps = parse(all).filter { it != packageName }.sortedBy { AppPicker.label(this, it).lowercase() }
            rebuild()
        }
    }

    override fun build() {
        val top = card()
        note(top, getString(R.string.rootapps_desc))
        switchRow(top, getString(R.string.rootapps_system), getString(R.string.rootapps_system_desc), system) {
            system = it; apps = null; rebuild(); load()
        }
        val list = card()
        val a = apps
        if (a == null) { note(list, getString(R.string.ob_checking)); return }
        for (pkg in a) {
            val isFrozen = pkg in frozen
            row(list, AppPicker.label(this, pkg), pkg, trailing = if (isFrozen) "❄" else null) { actions(pkg, isFrozen) }
        }
    }

    private fun actions(pkg: String, isFrozen: Boolean) {
        val items = mutableListOf<Pair<Int, () -> Unit>>()
        if (!isFrozen) items += R.string.ra_floating to {
            val comp = packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString()
            if (comp == null) toast(R.string.hidden_failed)
            else Root.run("am start --windowingMode 5 -n $comp") { if (!it.ok) toast(R.string.hidden_failed) }
        }
        items += (if (isFrozen) R.string.ra_unfreeze else R.string.ra_freeze) to {
            Root.run(if (isFrozen) "pm enable $pkg" else "pm disable-user --user 0 $pkg") { load() }
        }
        items += R.string.ra_stop to { Root.run("am force-stop $pkg") { toast(R.string.done) } }
        items += R.string.ra_clear_cache to { Root.run("rm -rf /data/data/$pkg/cache/* /sdcard/Android/data/$pkg/cache/*") { toast(R.string.done) } }
        AlertDialog.Builder(this).setTitle(AppPicker.label(this, pkg))
            .setItems(items.map { getString(it.first) }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
}

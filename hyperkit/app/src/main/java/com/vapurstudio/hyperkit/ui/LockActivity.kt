package com.vapurstudio.hyperkit.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.widget.EditText
import com.vapurstudio.hyperkit.Access
import com.vapurstudio.hyperkit.KitService
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R
import com.vapurstudio.hyperkit.lock.ClockStyle
import com.vapurstudio.hyperkit.lock.LockScreen

class LockActivity : BaseActivity() {
    override val screenTitle = R.string.mod_lock
    private val pickImage = 41

    override fun build() {
        val svc = KitService.running
        val top = card()
        if (!svc) {
            row(top, getString(R.string.service_off), getString(R.string.setup_body), R.drawable.ic_power) { openAccessibility() }
        }
        switchRow(top, getString(R.string.lock_enable), getString(R.string.lock_enable_desc),
            prefs.getBoolean(Prefs.LOCK_ON, false), enabled = svc) { prefs.edit().putBoolean(Prefs.LOCK_ON, it).apply() }
        button(top, getString(R.string.lock_preview)) {
            val s = KitService.instance ?: run { toast(R.string.enable_service_first); return@button }
            s.lock.show(asPreview = true)
        }
        note(top, getString(R.string.lock_note))

        // clock
        sectionTitle(R.string.lock_clock)
        val c = card()
        val style = ClockStyle.of(prefs.getString(Prefs.LOCK_STYLE, null))
        row(c, getString(R.string.lock_style), trailing = getString(style.label)) {
            choose(getString(R.string.lock_style), ClockStyle.entries.map { getString(it.label) }, style.ordinal) {
                prefs.edit().putString(Prefs.LOCK_STYLE, ClockStyle.entries[it].name).apply(); rebuild()
            }
        }
        val pos = prefs.getString(Prefs.LOCK_POS, "top")
        row(c, getString(R.string.lock_position), trailing = getString(if (pos == "center") R.string.pos_center else R.string.pos_top)) {
            choose(getString(R.string.lock_position), listOf(getString(R.string.pos_top), getString(R.string.pos_center)),
                if (pos == "center") 1 else 0) {
                prefs.edit().putString(Prefs.LOCK_POS, if (it == 1) "center" else "top").apply(); rebuild()
            }
        }
        slider(c, getString(R.string.lock_size), 60, 140, prefs.getInt(Prefs.LOCK_SIZE, 100), label = { "$it%" }) {
            prefs.edit().putInt(Prefs.LOCK_SIZE, it).apply()
        }
        note(c, getString(R.string.lock_color))
        swatches(c, LockScreen.COLORS, prefs.getInt(Prefs.LOCK_COLOR, Color.WHITE)) {
            prefs.edit().putInt(Prefs.LOCK_COLOR, it).apply(); rebuild()
        }
        val msg = prefs.getString(Prefs.LOCK_MSG, "")!!
        row(c, getString(R.string.lock_message), trailing = msg.ifBlank { "—" }) { editMessage(msg) }

        // background
        sectionTitle(R.string.lock_background)
        val b = card()
        val bg = prefs.getString(Prefs.LOCK_BG, "g0")!!
        val bgKeys = listOf("image", "black") + LockScreen.GRADIENTS.indices.map { "g$it" }
        val bgLabels = listOf(getString(R.string.bg_image), getString(R.string.bg_black)) +
            LockScreen.GRADIENTS.indices.map { getString(R.string.bg_gradient, it + 1) }
        row(b, getString(R.string.lock_background), trailing = bgLabels[bgKeys.indexOf(bg).coerceAtLeast(0)]) {
            choose(getString(R.string.lock_background), bgLabels, bgKeys.indexOf(bg)) { i ->
                if (bgKeys[i] == "image") pick() else { prefs.edit().putString(Prefs.LOCK_BG, bgKeys[i]).apply(); rebuild() }
            }
        }
        button(b, getString(R.string.bg_pick)) { pick() }
        slider(b, getString(R.string.lock_dim), 0, 80, prefs.getInt(Prefs.LOCK_DIM, 20), label = { "$it%" }) {
            prefs.edit().putInt(Prefs.LOCK_DIM, it).apply()
        }

        // content
        sectionTitle(R.string.lock_content_title)
        val n = card()
        if (!Access.listenerEnabled(this)) {
            row(n, getString(R.string.listener_off), getString(R.string.listener_why), R.drawable.ic_bell) { openListenerSettings() }
        }
        val modes = listOf("cards", "icons", "none")
        val modeLabels = listOf(getString(R.string.notif_cards), getString(R.string.notif_icons), getString(R.string.notif_none))
        val mode = prefs.getString(Prefs.LOCK_NOTIF, "cards")
        row(n, getString(R.string.lock_notifs), trailing = modeLabels[modes.indexOf(mode).coerceAtLeast(0)]) {
            choose(getString(R.string.lock_notifs), modeLabels, modes.indexOf(mode)) {
                prefs.edit().putString(Prefs.LOCK_NOTIF, modes[it]).apply(); rebuild()
            }
        }
        switchRow(n, getString(R.string.lock_show_content), getString(R.string.lock_show_content_desc),
            prefs.getBoolean(Prefs.LOCK_CONTENT, false)) { prefs.edit().putBoolean(Prefs.LOCK_CONTENT, it).apply() }
        switchRow(n, getString(R.string.lock_media), checked = prefs.getBoolean(Prefs.LOCK_MEDIA, true)) {
            prefs.edit().putBoolean(Prefs.LOCK_MEDIA, it).apply()
        }
        switchRow(n, getString(R.string.lock_battery), checked = prefs.getBoolean(Prefs.LOCK_BATTERY, true)) {
            prefs.edit().putBoolean(Prefs.LOCK_BATTERY, it).apply()
        }
        switchRow(n, getString(R.string.lock_double_tap), checked = prefs.getBoolean(Prefs.LOCK_DOUBLE_TAP, true)) {
            prefs.edit().putBoolean(Prefs.LOCK_DOUBLE_TAP, it).apply()
        }

        // shortcuts
        sectionTitle(R.string.lock_shortcuts)
        val s = card()
        shortcutRow(s, R.string.sc_left, Prefs.LOCK_SC_LEFT, "camera")
        shortcutRow(s, R.string.sc_right, Prefs.LOCK_SC_RIGHT, "flash")

        // ClockFace
        sectionTitle(R.string.mod_clockface)
        val f = card()
        note(f, getString(R.string.clockface_desc))
        val aod = ClockStyle.of(prefs.getString(Prefs.AOD_STYLE, ClockStyle.ANALOG.name))
        row(f, getString(R.string.lock_style), trailing = getString(aod.label)) {
            choose(getString(R.string.lock_style), ClockStyle.entries.map { getString(it.label) }, aod.ordinal) {
                prefs.edit().putString(Prefs.AOD_STYLE, ClockStyle.entries[it].name).apply(); rebuild()
            }
        }
        swatches(f, LockScreen.COLORS, prefs.getInt(Prefs.AOD_COLOR, Color.WHITE)) {
            prefs.edit().putInt(Prefs.AOD_COLOR, it).apply(); rebuild()
        }
        button(f, getString(R.string.clockface_open)) {
            tryStart(Intent(Settings.ACTION_DREAM_SETTINGS),
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$DreamSettingsActivity")))
        }
    }

    private fun scLabel(v: String) = when {
        v == "camera" -> getString(R.string.sc_camera)
        v == "flash" -> getString(R.string.act_flashlight)
        v.startsWith("app:") -> AppPicker.label(this, v.removePrefix("app:"))
        else -> getString(R.string.act_none)
    }

    private fun shortcutRow(parent: android.view.ViewGroup, title: Int, key: String, def: String) {
        val v = prefs.getString(key, def)!!
        row(parent, getString(title), trailing = scLabel(v)) {
            val opts = listOf("none", "camera", "flash", "app")
            choose(getString(title), listOf(getString(R.string.act_none), getString(R.string.sc_camera),
                getString(R.string.act_flashlight), getString(R.string.sc_app)),
                opts.indexOf(if (v.startsWith("app:")) "app" else v)) { i ->
                if (opts[i] == "app") AppPicker.pick(this, R.string.sc_app) { a ->
                    prefs.edit().putString(key, "app:${a.pkg}").apply(); rebuild()
                } else { prefs.edit().putString(key, opts[i]).apply(); rebuild() }
            }
        }
    }

    private fun editMessage(cur: String) {
        val et = EditText(this).apply { setText(cur); setSingleLine(false); maxLines = 3 }
        AlertDialog.Builder(this).setTitle(R.string.lock_message).setView(et)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit().putString(Prefs.LOCK_MSG, et.text.toString().trim()).apply(); rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun pick() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), pickImage)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != pickImage || resultCode != RESULT_OK) return
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.edit().putString(Prefs.LOCK_BG_URI, uri.toString()).putString(Prefs.LOCK_BG, "image").apply()
    }
}

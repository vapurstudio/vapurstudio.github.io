package com.vapurstudio.hyperkit.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.vapurstudio.hyperkit.R
import com.vapurstudio.hyperkit.Tuner

class TunerActivity : BaseActivity() {
    override val screenTitle = R.string.mod_tuner

    private val icons = listOf(
        "alarm_clock" to R.string.icon_alarm,
        "bluetooth" to R.string.icon_bluetooth,
        "nfc" to R.string.icon_nfc,
        "volume" to R.string.icon_volume,
        "zen" to R.string.icon_zen,
        "headset" to R.string.icon_headset,
        "hotspot" to R.string.icon_hotspot,
        "location" to R.string.icon_location,
        "vpn" to R.string.icon_vpn,
        "rotate" to R.string.icon_rotate,
        "airplane" to R.string.icon_airplane,
        "cast" to R.string.icon_cast,
    )
    private val timeouts = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000, Int.MAX_VALUE)

    override fun build() {
        val secure = Tuner.hasSecure(this)
        val perm = card()
        if (secure) {
            row(perm, getString(R.string.tuner_perm_ok), icon = R.drawable.ic_tune)
        } else {
            row(perm, getString(R.string.tuner_perm_title), getString(R.string.tuner_perm_body), R.drawable.ic_tune)
            val cmd = Tuner.adbCommand(this)
            perm.addView(text(cmd, 13f, ink).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                setTextIsSelectable(true)
                setPadding(dp(16), dp(4), dp(16), dp(8))
            })
            button(perm, getString(R.string.copy)) {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", cmd))
                toast(R.string.copied)
            }
        }

        sectionTitle(R.string.tuner_anim)
        val a = card()
        val names = listOf(R.string.tuner_anim_window, R.string.tuner_anim_transition, R.string.tuner_anim_duration)
        Tuner.ANIM_KEYS.forEachIndexed { i, key ->
            val step = (Tuner.getAnim(this, key) * 4).toInt().coerceIn(0, 40)
            slider(a, getString(names[i]), 0, 20, step, secure, { fmt(it / 4f) }, onStop = true) {
                Tuner.setAnim(this, key, it / 4f)
            }
        }
        note(a, getString(R.string.tuner_anim_hint))

        sectionTitle(R.string.tuner_icons)
        val ic = card()
        val hidden = Tuner.hiddenIcons(this)
        for ((slot, label) in icons) {
            switchRow(ic, getString(label), checked = slot in hidden, enabled = secure) { on ->
                val set = Tuner.hiddenIcons(this)
                if (on) set.add(slot) else set.remove(slot)
                Tuner.setHiddenIcons(this, set)
            }
        }
        note(ic, getString(R.string.tuner_icons_hint))

        sectionTitle(R.string.tuner_display)
        val d = card()
        val canWrite = Settings.System.canWrite(this)
        if (!canWrite) {
            note(d, getString(R.string.tuner_write_settings))
            button(d, getString(R.string.tuner_allow)) {
                tryStart(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        val cur = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
        row(d, getString(R.string.tuner_timeout), trailing = timeoutLabel(cur), enabled = canWrite) {
            if (canWrite) pickTimeout(cur)
        }
        val font = (Settings.System.getFloat(contentResolver, Settings.System.FONT_SCALE, 1f) * 20).toInt()
        slider(d, getString(R.string.tuner_font), 16, 30, font, canWrite, { fmt(it / 20f) + "×" }, onStop = true) {
            runCatching { Settings.System.putFloat(contentResolver, Settings.System.FONT_SCALE, it / 20f) }
        }
    }

    private fun fmt(f: Float) = if (f == f.toInt().toFloat()) f.toInt().toString() else "%.2f".format(f).trimEnd('0')

    private fun timeoutLabel(ms: Int): String = when {
        ms >= Int.MAX_VALUE / 2 -> getString(R.string.never)
        ms < 60_000 -> "${ms / 1000} s"
        else -> "${ms / 60_000} min"
    }

    private fun pickTimeout(cur: Int) {
        val labels = timeouts.map { timeoutLabel(it) }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.tuner_timeout)
            .setSingleChoiceItems(labels, timeouts.indexOf(cur)) { dlg, i ->
                runCatching { Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeouts[i]) }
                dlg.dismiss()
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

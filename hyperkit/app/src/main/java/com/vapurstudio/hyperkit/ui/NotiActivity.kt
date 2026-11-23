package com.vapurstudio.hyperkit.ui

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.widget.EditText
import android.widget.LinearLayout
import com.vapurstudio.hyperkit.Access
import com.vapurstudio.hyperkit.NotiDb
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R

/** NotiStar: searchable history of every notification, kept on the device. */
class NotiActivity : BaseActivity() {
    override val screenTitle = R.string.mod_noti
    private var search = ""
    private var pkg: String? = null
    private var results: LinearLayout? = null

    override fun build() {
        val c = card()
        if (!Access.listenerEnabled(this)) {
            row(c, getString(R.string.listener_off), getString(R.string.listener_why), R.drawable.ic_bell) { openListenerSettings() }
        }
        switchRow(c, getString(R.string.noti_record), getString(R.string.noti_record_desc), prefs.getBoolean(Prefs.NOTI_LOG, true)) {
            prefs.edit().putBoolean(Prefs.NOTI_LOG, it).apply()
        }
        row(c, getString(R.string.noti_filter), trailing = pkg?.let { AppPicker.label(this, it) } ?: getString(R.string.noti_all)) {
            val apps = NotiDb.get(this).apps()
            val labels = listOf(getString(R.string.noti_all)) + apps.map { it.second }
            choose(getString(R.string.noti_filter), labels, apps.indexOfFirst { it.first == pkg } + 1) { i ->
                pkg = if (i == 0) null else apps[i - 1].first; rebuild()
            }
        }
        row(c, getString(R.string.noti_clear)) {
            AlertDialog.Builder(this).setMessage(R.string.noti_clear_q)
                .setPositiveButton(android.R.string.ok) { _, _ -> NotiDb.get(this).clear(); rebuild() }
                .setNegativeButton(android.R.string.cancel, null).show()
        }

        val et = EditText(this).apply {
            hint = getString(R.string.noti_search)
            setText(search)
            setSingleLine()
            setTextColor(ink); setHintTextColor(muted)
            background = GradientDrawable().apply { cornerRadius = dpf(16f); setColor(getColor(R.color.card)) }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { search = s?.toString() ?: ""; fill() }
            })
        }
        root.addView(et, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        results = card()
        fill()
    }

    private fun fill() {
        val box = results ?: return
        box.removeAllViews()
        val list = NotiDb.get(this).query(search.trim(), pkg)
        if (list.isEmpty()) { note(box, getString(R.string.noti_empty)); return }
        for (n in list) {
            val sub = listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString("\n")
            row(box, n.app, sub, trailing = DateUtils.getRelativeTimeSpanString(n.time).toString()) {
                packageManager.getLaunchIntentForPackage(n.pkg)?.let { tryStart(it) }
            }
        }
    }
}

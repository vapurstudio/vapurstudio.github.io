package com.vapurstudio.hyperkit.ui

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.vapurstudio.hyperkit.HiddenItem
import com.vapurstudio.hyperkit.R

class HiddenActivity : BaseActivity() {
    override val screenTitle = R.string.mod_hidden

    override fun build() {
        note(card(), getString(R.string.hidden_note))
        val c = card()
        for (item in HiddenItem.ALL) {
            val ok = item.available(this)
            val sub = getString(item.desc) + if (ok) "" else "\n" + getString(R.string.hidden_unavailable)
            row(c, getString(item.title), sub, trailing = if (ok) "›" else null, enabled = ok,
                onLong = { if (ok) pin(item) }) {
                if (!item.launch(this)) toast(R.string.hidden_failed)
            }
        }
    }

    private fun pin(item: HiddenItem) {
        val sm = getSystemService(ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) { toast(R.string.pin_unsupported); return }
        val intent = Intent(this, LaunchActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(LaunchActivity.EXTRA_ID, item.id)
        val info = ShortcutInfo.Builder(this, "hidden_${item.id}")
            .setShortLabel(getString(item.title))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        if (!runCatching { sm.requestPinShortcut(info, null) }.getOrDefault(false)) toast(R.string.pin_unsupported)
    }
}

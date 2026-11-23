package com.vapurstudio.hyperkit.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R
import com.vapurstudio.hyperkit.Wonderland

class WonderlandActivity : BaseActivity() {
    override val screenTitle = R.string.mod_wonder
    private val req = 42

    override fun build() {
        val c = card()
        note(c, getString(R.string.wonder_desc))
        val has = prefs.getString(Prefs.WL_URI, null) != null
        row(c, getString(R.string.bg_pick), trailing = if (has) "✓" else null, icon = R.drawable.ic_wallpaper) { pick() }
        slider(c, getString(R.string.wonder_depth), 2, 20, prefs.getInt(Prefs.WL_DEPTH, 8), label = { "$it%" }) {
            prefs.edit().putInt(Prefs.WL_DEPTH, it).apply()
        }
        slider(c, getString(R.string.lock_dim), 0, 60, prefs.getInt(Prefs.WL_DIM, 0), label = { "$it%" }) {
            prefs.edit().putInt(Prefs.WL_DIM, it).apply()
        }
        button(c, getString(R.string.wonder_apply)) {
            if (!has) { pick(); return@button }
            tryStart(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, Wonderland::class.java)),
                Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
            )
        }
    }

    private fun pick() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), req)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != req || resultCode != RESULT_OK) return
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.edit().putString(Prefs.WL_URI, uri.toString()).apply()
    }
}

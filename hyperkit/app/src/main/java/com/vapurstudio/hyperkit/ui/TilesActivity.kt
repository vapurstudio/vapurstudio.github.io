package com.vapurstudio.hyperkit.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import com.vapurstudio.hyperkit.R
import com.vapurstudio.hyperkit.tiles.AnimationsTile
import com.vapurstudio.hyperkit.tiles.CaffeineTile
import com.vapurstudio.hyperkit.tiles.LockTile
import com.vapurstudio.hyperkit.tiles.MediaKeysTile
import com.vapurstudio.hyperkit.tiles.PowerTile
import com.vapurstudio.hyperkit.tiles.ScreenshotTile

class TilesActivity : BaseActivity() {
    override val screenTitle = R.string.mod_tiles

    private data class T(val cls: Class<*>, val title: Int, val desc: Int, val icon: Int)

    private val tiles = listOf(
        T(CaffeineTile::class.java, R.string.tile_caffeine, R.string.tile_caffeine_desc, R.drawable.ic_coffee),
        T(LockTile::class.java, R.string.tile_lock, R.string.tile_lock_desc, R.drawable.ic_lock),
        T(ScreenshotTile::class.java, R.string.tile_screenshot, R.string.tile_screenshot_desc, R.drawable.ic_screenshot),
        T(PowerTile::class.java, R.string.tile_power, R.string.tile_power_desc, R.drawable.ic_power),
        T(AnimationsTile::class.java, R.string.tile_animations, R.string.tile_animations_desc, R.drawable.ic_animation),
        T(MediaKeysTile::class.java, R.string.tile_media_keys, R.string.tile_media_keys_desc, R.drawable.ic_volume),
    )

    override fun build() {
        val info = card()
        note(info, getString(R.string.tiles_how))
        val c = card()
        for (t in tiles) {
            row(c, getString(t.title), getString(t.desc), t.icon, trailing = "＋") { add(t) }
        }
    }

    private fun add(t: T) {
        if (android.os.Build.VERSION.SDK_INT < 33) { toast(R.string.tiles_how); return }
        val sbm = getSystemService(StatusBarManager::class.java)
        try {
            sbm.requestAddTileService(
                ComponentName(this, t.cls), getString(t.title),
                Icon.createWithResource(this, t.icon), mainExecutor,
            ) { result ->
                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) toast(R.string.tile_added)
                else if (result >= 1000) toast(R.string.tiles_how) // error codes: fall back to manual steps
            }
        } catch (_: Exception) {
            toast(R.string.tiles_how)
        }
    }
}

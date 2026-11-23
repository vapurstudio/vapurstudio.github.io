package com.vapurstudio.hyperkit

import android.content.Context
import android.content.SharedPreferences

/** Every user setting lives here so the service, tiles and screens agree on keys and defaults. */
object Prefs {
    const val FILE = "hyperkit"

    // Edge gestures (per side: "l" / "r")
    fun enabled(side: String) = "edge_${side}_on"
    fun action(side: String, gesture: Gesture) = "edge_${side}_${gesture.key}"
    fun pos(side: String) = "edge_${side}_pos"        // 0..100 % of screen height (handle centre)
    fun height(side: String) = "edge_${side}_height"  // 5..60 %
    fun width(side: String) = "edge_${side}_width"    // 4..32 dp
    fun alpha(side: String) = "edge_${side}_alpha"    // 0..100 %
    const val HAPTIC = "edge_haptic"
    const val LANDSCAPE = "edge_landscape"
    const val DISTANCE = "edge_distance"              // 16..96 dp

    // Sound assistant
    const val MEDIA_KEYS = "sound_media_keys"
    const val LONG_PRESS_SKIP = "sound_long_press"

    // Caffeine
    const val CAFFEINE = "caffeine_on"

    // Animations tile: remembers scales before turning them off
    const val ANIM_SAVED = "anim_saved"

    fun get(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun defaultAction(side: String, gesture: Gesture): Action = when (gesture) {
        Gesture.SWIPE_IN -> Action.BACK
        Gesture.SWIPE_UP -> if (side == "l") Action.HOME else Action.RECENTS
        Gesture.SWIPE_DOWN -> if (side == "l") Action.NOTIFICATIONS else Action.QUICK_SETTINGS
        Gesture.HOLD -> if (side == "l") Action.LAST_APP else Action.SCREENSHOT
    }

    fun actionFor(p: SharedPreferences, side: String, gesture: Gesture): Action =
        Action.fromName(p.getString(action(side, gesture), null)) ?: defaultAction(side, gesture)

    fun edgeOn(p: SharedPreferences, side: String) = p.getBoolean(enabled(side), side == "r")
    fun posOf(p: SharedPreferences, side: String) = p.getInt(pos(side), 62)
    fun heightOf(p: SharedPreferences, side: String) = p.getInt(height(side), 22)
    fun widthOf(p: SharedPreferences, side: String) = p.getInt(width(side), 14)
    fun alphaOf(p: SharedPreferences, side: String) = p.getInt(alpha(side), 0)
    fun distance(p: SharedPreferences) = p.getInt(DISTANCE, 36)
}

enum class Gesture(val key: String, val label: Int) {
    SWIPE_IN("in", R.string.edge_swipe_in),
    SWIPE_UP("up", R.string.edge_swipe_up),
    SWIPE_DOWN("down", R.string.edge_swipe_down),
    HOLD("hold", R.string.edge_hold),
}

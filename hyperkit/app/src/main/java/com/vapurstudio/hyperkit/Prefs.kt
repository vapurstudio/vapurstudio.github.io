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

    // Mode chosen on first launch: "standard" or "root"
    const val MODE = "mode"
    const val KEEP_ALIVE = "keep_alive"

    // Lock screen (LockStar)
    const val LOCK_ON = "lock_on"
    const val LOCK_STYLE = "lock_style"          // ClockStyle name
    const val LOCK_COLOR = "lock_color"          // ARGB int
    const val LOCK_SIZE = "lock_size"            // 60..140 %
    const val LOCK_POS = "lock_pos"              // "top" | "center"
    const val LOCK_BG = "lock_bg"                // "image" | "black" | "g0".."g5"
    const val LOCK_BG_URI = "lock_bg_uri"
    const val LOCK_DIM = "lock_dim"              // 0..80 %
    const val LOCK_MSG = "lock_msg"
    const val LOCK_NOTIF = "lock_notif"          // "none" | "icons" | "cards"
    const val LOCK_CONTENT = "lock_content"      // show notification text
    const val LOCK_MEDIA = "lock_media"
    const val LOCK_BATTERY = "lock_battery"
    const val LOCK_DOUBLE_TAP = "lock_double_tap"
    const val LOCK_SC_LEFT = "lock_sc_l"         // "none" | "camera" | "flash" | "app:<pkg>"
    const val LOCK_SC_RIGHT = "lock_sc_r"

    // ClockFace (screensaver)
    const val AOD_STYLE = "aod_style"
    const val AOD_COLOR = "aod_color"

    // Edge lighting
    const val LIGHT_ON = "light_on"
    const val LIGHT_COLOR = "light_color"        // "app" | "accent" | "rainbow"
    const val LIGHT_WIDTH = "light_width"        // 2..16 dp
    const val LIGHT_TIME = "light_time"          // 1..8 s

    // Notification history (NotiStar)
    const val NOTI_LOG = "noti_log"

    // App profiles
    const val PROFILES = "profiles"
    const val PROFILE_BASE = "profile_base"

    // Wonderland
    const val WL_URI = "wl_uri"
    const val WL_DEPTH = "wl_depth"              // 2..20 %
    const val WL_DIM = "wl_dim"                  // 0..60 %

    // Root: charge limit
    const val CHG_ON = "chg_on"
    const val CHG_LIMIT = "chg_limit"
    const val CHG_NODE = "chg_node"

    fun isRoot(ctx: Context) = get(ctx).getString(MODE, null) == "root"

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

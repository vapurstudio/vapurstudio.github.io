package com.vapurstudio.hyperkit.lock

import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.provider.MediaStore
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.vapurstudio.hyperkit.Access
import com.vapurstudio.hyperkit.KitService
import com.vapurstudio.hyperkit.NotiService
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R
import com.vapurstudio.hyperkit.Torch

/**
 * LockStar-style lock screen drawn as an accessibility overlay above the HyperOS keyguard.
 * Swiping up hands over to the system keyguard (PIN / fingerprint / face), so security is unchanged.
 */
class LockScreen(private val svc: KitService) {
    companion object {
        val GRADIENTS = listOf(
            intArrayOf(0xFF141E30.toInt(), 0xFF243B55.toInt()),
            intArrayOf(0xFF42275A.toInt(), 0xFF734B6D.toInt()),
            intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
            intArrayOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt()),
            intArrayOf(0xFF134E5E.toInt(), 0xFF71B280.toInt()),
            intArrayOf(0xFFCB356B.toInt(), 0xFFBD3F32.toInt()),
        )
        val COLORS = listOf(Color.WHITE, 0xFFFFD43B.toInt(), 0xFF8AA6FF.toInt(), 0xFF69DB7C.toInt(),
            0xFFFF8787.toInt(), 0xFFDA77F2.toInt(), 0xFF66D9E8.toInt(), 0xFFFFA94D.toInt())
    }

    private val wm = svc.getSystemService(WindowManager::class.java)
    private val prefs = Prefs.get(svc)
    private val d = svc.resources.displayMetrics.density
    private var root: FrameLayout? = null
    private var content: LinearLayout? = null
    private var dynamic: LinearLayout? = null
    private var battery: TextView? = null
    private var bgCache: Pair<String, Bitmap>? = null
    var preview = false
        private set
    val showing get() = root != null

    private fun dp(v: Int) = (v * d).toInt()

    /**
     * Adds the overlay. With [hidden] it is prepared invisibly while the screen is off, so it never
     * covers HyperOS's Always-on display, and [reveal] shows it once the screen turns on.
     */
    fun show(asPreview: Boolean = false, hidden: Boolean = false) {
        if (showing) { if (asPreview) hide() else { if (!hidden) reveal(); return } }
        preview = asPreview
        val r = build()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            title = "HyperKit lock"
        }
        r.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        if (runCatching { wm.addView(r, lp) }.isSuccess) root = r
        refresh()
    }

    fun reveal() {
        val r = root ?: return
        content?.apply { translationY = 0f; alpha = 1f }
        r.visibility = View.VISIBLE
        refresh()
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null; content = null; dynamic = null; battery = null
        preview = false
    }

    fun invalidateBackground() { bgCache = null }

    // ---------------- building ----------------

    private fun background(): View {
        val bg = prefs.getString(Prefs.LOCK_BG, "g0")!!
        val uri = prefs.getString(Prefs.LOCK_BG_URI, null)
        if (bg == "image" && uri != null) {
            val bmp = bgCache?.takeIf { it.first == uri }?.second ?: decode(Uri.parse(uri))?.also { bgCache = uri to it }
            if (bmp != null) return ImageView(svc).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.CENTER_CROP }
        }
        return View(svc).apply {
            background = if (bg == "black") GradientDrawable().apply { setColor(Color.BLACK) }
            else GradientDrawable(GradientDrawable.Orientation.TL_BR,
                GRADIENTS[bg.removePrefix("g").toIntOrNull()?.coerceIn(0, GRADIENTS.size - 1) ?: 0])
        }
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        val m = wm.currentWindowMetrics.bounds
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(svc.contentResolver, uri)) { dec, info, _ ->
            val s = maxOf(m.width().toFloat() / info.size.width, m.height().toFloat() / info.size.height).coerceAtMost(1f)
            dec.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
        }
    }.getOrNull()

    private fun text(s: CharSequence, size: Float, bold: Boolean = false, alpha: Float = 1f) = TextView(svc).apply {
        text = s; textSize = size; setTextColor(Color.WHITE); this.alpha = alpha
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(6f, 0f, 1f, 0x66000000)
    }

    private fun glass() = GradientDrawable().apply { cornerRadius = 22 * d; setColor(0x40000000) }

    @SuppressLint("ClickableViewAccessibility")
    private fun build(): FrameLayout {
        val frame = FrameLayout(svc)
        frame.addView(background(), FrameLayout.LayoutParams(-1, -1))
        val dim = prefs.getInt(Prefs.LOCK_DIM, 20)
        frame.addView(View(svc).apply { setBackgroundColor(Color.argb((dim * 2.55f).toInt(), 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(28))
        }
        // top bar: battery / charging
        battery = text("", 13f, alpha = 0.9f).apply { gravity = Gravity.END }
        col.addView(battery, LinearLayout.LayoutParams(-1, -2))

        val center = prefs.getString(Prefs.LOCK_POS, "top") == "center"
        col.addView(View(svc), LinearLayout.LayoutParams(-1, 0, if (center) 1f else 0.18f))

        val clock = ClockView(svc).apply {
            style = ClockStyle.of(prefs.getString(Prefs.LOCK_STYLE, null))
            color = prefs.getInt(Prefs.LOCK_COLOR, Color.WHITE)
            scale = prefs.getInt(Prefs.LOCK_SIZE, 100) / 100f
        }
        col.addView(clock, LinearLayout.LayoutParams(-1, -2))

        val msg = prefs.getString(Prefs.LOCK_MSG, "")!!
        if (msg.isNotBlank()) col.addView(text(msg, 15f, alpha = 0.9f).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))

        dynamic = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(18), 0, 0) }
        col.addView(dynamic, LinearLayout.LayoutParams(-1, -2))

        col.addView(View(svc), LinearLayout.LayoutParams(-1, 0, 1f))

        // bottom: shortcuts + hint
        val bottom = LinearLayout(svc).apply { gravity = Gravity.CENTER_VERTICAL }
        bottom.addView(shortcut(Prefs.LOCK_SC_LEFT, "camera"))
        bottom.addView(text(svc.getString(R.string.lock_hint), 13f, alpha = 0.8f).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(shortcut(Prefs.LOCK_SC_RIGHT, "flash"))
        col.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        frame.addView(col, FrameLayout.LayoutParams(-1, -1))
        content = col

        // swipe up to unlock, double tap to sleep
        val threshold = 110 * d
        var downY = 0f
        val gd = GestureDetector(svc, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (prefs.getBoolean(Prefs.LOCK_DOUBLE_TAP, true) && !preview)
                    svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                return true
            }
        })
        frame.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> downY = e.rawY
                MotionEvent.ACTION_MOVE -> {
                    val dy = (e.rawY - downY).coerceAtMost(0f)
                    col.translationY = dy * 0.6f
                    col.alpha = 1f + dy / (threshold * 3)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (downY - e.rawY > threshold && e.actionMasked == MotionEvent.ACTION_UP) unlock()
                    else col.animate().translationY(0f).alpha(1f).setDuration(180).start()
                }
            }
            true
        }
        return frame
    }

    private fun shortcut(key: String, def: String): View {
        val v = prefs.getString(key, def)!!
        val iv = ImageView(svc).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x40000000) }
            setColorFilter(Color.WHITE)
        }
        val lp = LinearLayout.LayoutParams(dp(56), dp(56))
        iv.layoutParams = lp
        when {
            v == "camera" -> { iv.setImageResource(R.drawable.ic_camera); iv.setOnClickListener { openCamera() } }
            v == "flash" -> { iv.setImageResource(R.drawable.ic_flash); iv.setOnClickListener { Torch.toggle(svc) } }
            v.startsWith("app:") -> {
                val pkg = v.removePrefix("app:")
                runCatching { iv.setImageDrawable(svc.packageManager.getApplicationIcon(pkg)); iv.clearColorFilter(); iv.setPadding(dp(10), dp(10), dp(10), dp(10)) }
                iv.setOnClickListener { unlockThen { a -> a.packageManager.getLaunchIntentForPackage(pkg)?.let { a.startActivity(it) } } }
            }
            else -> iv.visibility = View.INVISIBLE
        }
        return iv
    }

    private fun openCamera() {
        if (preview) { hide(); return }
        val i = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { svc.startActivity(i) }.onFailure {
            unlockThen { a -> runCatching { a.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) } }
            return
        }
        hide()
    }

    private fun unlock() {
        val wasPreview = preview
        hide()
        if (!wasPreview) DismissActivity.start(svc)
    }

    private fun unlockThen(then: (android.app.Activity) -> Unit) {
        val wasPreview = preview
        hide()
        if (!wasPreview) DismissActivity.start(svc, then)
    }

    // ---------------- dynamic content ----------------

    fun refresh() {
        refreshBattery()
        val box = dynamic ?: return
        box.removeAllViews()
        if (prefs.getBoolean(Prefs.LOCK_MEDIA, true)) media(box)
        notifications(box)
    }

    fun refreshBattery() {
        val tv = battery ?: return
        if (!prefs.getBoolean(Prefs.LOCK_BATTERY, true)) { tv.text = ""; return }
        val i = svc.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        tv.text = if (plugged) svc.getString(R.string.lock_charging, level) else "%$level"
    }

    private fun media(box: LinearLayout) {
        val ctl: MediaController = runCatching {
            svc.getSystemService(MediaSessionManager::class.java).getActiveSessions(Access.listener(svc))
        }.getOrNull()?.firstOrNull { it.playbackState != null && it.metadata != null } ?: return
        val md = ctl.metadata ?: return
        val playing = ctl.playbackState?.state == PlaybackState.STATE_PLAYING
        val card = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = glass(); setPadding(dp(14), dp(12), dp(10), dp(12))
        }
        md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { art ->
            card.addView(ImageView(svc).apply {
                setImageBitmap(art); scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
                background = GradientDrawable().apply { cornerRadius = 10 * d; setColor(Color.DKGRAY) }
            }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(12) })
        }
        val t = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
        t.addView(text(md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "", 15f, bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        t.addView(text(md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "", 13f, alpha = 0.8f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        card.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        fun btn(icon: Int, act: () -> Unit) = ImageView(svc).apply {
            setImageResource(icon); setColorFilter(Color.WHITE); setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { act(); postDelayed({ refresh() }, 450) }
        }
        val tc = ctl.transportControls
        card.addView(btn(android.R.drawable.ic_media_previous) { tc.skipToPrevious() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        card.addView(btn(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play) {
            if (playing) tc.pause() else tc.play()
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        card.addView(btn(android.R.drawable.ic_media_next) { tc.skipToNext() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        box.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun visible(): List<StatusBarNotification> {
        val all = runCatching { NotiService.instance?.activeNotifications?.toList() }.getOrNull() ?: return emptyList()
        return all.filter {
            val n = it.notification
            !it.isOngoing && (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0 &&
                n.visibility != android.app.Notification.VISIBILITY_SECRET && it.packageName != svc.packageName
        }.sortedByDescending { it.postTime }
    }

    private fun notifications(box: LinearLayout) {
        val mode = prefs.getString(Prefs.LOCK_NOTIF, "cards")
        if (mode == "none") return
        val list = visible()
        if (list.isEmpty()) return
        val pm = svc.packageManager
        if (mode == "icons") {
            val row = LinearLayout(svc).apply { gravity = Gravity.CENTER }
            for (pkg in list.map { it.packageName }.distinct().take(8)) {
                row.addView(ImageView(svc).apply {
                    runCatching { setImageDrawable(pm.getApplicationIcon(pkg)) }
                }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(5); marginEnd = dp(5) })
            }
            box.addView(row, LinearLayout.LayoutParams(-1, -2))
            return
        }
        val showContent = prefs.getBoolean(Prefs.LOCK_CONTENT, false)
        for (sbn in list.take(4)) {
            val ex = sbn.notification.extras
            val card = LinearLayout(svc).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = glass(); setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            card.addView(ImageView(svc).apply { runCatching { setImageDrawable(pm.getApplicationIcon(sbn.packageName)) } },
                LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
            val t = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
            val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)) }.getOrDefault(sbn.packageName)
            val title = ex.getCharSequence(android.app.Notification.EXTRA_TITLE)
            t.addView(text(if (showContent && title != null) "$app · $title" else app, 14f, bold = true).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
            val body = if (showContent) ex.getCharSequence(android.app.Notification.EXTRA_TEXT) else title?.let { svc.getString(R.string.lock_new_notif) }
            if (!body.isNullOrBlank()) t.addView(text(body, 13f, alpha = 0.85f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            card.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            val pi = sbn.notification.contentIntent
            card.setOnClickListener { unlockThen { a -> if (pi != null) DismissActivity.send(a, pi) } }
            box.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        if (list.size > 4) box.addView(text("+${list.size - 4}", 13f, alpha = 0.8f).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}

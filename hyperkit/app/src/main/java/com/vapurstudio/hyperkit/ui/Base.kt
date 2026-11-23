package com.vapurstudio.hyperkit.ui

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R

/** Screens are built in code: a scrolling column of rounded cards, HyperOS settings style. */
abstract class BaseActivity : Activity() {
    protected lateinit var root: LinearLayout
    protected val prefs: SharedPreferences by lazy { Prefs.get(this) }
    protected open val showBack = true
    protected abstract val screenTitle: Int

    protected val ink get() = getColor(R.color.ink)
    protected val muted get() = getColor(R.color.muted)
    protected val accent get() = getColor(R.color.accent)

    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    fun dpf(v: Float) = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    fun rebuild() {
        val scrollY = (root.parent as? ScrollView)?.scrollY ?: 0
        root.removeAllViews()
        header()
        build()
        (root.parent as? ScrollView)?.post { (root.parent as ScrollView).scrollTo(0, scrollY) }
    }

    protected abstract fun build()

    protected open fun header() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(16))
        }
        if (showBack) {
            bar.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_back)
                imageTintList = ColorStateList.valueOf(ink)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = ripple(dp(24).toFloat(), 0)
                contentDescription = getString(android.R.string.cancel)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) })
        }
        bar.addView(text(getString(screenTitle), 26f, ink, bold = true))
        root.addView(bar)
    }

    // ---------- building blocks ----------

    fun text(s: CharSequence, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun ripple(radius: Float, fill: Int): RippleDrawable {
        val shape = GradientDrawable().apply { cornerRadius = radius; setColor(fill) }
        val mask = GradientDrawable().apply { cornerRadius = radius; setColor(0xFF000000.toInt()) }
        return RippleDrawable(ColorStateList.valueOf(getColor(R.color.accent_soft)), shape, mask)
    }

    fun sectionTitle(res: Int) {
        root.addView(text(getString(res), 13f, accent, bold = true).apply {
            setPadding(dp(8), dp(14), dp(8), dp(8))
        })
    }

    fun card(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dpf(20f); setColor(getColor(R.color.card)) }
            setPadding(0, dp(6), 0, dp(6))
            clipToOutline = true
        }
        root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return c
    }

    private fun rowBase(parent: ViewGroup, icon: Int?, title: CharSequence, sub: CharSequence?): Pair<LinearLayout, LinearLayout> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        if (icon != null) {
            val bg = GradientDrawable().apply { cornerRadius = dpf(12f); setColor(getColor(R.color.accent_soft)) }
            row.addView(ImageView(this).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(accent)
                background = bg
                setPadding(dp(9), dp(9), dp(9), dp(9))
            }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(14) })
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(title, 16f, ink, bold = icon != null))
        if (!sub.isNullOrEmpty()) col.addView(text(sub, 13f, muted).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
        return row to col
    }

    fun row(
        parent: ViewGroup, title: CharSequence, sub: CharSequence? = null, icon: Int? = null,
        trailing: CharSequence? = null, enabled: Boolean = true,
        onLong: (() -> Unit)? = null, onClick: (() -> Unit)? = null,
    ): View {
        val (row, _) = rowBase(parent, icon, title, sub)
        if (trailing != null) row.addView(text(trailing, 14f, accent).apply {
            maxWidth = dp(150); ellipsize = TextUtils.TruncateAt.END; maxLines = 2; gravity = Gravity.END
            setPadding(dp(12), 0, 0, 0)
        })
        if (onClick != null) {
            row.background = ripple(0f, 0)
            row.setOnClickListener { onClick() }
        }
        if (onLong != null) row.setOnLongClickListener { onLong(); true }
        row.alpha = if (enabled) 1f else 0.45f
        return row
    }

    fun switchRow(parent: ViewGroup, title: CharSequence, sub: CharSequence? = null, checked: Boolean,
                  enabled: Boolean = true, onChange: (Boolean) -> Unit): Switch {
        val (row, _) = rowBase(parent, null, title, sub)
        val sw = Switch(this).apply {
            isChecked = checked
            isEnabled = enabled
            thumbTintList = null
            setOnCheckedChangeListener { _, b -> onChange(b) }
        }
        row.addView(sw)
        row.setOnClickListener { if (sw.isEnabled) sw.toggle() }
        row.background = ripple(0f, 0)
        row.alpha = if (enabled) 1f else 0.45f
        return sw
    }

    /** Slider over integer steps; [label] formats the step for display, [onChange] gets the step. */
    fun slider(parent: ViewGroup, title: CharSequence, min: Int, max: Int, value: Int, enabled: Boolean = true,
               label: (Int) -> String = { it.toString() }, onStop: Boolean = false, onChange: (Int) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(6))
            alpha = if (enabled) 1f else 0.45f
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(text(title, 16f), LinearLayout.LayoutParams(0, -2, 1f))
        val v = text(label(value), 14f, accent, bold = true)
        head.addView(v)
        box.addView(head)
        box.addView(SeekBar(this).apply {
            this.min = min; this.max = max; progress = value.coerceIn(min, max)
            isEnabled = enabled
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) {
                    v.text = label(p)
                    if (user && !onStop) onChange(p)
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) { if (onStop) onChange(s.progress) }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        parent.addView(box)
    }

    fun note(parent: ViewGroup, s: CharSequence, color: Int = muted) {
        parent.addView(text(s, 13f, color).apply { setPadding(dp(16), dp(8), dp(16), dp(10)) })
    }

    fun button(parent: ViewGroup, label: CharSequence, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(getColor(R.color.card))
            textSize = 15f
            background = ripple(dpf(24f), accent)
            stateListAnimator = null
            setPadding(dp(20), 0, dp(20), 0)
            setOnClickListener { onClick() }
        }
        val wrap = LinearLayout(this).apply { setPadding(dp(16), dp(4), dp(16), dp(10)) }
        wrap.addView(b, LinearLayout.LayoutParams(-2, dp(44)))
        parent.addView(wrap)
        return b
    }

    fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    fun tryStart(vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
            } catch (_: Exception) {
            }
        }
        toast(R.string.hidden_failed)
        return false
    }

    fun openAccessibility() = tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

package app.stash.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.WindowCompat

fun Context.dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

/**
 * Native screens are built in code (no XML layouts to keep in sync). The colours, corner radii and spacing here
 * mirror the website (index.html): soft grey page, white rounded cards with a hairline border, indigo accent.
 * Light/dark follows the phone's setting.
 */
object Ui {
    class Palette(
        val bg: Int, val panel: Int, val ink: Int, val muted: Int, val line: Int,
        val accent: Int, val accentInk: Int, val accentSoft: Int, val danger: Int, val dark: Boolean,
    )

    // same values as the :root / [data-theme=dark] variables in index.html
    private val LIGHT = Palette(
        0xFFF5F6F9.toInt(), 0xFFFFFFFF.toInt(), 0xFF14171F.toInt(), 0xFF6A7384.toInt(), 0xFFE4E7EE.toInt(),
        0xFF5B5BF0.toInt(), 0xFFFFFFFF.toInt(), 0xFFECECFE.toInt(), 0xFFE5484D.toInt(), false,
    )
    private val DARK = Palette(
        0xFF0D0F14.toInt(), 0xFF161920.toInt(), 0xFFE8EBF2.toInt(), 0xFF8A92A5.toInt(), 0xFF262A35.toInt(),
        0xFF8080FF.toInt(), 0xFF0D0F14.toInt(), 0xFF23254D.toInt(), 0xFFFF6B70.toInt(), true,
    )

    fun pal(c: Context): Palette =
        if ((c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT

    /** Page background; also used by the "can't reach the server" overlay. */
    fun themeBackground(c: Context): Int = pal(c).bg

    // ---- shapes ------------------------------------------------------------------------------------------

    private fun shape(c: Context, fill: Int, stroke: Int?, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = c.dp(radiusDp).toFloat()
        if (stroke != null) setStroke(c.dp(1), stroke)
    }

    /** A rounded drawable with a ripple that is clipped to the same corners. */
    private fun pressable(c: Context, base: Drawable, radiusDp: Int, ripple: Int): Drawable {
        val mask = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = c.dp(radiusDp).toFloat() }
        return RippleDrawable(ColorStateList.valueOf(ripple), base, mask)
    }

    private fun lp(c: Context, topDp: Int = 0, bottomDp: Int = 0) =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = c.dp(topDp); bottomMargin = c.dp(bottomDp) }

    // ---- text --------------------------------------------------------------------------------------------

    fun title(c: Context, text: String) = TextView(c).apply {
        this.text = text
        textSize = 22f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(pal(c).ink)
    }

    fun body(c: Context, text: String = "") = TextView(c).apply {
        this.text = text
        textSize = 15f
        setTextColor(pal(c).ink)
        setPadding(0, c.dp(4), 0, c.dp(4))
    }

    /** Smaller grey text: hints, status lines, footnotes. */
    fun muted(c: Context, text: String = "") = TextView(c).apply {
        this.text = text
        textSize = 13.5f
        setTextColor(pal(c).muted)
        setPadding(0, c.dp(4), 0, c.dp(4))
    }

    fun gap(c: Context, dpHeight: Int) = View(c).apply {
        layoutParams = LinearLayout.LayoutParams(1, c.dp(dpHeight))
    }

    // ---- containers --------------------------------------------------------------------------------------

    /** White rounded box with a hairline border, like the website's cards. */
    fun card(c: Context, vararg views: View) = LinearLayout(c).apply {
        val p = pal(c)
        orientation = LinearLayout.VERTICAL
        background = shape(c, p.panel, p.line, 14)
        setPadding(c.dp(16), c.dp(12), c.dp(16), c.dp(12))
        layoutParams = lp(c, bottomDp = 12)
        views.forEach { addView(it) }
    }

    /** Logo tile + title, the same brand block as the website header. */
    fun header(c: Context, heading: String, subtitle: String? = null) = LinearLayout(c).apply {
        val p = pal(c)
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = lp(c, bottomDp = 16)
        addView(ImageView(c).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            background = shape(c, p.accent, null, 12)
            layoutParams = LinearLayout.LayoutParams(c.dp(44), c.dp(44)).apply { marginEnd = c.dp(12) }
        })
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(title(c, heading))
            if (subtitle != null) addView(muted(c, subtitle).apply { setPadding(0, 0, 0, 0) })
        })
    }

    /** Scrolling page with the soft grey background; also colours the status and navigation bars to match. */
    fun page(a: Activity, vararg views: View): ScrollView {
        val p = pal(a)
        a.window.statusBarColor = p.bg
        a.window.navigationBarColor = p.bg
        WindowCompat.getInsetsController(a.window, a.window.decorView).apply {
            isAppearanceLightStatusBars = !p.dark
            isAppearanceLightNavigationBars = !p.dark
        }
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(20), a.dp(28), a.dp(20), a.dp(28))
            views.forEach { addView(it) }
        }
        return ScrollView(a).apply {
            setBackgroundColor(p.bg)
            isFillViewport = true
            addView(root)
        }
    }

    // ---- controls ----------------------------------------------------------------------------------------

    enum class Kind { PRIMARY, SECONDARY, DANGER, GHOST }

    /** A Button that greys out when disabled (custom backgrounds don't do that by themselves). */
    private class SoftButton(c: Context) : Button(c) {
        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            alpha = if (enabled) 1f else 0.5f
        }
    }

    fun button(c: Context, text: String, kind: Kind = Kind.SECONDARY, onClick: () -> Unit): Button = SoftButton(c).apply {
        val p = pal(c)
        this.text = text
        isAllCaps = false
        textSize = 15f
        stateListAnimator = null
        elevation = 0f
        minHeight = 0
        minimumHeight = 0
        setPadding(c.dp(16), c.dp(11), c.dp(16), c.dp(11))
        val (fill, stroke, ink) = when (kind) {
            Kind.PRIMARY -> Triple(p.accent, null, p.accentInk)
            Kind.SECONDARY -> Triple(p.panel, p.line, p.ink)
            Kind.DANGER -> Triple(p.danger, null, Color.WHITE)
            Kind.GHOST -> Triple(Color.TRANSPARENT, null, p.ink)
        }
        if (kind == Kind.PRIMARY || kind == Kind.DANGER) setTypeface(typeface, Typeface.BOLD)
        setTextColor(ink)
        background = pressable(c, shape(c, fill, stroke, 10), 10, (p.ink and 0x00FFFFFF) or 0x22000000)
        layoutParams = lp(c, topDp = 8)
        setOnClickListener { onClick() }
    }

    /** Rounded text box that gets the accent border when focused (website `.field`). */
    fun field(c: Context, hint: String) = EditText(c).apply {
        val p = pal(c)
        this.hint = hint
        textSize = 15f
        setTextColor(p.ink)
        setHintTextColor(p.muted)
        setPadding(c.dp(12), c.dp(11), c.dp(12), c.dp(11))
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(c, p.bg, p.accent, 10))
            addState(intArrayOf(), shape(c, p.bg, p.line, 10))
        }
        layoutParams = lp(c, topDp = 8, bottomDp = 4)
    }

    private fun checkedTint(on: Int, off: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(on, off),
    )

    fun toggle(c: Context, text: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(c).apply {
        val p = pal(c)
        this.text = text
        textSize = 15f
        setTextColor(p.ink)
        isChecked = checked
        setPadding(0, c.dp(10), 0, c.dp(10))
        thumbTintList = checkedTint(p.accent, if (p.dark) 0xFFB5BBCB.toInt() else Color.WHITE)
        trackTintList = checkedTint((p.accent and 0x00FFFFFF) or 0x66000000, p.line)
        layoutParams = lp(c)
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    fun check(c: Context, text: String, checked: Boolean, onChange: (Boolean) -> Unit) = CheckBox(c).apply {
        val p = pal(c)
        this.text = text
        textSize = 15f
        setTextColor(p.ink)
        buttonTintList = checkedTint(p.accent, p.muted)
        isChecked = checked
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    /** Call right after `.show()`: rounded panel background and accent-coloured buttons for an AlertDialog. */
    fun styleDialog(d: AlertDialog) {
        val p = pal(d.context)
        d.window?.setBackgroundDrawable(shape(d.context, p.panel, p.line, 18))
        for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            d.getButton(which)?.apply { isAllCaps = false; setTextColor(p.accent) }
        }
    }
}

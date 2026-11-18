package app.stash.android

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

fun Context.dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

/** Tiny helpers so the native screens can be built in code (no XML layouts to keep in sync). */
object Ui {
    fun title(c: Context, text: String) = TextView(c).apply {
        this.text = text
        textSize = 24f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, c.dp(8))
    }

    fun body(c: Context, text: String = "") = TextView(c).apply {
        this.text = text
        textSize = 15f
        setPadding(0, c.dp(4), 0, c.dp(4))
    }

    fun button(c: Context, text: String, onClick: () -> Unit) = Button(c).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    fun toggle(c: Context, text: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(c).apply {
        this.text = text
        textSize = 15f
        isChecked = checked
        setPadding(0, c.dp(10), 0, c.dp(10))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    fun check(c: Context, text: String, checked: Boolean, onChange: (Boolean) -> Unit) = CheckBox(c).apply {
        this.text = text
        textSize = 15f
        isChecked = checked
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    fun gap(c: Context, dpHeight: Int) = View(c).apply {
        layoutParams = LinearLayout.LayoutParams(1, c.dp(dpHeight))
    }

    fun themeBackground(c: Context): Int {
        val tv = TypedValue()
        c.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
        return tv.data
    }
}

package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Look of the glasses HUD: black background (transparent on the waveguide), white text and a
 * green outline on the selected element. Selection uses `isSelected` instead of Android focus so
 * the one-axis navigation is fully under our control (and renders the same in snapshots).
 */
object Hud {
    /** The app draws inside a 480dp x 282dp band glued to the bottom of the display. */
    const val BAND_WIDTH_DP = 480
    const val BAND_HEIGHT_DP = 282

    val GREEN = Color.parseColor("#39FF14")
    val DIM_GREEN = Color.parseColor("#1F6B1F")
    val WHITE = Color.WHITE
    val GREY = Color.parseColor("#B8B8B8")
    val SCRIM = Color.parseColor("#B3000000")

    fun Context.dp(value: Number): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).roundToInt()

    fun View.dp(value: Number): Int = context.dp(value)

    /** Outline that is dim normally and bright + thick when selected. */
    fun outline(context: Context, fill: Int = Color.TRANSPARENT, radiusDp: Int = 3): Drawable {
        fun shape(color: Int, widthDp: Int) = GradientDrawable().apply {
            setColor(fill)
            setStroke(context.dp(widthDp), color)
            cornerRadius = context.dp(radiusDp).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), shape(GREEN, 3))
            // "activated" = the current category / mode, shown even when not highlighted.
            addState(intArrayOf(android.R.attr.state_activated), shape(GREEN, 1))
            addState(intArrayOf(), shape(DIM_GREEN, 1))
        }
    }

    val buttonText = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(android.R.attr.state_activated),
            intArrayOf()
        ),
        intArrayOf(GREEN, GREEN, WHITE)
    )

    fun text(context: Context, sp: Float, color: Int = WHITE, bold: Boolean = false) =
        TextView(context).apply {
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }

    fun button(context: Context, label: String, sp: Float = 13f) = text(context, sp, bold = true).apply {
        text = label
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(buttonText)
        background = outline(context)
        setPadding(context.dp(10), 0, context.dp(10), 0)
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun formatViews(count: Long): String = when {
        count < 0 -> ""
        count >= 100_000_000 -> "조회수 %.1f억회".format(count / 100_000_000.0)
        count >= 10_000 -> "조회수 %.1f만회".format(count / 10_000.0)
        else -> "조회수 ${count}회"
    }.replace(".0", "")
}

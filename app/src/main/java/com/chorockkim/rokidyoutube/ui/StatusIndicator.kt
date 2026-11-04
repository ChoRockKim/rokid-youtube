package com.chorockkim.rokidyoutube.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.chorockkim.rokidyoutube.ui.Hud.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small clock + battery at the right end, just above the band (the empty, transparent area),
 * so it never covers the app: "14:32  [▮▮▯] 82%".
 */
class StatusIndicator(context: Context) : LinearLayout(context) {
    private val clock = Hud.text(context, 12f, Hud.WHITE, bold = true)
    private val battery = BatteryIcon(context)
    private val percent = Hud.text(context, 12f, Hud.GREY)
    private val format = SimpleDateFormat("HH:mm", Locale.KOREA)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) renderBattery(intent) else renderClock()
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(clock)
        addView(battery, LayoutParams(dp(20), dp(10)).apply { marginStart = dp(10) })
        addView(percent, LayoutParams(-2, -2).apply { marginStart = dp(4) })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderClock()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        // Battery is a sticky broadcast: the current state comes back right away.
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.getOrNull()?.let(::renderBattery)
    }

    override fun onDetachedFromWindow() {
        runCatching { context.unregisterReceiver(receiver) }
        super.onDetachedFromWindow()
    }

    private var fixedTime: String? = null

    private fun renderClock() {
        clock.text = fixedTime ?: format.format(Date())
    }

    private fun renderBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (level >= 0) show(level * 100 / scale, charging)
    }

    /** Also used by snapshots. */
    fun show(batteryPercent: Int, charging: Boolean, time: String? = null) {
        if (time != null) {
            fixedTime = time
            clock.text = time
        }
        percent.text = "$batteryPercent%"
        battery.level = batteryPercent
        battery.charging = charging
        percent.setTextColor(if (batteryPercent <= LOW && !charging) Hud.RED else Hud.GREY)
        battery.invalidate()
    }

    private class BatteryIcon(context: Context) : View(context) {
        var level = 100
        var charging = false
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(1).toFloat()
            color = Hud.GREY
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val s = stroke.strokeWidth
            val tip = width * 0.1f
            rect.set(s / 2, s / 2, width - tip - s / 2, height - s / 2)
            canvas.drawRoundRect(rect, s * 2, s * 2, stroke)
            fill.color = Hud.GREY
            canvas.drawRect(width - tip, height * 0.3f, width.toFloat(), height * 0.7f, fill)
            fill.color = when {
                charging -> Hud.GREEN
                level <= LOW -> Hud.RED
                else -> Color.WHITE
            }
            val inner = 2 * s
            val w = (rect.width() - 2 * inner) * level.coerceIn(0, 100) / 100f
            canvas.drawRect(rect.left + inner, rect.top + inner, rect.left + inner + w, rect.bottom - inner, fill)
        }
    }

    companion object {
        const val LOW = 15

        /** Right end of the row just above the band, same row as the offline notice. */
        fun params(context: Context) = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            bottomMargin = context.dp(Hud.BAND_HEIGHT_DP + 8)
            marginEnd = context.dp(12)
        }
    }
}

package com.chorockkim.rokidyoutube.ui

import android.os.SystemClock
import android.view.KeyEvent

/**
 * Rokid glasses touchpad -> app actions. Same mapping as RokidPipe's RokidKeyMapper, which was
 * validated on the glasses: swipe = DPAD left/right (plus Rokid keys 183/184), tap = ENTER,
 * the Rokid double-tap key (202) = select, system back = back.
 */
class GlassesInput(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    enum class Action { NONE, DUPLICATE, PREVIOUS, NEXT, SELECT, BACK }

    private var lastDirectionAt = 0L

    fun map(event: KeyEvent): Action = map(event.action, event.keyCode, event.repeatCount)

    fun map(eventAction: Int, keyCode: Int, repeatCount: Int): Action {
        if (eventAction != KeyEvent.ACTION_DOWN) return Action.NONE
        if (repeatCount > 0) return Action.DUPLICATE
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KEYCODE_ROKID_PREVIOUS ->
                debounce(Action.PREVIOUS)

            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KEYCODE_ROKID_NEXT ->
                debounce(Action.NEXT)

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE, KEYCODE_ROKID_DOUBLE_TAP -> Action.SELECT

            KeyEvent.KEYCODE_BACK -> Action.BACK
            else -> Action.NONE
        }
    }

    /** One physical swipe can arrive as two directional events; drop the second one. */
    private fun debounce(action: Action): Action {
        val now = clock()
        if (lastDirectionAt > 0L && now - lastDirectionAt < DIRECTION_DEBOUNCE_MS) {
            return Action.DUPLICATE
        }
        lastDirectionAt = now
        return action
    }

    companion object {
        const val KEYCODE_ROKID_NEXT = 183
        const val KEYCODE_ROKID_PREVIOUS = 184
        const val KEYCODE_ROKID_DOUBLE_TAP = 202
        const val DIRECTION_DEBOUNCE_MS = 240L
    }
}

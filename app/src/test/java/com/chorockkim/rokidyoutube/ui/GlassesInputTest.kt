package com.chorockkim.rokidyoutube.ui

import android.view.KeyEvent
import com.chorockkim.rokidyoutube.ui.GlassesInput.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassesInputTest {
    private var now = 1_000L
    private val input = GlassesInput { now }

    private fun down(keyCode: Int) = input.map(KeyEvent.ACTION_DOWN, keyCode, 0)

    @Test
    fun swipeTapAndBackMapLikeRokidPipe() {
        assertEquals(Action.NEXT, down(KeyEvent.KEYCODE_DPAD_RIGHT))
        now += 500
        assertEquals(Action.PREVIOUS, down(GlassesInput.KEYCODE_ROKID_PREVIOUS))
        assertEquals(Action.SELECT, down(KeyEvent.KEYCODE_ENTER))
        assertEquals(Action.SELECT, down(GlassesInput.KEYCODE_ROKID_DOUBLE_TAP))
        assertEquals(Action.BACK, down(KeyEvent.KEYCODE_BACK))
        assertEquals(Action.NONE, down(KeyEvent.KEYCODE_A))
    }

    @Test
    fun secondDirectionalEventOfOneSwipeIsDropped() {
        assertEquals(Action.NEXT, down(KeyEvent.KEYCODE_DPAD_RIGHT))
        now += 100
        assertEquals(Action.DUPLICATE, down(GlassesInput.KEYCODE_ROKID_NEXT))
        now += GlassesInput.DIRECTION_DEBOUNCE_MS
        assertEquals(Action.NEXT, down(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun r08RingMediaKeysMapToSwipeAndTap() {
        assertEquals(Action.NEXT, down(KeyEvent.KEYCODE_MEDIA_NEXT))
        now += 500
        assertEquals(Action.PREVIOUS, down(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(Action.SELECT, down(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    @Test
    fun verticalSwipesAreTheirOwnActions() {
        assertEquals(Action.UP, down(KeyEvent.KEYCODE_DPAD_UP))
        now += 500
        assertEquals(Action.DOWN, down(KeyEvent.KEYCODE_DPAD_DOWN))
        now += 100
        assertEquals(Action.DUPLICATE, down(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun keyUpAndRepeatsAreIgnored() {
        assertEquals(Action.NONE, input.map(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
        assertEquals(Action.DUPLICATE, input.map(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 1))
    }
}

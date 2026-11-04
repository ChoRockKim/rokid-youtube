package org.schabi.newpipe.rokid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Before;
import org.junit.Test;

public class RokidKeyMapperTest {
    @Before
    public void setUp() {
        RokidKeyMapper.resetDebounceForTesting();
    }

    @Test
    public void rightThenDownWithinDebounceMovesOnce() {
        assertEquals(RokidKeyMapper.Action.NEXT,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_RIGHT, 1_000L));
        assertEquals(RokidKeyMapper.Action.DUPLICATE,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_DOWN, 1_120L));
        assertEquals(RokidKeyMapper.Action.NEXT,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_RIGHT, 1_241L));
    }

    @Test
    public void leftThenUpWithinDebounceMovesOnce() {
        assertEquals(RokidKeyMapper.Action.PREVIOUS,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_LEFT, 1_000L));
        assertEquals(RokidKeyMapper.Action.DUPLICATE,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_UP, 1_120L));
        assertEquals(RokidKeyMapper.Action.PREVIOUS,
                RokidKeyMapper.map(184, 1_241L));
    }

    @Test
    public void duplicateDoesNotExtendDebounceWindow() {
        assertEquals(RokidKeyMapper.Action.NEXT,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_RIGHT, 1_000L));
        assertEquals(RokidKeyMapper.Action.DUPLICATE,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_DOWN, 1_120L));
        assertEquals(RokidKeyMapper.Action.NEXT,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_RIGHT, 1_241L));
    }

    @Test
    public void tapAndBackAreMappedDirectly() {
        assertEquals(RokidKeyMapper.Action.SELECT,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_CENTER, 1_000L));
        assertEquals(RokidKeyMapper.Action.SELECT,
                RokidKeyMapper.map(KeyEvent.KEYCODE_ENTER, 1_000L));
        assertEquals(RokidKeyMapper.Action.SELECT,
                RokidKeyMapper.map(202, 1_000L));
        assertEquals(RokidKeyMapper.Action.BACK,
                RokidKeyMapper.map(KeyEvent.KEYCODE_BACK, 1_000L));
    }

    @Test
    public void customDoubleTapKeyIsRecognized() {
        assertTrue(RokidKeyMapper.isDoubleTapKey(RokidKeyMapper.KEYCODE_ROKID_DOUBLE_TAP));
        assertFalse(RokidKeyMapper.isDoubleTapKey(KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse(RokidKeyMapper.isDoubleTapKey(KeyEvent.KEYCODE_BACK));
    }

    @Test
    public void actionUpEventsAreIgnored() {
        assertEquals(RokidKeyMapper.Action.NONE,
                RokidKeyMapper.mapEventFields(KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        assertEquals(RokidKeyMapper.Action.NONE,
                RokidKeyMapper.mapEventFields(KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_DPAD_CENTER, 0));
    }

    @Test
    public void heldDirectionalKeyIsDuplicate() {
        assertEquals(RokidKeyMapper.Action.DUPLICATE,
                RokidKeyMapper.mapEventFields(KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_RIGHT, 1));
    }

    @Test
    public void customRokidSwipeKeysUseSameDebounceChannel() {
        assertEquals(RokidKeyMapper.Action.NEXT,
                RokidKeyMapper.map(183, 1_000L));
        assertEquals(RokidKeyMapper.Action.DUPLICATE,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_DOWN, 1_120L));

        RokidKeyMapper.resetDebounceForTesting();
        assertEquals(RokidKeyMapper.Action.PREVIOUS,
                RokidKeyMapper.map(184, 1_000L));
        assertEquals(RokidKeyMapper.Action.DUPLICATE,
                RokidKeyMapper.map(KeyEvent.KEYCODE_DPAD_UP, 1_120L));
    }
}

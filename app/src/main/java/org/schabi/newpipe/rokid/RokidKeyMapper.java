package org.schabi.newpipe.rokid;

import android.os.SystemClock;
import android.view.KeyEvent;

public final class RokidKeyMapper {
    public static final int KEYCODE_ROKID_DOUBLE_TAP = 202;
    private static final long DIRECTION_DEBOUNCE_MS = 240L;
    private static long lastDirectionAt;

    private RokidKeyMapper() {
    }

    public enum Action {
        NONE,
        DUPLICATE,
        PREVIOUS,
        NEXT,
        SELECT,
        BACK
    }

    public static Action map(final KeyEvent event) {
        if (event == null) {
            return Action.NONE;
        }

        return mapEventFields(event.getAction(), event.getKeyCode(), event.getRepeatCount());
    }

    static Action mapEventFields(
            final int eventAction,
            final int keyCode,
            final int repeatCount
    ) {
        if (eventAction != KeyEvent.ACTION_DOWN) {
            return Action.NONE;
        }
        if (repeatCount > 0) {
            return Action.DUPLICATE;
        }

        return map(keyCode);
    }

    public static Action map(final int keyCode) {
        return map(keyCode, SystemClock.elapsedRealtime());
    }

    static Action map(final int keyCode, final long now) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case 184:
                return debounce(Action.PREVIOUS, now);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case 183:
                return debounce(Action.NEXT, now);
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KEYCODE_ROKID_DOUBLE_TAP:
                return Action.SELECT;
            case KeyEvent.KEYCODE_BACK:
                return Action.BACK;
            default:
                return Action.NONE;
        }
    }

    public static boolean isDirectionalKey(final int keyCode) {
        return isPreviousKey(keyCode) || isNextKey(keyCode);
    }

    public static boolean isPreviousKey(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case 184:
                return true;
            default:
                return false;
        }
    }

    public static boolean isNextKey(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case 183:
                return true;
            default:
                return false;
        }
    }

    public static boolean isDoubleTapKey(final int keyCode) {
        return keyCode == KEYCODE_ROKID_DOUBLE_TAP;
    }

    static void resetDebounceForTesting() {
        lastDirectionAt = 0L;
    }

    private static Action debounce(final Action action, final long now) {
        if (lastDirectionAt > 0L && now - lastDirectionAt < DIRECTION_DEBOUNCE_MS) {
            return Action.DUPLICATE;
        }
        lastDirectionAt = now;
        return action;
    }
}

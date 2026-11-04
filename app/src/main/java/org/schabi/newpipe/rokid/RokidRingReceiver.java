package org.schabi.newpipe.rokid;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.view.KeyEvent;

import androidx.core.content.ContextCompat;

// Bridges the Rokid Ring (driven by the R08 Access Bridge accessibility service) into NewPipe's
// own touchpad navigation. The Ring cannot inject key events, so the bridge forwards a navigation
// command as a targeted broadcast; here it is replayed as the matching swipe/select key event so
// the Ring and the touchpad share a single navigation path (focus, list scroll, player rail).
public final class RokidRingReceiver extends BroadcastReceiver {
    // Cross-app contract with com.anezium.r08accessbridge (kept in sync by hand across repos).
    private static final String ACTION = "com.anezium.rokid.newpipe.action.RING_NAV";
    private static final String SENDER_PERMISSION =
            "com.anezium.r08accessbridge.permission.INTERNAL_COMMAND";
    private static final String EXTRA_NAV = "nav";
    private static final int NAV_NEXT = 1;
    private static final int NAV_PREV = 2;
    private static final int NAV_SELECT = 3;
    private static final int NAV_BACK = 4;

    private final Activity activity;

    private RokidRingReceiver(final Activity activity) {
        this.activity = activity;
    }

    /**
     * Registers a Ring bridge receiver for the given activity, restricted to broadcasts from the
     * R08 Access Bridge (signature permission). No-op when not running on Rokid glasses.
     *
     * @param activity the foreground activity that will replay the navigation key events
     * @return the registered receiver, or {@code null} when Rokid mode is disabled
     */
    public static RokidRingReceiver register(final Activity activity) {
        if (!RokidMode.enabled()) {
            return null;
        }
        final RokidRingReceiver receiver = new RokidRingReceiver(activity);
        ContextCompat.registerReceiver(activity, receiver, new IntentFilter(ACTION),
                SENDER_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED);
        return receiver;
    }

    /**
     * Unregisters a receiver previously returned by {@link #register(Activity)}.
     *
     * @param activity the activity the receiver was registered on
     * @param receiver the receiver to remove, may be {@code null}
     */
    public static void unregister(final Activity activity, final RokidRingReceiver receiver) {
        if (receiver == null) {
            return;
        }
        try {
            activity.unregisterReceiver(receiver);
        } catch (final IllegalArgumentException ignored) {
            // Receiver was not registered.
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) {
            return;
        }
        final int keyCode;
        switch (intent.getIntExtra(EXTRA_NAV, 0)) {
            case NAV_NEXT:
                keyCode = 183;
                break;
            case NAV_PREV:
                keyCode = 184;
                break;
            case NAV_SELECT:
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER;
                break;
            case NAV_BACK:
                keyCode = KeyEvent.KEYCODE_BACK;
                break;
            default:
                return;
        }
        final long now = SystemClock.uptimeMillis();
        activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }
}

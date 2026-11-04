package org.schabi.newpipe.rokid;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.snackbar.Snackbar;

public final class RokidSnackbarHelper {
    private RokidSnackbarHelper() {
    }

    @NonNull
    public static Snackbar prepare(@NonNull final Snackbar snackbar) {
        if (!RokidMode.enabled()) {
            return snackbar;
        }

        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onShown(final Snackbar transientBottomBar) {
                focusAction(transientBottomBar);
            }
        });
        return snackbar;
    }

    public static void show(@NonNull final Snackbar snackbar) {
        prepare(snackbar).show();
    }

    private static void focusAction(@NonNull final Snackbar snackbar) {
        final View action = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_action);
        if (action == null || action.getVisibility() != View.VISIBLE || !action.isEnabled()) {
            return;
        }

        action.setFocusable(true);
        action.setFocusableInTouchMode(true);
        action.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        if ((action.getContentDescription() == null
                || action.getContentDescription().length() == 0)
                && action instanceof TextView) {
            action.setContentDescription(((TextView) action).getText());
        }

        action.post(() -> {
            if (action.isShown() && action.isEnabled()) {
                action.requestFocusFromTouch();
                action.requestFocus();
            }
        });
    }
}

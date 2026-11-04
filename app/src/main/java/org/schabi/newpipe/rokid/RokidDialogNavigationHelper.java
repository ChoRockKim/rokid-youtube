package org.schabi.newpipe.rokid;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

public final class RokidDialogNavigationHelper {
    private RokidDialogNavigationHelper() {
    }

    public static void attach(
            @NonNull final Activity activity,
            @NonNull final Dialog dialog
    ) {
        if (!RokidMode.enabled()) {
            return;
        }

        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (dialog instanceof AlertDialog
                    && handleAlertDialogButtons((AlertDialog) dialog, event)) {
                return true;
            }
            if (RokidKeyboardController.forActivity(activity).handleKeyEvent(event)) {
                return true;
            }
            if (event.getRepeatCount() > 0
                    && RokidKeyMapper.isDirectionalKey(event.getKeyCode())) {
                return true;
            }

            final Window window = dialog.getWindow();
            if (window == null) {
                return false;
            }
            final View root = window.getDecorView();
            return root != null && RokidFocusNavigator.handle(activity, root, event);
        });
    }

    public static void attach(
            @NonNull final Context context,
            @NonNull final Dialog dialog
    ) {
        final Activity activity = findActivity(context);
        if (activity != null) {
            attach(activity, dialog);
        }
    }

    @NonNull
    public static AlertDialog show(
            @NonNull final Context context,
            @NonNull final AlertDialog.Builder builder
    ) {
        final AlertDialog dialog = builder.create();
        attach(context, dialog);
        dialog.show();
        applyRokidDialogStyle(dialog);
        focusInitialAlertButton(dialog);
        return dialog;
    }

    private static void applyRokidDialogStyle(@NonNull final Dialog dialog) {
        if (!RokidMode.enabled()) {
            return;
        }

        final Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            final View decor = window.getDecorView();
            if (decor != null) {
                decor.setBackgroundColor(Color.BLACK);
                styleDialogTree(decor);
            }
        }
    }

    private static void styleDialogTree(@NonNull final View view) {
        if (view instanceof Button) {
            styleButton((Button) view);
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(Color.WHITE);
        } else if (view instanceof ViewGroup) {
            view.setBackgroundColor(Color.BLACK);
        }

        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                styleDialogTree(group.getChildAt(index));
            }
        }
    }

    private static boolean handleAlertDialogButtons(
            @NonNull final AlertDialog dialog,
            @NonNull final KeyEvent event
    ) {
        final RokidKeyMapper.Action action = RokidKeyMapper.map(event);
        if (action == RokidKeyMapper.Action.NONE) {
            return false;
        }
        if (action == RokidKeyMapper.Action.DUPLICATE) {
            return true;
        }

        final Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        switch (action) {
            case NEXT:
                return focusButton(positiveButton, negativeButton);
            case PREVIOUS:
                return focusButton(negativeButton, positiveButton);
            case SELECT:
                if (positiveButton != null && positiveButton.isFocused()) {
                    positiveButton.performClick();
                    return true;
                }
                if (negativeButton != null && negativeButton.isFocused()) {
                    negativeButton.performClick();
                    return true;
                }
                return focusButton(negativeButton, positiveButton);
            case BACK:
                return false;
            default:
                return false;
        }
    }

    private static boolean focusButton(
            final Button preferredButton,
            final Button fallbackButton
    ) {
        final Button target = preferredButton != null ? preferredButton : fallbackButton;
        if (target == null) {
            return false;
        }
        target.requestFocusFromTouch();
        target.requestFocus();
        return true;
    }

    private static void styleButton(@NonNull final Button button) {
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setTextColor(Color.WHITE);
        button.setBackground(buttonBackground(button.isFocused()));
        button.setOnFocusChangeListener((view, hasFocus) ->
                view.setBackground(buttonBackground(hasFocus)));
    }

    private static void focusInitialAlertButton(@NonNull final AlertDialog dialog) {
        if (!RokidMode.enabled()) {
            return;
        }

        final Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (negativeButton != null) {
            negativeButton.requestFocusFromTouch();
            negativeButton.requestFocus();
        } else if (positiveButton != null) {
            positiveButton.requestFocusFromTouch();
            positiveButton.requestFocus();
        }
    }

    private static GradientDrawable buttonBackground(final boolean focused) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.BLACK);
        drawable.setStroke(focused ? 2 : 1, focused ? Color.WHITE : Color.rgb(120, 120, 120));
        drawable.setCornerRadius(3);
        return drawable;
    }

    private static Activity findActivity(@NonNull final Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }
}

package org.schabi.newpipe.rokid;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

public final class RokidTextInputHelper {
    private RokidTextInputHelper() {
    }

    public static void prepare(@NonNull final EditText editText) {
        if (!RokidMode.enabled()) {
            return;
        }

        editText.setShowSoftInputOnFocus(false);
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public static void attach(
            @NonNull final Activity activity,
            @NonNull final AlertDialog dialog,
            @NonNull final EditText editText
    ) {
        attach(activity, dialog, editText, () -> {
            final Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (positiveButton != null && positiveButton.isEnabled()) {
                positiveButton.performClick();
            } else {
                RokidKeyboardController.forActivity(activity).hide(editText);
            }
        });
    }

    public static void attach(
            @NonNull final Activity activity,
            @NonNull final AlertDialog dialog,
            @NonNull final EditText editText,
            @NonNull final Runnable onEnter
    ) {
        if (!RokidMode.enabled()) {
            return;
        }

        prepare(editText);
        RokidDialogNavigationHelper.attach(activity, dialog);
        dialog.setOnShowListener(dialogInterface -> RokidKeyboardController.forActivity(activity)
                .show(editText, onEnter, getPositiveButtonLabel(activity, dialog)));
        dialog.setOnDismissListener(dialogInterface ->
                RokidKeyboardController.forActivity(activity).hide(editText));
    }

    public static void attachOnShow(
            @NonNull final Activity activity,
            @NonNull final AlertDialog dialog,
            @NonNull final EditText editText
    ) {
        attachOnShow(activity, dialog, editText, () -> {
            final Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (positiveButton != null && positiveButton.isEnabled()) {
                positiveButton.performClick();
            } else {
                RokidKeyboardController.forActivity(activity).hide(editText);
            }
        });
    }

    public static void attachOnShow(
            @NonNull final Activity activity,
            @NonNull final AlertDialog dialog,
            @NonNull final EditText editText,
            @NonNull final Runnable onEnter
    ) {
        if (!RokidMode.enabled()) {
            return;
        }

        prepare(editText);
        RokidDialogNavigationHelper.attach(activity, dialog);
        dialog.setOnShowListener(dialogInterface -> RokidKeyboardController.forActivity(activity)
                .show(editText, onEnter, getPositiveButtonLabel(activity, dialog)));
    }

    public static void show(
            @NonNull final Activity activity,
            @NonNull final EditText editText,
            @NonNull final Runnable onEnter
    ) {
        if (!RokidMode.enabled()) {
            return;
        }

        prepare(editText);
        RokidKeyboardController.forActivity(activity)
                .show(editText, onEnter, activity.getString(org.schabi.newpipe.R.string.done));
    }

    public static void show(
            @NonNull final Activity activity,
            @NonNull final EditText editText,
            @NonNull final Runnable onEnter,
            @NonNull final CharSequence enterLabel
    ) {
        if (!RokidMode.enabled()) {
            return;
        }

        prepare(editText);
        RokidKeyboardController.forActivity(activity).show(editText, onEnter, enterLabel);
    }

    public static void hide(
            @NonNull final Activity activity,
            @NonNull final EditText editText
    ) {
        if (!RokidMode.enabled()) {
            return;
        }

        RokidKeyboardController.forActivity(activity).hide(editText);
    }

    @NonNull
    private static CharSequence getPositiveButtonLabel(
            @NonNull final Activity activity,
            @NonNull final AlertDialog dialog
    ) {
        final Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton == null || positiveButton.getText() == null
                || positiveButton.getText().length() == 0) {
            return activity.getString(org.schabi.newpipe.R.string.done);
        }
        return positiveButton.getText();
    }
}

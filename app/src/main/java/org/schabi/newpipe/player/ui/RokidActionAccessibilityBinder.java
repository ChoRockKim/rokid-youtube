package org.schabi.newpipe.player.ui;

import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;

final class RokidActionAccessibilityBinder {
    interface FocusListener {
        void onActionFocused(@NonNull View actionView);
    }

    private RokidActionAccessibilityBinder() {
    }

    static void bind(
            @NonNull final View actionView,
            @NonNull final FocusListener focusListener
    ) {
        actionView.setFocusable(true);
        actionView.setFocusableInTouchMode(true);
        actionView.setClickable(true);
        actionView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        actionView.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                focusListener.onActionFocused(view);
            }
        });
        actionView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public boolean performAccessibilityAction(
                    final View host,
                    final int action,
                    final Bundle args
            ) {
                if (action == R.id.rokid_accessibility_select) {
                    focusListener.onActionFocused(host);
                    return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                        || action == AccessibilityNodeInfo.ACTION_FOCUS) {
                    focusListener.onActionFocused(host);
                }
                return super.performAccessibilityAction(host, action, args);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(
                    final View host,
                    final AccessibilityNodeInfo info
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClickable(true);
                info.setFocusable(true);
                info.setSelected(host.isSelected());
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        R.id.rokid_accessibility_select,
                        host.getContext().getString(R.string.rokid_accessibility_select)));
            }
        });
    }

    static void bindAll(
            @NonNull final FocusListener focusListener,
            @NonNull final View... actionViews
    ) {
        for (final View actionView : actionViews) {
            bind(actionView, focusListener);
        }
    }
}

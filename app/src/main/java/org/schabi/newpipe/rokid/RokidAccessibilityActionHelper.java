package org.schabi.newpipe.rokid;

import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;

public final class RokidAccessibilityActionHelper {
    public interface ActionListener {
        void onFocused(@NonNull View view);

        boolean onClicked(@NonNull View view);
    }

    private RokidAccessibilityActionHelper() {
    }

    public static void bind(
            @NonNull final View view,
            @NonNull final ActionListener listener
    ) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        view.setOnFocusChangeListener((target, hasFocus) -> {
            if (hasFocus) {
                listener.onFocused(target);
            }
        });
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public boolean performAccessibilityAction(
                    final View host,
                    final int action,
                    final Bundle args
            ) {
                if (action == R.id.rokid_accessibility_select) {
                    listener.onFocused(host);
                    return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                        || action == AccessibilityNodeInfo.ACTION_FOCUS) {
                    listener.onFocused(host);
                } else if (action == AccessibilityNodeInfo.ACTION_CLICK
                        && listener.onClicked(host)) {
                    return true;
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
}

package org.schabi.newpipe.util;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public final class AccessibilityUtils {
    private AccessibilityUtils() {
    }

    public static void describeFocusableItem(
            @NonNull final View view,
            final CharSequence... parts
    ) {
        final ArrayList<String> labels = new ArrayList<>();
        for (final CharSequence part : parts) {
            if (!TextUtils.isEmpty(part)) {
                labels.add(part.toString());
            }
        }

        if (!labels.isEmpty()) {
            view.setContentDescription(Localization.concatenateStrings(
                    Localization.DOT_SEPARATOR, labels));
        }
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
}

package org.schabi.newpipe.rokid;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.List;

public final class RokidKioskNavBar {
    private static final int BUTTON_HEIGHT_DP = 40;
    private static final int BUTTON_MIN_WIDTH_DP = 84;
    private static final int BUTTON_MAX_WIDTH_DP = 160;
    private static final int BUTTON_HORIZONTAL_MARGIN_DP = 4;
    private static final int BUTTON_HORIZONTAL_PADDING_DP = 12;
    private static final int BUTTON_TEXT_SIZE_SP = 13;
    private static final int SCROLL_EDGE_PADDING_DP = 16;

    private RokidKioskNavBar() {
    }

    public static void populate(
            @NonNull final Fragment fragment,
            @NonNull final HorizontalScrollView scrollView,
            @NonNull final LinearLayout container,
            @Nullable final String currentKioskId,
            @NonNull final ErrorHandler errorHandler
    ) throws ExtractionException {
        final List<RokidKioskNavigator.KioskNavItem> items =
                RokidKioskNavigator.getTopBarItems(fragment.requireContext());
        container.removeAllViews();
        if (items.isEmpty()) {
            scrollView.setVisibility(View.GONE);
            return;
        }

        scrollView.setVisibility(View.VISIBLE);
        final int selectedIndex = findSelectedIndex(items, currentKioskId);
        for (int i = 0; i < items.size(); i++) {
            final RokidKioskNavigator.KioskNavItem item = items.get(i);
            final TextView button = createButton(fragment.requireContext(), item);
            final boolean selected = i == selectedIndex;
            button.setSelected(selected);
            button.setOnClickListener(v -> {
                try {
                    if (item.isSearchAction()) {
                        NavigationHelper.openSearchFragment(fragment.getParentFragmentManager(),
                                ServiceHelper.getSelectedServiceId(fragment.requireContext()), "");
                    } else {
                        RokidKioskNavigator.open(fragment, item.getKioskId());
                    }
                } catch (final Exception e) {
                    errorHandler.onOpenError(item, e);
                }
            });
            button.setOnFocusChangeListener((view, hasFocus) -> {
                view.setSelected(hasFocus || selected);
                if (hasFocus) {
                    scrollFocusedButtonIntoView(scrollView, view);
                }
            });
            container.addView(button);
        }

        if (selectedIndex >= 0) {
            final View selected = container.getChildAt(selectedIndex);
            selected.post(() -> {
                selected.requestFocusFromTouch();
                selected.requestFocus();
                scrollFocusedButtonIntoView(scrollView, selected);
            });
        }
    }

    private static int findSelectedIndex(
            @NonNull final List<RokidKioskNavigator.KioskNavItem> items,
            @Nullable final String currentKioskId
    ) {
        if (currentKioskId == null) {
            return -1;
        }

        for (int i = 0; i < items.size(); i++) {
            final RokidKioskNavigator.KioskNavItem item = items.get(i);
            if (currentKioskId.equals(item.getKioskId())) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    private static TextView createButton(
            @NonNull final Context context,
            @NonNull final RokidKioskNavigator.KioskNavItem item
    ) {
        final TextView button = new TextView(context);
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, BUTTON_HEIGHT_DP));
        final int margin = dp(context, BUTTON_HORIZONTAL_MARGIN_DP);
        params.setMargins(margin, 0, margin, 0);
        button.setLayoutParams(params);

        button.setBackgroundResource(R.drawable.rokid_outline_button);
        button.setClickable(true);
        button.setContentDescription(item.getLabel());
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setLetterSpacing(0.08f);
        button.setMaxWidth(dp(context, BUTTON_MAX_WIDTH_DP));
        button.setMinWidth(dp(context, BUTTON_MIN_WIDTH_DP));
        button.setPadding(dp(context, BUTTON_HORIZONTAL_PADDING_DP), 0,
                dp(context, BUTTON_HORIZONTAL_PADDING_DP), 0);
        button.setSingleLine(true);
        button.setText(item.getLabel());
        button.setAllCaps(true);
        button.setTextColor(ContextCompat.getColorStateList(context,
                R.color.rokid_hud_button_text));
        button.setTextSize(BUTTON_TEXT_SIZE_SP);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return button;
    }

    private static void scrollFocusedButtonIntoView(
            @NonNull final HorizontalScrollView scrollView,
            @NonNull final View button
    ) {
        button.post(() -> {
            final int edgePadding = dp(button.getContext(), SCROLL_EDGE_PADDING_DP);
            final int viewportLeft = scrollView.getScrollX();
            final int viewportRight = viewportLeft + scrollView.getWidth()
                    - scrollView.getPaddingLeft() - scrollView.getPaddingRight();
            final int buttonLeft = Math.max(0, button.getLeft() - edgePadding);
            final int buttonRight = button.getRight() + edgePadding;

            if (buttonLeft < viewportLeft) {
                scrollView.scrollTo(buttonLeft, 0);
            } else if (buttonRight > viewportRight) {
                scrollView.scrollTo(buttonRight - (viewportRight - viewportLeft), 0);
            }
        });
    }

    private static int dp(@NonNull final Context context, final int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public interface ErrorHandler {
        void onOpenError(
                @NonNull RokidKioskNavigator.KioskNavItem item,
                @NonNull Exception exception
        );
    }
}

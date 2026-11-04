package org.schabi.newpipe.player.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.rokid.RokidMode;

import java.util.ArrayList;

final class RokidPlayerMenuController {
    private static final int DIM_GREEN = Color.rgb(46, 120, 36);

    @NonNull
    private final Context context;
    @NonNull
    private final ViewGroup host;
    @NonNull
    private final PopupMenu.OnMenuItemClickListener itemClickListener;
    @NonNull
    private final PopupMenu.OnDismissListener dismissListener;
    @Nullable
    private FrameLayout overlay;
    @Nullable
    private ScrollView scrollView;
    @Nullable
    private LinearLayout panel;
    private final ArrayList<MenuItem> menuItems = new ArrayList<>();
    private final ArrayList<TextView> itemViews = new ArrayList<>();
    private int selectedIndex = -1;

    RokidPlayerMenuController(
            @NonNull final Context context,
            @NonNull final ViewGroup host,
            @NonNull final PopupMenu.OnMenuItemClickListener itemClickListener,
            @NonNull final PopupMenu.OnDismissListener dismissListener
    ) {
        this.context = context;
        this.host = host;
        this.itemClickListener = itemClickListener;
        this.dismissListener = dismissListener;
    }

    boolean show(@Nullable final PopupMenu popupMenu) {
        if (!RokidMode.enabled() || popupMenu == null || popupMenu.getMenu().size() == 0) {
            return false;
        }
        ensureOverlay();
        if (overlay == null || panel == null) {
            return false;
        }

        menuItems.clear();
        for (int index = 0; index < popupMenu.getMenu().size(); index++) {
            final MenuItem item = popupMenu.getMenu().getItem(index);
            if (item.isVisible() && item.isEnabled()) {
                menuItems.add(item);
            }
        }
        if (menuItems.isEmpty()) {
            return false;
        }

        panel.removeAllViews();
        itemViews.clear();
        for (int index = 0; index < menuItems.size(); index++) {
            final TextView itemView = makeItemView(index);
            itemViews.add(itemView);
            panel.addView(itemView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        updateOverlayLayout();
        selectedIndex = 0;
        overlay.setAlpha(1f);
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        if (scrollView != null) {
            scrollView.scrollTo(0, 0);
        }
        updateSelection();
        final TextView firstItemView = itemViews.get(0);
        firstItemView.requestFocusFromTouch();
        firstItemView.requestFocus();
        return true;
    }

    boolean isVisible() {
        return overlay != null && overlay.getParent() != null
                && overlay.getVisibility() == View.VISIBLE;
    }

    boolean moveFocus(final boolean forward) {
        if (!isVisible() || itemViews.isEmpty()) {
            return false;
        }

        if (selectedIndex < 0) {
            selectedIndex = forward ? 0 : itemViews.size() - 1;
        } else if (forward) {
            selectedIndex = selectedIndex == itemViews.size() - 1 ? 0 : selectedIndex + 1;
        } else {
            selectedIndex = selectedIndex == 0 ? itemViews.size() - 1 : selectedIndex - 1;
        }

        updateSelection();
        final TextView target = itemViews.get(selectedIndex);
        target.requestFocusFromTouch();
        target.requestFocus();
        return true;
    }

    boolean activateSelected() {
        if (!isVisible() || selectedIndex < 0 || selectedIndex >= menuItems.size()) {
            return false;
        }

        activateItem(selectedIndex);
        return true;
    }

    boolean dismiss(final boolean notifyDismiss) {
        if (!isVisible()) {
            return false;
        }

        overlay.setVisibility(View.GONE);
        overlay.setAlpha(1f);
        if (panel != null) {
            panel.removeAllViews();
        }
        if (overlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }
        menuItems.clear();
        itemViews.clear();
        selectedIndex = -1;
        if (notifyDismiss) {
            dismissListener.onDismiss(null);
        }
        return true;
    }

    void destroy() {
        dismiss(false);
        if (overlay != null && overlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }
        overlay = null;
        scrollView = null;
        panel = null;
    }

    private void ensureOverlay() {
        if (overlay == null) {
            overlay = new FrameLayout(context);
            overlay.setId(R.id.rokidPlayerMenuOverlay);
            overlay.setVisibility(View.GONE);
            overlay.setFocusable(false);
            overlay.setClickable(false);
            overlay.setContentDescription(context.getString(R.string.rokid_player_actions));

            panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(8), dp(8), dp(8), dp(8));
            panel.setBackground(panelBackground());
            panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            scrollView = new ScrollView(context);
            scrollView.setFillViewport(false);
            scrollView.setClipToPadding(false);
            scrollView.setVerticalScrollBarEnabled(false);
            scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scrollView.addView(panel, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            overlay.addView(scrollView, makeScrollLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (overlay.getParent() == null) {
            host.addView(overlay, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private TextView makeItemView(final int index) {
        final MenuItem menuItem = menuItems.get(index);
        final TextView itemView = new TextView(context);
        itemView.setText(menuItem.getTitle());
        itemView.setTextColor(Color.WHITE);
        itemView.setTextSize(16);
        itemView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        itemView.setGravity(Gravity.CENTER_VERTICAL);
        itemView.setLetterSpacing(0f);
        itemView.setMinHeight(dp(48));
        itemView.setPadding(dp(14), dp(8), dp(14), dp(8));
        itemView.setClickable(true);
        itemView.setFocusable(true);
        itemView.setFocusableInTouchMode(true);
        itemView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        itemView.setOnClickListener(view -> activateItem(index));
        itemView.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                selectFromAccessibility(index);
            }
        });
        itemView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public boolean performAccessibilityAction(
                    final View view,
                    final int action,
                    final Bundle args
            ) {
                if (action == R.id.rokid_accessibility_select) {
                    selectFromAccessibility(index);
                    return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                        || action == AccessibilityNodeInfo.ACTION_FOCUS) {
                    selectFromAccessibility(index);
                }
                return super.performAccessibilityAction(view, action, args);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(
                    final View view,
                    final AccessibilityNodeInfo info
            ) {
                super.onInitializeAccessibilityNodeInfo(view, info);
                info.setClickable(true);
                info.setFocusable(true);
                info.setSelected(index == selectedIndex);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        R.id.rokid_accessibility_select,
                        view.getContext().getString(R.string.rokid_accessibility_select)));
            }
        });
        return itemView;
    }

    private void selectFromAccessibility(final int index) {
        if (index < 0 || index >= itemViews.size() || selectedIndex == index) {
            return;
        }

        selectedIndex = index;
        updateSelection();
    }

    private void activateItem(final int index) {
        if (index < 0 || index >= menuItems.size()) {
            return;
        }

        final MenuItem menuItem = menuItems.get(index);
        dismiss(false);
        itemClickListener.onMenuItemClick(menuItem);
        dismissListener.onDismiss(null);
    }

    private void updateSelection() {
        for (int index = 0; index < itemViews.size(); index++) {
            final TextView itemView = itemViews.get(index);
            final boolean selected = index == selectedIndex;
            itemView.setSelected(selected);
            itemView.setTextColor(selected ? hudGreen() : Color.WHITE);
            itemView.setContentDescription(menuItems.get(index).getTitle());
            itemView.setBackground(itemBackground(selected));
        }
        scrollSelectedItemIntoView();
    }

    private GradientDrawable panelBackground() {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.BLACK);
        drawable.setStroke(dp(2), hudGreen());
        drawable.setCornerRadius(dp(3));
        return drawable;
    }

    private GradientDrawable itemBackground(final boolean selected) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.BLACK);
        drawable.setStroke(dp(selected ? 4 : 1),
                selected ? hudGreen() : DIM_GREEN);
        drawable.setCornerRadius(dp(3));
        return drawable;
    }

    private void updateOverlayLayout() {
        if (scrollView == null) {
            return;
        }

        final DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        final int width = Math.min(dp(420),
                Math.max(dp(240), displayMetrics.widthPixels - dp(48)));
        final int maxHeight = Math.max(dp(180),
                Math.min(dp(440), displayMetrics.heightPixels - dp(96)));
        final int estimatedHeight = dp(16) + itemViews.size() * dp(48);
        final int height = estimatedHeight > maxHeight
                ? maxHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
        scrollView.setLayoutParams(makeScrollLayoutParams(width, height));
    }

    private FrameLayout.LayoutParams makeScrollLayoutParams(final int width, final int height) {
        return new FrameLayout.LayoutParams(width, height, Gravity.CENTER);
    }

    private void scrollSelectedItemIntoView() {
        if (scrollView == null || selectedIndex < 0 || selectedIndex >= itemViews.size()) {
            return;
        }

        final TextView target = itemViews.get(selectedIndex);
        target.post(() -> {
            if (scrollView == null) {
                return;
            }

            final int top = target.getTop();
            final int bottom = target.getBottom();
            final int visibleTop = scrollView.getScrollY();
            final int visibleBottom = visibleTop + scrollView.getHeight();
            if (top < visibleTop) {
                scrollView.smoothScrollTo(0, top);
            } else if (bottom > visibleBottom) {
                scrollView.smoothScrollTo(0, bottom - scrollView.getHeight());
            }
        });
    }

    private int hudGreen() {
        return ContextCompat.getColor(context, R.color.rokid_focus_green);
    }

    private int dp(final int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}

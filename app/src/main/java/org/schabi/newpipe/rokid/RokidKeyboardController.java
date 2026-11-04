package org.schabi.newpipe.rokid;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

public final class RokidKeyboardController {
    interface StringProvider {
        String getString(int resId, Object... args);
    }

    private static final int KEY_COLUMNS = 8;
    private static final String[] LETTER_KEYS = {
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "space", "del", "clear", "voice", "go", "123"
    };
    private static final String[] LETTER_KEYS_NO_VOICE = {
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "space", "del", "clear", "go", "123"
    };
    private static final String[] SYMBOL_KEYS = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            ".", "-", "_", "@", "/", ":", "#", "+", "space", "del", "clear", "voice", "go", "abc"
    };
    private static final String[] SYMBOL_KEYS_NO_VOICE = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            ".", "-", "_", "@", "/", ":", "#", "+", "space", "del", "clear", "go", "abc"
    };
    private static final WeakHashMap<Activity, RokidKeyboardController> INSTANCES =
            new WeakHashMap<>();

    private final WeakReference<Activity> activityReference;
    @Nullable
    private EditText target;
    @Nullable
    private Runnable enterAction;
    @Nullable
    private Runnable voiceAction;
    @Nullable
    private CharSequence enterKeyLabel;
    @Nullable
    private FrameLayout overlay;
    @Nullable
    private TextView preview;
    @Nullable
    private TextView selectedKey;
    @Nullable
    private LinearLayout keyRows;
    private final ArrayList<TextView> keyViews = new ArrayList<>();
    private String[] keys = LETTER_KEYS;
    private int selectedIndex = 0;

    private RokidKeyboardController(@NonNull final Activity activity) {
        activityReference = new WeakReference<>(activity);
    }

    @NonNull
    public static RokidKeyboardController forActivity(@NonNull final Activity activity) {
        RokidKeyboardController controller = INSTANCES.get(activity);
        if (controller == null) {
            controller = new RokidKeyboardController(activity);
            INSTANCES.put(activity, controller);
        }
        return controller;
    }

    public static void hideAll() {
        for (final RokidKeyboardController controller
                : new java.util.ArrayList<>(INSTANCES.values())) {
            controller.hide(null);
        }
    }

    public static void hideAll(@Nullable final Activity activity) {
        hideAll();
        if (activity == null) {
            return;
        }

        View overlayView = activity.findViewById(R.id.rokidKeyboardOverlay);
        while (overlayView != null) {
            final ViewGroup parent = (ViewGroup) overlayView.getParent();
            if (parent == null) {
                break;
            }
            parent.removeView(overlayView);
            overlayView = activity.findViewById(R.id.rokidKeyboardOverlay);
        }
    }

    public boolean isVisible() {
        return overlay != null && overlay.getVisibility() == View.VISIBLE;
    }

    public void show(@NonNull final EditText editText, @NonNull final Runnable onEnter) {
        show(editText, onEnter, null, null);
    }

    public void show(
            @NonNull final EditText editText,
            @NonNull final Runnable onEnter,
            @Nullable final CharSequence onEnterLabel
    ) {
        show(editText, onEnter, null, onEnterLabel);
    }

    public void show(
            @NonNull final EditText editText,
            @NonNull final Runnable onEnter,
            @Nullable final Runnable onVoice
    ) {
        show(editText, onEnter, onVoice, null);
    }

    public void show(
            @NonNull final EditText editText,
            @NonNull final Runnable onEnter,
            @Nullable final Runnable onVoice,
            @Nullable final CharSequence onEnterLabel
    ) {
        final Activity activity = activityReference.get();
        if (activity == null) {
            return;
        }
        ensureOverlay(activity, editText);
        final boolean targetChanged = target != editText;
        target = editText;
        enterAction = onEnter;
        voiceAction = onVoice;
        enterKeyLabel = onEnterLabel == null || onEnterLabel.length() == 0
                ? activity.getString(R.string.search) : onEnterLabel;
        if (targetChanged) {
            keys = letterKeys();
            selectedIndex = 0;
        } else {
            keys = keysForCurrentVoiceMode();
        }
        selectedIndex = Math.min(selectedIndex, keys.length - 1);
        editText.setShowSoftInputOnFocus(false);
        editText.requestFocus();
        hideAndroidKeyboard(activity, editText);
        overlay.setVisibility(View.VISIBLE);
        updateText();
    }

    public void hide(@Nullable final EditText editText) {
        if (target != null && editText != null && target != editText) {
            return;
        }
        if (overlay != null) {
            final ViewGroup parent = (ViewGroup) overlay.getParent();
            if (parent != null) {
                parent.removeView(overlay);
            }
            overlay = null;
        }
        if (target != null) {
            target.clearFocus();
        }
        preview = null;
        selectedKey = null;
        keyRows = null;
        keyViews.clear();
        target = null;
        enterAction = null;
        voiceAction = null;
        enterKeyLabel = null;
    }

    public boolean handleKeyEvent(@NonNull final KeyEvent event) {
        if (!isVisible()) {
            return false;
        }
        final RokidKeyMapper.Action action = RokidKeyMapper.map(event);
        switch (action) {
            case DUPLICATE:
                return true;
            case PREVIOUS:
                selectOffset(-1);
                return true;
            case NEXT:
                selectOffset(1);
                return true;
            case SELECT:
                activateSelected();
                return true;
            case BACK:
                hide(target);
                return true;
            default:
                return false;
        }
    }

    private void ensureOverlay(
            @NonNull final Activity activity,
            @NonNull final EditText editText
    ) {
        final ViewGroup decor = getOverlayParent(activity, editText);
        if (overlay != null) {
            if (overlay.getParent() != decor) {
                final ViewGroup parent = (ViewGroup) overlay.getParent();
                if (parent != null) {
                    parent.removeView(overlay);
                }
                decor.addView(overlay, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
            return;
        }

        overlay = new FrameLayout(activity);
        overlay.setId(R.id.rokidKeyboardOverlay);
        overlay.setVisibility(View.GONE);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        final LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 14));
        panel.setBackground(outline(Color.WHITE, 2));

        preview = makeText(activity, 18, Typeface.NORMAL);
        preview.setSingleLine(true);
        selectedKey = makeText(activity, 34, Typeface.BOLD);
        selectedKey.setGravity(Gravity.CENTER);
        selectedKey.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        keyRows = new LinearLayout(activity);
        keyRows.setOrientation(LinearLayout.VERTICAL);
        keyRows.setGravity(Gravity.CENTER);
        keyRows.setPadding(0, dp(activity, 4), 0, 0);
        keyRows.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        panel.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(selectedKey, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(keyRows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        panelParams.setMargins(dp(activity, 10), 0, dp(activity, 10), dp(activity, 10));
        overlay.addView(panel, panelParams);

        decor.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @NonNull
    private static ViewGroup getOverlayParent(
            @NonNull final Activity activity,
            @NonNull final EditText editText
    ) {
        final View root = editText.getRootView();
        if (root instanceof ViewGroup && root.getWindowToken() != null) {
            return (ViewGroup) root;
        }
        return activity.findViewById(android.R.id.content);
    }

    private TextView makeText(
            @NonNull final Activity activity,
            final int sp,
            final int typefaceStyle
    ) {
        final TextView textView = new TextView(activity);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(sp);
        textView.setTypeface(Typeface.DEFAULT, typefaceStyle);
        textView.setIncludeFontPadding(false);
        textView.setPadding(0, dp(activity, 5), 0, dp(activity, 5));
        return textView;
    }

    private GradientDrawable outline(final int strokeColor, final int strokeDp) {
        final Activity activity = activityReference.get();
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.BLACK);
        drawable.setStroke(activity == null ? strokeDp : dp(activity, strokeDp), strokeColor);
        drawable.setCornerRadius(0);
        return drawable;
    }

    private void selectOffset(final int offset) {
        selectedIndex = (selectedIndex + offset + keys.length) % keys.length;
        updateText();
    }

    private void activateSelected() {
        if (target == null) {
            return;
        }
        final String key = keys[selectedIndex];
        switch (key) {
            case "space":
                insert(" ");
                break;
            case "del":
                delete();
                break;
            case "clear":
                target.getText().clear();
                break;
            case "voice":
                if (voiceAction != null) {
                    voiceAction.run();
                }
                break;
            case "go":
                if (enterAction != null) {
                    enterAction.run();
                }
                break;
            case "123":
                keys = symbolKeys();
                selectedIndex = 0;
                break;
            case "abc":
                keys = letterKeys();
                selectedIndex = 0;
                break;
            default:
                insert(key);
                break;
        }
        updateText();
    }

    private void insert(final String value) {
        final int start = Math.max(target.getSelectionStart(), 0);
        final int end = Math.max(target.getSelectionEnd(), 0);
        final int from = Math.min(start, end);
        final int to = Math.max(start, end);
        target.getText().replace(from, to, value);
        target.setSelection(from + value.length());
    }

    private void delete() {
        final int start = Math.max(target.getSelectionStart(), 0);
        final int end = Math.max(target.getSelectionEnd(), 0);
        if (start != end) {
            target.getText().delete(Math.min(start, end), Math.max(start, end));
        } else if (start > 0) {
            target.getText().delete(start - 1, start);
        }
    }

    private void updateText() {
        if (target == null || preview == null || selectedKey == null || keyRows == null) {
            return;
        }
        final Activity activity = activityReference.get();
        if (activity == null) {
            return;
        }
        final CharSequence targetText = target.getText();
        preview.setText(targetText);
        preview.setContentDescription(targetText == null || targetText.length() == 0
                ? activity.getString(R.string.rokid_keyboard_text_empty)
                : activity.getString(R.string.rokid_keyboard_text, targetText));

        selectedKey.setText(keyDisplayLabel(activity, keys[selectedIndex]));
        rebuildKeyRowsIfNeeded();
        updateKeyRows();
    }

    @NonNull
    private String[] keysForCurrentVoiceMode() {
        return isShowingSymbols() ? symbolKeys() : letterKeys();
    }

    private boolean isShowingSymbols() {
        return keys == SYMBOL_KEYS || keys == SYMBOL_KEYS_NO_VOICE;
    }

    @NonNull
    private String[] letterKeys() {
        return voiceAction == null ? LETTER_KEYS_NO_VOICE : LETTER_KEYS;
    }

    @NonNull
    private String[] symbolKeys() {
        return voiceAction == null ? SYMBOL_KEYS_NO_VOICE : SYMBOL_KEYS;
    }

    @NonNull
    static String[] keysForTesting(final boolean symbols, final boolean voiceEnabled) {
        if (symbols) {
            return voiceEnabled ? SYMBOL_KEYS : SYMBOL_KEYS_NO_VOICE;
        }
        return voiceEnabled ? LETTER_KEYS : LETTER_KEYS_NO_VOICE;
    }

    private void rebuildKeyRowsIfNeeded() {
        if (keyRows == null || keyViews.size() == keys.length) {
            return;
        }

        keyRows.removeAllViews();
        keyViews.clear();

        final Activity activity = activityReference.get();
        if (activity == null) {
            return;
        }

        LinearLayout row = null;
        for (int index = 0; index < keys.length; index++) {
            if (index % KEY_COLUMNS == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                keyRows.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            final TextView keyView = makeKeyView(activity, index);
            keyViews.add(keyView);

            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, dp(activity, 32), 1f);
            params.setMargins(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
            row.addView(keyView, params);
        }
    }

    private TextView makeKeyView(@NonNull final Activity activity, final int index) {
        final TextView keyView = makeText(activity, 14, Typeface.BOLD);
        keyView.setGravity(Gravity.CENTER);
        keyView.setSingleLine(true);
        keyView.setClickable(true);
        keyView.setFocusable(true);
        keyView.setFocusableInTouchMode(true);
        keyView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        keyView.setOnClickListener(view -> {
            selectedIndex = index;
            updateText();
            activateSelected();
        });
        keyView.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                selectFromAccessibility(index);
            }
        });
        keyView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public boolean performAccessibilityAction(
                    final View host,
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
                info.setSelected(index == selectedIndex);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        R.id.rokid_accessibility_select,
                        host.getContext().getString(R.string.rokid_accessibility_select)));
            }
        });
        return keyView;
    }

    private void selectFromAccessibility(final int index) {
        if (index < 0 || index >= keys.length || selectedIndex == index) {
            return;
        }
        selectedIndex = index;
        updateText();
    }

    private void updateKeyRows() {
        final Activity activity = activityReference.get();
        if (activity == null) {
            return;
        }

        for (int index = 0; index < keyViews.size(); index++) {
            final TextView keyView = keyViews.get(index);
            final String key = keys[index];
            final boolean selected = index == selectedIndex;
            keyView.setText(keyDisplayLabel(activity, key));
            keyView.setSelected(selected);
            keyView.setTextColor(Color.WHITE);
            keyView.setBackground(keyBackground(activity, selected));
            keyView.setContentDescription(keyAccessibilityDescription(activity, key, selected));
        }
    }

    private GradientDrawable keyBackground(
            @NonNull final Activity activity,
            final boolean selected
    ) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.BLACK);
        drawable.setStroke(dp(activity, selected ? 2 : 1),
                selected ? Color.WHITE : Color.rgb(120, 120, 120));
        drawable.setCornerRadius(dp(activity, 3));
        return drawable;
    }

    private static String keyDisplayLabel(
            @NonNull final Activity activity,
            @NonNull final String key
    ) {
        switch (key) {
            case "space":
                return activity.getString(R.string.rokid_keyboard_display_space);
            case "del":
                return activity.getString(R.string.rokid_keyboard_display_delete);
            case "clear":
                return activity.getString(R.string.rokid_keyboard_display_clear);
            case "voice":
                return activity.getString(R.string.rokid_keyboard_display_voice);
            default:
                return key;
        }
    }

    private String keyAccessibilityDescription(
            @NonNull final Activity activity,
            @NonNull final String key,
            final boolean selected
    ) {
        final String label = keyAccessibilityLabel(activity, key);
        return selected ? activity.getString(R.string.rokid_keyboard_selected_key, label) : label;
    }

    private String keyAccessibilityLabel(
            @NonNull final Activity activity,
            @NonNull final String key
    ) {
        if ("go".equals(key)) {
            return enterKeyLabel == null ? activity.getString(R.string.search)
                    : enterKeyLabel.toString();
        }
        return keyAccessibilityLabel(
                key,
                activity.getString(R.string.search),
                (resId, args) -> activity.getString(resId, args));
    }

    static String keyAccessibilityLabelForTesting(
            @NonNull final String key,
            @NonNull final String enterLabel,
            @NonNull final StringProvider strings
    ) {
        return keyAccessibilityLabel(key, enterLabel, strings);
    }

    private static String keyAccessibilityLabel(
            @NonNull final String key,
            @NonNull final String enterLabel,
            @NonNull final StringProvider strings
    ) {
        switch (key) {
            case "space":
                return strings.getString(R.string.rokid_keyboard_key_space);
            case "del":
                return strings.getString(R.string.rokid_keyboard_key_delete);
            case "clear":
                return strings.getString(R.string.rokid_keyboard_key_clear_text);
            case "voice":
                return strings.getString(R.string.rokid_keyboard_key_voice_search);
            case "go":
                return enterLabel;
            case "123":
                return strings.getString(R.string.rokid_keyboard_key_numbers_symbols);
            case "abc":
                return strings.getString(R.string.rokid_keyboard_key_letters);
            case ".":
                return strings.getString(R.string.rokid_keyboard_key_dot);
            case "-":
                return strings.getString(R.string.rokid_keyboard_key_dash);
            case "_":
                return strings.getString(R.string.rokid_keyboard_key_underscore);
            case "@":
                return strings.getString(R.string.rokid_keyboard_key_at_sign);
            case "/":
                return strings.getString(R.string.rokid_keyboard_key_slash);
            case ":":
                return strings.getString(R.string.rokid_keyboard_key_colon);
            case "#":
                return strings.getString(R.string.rokid_keyboard_key_hash);
            case "+":
                return strings.getString(R.string.rokid_keyboard_key_plus);
            default:
                return key.length() == 1
                        ? strings.getString(R.string.rokid_keyboard_letter_key, key)
                        : key;
        }
    }

    private static void hideAndroidKeyboard(
            @NonNull final Activity activity,
            @NonNull final EditText editText
    ) {
        final InputMethodManager imm = ContextCompat.getSystemService(activity,
                InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        }
    }

    private static int dp(@NonNull final Activity activity, final int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}

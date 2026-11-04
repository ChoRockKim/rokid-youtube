package org.schabi.newpipe.rokid;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;

/**
 * Confines the whole app to a wide HUD band (480dp x 282dp) glued to the bottom edge of the
 * glasses display, like the built-in navigation overlay. Everything outside the band stays
 * black, which is transparent on the glasses.
 */
public final class RokidViewport implements Application.ActivityLifecycleCallbacks {
    public static final int WIDTH_DP = 480;
    public static final int HEIGHT_DP = 282;

    private RokidViewport() {
    }

    public static void install(@NonNull final Application application) {
        if (RokidMode.enabled()) {
            application.registerActivityLifecycleCallbacks(new RokidViewport());
        }
    }

    /**
     * @param context any context
     * @return the band height in pixels, never larger than the physical display
     */
    public static int heightPx(@NonNull final Context context) {
        return Math.min(dp(context, HEIGHT_DP),
                context.getResources().getDisplayMetrics().heightPixels);
    }

    /**
     * @param context any context
     * @return the band width in pixels, never larger than the physical display
     */
    public static int widthPx(@NonNull final Context context) {
        return Math.min(dp(context, WIDTH_DP),
                context.getResources().getDisplayMetrics().widthPixels);
    }

    /**
     * @param context any context
     * @return layout params that put an overlay added to the decor view inside the band
     */
    @NonNull
    public static FrameLayout.LayoutParams bandLayoutParams(@NonNull final Context context) {
        return new FrameLayout.LayoutParams(widthPx(context), heightPx(context),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    }

    /**
     * Places a dialog window inside the band instead of centering it on the full display.
     *
     * @param window the dialog window
     */
    public static void applyToDialog(@Nullable final Window window) {
        if (!RokidMode.enabled() || window == null) {
            return;
        }
        final Context context = window.getContext();
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        window.setLayout(widthPx(context), heightPx(context));
        final WindowManager.LayoutParams params = window.getAttributes();
        params.y = 0;
        params.horizontalMargin = 0;
        params.verticalMargin = 0;
        window.setAttributes(params);
    }

    private static void apply(@NonNull final Activity activity) {
        final View content = activity.findViewById(android.R.id.content);
        if (content == null || content.getTag(R.id.rokid_viewport_applied) != null) {
            return;
        }
        content.setTag(R.id.rokid_viewport_applied, Boolean.TRUE);
        activity.getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        content.setBackgroundColor(Color.BLACK);

        // The content view keeps filling the window; padding shrinks the area its children
        // can use down to the band. Its own size does not depend on the padding, so updating
        // the padding from a layout listener cannot loop.
        content.addOnLayoutChangeListener((v, left, top, right, bottom,
                                           oldLeft, oldTop, oldRight, oldBottom) ->
                updatePadding(v, right - left, bottom - top));
        updatePadding(content, content.getWidth(), content.getHeight());
    }

    private static void updatePadding(@NonNull final View content, final int width,
                                      final int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        final Context context = content.getContext();
        final int top = Math.max(0, height - dp(context, HEIGHT_DP));
        final int side = Math.max(0, (width - dp(context, WIDTH_DP)) / 2);
        if (content.getPaddingTop() != top || content.getPaddingLeft() != side
                || content.getPaddingRight() != side || content.getPaddingBottom() != 0) {
            content.post(() -> content.setPadding(side, top, side, 0));
        }
    }

    private static int dp(@NonNull final Context context, final int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    @Override
    public void onActivityCreated(@NonNull final Activity activity,
                                  @Nullable final Bundle savedInstanceState) {
        // setContentView() usually runs after this callback; apply in onActivityStarted.
    }

    @Override
    public void onActivityStarted(@NonNull final Activity activity) {
        apply(activity);
    }

    @Override
    public void onActivityResumed(@NonNull final Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull final Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull final Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull final Activity activity,
                                            @NonNull final Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull final Activity activity) {
    }
}

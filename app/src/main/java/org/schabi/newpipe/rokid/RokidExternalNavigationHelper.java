package org.schabi.newpipe.rokid;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;

public final class RokidExternalNavigationHelper {
    private RokidExternalNavigationHelper() {
    }

    public static boolean confirmAndOpen(
            @NonNull final Context context,
            @NonNull final Intent intent,
            @StringRes final int title,
            @StringRes final int message
    ) {
        return confirmAndOpen(context, intent, null, title, message);
    }

    public static boolean confirmAndOpen(
            @NonNull final Context context,
            @NonNull final Intent intent,
            @Nullable final Intent fallbackIntent,
            @StringRes final int title,
            @StringRes final int message
    ) {
        final Activity activity = findActivity(context);
        if (!RokidMode.enabled() || activity == null) {
            return openExternalActivity(context, intent, fallbackIntent);
        }

        RokidDialogNavigationHelper.show(activity, new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.rokid_open_android_settings,
                        (dialog, which) -> openExternalActivity(context, intent, fallbackIntent))
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(true));
        return true;
    }

    public static boolean openExternalActivity(
            @NonNull final Context context,
            @NonNull final Intent intent
    ) {
        return openExternalActivity(context, intent, null);
    }

    public static boolean openExternalActivity(
            @NonNull final Context context,
            @NonNull final Intent intent,
            @Nullable final Intent fallbackIntent
    ) {
        if (tryStartActivity(context, intent)) {
            return true;
        }
        if (fallbackIntent != null && tryStartActivity(context, fallbackIntent)) {
            return true;
        }

        Toast.makeText(context, R.string.general_error, Toast.LENGTH_SHORT).show();
        return false;
    }

    private static boolean tryStartActivity(
            @NonNull final Context context,
            @NonNull final Intent intent
    ) {
        final Intent launchIntent = new Intent(intent);
        if (!(context instanceof Activity)) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(launchIntent);
            return true;
        } catch (final ActivityNotFoundException ignored) {
            return false;
        }
    }

    @Nullable
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

package org.schabi.newpipe.player.ui;

import static org.schabi.newpipe.player.Player.RENDERER_UNAVAILABLE;

import android.content.Context;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.helper.PlayerHelper;

final class RokidCaptionMenuHandler {
    private RokidCaptionMenuHandler() {
    }

    static void onCaptionItemClick(
            @NonNull final Context context,
            @NonNull final Player player,
            @NonNull final MenuItem menuItem
    ) {
        final int textRendererIndex = player.getCaptionRendererIndex();
        if (textRendererIndex == RENDERER_UNAVAILABLE) {
            return;
        }

        if (menuItem.getItemId() == 0) {
            player.getTrackSelector().setParameters(player.getTrackSelector()
                    .buildUponParameters().setRendererDisabled(textRendererIndex, true));
            player.getPrefs().edit()
                    .remove(context.getString(R.string.caption_user_set_key)).apply();
            return;
        }

        final String captionLanguage = String.valueOf(menuItem.getTitle());
        player.getTrackSelector().setParameters(player.getTrackSelector()
                .buildUponParameters()
                .setPreferredTextLanguages(captionLanguage,
                        PlayerHelper.captionLanguageStemOf(captionLanguage))
                .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                .setRendererDisabled(textRendererIndex, false));
        player.getPrefs().edit().putString(context.getString(
                R.string.caption_user_set_key), captionLanguage).apply();
    }
}

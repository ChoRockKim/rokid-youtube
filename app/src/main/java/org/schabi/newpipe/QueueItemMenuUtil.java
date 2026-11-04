package org.schabi.newpipe;

import static org.schabi.newpipe.util.SparseItemUtil.fetchStreamInfoAndSaveToDatabase;
import static org.schabi.newpipe.util.external_communication.ShareUtils.shareText;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.PopupMenu;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.rokid.RokidDialogNavigationHelper;
import org.schabi.newpipe.rokid.RokidMode;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.SparseItemUtil;

import java.util.ArrayList;
import java.util.List;

public final class QueueItemMenuUtil {
    private QueueItemMenuUtil() {
    }

    public static void openPopupMenu(final PlayQueue playQueue,
                                     final PlayQueueItem item,
                                     final View view,
                                     final boolean hideDetails,
                                     final FragmentManager fragmentManager,
                                     final Context context) {
        if (RokidMode.enabled() && showRokidDialogMenu(playQueue, item, view, hideDetails,
                fragmentManager, context)) {
            return;
        }

        final ContextThemeWrapper themeWrapper =
                new ContextThemeWrapper(context, R.style.DarkPopupMenu);

        final PopupMenu popupMenu = new PopupMenu(themeWrapper, view);
        popupMenu.inflate(R.menu.menu_play_queue_item);

        if (hideDetails) {
            popupMenu.getMenu().findItem(R.id.menu_item_details).setVisible(false);
        }

        popupMenu.setOnMenuItemClickListener(menuItem -> {
            return handleMenuItem(playQueue, item, menuItem.getItemId(), fragmentManager, context);
        });

        popupMenu.show();
    }

    private static boolean showRokidDialogMenu(final PlayQueue playQueue,
                                               final PlayQueueItem item,
                                               final View view,
                                               final boolean hideDetails,
                                               final FragmentManager fragmentManager,
                                               final Context context) {
        final Activity activity = findActivity(view.getContext());
        if (activity == null) {
            return false;
        }

        final List<Integer> itemIds = new ArrayList<>();
        final List<CharSequence> labels = new ArrayList<>();
        addMenuItem(labels, itemIds, context, R.string.play_queue_remove, R.id.menu_item_remove);
        if (!hideDetails) {
            addMenuItem(labels, itemIds, context, R.string.play_queue_stream_detail,
                    R.id.menu_item_details);
        }
        addMenuItem(labels, itemIds, context, R.string.add_to_playlist,
                R.id.menu_item_append_playlist);
        addMenuItem(labels, itemIds, context, R.string.show_channel_details,
                R.id.menu_item_channel_details);
        addMenuItem(labels, itemIds, context, R.string.share, R.id.menu_item_share);
        addMenuItem(labels, itemIds, context, R.string.download, R.id.menu_item_download);

        final DialogInterface.OnClickListener listener = (dialogInterface, which) ->
                handleMenuItem(playQueue, item, itemIds.get(which), fragmentManager, context);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(item.getTitle())
                .setItems(labels.toArray(new CharSequence[0]), listener)
                .create();
        RokidDialogNavigationHelper.attach(activity, dialog);
        dialog.show();
        return true;
    }

    private static void addMenuItem(final List<CharSequence> labels,
                                    final List<Integer> itemIds,
                                    final Context context,
                                    final int labelResId,
                                    final int itemId) {
        labels.add(context.getString(labelResId));
        itemIds.add(itemId);
    }

    private static boolean handleMenuItem(final PlayQueue playQueue,
                                          final PlayQueueItem item,
                                          final int itemId,
                                          final FragmentManager fragmentManager,
                                          final Context context) {
        if (itemId == R.id.menu_item_remove) {
            final int index = playQueue.indexOf(item);
            playQueue.remove(index);
            return true;
        } else if (itemId == R.id.menu_item_details) {
            // playQueue is null since we don't want any queue change
            NavigationHelper.openVideoDetail(context, item.getServiceId(),
                    item.getUrl(), item.getTitle(), null,
                    false);
            return true;
        } else if (itemId == R.id.menu_item_append_playlist) {
            PlaylistDialog.createCorrespondingDialog(
                    context,
                    List.of(new StreamEntity(item)),
                    dialog -> dialog.show(
                            fragmentManager,
                            "QueueItemMenuUtil@append_playlist"
                    )
            );

            return true;
        } else if (itemId == R.id.menu_item_channel_details) {
            SparseItemUtil.fetchUploaderUrlIfSparse(context, item.getServiceId(),
                    item.getUrl(), item.getUploaderUrl(),
                    // An intent must be used here.
                    // Opening with FragmentManager transactions is not working,
                    // as PlayQueueActivity doesn't use fragments.
                    uploaderUrl -> NavigationHelper.openChannelFragmentUsingIntent(
                            context, item.getServiceId(), uploaderUrl, item.getUploader()
                    ));
            return true;
        } else if (itemId == R.id.menu_item_share) {
            shareText(context, item.getTitle(), item.getUrl(),
                    item.getThumbnails());
            return true;
        } else if (itemId == R.id.menu_item_download) {
            fetchStreamInfoAndSaveToDatabase(context, item.getServiceId(), item.getUrl(),
                    info -> {
                        final DownloadDialog downloadDialog = new DownloadDialog(context,
                                info);
                        downloadDialog.show(fragmentManager, "downloadDialog");
                    });
            return true;
        }
        return false;
    }

    private static Activity findActivity(final Context context) {
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

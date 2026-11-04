package org.schabi.newpipe.local.holder;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.local.LocalItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.rokid.RokidMode;
import org.schabi.newpipe.util.AccessibilityUtils;

import java.time.format.DateTimeFormatter;

public class RemoteBookmarkPlaylistItemHolder extends RemotePlaylistItemHolder {
    private final View itemHandleView;

    public RemoteBookmarkPlaylistItemHolder(final LocalItemBuilder infoItemBuilder,
                                            final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_playlist_bookmark_item, parent);
    }

    RemoteBookmarkPlaylistItemHolder(final LocalItemBuilder infoItemBuilder, final int layoutId,
                                     final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
        itemHandleView = itemView.findViewById(R.id.itemHandle);
    }

    @Override
    public void updateFromItem(final LocalItem localItem,
                               final HistoryRecordManager historyRecordManager,
                               final DateTimeFormatter dateTimeFormatter) {
        if (!(localItem instanceof PlaylistRemoteEntity)) {
            return;
        }
        final PlaylistRemoteEntity item = (PlaylistRemoteEntity) localItem;

        super.updateFromItem(localItem, historyRecordManager, dateTimeFormatter);

        itemHandleView.setOnClickListener(null);
        if (RokidMode.enabled()) {
            itemHandleView.setOnTouchListener(null);
            AccessibilityUtils.describeFocusableItem(itemHandleView,
                    item.getOrderingName(), itemView.getContext()
                            .getString(R.string.rokid_move_down));
            itemHandleView.setOnClickListener(view -> {
                if (itemBuilder.getOnItemSelectedListener() != null) {
                    itemBuilder.getOnItemSelectedListener().drag(item,
                            RemoteBookmarkPlaylistItemHolder.this);
                }
            });
        } else {
            itemHandleView.setOnTouchListener(getOnTouchListener(item));
        }
    }

    private View.OnTouchListener getOnTouchListener(final PlaylistRemoteEntity item) {
        return (view, motionEvent) -> {
            view.performClick();
            if (itemBuilder != null && itemBuilder.getOnItemSelectedListener() != null
                    && motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                itemBuilder.getOnItemSelectedListener().drag(item,
                        RemoteBookmarkPlaylistItemHolder.this);
            }
            return false;
        };
    }
}

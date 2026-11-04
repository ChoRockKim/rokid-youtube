package org.schabi.newpipe.player.playqueue;

import android.content.Context;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import org.schabi.newpipe.R;
import org.schabi.newpipe.rokid.RokidMode;
import org.schabi.newpipe.util.AccessibilityUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.image.CoilHelper;

public class PlayQueueItemBuilder {
    private static final String TAG = PlayQueueItemBuilder.class.toString();
    private OnSelectedListener onItemClickListener;

    public PlayQueueItemBuilder(final Context context) {
    }

    public void setOnSelectedListener(final OnSelectedListener listener) {
        this.onItemClickListener = listener;
    }

    public void buildStreamInfoItem(final PlayQueueItemHolder holder, final PlayQueueItem item) {
        if (!TextUtils.isEmpty(item.getTitle())) {
            holder.itemVideoTitleView.setText(item.getTitle());
        }
        holder.itemAdditionalDetailsView.setText(Localization.concatenateStrings(item.getUploader(),
                ServiceHelper.getNameOfServiceById(item.getServiceId())));

        if (item.getDuration() > 0) {
            holder.itemDurationView.setText(Localization.getDurationString(item.getDuration()));
            holder.itemDurationView.setVisibility(View.VISIBLE);
        } else {
            holder.itemDurationView.setText("");
            holder.itemDurationView.setVisibility(View.GONE);
        }

        CoilHelper.INSTANCE.loadThumbnail(holder.itemThumbnailView, item.getThumbnails());
        AccessibilityUtils.describeFocusableItem(holder.itemRoot, item.getTitle(),
                holder.itemAdditionalDetailsView.getText(), holder.itemDurationView.getText());
        holder.itemThumbnailView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        holder.itemRoot.setOnClickListener(view -> {
            if (onItemClickListener != null) {
                onItemClickListener.selected(item, view);
            }
        });

        holder.itemRoot.setOnLongClickListener(view -> {
            if (onItemClickListener != null) {
                onItemClickListener.held(item, view);
                return true;
            }
            return false;
        });

        holder.itemHandle.setOnClickListener(null);
        if (RokidMode.enabled()) {
            AccessibilityUtils.describeFocusableItem(holder.itemHandle, item.getTitle(),
                    holder.itemHandle.getContext().getString(R.string.rokid_move_down));
            holder.itemHandle.setClickable(true);
            holder.itemHandle.setOnTouchListener(null);
            holder.itemHandle.setOnClickListener(view -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onStartDrag(holder);
                }
            });
        } else {
            holder.itemHandle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            holder.itemHandle.setFocusable(false);
            holder.itemHandle.setOnTouchListener(getOnTouchListener(holder));
        }
    }

    private View.OnTouchListener getOnTouchListener(final PlayQueueItemHolder holder) {
        return (view, motionEvent) -> {
            view.performClick();
            if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN
                    && onItemClickListener != null) {
                onItemClickListener.onStartDrag(holder);
            }
            return false;
        };
    }

    public interface OnSelectedListener {
        void selected(PlayQueueItem item, View view);

        void held(PlayQueueItem item, View view);

        void onStartDrag(PlayQueueItemHolder viewHolder);
    }
}

package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.ui.Hud.dp

/** Vertical list of videos with its own "selected row" (no Android focus involved). */
class VideoList(context: Context) : RecyclerView(context) {
    private val adapterImpl = Adapter()
    var items: List<VideoItem> = emptyList()
        set(value) {
            field = value
            selected = -1
            adapterImpl.notifyDataSetChanged()
            scrollToPosition(0)
        }

    /** Index of the highlighted row, or -1 when the highlight is elsewhere on the screen. */
    var selected: Int = -1
        set(value) {
            val old = field
            field = value
            if (old >= 0) adapterImpl.notifyItemChanged(old)
            if (value >= 0) {
                adapterImpl.notifyItemChanged(value)
                (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(value, dp(4))
            }
        }

    /** Optional hook so tests can render thumbnails without network. */
    var thumbnailBinder: ((ImageView, VideoItem) -> Unit) = { view, item ->
        view.load(item.thumbnailUrl)
    }

    init {
        layoutManager = LinearLayoutManager(context)
        adapter = adapterImpl
        itemAnimator = null
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isFocusable = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
    }

    private inner class Adapter : RecyclerView.Adapter<RowHolder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = RowHolder(parent.context)
        override fun onBindViewHolder(holder: RowHolder, position: Int) =
            holder.bind(items[position], position == selected)
    }

    private inner class RowHolder(context: Context) : ViewHolder(LinearLayout(context)) {
        private val root = itemView as LinearLayout
        private val thumb = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.DKGRAY)
        }
        private val badge = Hud.text(context, 11f).apply {
            setBackgroundColor(Hud.SCRIM)
            setPadding(dp(4), dp(1), dp(4), dp(1))
        }
        private val title = Hud.text(context, 16f, bold = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.05f)
        }
        private val uploader = Hud.text(context, 13f, Hud.GREY).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        init {
            root.layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, dp(86)).apply {
                setMargins(dp(4), dp(2), dp(4), dp(2))
            }
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.setPadding(dp(8), dp(6), dp(8), dp(6))
            root.background = Hud.outline(context)

            val thumbFrame = FrameLayout(context)
            thumbFrame.addView(thumb, FrameLayout.LayoutParams(dp(124), dp(70)))
            thumbFrame.addView(
                badge,
                FrameLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END
                ).apply { setMargins(0, 0, dp(2), dp(2)) }
            )
            root.addView(thumbFrame, LinearLayout.LayoutParams(dp(124), dp(70)))

            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(uploader, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            }
            root.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        }

        fun bind(item: VideoItem, isSelected: Boolean) {
            root.isSelected = isSelected
            title.text = item.title
            // One meta line: a two-line title plus two meta lines does not fit the 86dp row.
            uploader.text = listOf(item.uploader, Hud.formatViews(item.viewCount))
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            badge.text = if (item.isLive) "LIVE" else Hud.formatDuration(item.durationSeconds)
            badge.setBackgroundColor(if (item.isLive) Color.parseColor("#CC2222") else Hud.SCRIM)
            badge.visibility = if (badge.text.isNullOrEmpty()) GONE else VISIBLE
            thumb.setImageDrawable(null)
            thumbnailBinder(thumb, item)
        }
    }
}

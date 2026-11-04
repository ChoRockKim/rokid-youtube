package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.ui.Hud.dp

/**
 * Grid of video cards, 3 per row, so two rows (6 videos) fit the 480x282 band at once. Has its
 * own "selected card" (no Android focus); swipe order is row by row, left to right.
 */
class VideoList(context: Context) : RecyclerView(context) {
    private val adapterImpl = Adapter()
    var items: List<VideoItem> = emptyList()
        set(value) {
            field = value
            selected = -1
            adapterImpl.notifyDataSetChanged()
            scrollToPosition(0)
        }

    /** Index of the highlighted card, or -1 when the highlight is elsewhere on the screen. */
    var selected: Int = -1
        set(value) {
            val old = field
            field = value
            if (old >= 0) adapterImpl.notifyItemChanged(old)
            if (value >= 0) {
                adapterImpl.notifyItemChanged(value)
                // Keep the selected card's row at the top so the row below stays visible too.
                val rowStart = value - value % COLUMNS
                (layoutManager as GridLayoutManager).scrollToPositionWithOffset(rowStart, 0)
            }
        }

    /** Optional hook so tests can render thumbnails without network. */
    var thumbnailBinder: ((ImageView, VideoItem) -> Unit) = { view, item ->
        view.load(item.thumbnailUrl)
    }

    init {
        layoutManager = GridLayoutManager(context, COLUMNS)
        adapter = adapterImpl
        itemAnimator = null
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isFocusable = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        setPadding(dp(3), 0, dp(3), 0)
        clipToPadding = false
    }

    private inner class Adapter : RecyclerView.Adapter<CardHolder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = CardHolder(parent.context)
        override fun onBindViewHolder(holder: CardHolder, position: Int) =
            holder.bind(items[position], position == selected)
    }

    private inner class CardHolder(context: Context) : ViewHolder(LinearLayout(context)) {
        private val root = itemView as LinearLayout
        private val thumb = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.DKGRAY)
        }
        private val badge = Hud.text(context, 10f).apply {
            setBackgroundColor(Hud.SCRIM)
            setPadding(dp(3), dp(1), dp(3), dp(1))
        }
        private val title = Hud.text(context, 13f, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val uploader = Hud.text(context, 11f, Hud.GREY).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        init {
            // 2 rows x (116 + 2) = 236dp: fits under the 44dp chip row of the 282dp band.
            root.layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, dp(CARD_HEIGHT_DP)).apply {
                setMargins(dp(2), dp(1), dp(2), dp(1))
            }
            root.orientation = LinearLayout.VERTICAL
            root.setPadding(dp(4), dp(4), dp(4), dp(4))
            root.background = Hud.outline(context)

            val thumbFrame = FrameLayout(context).apply {
                addView(thumb, FrameLayout.LayoutParams(-1, -1))
                addView(
                    badge,
                    FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                        setMargins(0, 0, dp(2), dp(2))
                    }
                )
            }
            root.addView(thumbFrame, LinearLayout.LayoutParams(-1, dp(68)))
            root.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            root.addView(uploader, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }

        fun bind(item: VideoItem, isSelected: Boolean) {
            root.isSelected = isSelected
            title.text = item.title
            uploader.text = item.uploader
            badge.text = if (item.isLive) "LIVE" else Hud.formatDuration(item.durationSeconds)
            badge.setBackgroundColor(if (item.isLive) Color.parseColor("#CC2222") else Hud.SCRIM)
            badge.visibility = if (badge.text.isNullOrEmpty()) GONE else VISIBLE
            thumb.setImageDrawable(null)
            thumbnailBinder(thumb, item)
        }
    }

    companion object {
        const val COLUMNS = 3
        const val CARD_HEIGHT_DP = 116
    }
}

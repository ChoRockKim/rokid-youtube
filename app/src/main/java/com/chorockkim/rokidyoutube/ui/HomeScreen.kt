package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.chorockkim.rokidyoutube.data.Category
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.ui.GlassesInput.Action
import com.chorockkim.rokidyoutube.ui.Hud.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Home: a row of chips ([검색] + YouTube categories) above a list of videos.
 * Swipe walks chips first, then list rows; tap opens the chip or plays the row.
 */
class HomeScreen(
    context: Context,
    private val navigator: Navigator,
    private val scope: CoroutineScope,
    categories: List<Category>,
    private val loader: suspend (Category) -> List<VideoItem>
) : Screen {
    override val view: LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val chipRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
    }
    private val chipScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(chipRow, FrameLayout.LayoutParams(-2, -1))
    }
    private val chips = mutableListOf<TextView>()
    private val chipActions = mutableListOf<() -> Unit>()

    val list = VideoList(context)
    private val status = Hud.text(context, 15f, Hud.GREY).apply { gravity = Gravity.CENTER }

    private var cursor = 0
    private var loadJob: Job? = null
    private var activeCategory: Int = -1

    init {
        addChip("검색") { navigator.openSearch() }
        categories.forEachIndexed { index, category ->
            addChip(category.label) { load(index, category) }
        }

        view.addView(chipScroll, LinearLayout.LayoutParams(-1, dp(44)))
        val body = FrameLayout(context).apply {
            addView(list, FrameLayout.LayoutParams(-1, -1))
            addView(status, FrameLayout.LayoutParams(-1, -1))
        }
        view.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        moveTo(0)

        categories.firstOrNull()?.let { load(0, it) }
    }

    private fun addChip(label: String, action: () -> Unit) {
        val chip = Hud.button(view.context, label).apply { minWidth = dp(64) }
        chipRow.addView(chip, LinearLayout.LayoutParams(-2, dp(34)).apply {
            setMargins(dp(3), 0, dp(3), 0)
        })
        chips += chip
        chipActions += action
    }

    private fun load(categoryIndex: Int, category: Category) {
        activeCategory = categoryIndex
        chips.forEachIndexed { i, chip -> chip.isActivated = i == categoryIndex + 1 }
        showStatus("불러오는 중…")
        list.items = emptyList()
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                showItems(loader(category))
            } catch (e: Exception) {
                showStatus("불러오지 못했어요\n${e.javaClass.simpleName}")
            }
        }
    }

    fun showItems(items: List<VideoItem>) {
        list.items = items
        showStatus(if (items.isEmpty()) "영상이 없어요" else null)
        if (cursor >= chips.size) moveTo(chips.size.coerceAtMost(lastIndex()))
    }

    private fun showStatus(text: String?) {
        status.text = text
        status.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun lastIndex() = chips.size + list.items.size - 1

    private fun moveTo(index: Int) {
        cursor = index.coerceIn(0, lastIndex())
        chips.forEachIndexed { i, chip -> chip.isSelected = i == cursor }
        list.selected = if (cursor >= chips.size) cursor - chips.size else -1
        if (cursor < chips.size) {
            val chip = chips[cursor]
            chipScroll.post {
                chipScroll.smoothScrollTo((chip.left - dp(40)).coerceAtLeast(0), 0)
            }
        }
    }

    override fun onAction(action: Action): Boolean = when (action) {
        Action.NEXT -> { moveTo(cursor + 1); true }
        Action.PREVIOUS -> { moveTo(cursor - 1); true }
        Action.SELECT -> {
            if (cursor < chips.size) {
                chipActions[cursor]()
            } else {
                list.items.getOrNull(cursor - chips.size)?.let { navigator.openPlayer(it.url, it.title) }
            }
            true
        }
        Action.BACK -> if (cursor != 0) { moveTo(0); true } else false
        else -> false
    }

    override fun onDestroy() {
        loadJob?.cancel()
    }
}

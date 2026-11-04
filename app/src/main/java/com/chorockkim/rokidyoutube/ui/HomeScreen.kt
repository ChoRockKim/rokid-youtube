package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.data.YouTubeAccountApi
import com.chorockkim.rokidyoutube.ui.GlassesInput.Action
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Home: a row of chips ([검색] + personal tabs + account) above a 3-column video grid.
 * Swipe walks chips first, then the cards; tap opens the chip or plays the card.
 */
class HomeScreen(
    context: Context,
    private val navigator: Navigator,
    private val scope: CoroutineScope,
    private val tabs: List<Tab>,
    accountLabel: () -> String
) : Screen {
    /** One personal feed. [reloadOnReturn]: cheap local feeds refresh after watching something. */
    class Tab(
        val label: String,
        val emptyText: String,
        val reloadOnReturn: Boolean = false,
        val load: suspend () -> List<VideoItem>
    )

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
    private val accountChip: TextView
    private val accountLabel = accountLabel

    val list = VideoList(context)
    private val status = Hud.text(context, 14f, Hud.GREY).apply {
        gravity = Gravity.CENTER
        setPadding(dp(24), 0, dp(24), 0)
    }

    private var cursor = 0
    private var loadJob: Job? = null
    private var activeTab = -1
    private var shownOnce = false

    init {
        addChip("검색") { navigator.openSearch() }
        tabs.forEachIndexed { index, _ -> addChip(tabs[index].label) { load(index) } }
        accountChip = addChip(accountLabel()) { navigator.openAccount() }

        view.addView(chipScroll, LinearLayout.LayoutParams(-1, dp(44)))
        val body = FrameLayout(context).apply {
            addView(list, FrameLayout.LayoutParams(-1, -1))
            addView(status, FrameLayout.LayoutParams(-1, -1))
        }
        view.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        moveTo(0)
        if (tabs.isNotEmpty()) load(0)
    }

    private fun addChip(label: String, action: () -> Unit): TextView {
        val chip = Hud.button(view.context, label).apply { minWidth = dp(56) }
        chipRow.addView(chip, LinearLayout.LayoutParams(-2, dp(34)).apply {
            setMargins(dp(3), 0, dp(3), 0)
        })
        chips += chip
        chipActions += action
        return chip
    }

    /** Reloads the current tab, e.g. after signing in or out. */
    fun reload() {
        if (activeTab >= 0) load(activeTab)
    }

    private fun load(tabIndex: Int) {
        activeTab = tabIndex
        chips.forEachIndexed { i, chip -> chip.isActivated = i == tabIndex + 1 }
        showStatus("불러오는 중…")
        list.items = emptyList()
        if (cursor > lastIndex()) moveTo(lastIndex())
        loadJob?.cancel()
        val tab = tabs[tabIndex]
        loadJob = scope.launch {
            try {
                showItems(tab.load(), tab.emptyText)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showStatus(errorText(e))
            }
        }
    }

    fun showItems(items: List<VideoItem>, emptyText: String = "영상이 없어요") {
        list.items = items
        showStatus(if (items.isEmpty()) emptyText else null)
        moveTo(cursor)
    }

    private fun errorText(e: Exception) = when (e) {
        is YouTubeAccountApi.NotSignedInException ->
            "구글 계정을 연결하면 볼 수 있어요\n오른쪽 끝 [로그인]을 눌러 주세요"
        is YouTubeAccountApi.QuotaException ->
            "오늘 YouTube API 사용량을 다 썼어요\n내일(태평양 시간 자정) 다시 돼요"
        else -> "불러오지 못했어요\n${e.javaClass.simpleName}"
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
            chipScroll.post { chipScroll.smoothScrollTo((chip.left - dp(40)).coerceAtLeast(0), 0) }
        }
    }

    override fun onShown() {
        accountChip.text = accountLabel()
        // Coming back from the player or the account screen: refresh local feeds / account state.
        if (shownOnce && activeTab >= 0 && tabs[activeTab].reloadOnReturn) load(activeTab)
        shownOnce = true
    }

    override fun onAction(action: Action): Boolean = when (action) {
        Action.NEXT -> { moveTo(cursor + 1); true }
        Action.PREVIOUS -> { moveTo(cursor - 1); true }
        Action.SELECT -> {
            if (cursor < chips.size) {
                chipActions[cursor]()
            } else {
                list.items.getOrNull(cursor - chips.size)?.let { navigator.openPlayer(it) }
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

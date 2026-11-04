package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.ui.GlassesInput.Action
import com.chorockkim.rokidyoutube.ui.Hud.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Search: the query bar on top, then either the glasses keyboard (typing) or the results list.
 * In results mode the query bar is the first item; selecting it goes back to the keyboard.
 */
class SearchScreen(
    context: Context,
    private val navigator: Navigator,
    private val scope: CoroutineScope,
    private val searcher: suspend (String) -> List<VideoItem>
) : Screen {
    override val view: LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val queryBar = Hud.text(context, 16f).apply {
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.START
        background = Hud.outline(context)
        setPadding(dp(12), 0, dp(12), 0)
    }
    val keyboard = GlassesKeyboard(context)
    val results = VideoList(context)
    private val status = Hud.text(context, 15f, Hud.GREY).apply { gravity = Gravity.CENTER }
    private val body = FrameLayout(context)

    private var typing = true
    /** In results mode: 0 = query bar, 1.. = result rows. */
    private var cursor = 0
    private var job: Job? = null

    init {
        view.addView(queryBar, LinearLayout.LayoutParams(-1, dp(36)).apply {
            setMargins(dp(8), dp(4), dp(8), dp(2))
        })
        body.addView(keyboard, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        body.addView(results, FrameLayout.LayoutParams(-1, -1))
        body.addView(status, FrameLayout.LayoutParams(-1, -1))
        view.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        keyboard.onTextChanged = ::renderQuery
        keyboard.onSearch = ::search
        keyboard.onVoice = {
            queryBar.hint = "말씀하세요…"
            navigator.startVoiceSearch { spoken ->
                keyboard.setText(spoken)
                search(spoken)
            }
        }
        renderQuery("")
        showKeyboard()
    }

    private fun renderQuery(text: String) {
        queryBar.text = when {
            text.isEmpty() -> ""
            typing -> "$text▏"
            else -> text
        }
        if (text.isEmpty()) queryBar.hint = "검색어 입력 · 한국어는 음성 키"
        queryBar.setHintTextColor(Hud.GREY)
    }

    fun showKeyboard() {
        typing = true
        keyboard.visibility = View.VISIBLE
        results.visibility = View.GONE
        status.visibility = View.GONE
        queryBar.isSelected = false
        keyboard.selected = keyboard.selected.coerceAtLeast(0)
        renderQuery(keyboard.text)
    }

    fun search(query: String) {
        typing = false
        renderQuery(query)
        keyboard.visibility = View.GONE
        results.visibility = View.VISIBLE
        results.items = emptyList()
        setStatus("검색 중…")
        moveTo(0)
        job?.cancel()
        job = scope.launch {
            try {
                showResults(searcher(query))
            } catch (e: Exception) {
                setStatus("검색하지 못했어요\n${e.javaClass.simpleName}")
            }
        }
    }

    fun showResults(items: List<VideoItem>) {
        results.items = items
        setStatus(if (items.isEmpty()) "결과가 없어요" else null)
        moveTo(if (items.isEmpty()) 0 else 1)
    }

    private fun setStatus(text: String?) {
        status.text = text
        status.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun moveTo(index: Int) {
        cursor = index.coerceIn(0, results.items.size)
        queryBar.isSelected = cursor == 0
        results.selected = cursor - 1
    }

    override fun onAction(action: Action): Boolean {
        if (typing) {
            return when (action) {
                Action.NEXT -> { keyboard.selected = (keyboard.selected + 1) % keyboard.keyCount; true }
                Action.PREVIOUS -> {
                    keyboard.selected = (keyboard.selected - 1 + keyboard.keyCount) % keyboard.keyCount
                    true
                }
                Action.SELECT -> { keyboard.press(); true }
                else -> false
            }
        }
        return when (action) {
            Action.NEXT -> { moveTo(cursor + 1); true }
            Action.PREVIOUS -> { moveTo(cursor - 1); true }
            Action.SELECT -> {
                if (cursor == 0) {
                    showKeyboard()
                } else {
                    results.items.getOrNull(cursor - 1)?.let { navigator.openPlayer(it) }
                }
                true
            }
            Action.BACK -> { showKeyboard(); true }
            else -> false
        }
    }

    override fun onDestroy() {
        job?.cancel()
    }
}

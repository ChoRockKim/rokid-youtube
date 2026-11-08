package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.view.Gravity
import android.widget.GridLayout
import android.widget.TextView
import com.chorockkim.rokidyoutube.ui.Hud.dp

/**
 * One-axis on-screen keyboard (idea from RokidPipe's RokidKeyboardController): swipe walks the
 * keys in order, tap presses the highlighted key. Pages: 한글 (jamo, composed by [Hangul]),
 * abc, 123; the toggle key shows the next page. The voice key is there too.
 */
class GlassesKeyboard(context: Context) : GridLayout(context) {
    sealed interface Key {
        val label: String

        data class Char(val value: String) : Key {
            override val label get() = value
        }

        data object Space : Key { override val label = "␣" }
        data object Delete : Key { override val label = "⌫" }
        data object Clear : Key { override val label = "지움" }
        data object Toggle : Key { override val label = "123" }
        data object Voice : Key { override val label = "음성" }
        data object Search : Key { override val label = "검색" }
    }

    var text: String = ""
        private set
    var onTextChanged: (String) -> Unit = {}
    var onSearch: (String) -> Unit = {}
    var onVoice: () -> Unit = {}

    enum class Page(val nextLabel: String) { KOREAN("abc"), LETTERS("123"), SYMBOLS("한") }

    var page = Page.KOREAN
        private set
    private var keys: List<Key> = keysFor(page)
    private val keyViews = mutableListOf<TextView>()

    /** Highlighted key, or -1 when the highlight is outside the keyboard. */
    var selected: Int = 0
        set(value) {
            field = value
            keyViews.forEachIndexed { i, v -> v.isSelected = i == value }
        }

    val keyCount get() = keys.size

    init {
        columnCount = COLUMNS
        setPadding(dp(6), dp(4), dp(6), 0)
        rebuild()
    }

    fun setText(value: String) {
        text = value
        onTextChanged(text)
    }

    fun press() {
        when (val key = keys.getOrNull(selected) ?: return) {
            is Key.Char -> setText(Hangul.append(text, key.value))
            Key.Space -> if (text.isNotEmpty() && !text.endsWith(" ")) setText("$text ")
            Key.Delete -> setText(Hangul.backspace(text))
            Key.Clear -> setText("")
            Key.Toggle -> showPage(Page.entries[(page.ordinal + 1) % Page.entries.size])
            Key.Voice -> onVoice()
            Key.Search -> if (text.isNotBlank()) onSearch(text.trim())
        }
    }

    fun showPage(p: Page) {
        page = p
        keys = keysFor(p)
        rebuild()
        selected = keys.indexOf(Key.Toggle)
    }

    private fun rebuild() {
        removeAllViews()
        keyViews.clear()
        keys.forEach { key ->
            // The toggle key names the next page.
            val label = if (key == Key.Toggle) page.nextLabel else key.label
            val view = Hud.button(context, label, 15f).apply {
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                if (key == Key.Search) isActivated = true
            }
            val params = LayoutParams(spec(UNDEFINED, 1f), spec(UNDEFINED, 1f)).apply {
                width = 0
                height = dp(if (page == Page.KOREAN) 40 else 42)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            addView(view, params)
            keyViews += view
        }
        selected = selected.coerceIn(0, keys.size - 1)
    }

    companion object {
        const val COLUMNS = 11

        fun keysFor(page: Page): List<Key> = when (page) {
            Page.KOREAN -> koreanKeys()
            Page.LETTERS -> letterKeys()
            Page.SYMBOLS -> symbolKeys()
        }

        /** 19 consonants, 14 vowels (ㅘ, ㅢ, … by combining, see [Hangul]), then the function keys. */
        fun koreanKeys(): List<Key> =
            (Hangul.consonants + Hangul.vowels).map { Key.Char(it) } +
                listOf(Key.Space, Key.Delete, Key.Clear, Key.Toggle, Key.Voice, Key.Search)

        fun letterKeys(): List<Key> =
            ('a'..'z').map { Key.Char(it.toString()) } +
                listOf(Key.Space, Key.Delete, Key.Clear, Key.Toggle, Key.Voice, Key.Search)

        fun symbolKeys(): List<Key> =
            ('0'..'9').map { Key.Char(it.toString()) } +
                listOf(".", "-", "'", "&", "!", "?", "#", "+", ":").map { Key.Char(it) } +
                listOf(Key.Space, Key.Delete, Key.Clear, Key.Toggle, Key.Voice, Key.Search)
    }
}

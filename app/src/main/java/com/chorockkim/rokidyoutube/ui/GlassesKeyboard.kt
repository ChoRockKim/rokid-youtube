package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.view.Gravity
import android.widget.GridLayout
import android.widget.TextView
import com.chorockkim.rokidyoutube.ui.Hud.dp

/**
 * One-axis on-screen keyboard (idea from RokidPipe's RokidKeyboardController): swipe walks the
 * keys in order, tap presses the highlighted key. Korean is best entered with the voice key.
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

    private var symbols = false
    private var keys: List<Key> = letterKeys()
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
            is Key.Char -> setText(text + key.value)
            Key.Space -> if (text.isNotEmpty() && !text.endsWith(" ")) setText("$text ")
            Key.Delete -> setText(text.dropLast(1))
            Key.Clear -> setText("")
            Key.Toggle -> {
                symbols = !symbols
                keys = if (symbols) symbolKeys() else letterKeys()
                rebuild()
                selected = keys.indexOf(Key.Toggle)
            }
            Key.Voice -> onVoice()
            Key.Search -> if (text.isNotBlank()) onSearch(text.trim())
        }
    }

    private fun rebuild() {
        removeAllViews()
        keyViews.clear()
        keys.forEach { key ->
            // The toggle key reads "abc" on the symbol page.
            val label = if (key == Key.Toggle && symbols) "abc" else key.label
            val view = Hud.button(context, label, 15f).apply {
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                if (key == Key.Search) isActivated = true
            }
            val params = LayoutParams(spec(UNDEFINED, 1f), spec(UNDEFINED, 1f)).apply {
                width = 0
                height = dp(42)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            addView(view, params)
            keyViews += view
        }
        selected = selected.coerceIn(0, keys.size - 1)
    }

    companion object {
        const val COLUMNS = 11

        fun letterKeys(): List<Key> =
            ('a'..'z').map { Key.Char(it.toString()) } +
                listOf(Key.Space, Key.Delete, Key.Clear, Key.Toggle, Key.Voice, Key.Search)

        fun symbolKeys(): List<Key> =
            ('0'..'9').map { Key.Char(it.toString()) } +
                listOf(".", "-", "'", "&", "!", "?", "#", "+", ":").map { Key.Char(it) } +
                listOf(Key.Space, Key.Delete, Key.Clear, Key.Toggle, Key.Voice, Key.Search)
    }
}

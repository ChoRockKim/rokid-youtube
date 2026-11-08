package com.chorockkim.rokidyoutube.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HangulTest {
    private fun type(vararg keys: String, start: String = "") = keys.fold(start, Hangul::append)
    private fun keys(s: String) = s.map { it.toString() }.toTypedArray()

    @Test
    fun composesSyllables() {
        assertEquals("간", type(*keys("ㄱㅏㄴ")))
        assertEquals("한국어", type(*keys("ㅎㅏㄴㄱㅜㄱㅇㅓ")))
        assertEquals("유튜브", type(*keys("ㅇㅠㅌㅠㅂㅡ")))
        assertEquals("백종원", type(*keys("ㅂㅐㄱㅈㅗㅇㅇㅜㅓㄴ")))
    }

    @Test
    fun finalConsonantMovesToTheNextSyllable() {
        assertEquals("가라", type(*keys("ㄱㅏㄹㅏ")))
        assertEquals("닭이", type(*keys("ㄷㅏㄹㄱㅇㅣ")))
        assertEquals("달기", type(*keys("ㄷㅏㄹㄱㅣ")))
    }

    @Test
    fun compoundVowelsAndFinals() {
        assertEquals("과", type(*keys("ㄱㅗㅏ")))
        assertEquals("의", type(*keys("ㅇㅡㅣ")))
        assertEquals("값", type(*keys("ㄱㅏㅂㅅ")))
        assertEquals("앉", type(*keys("ㅇㅏㄴㅈ")))
        assertEquals("ㅘ", type(*keys("ㅗㅏ")))
    }

    @Test
    fun doubleConsonantsCannotBeFinalsWhereKoreanForbidsIt() {
        assertEquals("가ㄸ", type(*keys("ㄱㅏㄸ")))
        assertEquals("까", type(*keys("ㄲㅏ")))
        assertEquals("갔다", type(*keys("ㄱㅏㅆㄷㅏ")))
    }

    @Test
    fun mixesWithLatinAndSpaces() {
        assertEquals("lck 하이라이트", type("l", "c", "k", " ", *keys("ㅎㅏㅇㅣㄹㅏㅇㅣㅌㅡ")))
        assertEquals("a가", type("a", "ㄱ", "ㅏ"))
    }

    @Test
    fun backspaceRemovesOneJamo() {
        var t = "닭"
        val steps = mutableListOf<String>()
        while (t.isNotEmpty()) { t = Hangul.backspace(t); steps += t }
        assertEquals(listOf("달", "다", "ㄷ", ""), steps)
        assertEquals("고", Hangul.backspace("과"))
        assertEquals("ab", Hangul.backspace("abc"))
        assertEquals("", Hangul.backspace(""))
    }
}

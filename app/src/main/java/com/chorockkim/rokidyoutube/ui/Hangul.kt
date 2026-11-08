package com.chorockkim.rokidyoutube.ui

/**
 * 두벌식 composition for the glasses keyboard: keys are single compatibility jamo (ㄱ, ㅏ, …) and
 * [append] / [backspace] work on the end of the text, the way a phone keyboard composes:
 * ㄱ ㅏ ㄴ -> 간, 간 + ㅏ -> 가나, ㅗ + ㅏ -> ㅘ, ㄹ + ㄱ (받침) -> ㄺ, and backspace removes one
 * jamo at a time (갉 -> 갈 -> 가 -> ㄱ).
 */
object Hangul {
    private const val BASE = 0xAC00
    private const val LAST = 0xD7A3

    /** Initial consonants in Unicode order. */
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    /** Final consonants; index 0 = none. */
    private const val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    private val vowelPairs = mapOf(
        "ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ', "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ'
    )
    private val finalPairs = mapOf(
        "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ',
        "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ'
    )
    private val vowelSplit = vowelPairs.entries.associate { (k, v) -> v to k }
    private val finalSplit = finalPairs.entries.associate { (k, v) -> v to k }

    /** The keys of the Korean keyboard page. */
    val consonants = listOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ", "ㄲ", "ㄸ", "ㅃ", "ㅆ", "ㅉ")
    val vowels = listOf("ㅏ", "ㅑ", "ㅓ", "ㅕ", "ㅗ", "ㅛ", "ㅜ", "ㅠ", "ㅡ", "ㅣ", "ㅐ", "ㅔ", "ㅒ", "ㅖ")

    fun isJamo(c: Char) = c in CHO || c in JUNG || c in JONG.substring(1)
    private fun isVowel(c: Char) = c in JUNG
    private fun isSyllable(c: Char) = c.code in BASE..LAST

    private data class Syllable(val cho: Int, val jung: Int, val jong: Int) {
        fun char() = (BASE + (cho * 21 + jung) * 28 + jong).toChar()
    }

    private fun split(c: Char): Syllable {
        val i = c.code - BASE
        return Syllable(i / (21 * 28), i % (21 * 28) / 28, i % 28)
    }

    /** Types the jamo [key] at the end of [text]. Non-jamo keys are just appended. */
    fun append(text: String, key: String): String {
        val j = key.singleOrNull() ?: return text + key
        if (!isJamo(j)) return text + key
        val last = text.lastOrNull() ?: return text + j
        val head = text.dropLast(1)

        if (!isVowel(j)) {
            if (isSyllable(last)) {
                val s = split(last)
                if (s.jong == 0) {
                    val jong = JONG.indexOf(j)
                    if (jong > 0) return head + s.copy(jong = jong).char()
                } else {
                    val pair = finalPairs["${JONG[s.jong]}$j"]
                    if (pair != null) return head + s.copy(jong = JONG.indexOf(pair)).char()
                }
            }
            return text + j
        }

        // A vowel.
        if (isSyllable(last)) {
            val s = split(last)
            if (s.jong == 0) {
                val pair = vowelPairs["${JUNG[s.jung]}$j"]
                if (pair != null) return head + s.copy(jung = JUNG.indexOf(pair)).char()
                return text + j
            }
            // The final consonant moves to the new syllable (갈 + ㅏ -> 가라, 갉 + ㅏ -> 갈가).
            val final = JONG[s.jong]
            val (keep, move) = finalSplit[final]?.let { it[0] to it[1] } ?: (null to final)
            val cho = CHO.indexOf(move)
            if (cho < 0) return text + j
            val stays = s.copy(jong = keep?.let { JONG.indexOf(it) } ?: 0).char()
            return head + stays + Syllable(cho, JUNG.indexOf(j), 0).char()
        }
        val cho = CHO.indexOf(last)
        if (cho >= 0) return head + Syllable(cho, JUNG.indexOf(j), 0).char()
        if (isVowel(last)) vowelPairs["$last$j"]?.let { return head + it }
        return text + j
    }

    /** Removes one jamo (or one character) from the end of [text]. */
    fun backspace(text: String): String {
        val last = text.lastOrNull() ?: return text
        val head = text.dropLast(1)
        if (isSyllable(last)) {
            val s = split(last)
            return when {
                s.jong != 0 -> head + s.copy(jong = finalSplit[JONG[s.jong]]?.let { JONG.indexOf(it[0]) } ?: 0).char()
                vowelSplit[JUNG[s.jung]] != null -> head + s.copy(jung = JUNG.indexOf(vowelSplit.getValue(JUNG[s.jung])[0])).char()
                else -> head + CHO[s.cho]
            }
        }
        vowelSplit[last]?.let { return head + it[0] }
        finalSplit[last]?.let { return head + it[0] }
        return head
    }
}

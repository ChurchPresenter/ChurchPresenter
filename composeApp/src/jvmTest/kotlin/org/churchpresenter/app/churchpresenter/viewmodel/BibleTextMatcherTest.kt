package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.BibleVerse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Reverse-lookup matcher. Thresholds (Conservative ≥6, Balanced ≥5, Aggressive ≥4 contiguous words)
 * were tuned on real services where reading produces runs of 11-23 and commentary rarely exceeds 5.
 */
class BibleTextMatcherTest {

    private val verses = listOf(
        BibleVerse("", 20, 30, 5, "Всякое слово Бога чисто; Он - щит уповающим на Него."),
        BibleVerse("", 1, 1, 1, "В начале сотворил Бог небо и землю."),
        BibleVerse("", 43, 3, 16, "Ибо так возлюбил Бог мир, что отдал Сына Своего Единородного."),
    )
    private val matcher = BibleTextMatcher(verses)

    @Test fun matches_verbatim_reading_conservative() {
        val m = matcher.match(
            "итак давайте прочитаем всякое слово бога чисто он щит уповающим на него",
            TextMatchLevel.CONSERVATIVE,
        )
        assertNotNull(m)
        assertEquals(20, m.book); assertEquals(30, m.chapter); assertEquals(5, m.verse)
    }

    @Test fun commentary_does_not_match_conservative() {
        assertNull(matcher.match("бог дал нам спасение и любовь сегодня здесь", TextMatchLevel.CONSERVATIVE))
    }

    @Test fun off_never_matches() {
        assertNull(matcher.match("в начале сотворил бог небо и землю", TextMatchLevel.OFF))
    }

    @Test fun balanced_catches_a_five_word_run() {
        val m = matcher.match("мы читаем в начале сотворил бог небо и землю аминь", TextMatchLevel.BALANCED)
        assertNotNull(m)
        assertEquals(1, m.book); assertEquals(1, m.chapter); assertEquals(1, m.verse)
    }
}

package engine

import engine.detection.NumberWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NumberWordsTest {

    @Test fun `plain digits`() {
        assertEquals(28, NumberWords.parseToken("28"))
        assertEquals(5, NumberWords.parseToken("5"))
        assertEquals(150, NumberWords.parseToken("150"))
    }

    @Test fun `digit ordinals`() {
        assertEquals(3, NumberWords.parseToken("3-я"))
        assertEquals(21, NumberWords.parseToken("21-й"))
        assertEquals(19, NumberWords.parseToken("19-го"))
        assertEquals(1, NumberWords.parseToken("1-е"))
    }

    @Test fun `russian ordinal words`() {
        assertEquals(3, NumberWords.parseToken("третья"))
        assertEquals(5, NumberWords.parseToken("пятый"))
        assertEquals(10, NumberWords.parseToken("Десятая"))
        assertEquals(4, NumberWords.parseToken("Четвертый"))
        assertEquals(17, NumberWords.parseToken("Семнадцатый"))
        assertEquals(9, NumberWords.parseToken("девятый"))
        assertEquals(10, NumberWords.parseToken("десятой"))
    }

    @Test fun `russian cardinal words`() {
        assertEquals(6, NumberWords.parseToken("шесть"))
        assertEquals(20, NumberWords.parseToken("двадцать"))
        assertEquals(11, NumberWords.parseToken("одиннадцать"))
        assertEquals(90, NumberWords.parseToken("девяносто"))
    }

    @Test fun `look-alikes are not numbers`() {
        assertNull(NumberWords.parseToken("столько"))
        assertNull(NumberWords.parseToken("сторона"))
        assertNull(NumberWords.parseToken("дважды"))
        assertNull(NumberWords.parseToken("глава"))
        assertNull(NumberWords.parseToken("стих"))
        assertNull(NumberWords.parseToken("слово"))
    }

    @Test fun `compound sequences`() {
        // "двадцать первый" = 21
        assertEquals(21 to 2, NumberWords.parseSequence(listOf("двадцать", "первый"), 0))
        // "сто пятый" = 105
        assertEquals(105 to 2, NumberWords.parseSequence(listOf("сто", "пятый"), 0))
        // "сто пятьдесят" = 150
        assertEquals(150 to 2, NumberWords.parseSequence(listOf("сто", "пятьдесят"), 0))
    }

    @Test fun `two equal-or-rising words do not combine`() {
        // "третий четвёртый" are two separate verses, not 7
        assertEquals(3 to 1, NumberWords.parseSequence(listOf("третий", "четвёртый"), 0))
    }

    @Test fun `digit tokens stand alone`() {
        assertEquals(37 to 1, NumberWords.parseSequence(listOf("37", "38"), 0))
    }

    @Test fun `english spelled out`() {
        assertEquals(20, NumberWords.parseToken("twenty"))
        assertEquals(16, NumberWords.parseToken("sixteen"))
        assertEquals(5, NumberWords.parseToken("fifth"))
    }
}

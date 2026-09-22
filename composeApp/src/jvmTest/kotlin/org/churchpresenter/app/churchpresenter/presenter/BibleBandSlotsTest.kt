package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.BibleTranslationSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/** [verses] laid across a template's `TextN`/`ReferenceN` slots, from one to four of them. */
class BibleBandSlotsTest {

    private companion object {
        const val TEXT_1 = BibleLottieTemplate.LAYER_TEXT_1
        const val TEXT_2 = BibleLottieTemplate.LAYER_TEXT_2
        const val TEXT_3 = BibleLottieTemplate.LAYER_TEXT_3
        const val TEXT_4 = BibleLottieTemplate.LAYER_TEXT_4
        const val REFERENCE_1 = BibleLottieTemplate.LAYER_REFERENCE_1
        const val REFERENCE_2 = BibleLottieTemplate.LAYER_REFERENCE_2
        const val REFERENCE_3 = BibleLottieTemplate.LAYER_REFERENCE_3
        const val REFERENCE_4 = BibleLottieTemplate.LAYER_REFERENCE_4
    }

    private fun verse(text: String, abbreviation: String, chapter: Int = 3, number: Int = 16) = SelectedVerse(
        bibleAbbreviation = abbreviation,
        bookName = "John",
        chapter = chapter,
        verseNumber = number,
        verseText = text,
    )

    private val kjv = verse("For God so loved the world", "KJV")
    private val rst = verse("Ибо так возлюбил Бог мир", "RST")
    private val lsg = verse("Car Dieu a tant aimé le monde", "LSG")
    private val asv = verse("For God so loved the world that he gave", "ASV")

    private val t0 = BibleTranslationSettings(showAbbreviation = true)
    private val t1 = BibleTranslationSettings(showAbbreviation = true)
    private val t2 = BibleTranslationSettings(showAbbreviation = true)
    private val t3 = BibleTranslationSettings(showAbbreviation = true)

    private fun Map<String, BandSlotText>.text(name: String) = getValue(name).text

    @Test
    fun `a two-slot template gets the first two verses, one each`() {
        val slots = bibleBandSlots(
            listOf(kjv, rst, lsg, asv),
            listOf(t0, t1, t2, t3),
            isKey = false,
            availableSlots = 2,
        )
        assertEquals("For God so loved the world", slots.text(TEXT_1))
        assertEquals("Ибо так возлюбил Бог мир", slots.text(TEXT_2))
    }

    @Test
    fun `a four-slot template gets all four verses, one each`() {
        val slots = bibleBandSlots(
            listOf(kjv, rst, lsg, asv),
            listOf(t0, t1, t2, t3),
            isKey = false,
            availableSlots = 4,
        )
        assertEquals("For God so loved the world", slots.text(TEXT_1))
        assertEquals("Ибо так возлюбил Бог мир", slots.text(TEXT_2))
        assertEquals("Car Dieu a tant aimé le monde", slots.text(TEXT_3))
        assertEquals("For God so loved the world that he gave", slots.text(TEXT_4))
        assertEquals("KJV John 3:16", slots.text(REFERENCE_1))
        assertEquals("RST John 3:16", slots.text(REFERENCE_2))
        assertEquals("LSG John 3:16", slots.text(REFERENCE_3))
        assertEquals("ASV John 3:16", slots.text(REFERENCE_4))
    }

    @Test
    fun `four verses on a two-slot template show only the first two, not crammed`() {
        val slots = bibleBandSlots(
            listOf(kjv, rst, lsg, asv),
            listOf(t0, t1, t2, t3),
            isKey = false,
            availableSlots = 2,
        )
        // Neither the third nor the fourth verse's own text appears anywhere -- not in a slot of its
        // own (there isn't one) and not folded into another slot's text either.
        assertEquals("For God so loved the world", slots.text(TEXT_1))
        assertEquals("Ибо так возлюбил Бог мир", slots.text(TEXT_2))
    }

    @Test
    fun `a one-slot template stacks every verse in it, references on one line`() {
        val slots = bibleBandSlots(listOf(kjv, rst, lsg), listOf(t0, t1, t2), isKey = false, availableSlots = 1)
        assertEquals(
            "For God so loved the world\nИбо так возлюбил Бог мир\nCar Dieu a tant aimé le monde",
            slots.text(TEXT_1),
        )
        assertEquals("KJV John 3:16  ·  RST John 3:16  ·  LSG John 3:16", slots.text(REFERENCE_1))
    }

    @Test
    fun `a single verse never needs a second slot, whatever the template offers`() {
        val slots = bibleBandSlots(listOf(kjv), listOf(t0, t1), isKey = false, availableSlots = 2)
        assertEquals("For God so loved the world", slots.text(TEXT_1))
        assertEquals("KJV John 3:16", slots.text(REFERENCE_1))
    }

    @Test
    fun `an occupied slot past the verses on hand is left empty`() {
        val slots = bibleBandSlots(listOf(kjv, rst), listOf(t0, t1, t2, t3), isKey = false, availableSlots = 4)
        assertEquals("", slots.text(TEXT_3))
        assertEquals("", slots.text(TEXT_4))
        assertEquals("", slots.text(REFERENCE_3))
        assertEquals("", slots.text(REFERENCE_4))
    }

    @Test
    fun `each slot draws in its own translation's typography`() {
        val styled = BibleTranslationSettings(lowerThirdTextFontSize = 55, lowerThirdTextColor = "#FF0000")
        val slots = bibleBandSlots(listOf(kjv, rst), listOf(t0, styled), isKey = false, availableSlots = 2)
        assertEquals(55, slots.getValue(TEXT_2).style.fontSizePt)
    }
}

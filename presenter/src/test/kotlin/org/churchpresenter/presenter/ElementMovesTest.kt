package org.churchpresenter.presenter

import org.churchpresenter.settings.BibleTranslationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElementMovesTest {

    @Test
    fun `a song move is kept per element, per output, and per language where there is one`() {
        assertEquals("LYRICS#1", songShiftKey(SongStyleElement.LYRICS, lowerThird = false, language = 1))
        assertEquals("LYRICS@LT", songShiftKey(SongStyleElement.LYRICS, lowerThird = true))
        // The number is the same digits in every language, so it has no language's own move.
        assertEquals("NUMBER", songShiftKey(SongStyleElement.NUMBER, lowerThird = false, language = 1))
        // The title slide's title is its own, so moving it leaves the title above every verse.
        assertEquals(
            "TITLE_SLIDE_TITLE#0",
            songShiftKey(SongStyleElement.TITLE, lowerThird = false, language = 0, titleSlide = true),
        )
    }

    @Test
    fun `a reference is moved on its own, per output, and taken back with the block's move`() {
        val moved = BibleTranslationSettings(fileName = "kjv.spb", shiftX = 5)
            .withReferenceShift(lowerThird = false, x = 81, y = 250)
        assertEquals(81 to 250, moved.referenceShiftFor(lowerThird = false))
        assertEquals(0 to 0, moved.referenceShiftFor(lowerThird = true))
        assertTrue(moved.movedOn(lowerThird = false))
        assertFalse(moved.movedOn(lowerThird = true))
        val cleared = moved.withMovesCleared(lowerThird = false)
        assertFalse(cleared.movedOn(lowerThird = false))
        assertEquals(0, cleared.shiftX)
        val lt = BibleTranslationSettings(lowerThirdShiftY = 3).withReferenceShift(lowerThird = true, x = 1, y = 2)
        assertTrue(lt.movedOn(lowerThird = true))
        assertFalse(lt.withMovesCleared(lowerThird = true).movedOn(lowerThird = true))
    }
}

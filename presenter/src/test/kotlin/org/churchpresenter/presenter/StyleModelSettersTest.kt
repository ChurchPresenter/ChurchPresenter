package org.churchpresenter.presenter

import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.SongNumberOffset
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTranslationSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StyleModelSettersTest {

    @Test
    fun `only the lyrics and the lines that preview them fit themselves`() {
        assertEquals(
            setOf(SongStyleElement.LYRICS, SongStyleElement.LOOK_AHEAD, SongStyleElement.NEXT_SECTION),
            SongStyleElement.entries.filter { it.hasAutoFit }.toSet(),
        )
        assertTrue(SongStyleElement.LYRICS.hasChordColor)
        assertFalse(SongStyleElement.TITLE.hasChordColor)
    }

    @Test
    fun `a Bible style written to each element and output is read back from there alone`() {
        val style = BibleElementStyle(color = "#123456", fontSize = 41, bold = true)
        BibleStyleElement.entries.forEach { element ->
            BibleStyleTarget.entries.forEach { target ->
                val written = BibleTranslationSettings().withElementStyle(element, target, style)
                assertEquals(style.color, written.elementStyle(element, target).color, "$element $target")
                assertEquals(style.fontSize, written.elementStyle(element, target).fontSize, "$element $target")
                val others = BibleStyleElement.entries.flatMap { e -> BibleStyleTarget.entries.map { e to it } }
                    .filter { it != element to target }
                others.forEach { (e, t) ->
                    assertEquals(
                        BibleTranslationSettings().elementStyle(e, t), written.elementStyle(e, t),
                        "writing $element $target left $e $t alone",
                    )
                }
            }
        }
    }

    @Test
    fun `a song style for the first language writes the flat fields`() {
        val style = SongSettings().elementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN)
            .copy(fontSize = 77)
        val written = SongSettings().withElementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN, 0, style)
        assertEquals(77, written.elementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN).fontSize)
    }

    @Test
    fun `a song style for a later language writes that language's own profile`() {
        val base = SongSettings(translations = listOf(SongTranslationSettings(overrideStyle = true)))
        val style = base.elementStyle(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD).copy(fontSize = 33)
        val written = base.withElementStyle(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD, 1, style)

        assertEquals(33, written.elementStyle(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD, 1).fontSize)
        assertEquals(
            base.elementStyle(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD),
            written.elementStyle(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD),
            "the first language is untouched",
        )
    }

    @Test
    fun `an element with no per-language form writes the flat fields whatever the language`() {
        val style = SongSettings().elementStyle(SongStyleElement.NUMBER, SongStyleTarget.FULL_SCREEN)
            .copy(fontSize = 12)
        val written = SongSettings().withElementStyle(SongStyleElement.NUMBER, SongStyleTarget.FULL_SCREEN, 2, style)
        assertEquals(12, written.elementStyle(SongStyleElement.NUMBER, SongStyleTarget.FULL_SCREEN).fontSize)
    }

    @Test
    fun `the number's corner and nudge are kept per output`() {
        val song = SongSettings()
            .withNumberCorner(lowerThird = false, value = Constants.TOP_LEFT)
            .withNumberCorner(lowerThird = true, value = Constants.BOTTOM_RIGHT)
            .withNumberOffset(lowerThird = false, value = SongNumberOffset(xPercent = 4, yPercent = 5))
            .withNumberOffset(lowerThird = true, value = SongNumberOffset(xPercent = 6, yPercent = 7))

        assertEquals(Constants.TOP_LEFT, song.numberCorner(lowerThird = false))
        assertEquals(Constants.BOTTOM_RIGHT, song.numberCorner(lowerThird = true))
        assertEquals(SongNumberOffset(xPercent = 4, yPercent = 5), song.numberOffset(lowerThird = false))
        assertEquals(SongNumberOffset(xPercent = 6, yPercent = 7), song.numberOffset(lowerThird = true))
    }

    @Test
    fun `each title-slide element has its own switch tag`() {
        assertEquals("title_slide_offset_title", titleSlideOffsetTag(SongStyleElement.TITLE))
    }
}

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.translationSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The second language's look: the fallback that makes the language switch free to press, the write
 * that switches its own look on and seeds both outputs, and the reset that puts it back to following
 * the first. Both the lyrics and the title go through the same per-language profile.
 */
class SongLanguageStyleTest {

    private val styledPrimary = SongSettings(
        lyricsColor = "#2B14CC",
        lyricsFontType = "Helvetica",
        lyricsFontSize = 100,
        lyricsBold = true,
        lyricsTransform = Constants.TEXT_TRANSFORM_UPPERCASE,
        lyricsLowerThirdColor = "#00FF00",
        lyricsLowerThirdFontSize = 33,
        titleColor = "#112233",
        titleFontSize = 44,
        titleLowerThirdColor = "#445566",
        titleLowerThirdFontSize = 21,
    )

    private fun SongSettings.second(element: SongStyleElement, target: SongStyleTarget) =
        elementStyle(element, target, SongStyleLanguage.SECONDARY)

    private fun SongSettings.editSecond(
        element: SongStyleElement,
        target: SongStyleTarget,
        change: (SongElementStyle) -> SongElementStyle,
    ) = withElementStyle(element, target, SongStyleLanguage.SECONDARY, change(second(element, target)))

    @Test
    fun `until it is styled the second language reads back as the first`() {
        for (element in SECOND_LANGUAGE_ELEMENTS) {
            assertEquals(
                styledPrimary.elementStyle(element, SongStyleTarget.FULL_SCREEN),
                styledPrimary.second(element, SongStyleTarget.FULL_SCREEN),
                "picking Secondary on a song that has never been styled shows what is on the slide",
            )
        }
        assertFalse(styledPrimary.translationSettings(0).overrideStyle)
    }

    @Test
    fun `the fallback is per output, not one profile for both`() {
        assertEquals("#00FF00", styledPrimary.second(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD).color)
        assertEquals(33, styledPrimary.second(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD).fontSize)
        assertEquals("#445566", styledPrimary.second(SongStyleElement.TITLE, SongStyleTarget.LOWER_THIRD).color)
    }

    @Test
    fun `one edit changes one property and seeds the rest from the first language`() {
        val edited = styledPrimary.editSecond(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN) {
            it.copy(color = "#FFAA00")
        }
        val stored = edited.second(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN)

        assertTrue(edited.translationSettings(0).overrideStyle, "an edit is what turns the second look on")
        assertEquals("#FFAA00", stored.color)
        assertEquals("Helvetica", stored.fontType, "everything untouched stays what the first language had")
        assertEquals(100, stored.fontSize)
        assertTrue(stored.bold)
        assertEquals(Constants.TEXT_TRANSFORM_UPPERCASE, stored.transform)
    }

    @Test
    fun `styling one output leaves the other and the other elements drawn as the first language`() {
        val edited = styledPrimary.editSecond(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN) {
            it.copy(color = "#FFAA00")
        }
        // The flag covers the whole language, so everything it now reads must have been seeded
        // rather than left at the class default.
        assertEquals("#00FF00", edited.second(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD).color)
        assertEquals(33, edited.second(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD).fontSize)
        assertEquals("#112233", edited.second(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN).color)
        assertEquals(21, edited.second(SongStyleElement.TITLE, SongStyleTarget.LOWER_THIRD).fontSize)
    }

    @Test
    fun `the lyrics and the title are styled separately`() {
        val edited = styledPrimary.editSecond(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN) {
            it.copy(color = "#FF0000")
        }
        assertEquals("#FF0000", edited.second(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN).color)
        assertEquals("#2B14CC", edited.second(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN).color)
        assertEquals("#112233", edited.elementStyle(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN).color)
    }

    @Test
    fun `the first language moving on does not drag the second with it`() {
        val edited = styledPrimary
            .editSecond(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN) { it.copy(color = "#FFAA00") }
            .copy(lyricsFontType = "Courier", lyricsColor = "#123456")

        val stored = edited.second(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN)
        assertEquals("Helvetica", stored.fontType, "the seed was taken once, not re-read on every draw")
        assertEquals("#FFAA00", stored.color)
    }

    @Test
    fun `reset puts the second language back to being drawn like the first`() {
        val edited = styledPrimary.editSecond(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN) {
            it.copy(color = "#FFAA00")
        }
        val reset = edited.withElementReset(
            SongStyleElement.LYRICS,
            SongStyleTarget.FULL_SCREEN,
            SongStyleLanguage.SECONDARY,
        )

        assertFalse(reset.translationSettings(0).overrideStyle)
        assertEquals(
            styledPrimary.elementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN),
            reset.second(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN),
        )
    }

    @Test
    fun `an element a language does not carry is written to its only profile`() {
        val edited = styledPrimary.withElementStyle(
            SongStyleElement.NUMBER,
            SongStyleTarget.FULL_SCREEN,
            SongStyleLanguage.SECONDARY,
            styledPrimary.elementStyle(SongStyleElement.NUMBER, SongStyleTarget.FULL_SCREEN).copy(color = "#ABCDEF"),
        )
        assertEquals("#ABCDEF", edited.songNumberColor)
        assertFalse(edited.translationSettings(0).overrideStyle)
    }

    @Test
    fun `every property of the second language round-trips, outline included`() {
        val written = SongElementStyle(
            color = "#AABBCC",
            fontType = "Georgia",
            fontSize = 88,
            bold = true,
            italic = true,
            underline = true,
            strikethrough = true,
            shadow = true,
            shadowColor = "#334455",
            shadowSize = 55,
            shadowOpacity = 66,
            horizontalAlignment = Constants.RIGHT,
            letterSpacing = 7,
            wordSpacing = 9,
            transform = Constants.TEXT_TRANSFORM_LOWERCASE,
            autoFit = false,
            outline = TextOutline(enabled = true, color = "#FF0000", width = 12),
        )
        val stored = SongSettings()
            .withElementStyle(
                SongStyleElement.LYRICS,
                SongStyleTarget.LOWER_THIRD,
                SongStyleLanguage.SECONDARY,
                written,
            )
            .second(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD)

        assertEquals(written, stored)
    }
}

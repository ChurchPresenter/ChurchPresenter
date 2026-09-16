package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongLanguageStyleTest {

    private val styledPrimary = SongSettings(
        lyricsColor = "#2B14CC",
        lyricsFontType = "Helvetica",
        lyricsFontSize = 100,
        lyricsBold = true,
        lyricsTransform = Constants.TEXT_TRANSFORM_UPPERCASE,
        lyricsLowerThirdColor = "#00FF00",
        lyricsLowerThirdFontSize = 33,
    )

    @Test
    fun `until it is styled the second language reads back as the first`() {
        val secondary = styledPrimary.secondaryLyricsStyle(SongStyleTarget.FULL_SCREEN)
        assertEquals(
            styledPrimary.elementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN),
            secondary,
            "picking Secondary on a song that has never been styled shows what is on the slide",
        )
        assertFalse(styledPrimary.secondaryLanguage.enabled)
    }

    @Test
    fun `the fallback is per output, not one profile for both`() {
        assertEquals(
            "#00FF00",
            styledPrimary.secondaryLyricsStyle(SongStyleTarget.LOWER_THIRD).color,
            "the band falls back to the band's first language, not the screen's",
        )
        assertEquals(33, styledPrimary.secondaryLyricsStyle(SongStyleTarget.LOWER_THIRD).fontSize)
    }

    @Test
    fun `one edit changes one property and seeds the rest from the first language`() {
        val seed = styledPrimary.secondaryLyricsStyle(SongStyleTarget.FULL_SCREEN)
        val edited = styledPrimary.withSecondaryLyricsStyle(
            SongStyleTarget.FULL_SCREEN,
            seed.copy(color = "#FFAA00"),
        )
        val stored = edited.secondaryLyricsStyle(SongStyleTarget.FULL_SCREEN)

        assertTrue(edited.secondaryLanguage.enabled, "an edit is what turns the second profile on")
        assertEquals("#FFAA00", stored.color)
        assertEquals("Helvetica", stored.fontType, "everything untouched stays what the first language had")
        assertEquals(100, stored.fontSize)
        assertTrue(stored.bold)
        assertEquals(Constants.TEXT_TRANSFORM_UPPERCASE, stored.transform)
    }

    @Test
    fun `styling one output leaves the other drawn as its own first language`() {
        val edited = styledPrimary.withSecondaryLyricsStyle(
            SongStyleTarget.FULL_SCREEN,
            styledPrimary.secondaryLyricsStyle(SongStyleTarget.FULL_SCREEN).copy(color = "#FFAA00"),
        )
        assertEquals(
            "#00FF00",
            edited.secondaryLyricsStyle(SongStyleTarget.LOWER_THIRD).color,
            "the band was never styled, so it must not fall to the class default the flag exposes",
        )
        assertEquals(33, edited.secondaryLyricsStyle(SongStyleTarget.LOWER_THIRD).fontSize)
    }

    @Test
    fun `the first language moving on does not drag the second with it`() {
        val edited = styledPrimary
            .withSecondaryLyricsStyle(
                SongStyleTarget.FULL_SCREEN,
                styledPrimary.secondaryLyricsStyle(SongStyleTarget.FULL_SCREEN).copy(color = "#FFAA00"),
            )
            .copy(lyricsFontType = "Courier", lyricsColor = "#123456")

        val stored = edited.secondaryLyricsStyle(SongStyleTarget.FULL_SCREEN)
        assertEquals("Helvetica", stored.fontType, "the seed was taken once, not re-read on every draw")
        assertEquals("#FFAA00", stored.color)
    }

    @Test
    fun `reset puts the second language back to being drawn like the first`() {
        val edited = styledPrimary.withSecondaryLyricsStyle(
            SongStyleTarget.FULL_SCREEN,
            styledPrimary.secondaryLyricsStyle(SongStyleTarget.FULL_SCREEN).copy(color = "#FFAA00"),
        )
        val reset = edited.withSecondaryLyricsFollowingPrimary()

        assertFalse(reset.secondaryLanguage.enabled)
        assertEquals(
            styledPrimary.elementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN),
            reset.secondaryLyricsStyle(SongStyleTarget.FULL_SCREEN),
        )
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
            .withSecondaryLyricsStyle(SongStyleTarget.LOWER_THIRD, written)
            .secondaryLyricsStyle(SongStyleTarget.LOWER_THIRD)

        // position and chordColor have nowhere to live on a language profile and are dropped.
        assertEquals(written.copy(position = stored.position, chordColor = stored.chordColor), stored)
    }
}

package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The title slide's credit profiles: what a fresh install draws them with, and that a file written
 * by an older build -- one with no such fields -- reads back with those same defaults.
 */
class SongCreditStyleTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `a credit opens in the title's face, plain and centred`() {
        val style = SongCreditStyle()
        assertEquals("Arial", style.fontType)
        assertEquals(Constants.CENTER, style.horizontalAlignment)
        assertEquals(Constants.TEXT_TRANSFORM_NONE, style.transform)
        assertFalse(style.bold || style.italic || style.underline || style.strikethrough || style.shadow)
        assertEquals(0, style.letterSpacing)
        assertEquals(0, style.wordSpacing)
    }

    @Test
    fun `the author and composer default larger and brighter than the licence and tempo`() {
        val song = SongSettings()
        assertTrue(song.titleSlideAuthor.fontSize > song.titleSlideCcli.fontSize)
        assertEquals(song.titleSlideAuthor, song.titleSlideComposer)
        assertEquals(song.titleSlideCcli, song.titleSlideTempo)
        assertTrue(
            song.titleSlideAuthor.fontSize < song.titleFontSize,
            "a credit sits under the title, not level with it",
        )
    }

    @Test
    fun `the band's profiles are the screen's, smaller`() {
        val song = SongSettings()
        listOf(
            song.titleSlideAuthor to song.titleSlideAuthorLowerThird,
            song.titleSlideComposer to song.titleSlideComposerLowerThird,
            song.titleSlideCcli to song.titleSlideCcliLowerThird,
            song.titleSlideTempo to song.titleSlideTempoLowerThird,
        ).forEach { (screen, band) ->
            assertTrue(band.fontSize < screen.fontSize)
            assertEquals(screen.copy(fontSize = band.fontSize), band, "the band differs from the screen in size alone")
        }
    }

    @Test
    fun `the title slide's switches ship as the slide always drew it`() {
        val song = SongSettings()
        assertTrue(song.titleSlideShowSongNumber)
        assertTrue(song.titleSlideShowTitle)
        assertTrue(song.titleSlideShowAuthor)
        assertTrue(song.titleSlideShowComposer)
        assertFalse(song.titleSlideShowCcli)
        assertFalse(song.titleSlideShowTempo)
        assertTrue(song.titleSlideNumberBeforeTitle)
        assertEquals(Constants.MIDDLE, song.titleSlideVerticalAlignment)
    }

    @Test
    fun `a file from before the credits existed reads back with the defaults`() {
        val older = """{"titleSlideEnabled":true,"titleSlideShowSongNumber":false}"""
        val decoded = json.decodeFromString(SongSettings.serializer(), older)
        assertEquals(SongSettings(titleSlideEnabled = true, titleSlideShowSongNumber = false), decoded)
    }

    @Test
    fun `an edited credit survives the file`() {
        val edited = SongSettings(
            titleSlideCcli = SongCreditStyle(color = "#123456", fontType = "Georgia", fontSize = 40, bold = true),
            titleSlideShowTempo = true,
            titleSlideNumberBeforeTitle = false,
            titleSlideVerticalAlignment = Constants.BOTTOM,
        )
        val decoded = json.decodeFromString(SongSettings.serializer(), json.encodeToString(edited))
        assertEquals(edited, decoded)
    }
}

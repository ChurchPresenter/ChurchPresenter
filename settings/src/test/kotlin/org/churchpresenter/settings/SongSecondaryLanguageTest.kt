package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The second language of a bilingual song: what it is drawn with before anyone touches it, and how
 * an edit to one output's profile lands without disturbing the other.
 */
class SongSecondaryLanguageTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `off by default, so an existing install keeps drawing both languages alike`() {
        assertFalse(SongSecondaryLanguage().enabled)
    }

    @Test
    fun `a file written before the setting existed reads back with it off`() {
        val older = """{"secondaryLanguage":{}}"""
        assertEquals(SongSecondaryLanguage(), json.decodeFromString(SongSettings.serializer(), older).secondaryLanguage)
    }

    @Test
    fun `the band's profile starts smaller than the screen's and differs in nothing else`() {
        val language = SongSecondaryLanguage()
        assertTrue(language.lowerThird.fontSize < language.fullScreen.fontSize)
        assertEquals(
            language.fullScreen.copy(fontSize = language.lowerThird.fontSize),
            language.lowerThird,
            "the band differs from the screen in size alone",
        )
    }

    @Test
    fun `styleFor picks the output asked for`() {
        val language = SongSecondaryLanguage(
            fullScreen = SongLyricStyle(color = "#111111"),
            lowerThird = SongLyricStyle(color = "#222222"),
        )
        assertEquals("#111111", language.styleFor(lowerThird = false).color)
        assertEquals("#222222", language.styleFor(lowerThird = true).color)
    }

    @Test
    fun `writing one output's profile turns the setting on and leaves the other alone`() {
        val edited = SongLyricStyle(color = "#ABCDEF", fontType = "Georgia", bold = true)

        val band = SongSecondaryLanguage().withStyle(lowerThird = true, style = edited)
        assertTrue(band.enabled, "editing anything here is what turns it on")
        assertEquals(edited, band.lowerThird)
        assertEquals(SongSecondaryLanguage().fullScreen, band.fullScreen, "the screen profile is untouched")

        val screen = SongSecondaryLanguage().withStyle(lowerThird = false, style = edited)
        assertTrue(screen.enabled)
        assertEquals(edited, screen.fullScreen)
        assertEquals(SongSecondaryLanguage().lowerThird, screen.lowerThird, "the band profile is untouched")
    }

    @Test
    fun `a lyric style opens plain, centred and auto-fitting`() {
        val style = SongLyricStyle()
        assertEquals("Arial", style.fontType)
        assertEquals(Constants.CENTER, style.horizontalAlignment)
        assertEquals(Constants.TEXT_TRANSFORM_NONE, style.transform)
        assertTrue(style.fontSizeAutoFit)
        assertFalse(style.bold || style.italic || style.underline || style.strikethrough || style.shadow)
    }

    @Test
    fun `an edited secondary language survives the file`() {
        val settings = SongSettings(
            secondaryLanguage = SongSecondaryLanguage().withStyle(
                lowerThird = true,
                style = SongLyricStyle(
                    color = "#00FF00",
                    fontSize = 33,
                    transform = Constants.TEXT_TRANSFORM_UPPERCASE,
                ),
            ),
        )
        val decoded = json.decodeFromString(
            SongSettings.serializer(),
            json.encodeToString(SongSettings.serializer(), settings),
        )
        assertEquals(settings.secondaryLanguage, decoded.secondaryLanguage)
    }

    @Test
    fun `every song profile starts with no stroke around it`() {
        val outlines = SongOutlines()
        val every = listOf(
            outlines.songNumber, outlines.songNumberLowerThird,
            outlines.title, outlines.titleLowerThird,
            outlines.lyrics, outlines.lyricsLowerThird,
            outlines.lookAhead, outlines.lookAheadLowerThird,
            outlines.nextSection, outlines.nextSectionLowerThird,
        )
        assertEquals(10, every.size, "one per profile the presenter draws")
        assertTrue(every.all { it == every.first() }, "nothing is stroked until it is asked for")
    }

    @Test
    fun `an edited outline survives the file`() {
        val settings = SongSettings(outlines = SongOutlines(lyricsLowerThird = TextOutline(enabled = true)))
        val decoded = json.decodeFromString(
            SongSettings.serializer(),
            json.encodeToString(SongSettings.serializer(), settings),
        )
        assertEquals(settings.outlines, decoded.outlines)
        assertEquals(SongOutlines().lyrics, decoded.outlines.lyrics, "its siblings are left alone")
    }
}

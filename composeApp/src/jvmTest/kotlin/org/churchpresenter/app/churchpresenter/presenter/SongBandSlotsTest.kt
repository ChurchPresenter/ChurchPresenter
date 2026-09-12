package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.Color
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

class SongBandSlotsTest {

    private companion object {
        const val TEXT_1 = BibleLottieTemplate.LAYER_TEXT_1
        const val TEXT_2 = BibleLottieTemplate.LAYER_TEXT_2
        const val REFERENCE_1 = BibleLottieTemplate.LAYER_REFERENCE_1
        const val REFERENCE_2 = BibleLottieTemplate.LAYER_REFERENCE_2
    }

    private val verse = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        secondaryTitle = "Sublime Gracia",
        songNumber = 12,
        type = "verse",
        lines = listOf("Amazing grace, how sweet the sound", "That saved a wretch like me"),
        secondaryLines = listOf("Sublime gracia del Señor", "Que a un pecador salvó"),
    )
    private val chorus = verse.copy(header = "[Chorus]", type = Constants.SECTION_TYPE_CHORUS)
    private val lineMode = SongSettings(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_LINE)
    private val verseMode = SongSettings(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE)

    private fun slots(
        section: LyricSection = verse,
        settings: SongSettings = lineMode,
        language: String = Constants.SONG_LANG_PRIMARY,
        line: Int = 0,
        second: Boolean = false,
        all: List<LyricSection> = listOf(verse, chorus),
        index: Int = 0,
    ) = songBandSlots(SongBandPage(section, all, index, line), settings, language, second, isKey = false)

    private fun Map<String, BandSlotText>.text(name: String) = getValue(name).text

    @Test
    fun `one line at a time in line mode, the whole section in verse mode`() {
        assertEquals("That saved a wretch like me", slots(line = 1).text(TEXT_1))
        assertEquals(verse.lines.joinToString("\n"), slots(settings = verseMode, line = 1).text(TEXT_1))
        val pastTheEnd = slots(line = 99).text(TEXT_1)
        assertEquals("That saved a wretch like me", pastTheEnd, "a line past the end is the last one")
    }

    @Test
    fun `languages go to the slots the template has`() {
        val both = slots(language = Constants.SONG_LANG_BOTH, second = true)
        assertEquals("Amazing grace, how sweet the sound", both.text(TEXT_1))
        assertEquals("Sublime gracia del Señor", both.text(TEXT_2))
        val stacked = slots(language = Constants.SONG_LANG_BOTH, second = false)
        assertEquals("Amazing grace, how sweet the sound\nSublime gracia del Señor", stacked.text(TEXT_1))
        assertEquals("", stacked.text(TEXT_2))
        assertEquals("Sublime gracia del Señor", slots(language = Constants.SONG_LANG_SECONDARY).text(TEXT_1))
        val primaryBySetting = lineMode.copy(lowerThirdLanguageDisplay = Constants.SONG_LANG_PRIMARY)
        val fromSetting = slots(language = "", settings = primaryBySetting).text(TEXT_1)
        assertEquals("Amazing grace, how sweet the sound", fromSetting, "no override falls back to the setting")
    }

    @Test
    fun `the title follows the title display rule and carries the number when it shows`() {
        val firstPage = slots(
            settings = lineMode.copy(
                titleLowerThirdDisplay = Constants.FIRST_PAGE,
                showNumberLowerThird = Constants.FIRST_PAGE,
            ),
        )
        assertEquals("12. Amazing Grace", firstPage.text(REFERENCE_1))
        val onChorus =
            slots(section = chorus, index = 1, settings = lineMode.copy(titleLowerThirdDisplay = Constants.FIRST_PAGE))
        assertEquals("", onChorus.text(REFERENCE_1), "the chorus is not the first page")
        val never = slots(settings = lineMode.copy(titleLowerThirdDisplay = Constants.NONE))
        assertEquals("", never.text(REFERENCE_1))
        val secondTitle = slots(
            language = Constants.SONG_LANG_BOTH,
            second = true,
            settings = lineMode.copy(
                titleLowerThirdDisplay = Constants.EVERY_PAGE,
                showNumberLowerThird = Constants.NONE,
            ),
        )
        assertEquals("Amazing Grace", secondTitle.text(REFERENCE_1))
        assertEquals("Sublime Gracia", secondTitle.text(REFERENCE_2))
    }

    @Test
    fun `the title slide lays its titles in the text slots and its credits on the reference lines`() {
        val titleSlide = LyricSection(
            type = Constants.SECTION_TYPE_TITLE_SLIDE,
            title = "Amazing Grace",
            secondaryTitle = "Sublime Gracia",
            songNumber = 12,
            author = "John Newton", composer = "Traditional", ccli = "22025", bpm = 84,
        )
        val settings = lineMode.copy(titleSlideShowCcli = true, titleSlideShowTempo = true)
        val pages = listOf(titleSlide, verse)
        val both = Constants.SONG_LANG_BOTH
        val two = slots(section = titleSlide, settings = settings, language = both, second = true, all = pages)
        assertEquals("12 – Amazing Grace", two.text(TEXT_1))
        assertEquals("Sublime Gracia", two.text(TEXT_2))
        assertEquals("John Newton  ·  Traditional", two.text(REFERENCE_1))
        assertEquals("CCLI #22025  ·  ♩ = 84 BPM", two.text(REFERENCE_2))
        val one = slots(section = titleSlide, settings = settings, language = both, second = false, all = pages)
        assertEquals("12 – Amazing Grace\nSublime Gracia", one.text(TEXT_1))
        assertEquals("John Newton  ·  Traditional  ·  CCLI #22025  ·  ♩ = 84 BPM", one.text(REFERENCE_1))
        assertEquals("", one.text(REFERENCE_2))
    }

    @Test
    fun `slots draw with the lower-third typography and the key role goes white`() {
        val settings = SongSettings(
            lyricsLowerThirdColor = "#FF0000",
            lyricsLowerThirdFontSize = 44,
            titleLowerThirdFontType = "Georgia",
            lyricsLowerThirdTransform = Constants.TEXT_TRANSFORM_UPPERCASE,
        )
        val page = SongBandPage(verse, listOf(verse), 0, 0)
        val normal = songBandSlots(page, settings, Constants.SONG_LANG_PRIMARY, false, isKey = false)
        assertEquals(44, normal.getValue(TEXT_1).style.fontSizePt)
        assertEquals("AMAZING GRACE, HOW SWEET THE SOUND", normal.text(TEXT_1))
        assertEquals("Georgia", normal.getValue(REFERENCE_1).style.font.family)
        val key = songBandSlots(page, settings, Constants.SONG_LANG_PRIMARY, false, isKey = true)
        assertEquals(Color.White, key.getValue(TEXT_1).style.color)
    }
}

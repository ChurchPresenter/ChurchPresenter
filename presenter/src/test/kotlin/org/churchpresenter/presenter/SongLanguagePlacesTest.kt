package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.languageDisplayOrder
import org.churchpresenter.settings.songLanguageSelection
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a song draws once a profile's languages are places in the display order (#1029): with Spanish
 * moved ahead of English, "primary" is Spanish, and a language is boxed and styled by the look it
 * is drawn in -- its place's -- while its words stay its own.
 */
class SongLanguagePlacesTest {

    private val verse = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        songNumber = 12,
        type = "verse",
        lines = listOf("Amazing grace, how sweet the sound"),
        translations = listOf(SectionTranslation(title = "Sublime Gracia", lines = listOf("Sublime gracia del Señor"))),
    )
    private val spanishFirst = SongSettings(
        lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
        languageOrder = listOf(1, 0),
    )

    private fun band(language: String, slots: Int = 1, translations: List<Int> = emptyList()) = songBandSlots(
        SongBandPage(verse, listOf(verse), 0, 0),
        spanishFirst,
        // As `SongLook` resolves the output's languages before handing them to the band.
        songLanguageSelection(language, translations, available = 2, order = spanishFirst.languageDisplayOrder()),
        slots,
        isKey = false,
    )

    @Test
    fun `the band's primary is whichever language is first in the order`() {
        val primary = band(Constants.SONG_LANG_PRIMARY)

        assertEquals("Sublime gracia del Señor", primary.getValue(BibleLottieTemplate.LAYER_TEXT_1).text)
        assertEquals("12. Sublime Gracia", primary.getValue(BibleLottieTemplate.LAYER_REFERENCE_1).text)
        assertEquals(
            "Amazing grace, how sweet the sound",
            band(Constants.SONG_LANG_SECONDARY).getValue(BibleLottieTemplate.LAYER_TEXT_1).text,
        )
    }

    @Test
    fun `the band lays every language out in the order`() {
        val both = band(Constants.SONG_LANG_BOTH, slots = 2)

        assertEquals("Sublime gracia del Señor", both.getValue(BibleLottieTemplate.LAYER_TEXT_1).text)
        assertEquals("Amazing grace, how sweet the sound", both.getValue(BibleLottieTemplate.LAYER_TEXT_2).text)
    }

    @Test
    fun `the band shows only the places the profile picked`() {
        // Picked in Content & sources: the first place alone, on a template with room for two.
        val first = band(Constants.SONG_LANG_BOTH, slots = 2, translations = listOf(0))

        assertEquals("Sublime gracia del Señor", first.getValue(BibleLottieTemplate.LAYER_TEXT_1).text)
        assertEquals("", first.getValue(BibleLottieTemplate.LAYER_TEXT_2).text)
    }

    @Test
    fun `a language is boxed in its look's box, with its own words`() {
        val on = TextBox(enabled = true, xPercent = 0f, yPercent = 0f, widthPercent = 50f, heightPercent = 50f)
        val settings = SongSettings(layoutExtras = SongLayoutExtras(textBoxes = mapOf("LYRICS#0" to on)))
        val spanish = SongLanguageBlock(1, listOf("Sublime gracia del Señor"), emptyList())

        val items = songBoxItems(
            settings,
            lowerThird = false,
            lyricsElement = SongStyleElement.LYRICS,
            slide = SongBoxSlide(listOf(spanish), listOf(listOf(spanish)), SongBoxTexts(null, null, null)),
            lookOf = { slot -> if (slot == 1) 0 else 1 },
        )

        assertEquals(listOf("LYRICS#0"), items.map { it.key })
        assertEquals(listOf("Sublime gracia del Señor"), items.single().lines)
        assertEquals(0, items.single().language, "styled by the first place's look")
    }

    @Test
    fun `a title slide's title is drawn in its look, in its own language`() {
        val titles = titleSlideLines(verse, SongSettings(), languages = listOf(1), lookOf = { 0 })
            .filter { it.element == SongStyleElement.TITLE }

        assertEquals(listOf("Sublime Gracia"), titles.map { it.text })
        assertEquals(listOf(0), titles.map { it.language })
    }
}

package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/** The title slide the Songs tab sends ahead of a song: its fields, its plain lines and its backgrounds. */
class TitleSlideSectionTest {
    private fun song(
        number: String = "123",
        title: String = "Amazing Grace",
        author: String = "",
        composer: String = "",
        secondaryTitle: String = "",
        lyrics: List<String> = emptyList(),
        secondaryLyrics: List<String> = emptyList(),
        background: SongBackground = SongBackground(),
        lowerThirdBackground: SongBackground = SongBackground(),
        ccliNumber: String = "",
    ) = SongItem(
        number = number, title = title, author = author, composer = composer, ccliNumber = ccliNumber,
        secondaryTitle = secondaryTitle, lyrics = lyrics, secondaryLyrics = secondaryLyrics,
        background = background, lowerThirdBackground = lowerThirdBackground,
    )

    private val dusk = SongBackground(
        type = SongBackgroundType.GRADIENT, color = "#131a3a", colorEnd = "#3a2352", dim = 25,
    )

    private val band = SongBackground(type = SongBackgroundType.COLOR, color = "#2a1130", dim = 65)

    @Test fun `a title slide carries the credits as fields and lines, and the given bpm`() {
        val section = titleSlideSection(song(author = "Newton", composer = "Excell"), SongTuning(bpm = 90))
        assertEquals("title_slide", section.type)
        assertEquals("Amazing Grace", section.title)
        assertEquals(123, section.songNumber)
        assertEquals("Newton", section.author)
        assertEquals("Excell", section.composer)
        assertEquals(listOf("123 – Amazing Grace", "Newton", "Excell"), section.lines)
        assertEquals(90, section.bpm)
    }

    @Test fun `a title slide with no credit has only the heading line`() =
        assertEquals(listOf("123 – Amazing Grace"), titleSlideSection(song(), SongTuning(bpm = 0)).lines)

    @Test fun `a title slide can omit the number from its heading`() =
        assertEquals(
            listOf("Amazing Grace"),
            titleSlideSection(song(), SongTuning(bpm = 0), SongSettings(titleSlideShowSongNumber = false)).lines,
        )

    @Test fun `a title slide carries the licence number and one line per element the slide shows`() {
        val section = titleSlideSection(
            song(author = "Newton", composer = "Excell", ccliNumber = "22025"),
            SongTuning(bpm = 90),
            SongSettings(titleSlideShowCcli = true, titleSlideShowTempo = true, titleSlideShowComposer = false),
        )
        assertEquals("22025", section.ccli)
        assertEquals(listOf("123 – Amazing Grace", "Newton", "CCLI #22025", "\u2669 = 90 BPM"), section.lines)
    }

    @Test fun `a non-numeric song number becomes zero`() =
        assertEquals(0, titleSlideSection(song(number = "12b"), SongTuning(bpm = 0)).songNumber)

    @Test fun `a title slide carries the song's own backgrounds`() {
        val slide = titleSlideSection(song(background = dusk, lowerThirdBackground = band), SongTuning())

        assertEquals(dusk, slide.background)
        assertEquals(band, slide.lowerThirdBackground)
    }

    @Test fun `a title slide for a song that inherits carries an inheriting background`() =
        assertEquals(false, titleSlideSection(song(), SongTuning()).background.isCustom)
}

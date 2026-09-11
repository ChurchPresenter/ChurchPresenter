package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a title slide says, line by line -- the one definition the presenter draws from and the
 * stage monitor and the companion app read as text.
 */
class SongTitleSlideLinesTest {

    private val section = LyricSection(
        type = Constants.SECTION_TYPE_TITLE_SLIDE,
        title = "Amazing Grace",
        secondaryTitle = "О, благодать",
        songNumber = 427,
        author = "John Newton",
        composer = "William Walker",
        ccli = "22025",
        bpm = 84,
    )

    private val everything = SongSettings(
        titleSlideShowCcli = true,
        titleSlideShowTempo = true,
    )

    private fun elements(lines: List<TitleSlideLine>) = lines.map { it.element }

    private companion object {
        const val BOTH = Constants.SONG_LANG_BOTH
    }

    // ── Order and visibility ──────────────────────────────────────────────────

    @Test
    fun `the heading comes first and the credits follow in a fixed order`() {
        val lines = titleSlideLines(section, everything.copy(titleSlideNumberBeforeTitle = false))
        assertEquals(
            listOf(
                SongStyleElement.NUMBER,
                SongStyleElement.TITLE,
                SongStyleElement.AUTHOR,
                SongStyleElement.COMPOSER,
                SongStyleElement.CCLI,
                SongStyleElement.TEMPO,
            ),
            elements(lines),
        )
    }

    @Test
    fun `the defaults show the author and the composer but not the licence or the tempo`() {
        assertEquals(
            listOf(SongStyleElement.TITLE, SongStyleElement.AUTHOR, SongStyleElement.COMPOSER),
            elements(titleSlideLines(section, SongSettings())),
        )
    }

    @Test
    fun `an element switched off is absent`() {
        val settings = everything.copy(
            titleSlideShowSongNumber = false,
            titleSlideShowAuthor = false,
            titleSlideShowTempo = false,
        )
        assertEquals(
            listOf(SongStyleElement.TITLE, SongStyleElement.COMPOSER, SongStyleElement.CCLI),
            elements(titleSlideLines(section, settings)),
        )
    }

    @Test
    fun `a credit the song has nothing for is never drawn blank`() {
        val bare = section.copy(author = "", composer = " ", ccli = "", bpm = 0)
        assertEquals(listOf(SongStyleElement.TITLE), elements(titleSlideLines(bare, everything)))
    }

    @Test
    fun `a song with no numeric number has no number line`() {
        val lines = titleSlideLines(section.copy(songNumber = 0), everything)
        assertEquals(listOf("Amazing Grace"), titles(lines))
        assertEquals(0, lines.count { it.element == SongStyleElement.NUMBER })
    }

    // ── The number's place ────────────────────────────────────────────────────

    @Test
    fun `by default the number shares the title's line, ahead of it`() {
        val first = titleSlideLines(section, SongSettings()).first()
        assertEquals(SongStyleElement.TITLE, first.element)
        assertEquals("427", first.number)
        assertEquals("Amazing Grace", first.text)
        assertEquals("427 – Amazing Grace", first.plainText)
    }

    @Test
    fun `switched off, the number takes a line of its own above the title`() {
        val lines = titleSlideLines(section, SongSettings(titleSlideNumberBeforeTitle = false))
        assertEquals(TitleSlideLine(SongStyleElement.NUMBER, "427"), lines[0])
        assertEquals(TitleSlideLine(SongStyleElement.TITLE, "Amazing Grace"), lines[1])
    }

    @Test
    fun `with the title hidden the number still has a line of its own`() {
        val lines = titleSlideLines(section, SongSettings(titleSlideShowTitle = false))
        assertEquals(listOf(TitleSlideLine(SongStyleElement.NUMBER, "427")), lines.take(1))
    }

    @Test
    fun `with two titles the number joins the first only`() {
        val lines = titleSlideLines(section, SongSettings(), Constants.SONG_LANG_BOTH)
        assertEquals("427", lines[0].number)
        assertEquals(null, lines[1].number)
        assertEquals("О, благодать", lines[1].text)
    }

    // ── Languages ─────────────────────────────────────────────────────────────

    @Test
    fun `the primary output shows the primary title alone`() =
        assertEquals(
            listOf("Amazing Grace"),
            titles(titleSlideLines(section, SongSettings(), Constants.SONG_LANG_PRIMARY)),
        )

    @Test
    fun `the secondary output shows the secondary title alone`() =
        assertEquals(
            listOf("О, благодать"),
            titles(titleSlideLines(section, SongSettings(), Constants.SONG_LANG_SECONDARY)),
        )

    @Test
    fun `the secondary output falls back to the primary title when there is no translation`() =
        assertEquals(
            listOf("Amazing Grace"),
            titles(titleSlideLines(section.copy(secondaryTitle = ""), SongSettings(), Constants.SONG_LANG_SECONDARY)),
        )

    @Test
    fun `an output showing both languages shows both titles, the primary first`() =
        assertEquals(
            listOf("Amazing Grace", "О, благодать"),
            titles(titleSlideLines(section, SongSettings(), Constants.SONG_LANG_BOTH)),
        )

    @Test
    fun `a secondary title equal to the primary is not shown twice`() =
        assertEquals(
            listOf("Amazing Grace"),
            titles(titleSlideLines(section.copy(secondaryTitle = "Amazing Grace"), SongSettings(), BOTH)),
        )

    private fun titles(lines: List<TitleSlideLine>) =
        lines.filter { it.element == SongStyleElement.TITLE }.map { it.text }

    // ── The credits' wording ──────────────────────────────────────────────────

    @Test
    fun `the licence and the tempo read as they do on a sheet`() {
        val byElement = titleSlideLines(section, everything).associate { it.element to it.text }
        assertEquals("CCLI #22025", byElement[SongStyleElement.CCLI])
        assertEquals("♩ = 84 BPM", byElement[SongStyleElement.TEMPO])
        assertEquals("John Newton", byElement[SongStyleElement.AUTHOR])
        assertEquals("William Walker", byElement[SongStyleElement.COMPOSER])
    }

    @Test
    fun `plain text is the line itself unless the number shares it`() {
        assertEquals("John Newton", TitleSlideLine(SongStyleElement.AUTHOR, "John Newton").plainText)
        assertEquals("12 – Hymn", TitleSlideLine(SongStyleElement.TITLE, "Hymn", number = "12").plainText)
    }
}

package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How a song is prepared for the presenter — the title slide it builds and where a live edit lands.
 * These were inline in SongsTab; a wrong title/credit line mislabels the slide, and a wrong
 * section/line index sends the operator to the wrong part of the song after an edit.
 */
class SongPresenterPushTest {

    private fun song(
        number: String = "123",
        title: String = "Amazing Grace",
        author: String = "",
        composer: String = "",
        secondaryTitle: String = "",
        lyrics: List<String> = emptyList(),
        secondaryLyrics: List<String> = emptyList(),
    ) = SongItem(
        number = number, title = title, author = author, composer = composer,
        secondaryTitle = secondaryTitle, lyrics = lyrics, secondaryLyrics = secondaryLyrics,
    )

    // ── title / credit lines ──────────────────────────────────────────────────

    @Test fun `title line joins number and title`() =
        assertEquals("123 – Amazing Grace", songTitleLine(song()))

    @Test fun `title line drops a blank number`() =
        assertEquals("Amazing Grace", songTitleLine(song(number = "")))

    @Test fun `title line drops a blank title`() =
        assertEquals("123", songTitleLine(song(title = "")))

    @Test fun `title line omits the number when disabled`() =
        assertEquals("Amazing Grace", songTitleLine(song(), showSongNumber = false))

    @Test fun `credit line joins author and composer`() =
        assertEquals("Newton / Excell", songCreditLine(song(author = "Newton", composer = "Excell")))

    @Test fun `credit line drops a blank part`() =
        assertEquals("Newton", songCreditLine(song(author = "Newton")))

    @Test fun `credit line is empty when neither is present`() =
        assertEquals("", songCreditLine(song()))

    // ── titleSlideSection ─────────────────────────────────────────────────────

    @Test fun `a title slide carries heading and credit lines and the given bpm`() {
        val section = titleSlideSection(song(author = "Newton", composer = "Excell"), SongTuning(bpm = 90))
        assertEquals("title_slide", section.type)
        assertEquals("Amazing Grace", section.title)
        assertEquals(123, section.songNumber)
        assertEquals(listOf("123 – Amazing Grace", "Newton / Excell"), section.lines)
        assertEquals(90, section.bpm)
    }

    @Test fun `a title slide with no credit has only the heading line`() =
        assertEquals(listOf("123 – Amazing Grace"), titleSlideSection(song(), SongTuning(bpm = 0)).lines)

    @Test fun `a title slide can omit the number from its heading`() =
        assertEquals(
            listOf("Amazing Grace"),
            titleSlideSection(song(), SongTuning(bpm = 0), showSongNumber = false).lines,
        )

    @Test fun `a non-numeric song number becomes zero`() =
        assertEquals(0, titleSlideSection(song(number = "12b"), SongTuning(bpm = 0)).songNumber)

    // ── resolveEditedSongPush ─────────────────────────────────────────────────

    private val sections = listOf(
        LyricSection(type = Constants.SECTION_TYPE_VERSE, lines = listOf("v1 a", "v1 b")),
        LyricSection(type = Constants.SECTION_TYPE_CHORUS, lines = listOf("c1 a", "c1 b", "c1 c")),
    )

    @Test fun `an edit lands on the previously-live section and line, with bpm stamped`() {
        val push = resolveEditedSongPush(
            sections,
            liveSectionIndex = 1,
            liveLineIndex = 2,
            song(),
            SongTuning(bpm = 80),
        )
        assertEquals(1, push.sectionIndex)
        assertEquals(2, push.lineIndex)
        assertEquals(80, push.section.bpm)
        assertEquals(sections[1].lines, push.section.lines)
    }

    @Test fun `a section index past the end is clamped to the last section`() =
        assertEquals(
            1,
            resolveEditedSongPush(
                sections,
                liveSectionIndex = 9,
                liveLineIndex = 0,
                song(),
                SongTuning(bpm = 0),
            ).sectionIndex,
        )

    @Test fun `a line index past the end is clamped to the section's last line`() =
        assertEquals(
            1,
            resolveEditedSongPush(
                sections,
                liveSectionIndex = 0,
                liveLineIndex = 9,
                song(),
                SongTuning(bpm = 0),
            ).lineIndex,
        )

    @Test fun `with no sections the push falls back to a section built from the edited song`() {
        val edited = song(lyrics = listOf("line one", "line two"))
        val push = resolveEditedSongPush(
            emptyList(),
            liveSectionIndex = 0,
            liveLineIndex = 5,
            edited,
            SongTuning(bpm = 70),
        )
        assertEquals(-1, push.sectionIndex, "no section to select in an empty list")
        assertEquals(Constants.SECTION_TYPE_SONG, push.section.type)
        assertEquals(listOf("line one", "line two"), push.section.lines)
        assertEquals(1, push.lineIndex, "clamped into the fallback section's line range")
        assertEquals(70, push.section.bpm)
    }
}

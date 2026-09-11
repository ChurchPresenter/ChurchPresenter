package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.StageMonitorContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the stage monitor decides to draw, as the three pure functions behind the screen:
 * which content types a mode makes live, and the words the live and look-ahead zones carry.
 */
class StageMonitorContentTest {

    private fun verse(
        book: String = "John",
        chapter: Int = 3,
        verseNumber: Int = 16,
        verseRange: String = "",
        text: String = "For God so loved the world",
    ) = SelectedVerse(
        bookName = book,
        chapter = chapter,
        verseNumber = verseNumber,
        verseRange = verseRange,
        verseText = text,
    )

    private fun section(vararg lines: String) = LyricSection(lines = lines.toList())

    // ── activeStageTypes ──────────────────────────────────────────────────────────────────────

    @Test
    fun `presenting Bible makes the verse zone and the look-ahead live`() {
        assertEquals(
            setOf(StageMonitorContentType.BIBLE, StageMonitorContentType.NEXT),
            activeStageTypes(Presenting.BIBLE, announcementActive = false),
        )
    }

    @Test
    fun `presenting lyrics makes the song zone and the look-ahead live`() {
        assertEquals(
            setOf(StageMonitorContentType.SONGS, StageMonitorContentType.NEXT),
            activeStageTypes(Presenting.LYRICS, announcementActive = false),
        )
    }

    @Test
    fun `a presentation brings its notes with it`() {
        assertEquals(
            setOf(StageMonitorContentType.PRESENTATION, StageMonitorContentType.PRESENTATION_NOTES),
            activeStageTypes(Presenting.PRESENTATION, announcementActive = false),
        )
    }

    @Test
    fun `each single-type mode makes exactly its own type live`() {
        val expected = mapOf(
            Presenting.PICTURES to StageMonitorContentType.PICTURES,
            Presenting.MEDIA to StageMonitorContentType.MEDIA,
            Presenting.LOWER_THIRD to StageMonitorContentType.LOWER_THIRD,
            Presenting.WEBSITE to StageMonitorContentType.WEB,
            Presenting.STT to StageMonitorContentType.STT,
            Presenting.CANVAS to StageMonitorContentType.CANVAS,
            Presenting.QA to StageMonitorContentType.QA,
            Presenting.DICTIONARY to StageMonitorContentType.DICTIONARY,
        )
        expected.forEach { (mode, type) ->
            assertEquals(setOf(type), activeStageTypes(mode, announcementActive = false), mode.name)
        }
    }

    @Test
    fun `nothing live means no type is live`() {
        assertEquals(emptySet(), activeStageTypes(Presenting.NONE, announcementActive = false))
    }

    @Test
    fun `the announcements mode alone makes nothing live - the banner flag is what does`() {
        assertEquals(emptySet(), activeStageTypes(Presenting.ANNOUNCEMENTS, announcementActive = false))
        assertEquals(
            setOf(StageMonitorContentType.ANNOUNCEMENT_TEXT),
            activeStageTypes(Presenting.ANNOUNCEMENTS, announcementActive = true),
        )
    }

    @Test
    fun `an announcement is additive rather than exclusive`() {
        val live = activeStageTypes(Presenting.LYRICS, announcementActive = true)
        assertEquals(
            setOf(
                StageMonitorContentType.SONGS,
                StageMonitorContentType.NEXT,
                StageMonitorContentType.ANNOUNCEMENT_TEXT,
            ),
            live,
        )
    }

    @Test
    fun `the clock is never live - it is what a zone falls back to`() {
        Presenting.entries.forEach { mode ->
            listOf(true, false).forEach { announcing ->
                assertFalse(
                    StageMonitorContentType.CLOCK in activeStageTypes(mode, announcing),
                    "$mode/$announcing",
                )
            }
        }
    }

    @Test
    fun `every presenting mode is answered without throwing`() {
        Presenting.entries.forEach { mode ->
            assertTrue(activeStageTypes(mode, announcementActive = false).size <= 2, mode.name)
        }
    }

    // ── stageCurrentText ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the live zone shows the song section's lines, one per row`() {
        assertEquals(
            "Amazing grace\nhow sweet the sound",
            stageCurrentText(Presenting.LYRICS, section("Amazing grace", "how sweet the sound"), emptyList()),
        )
    }

    @Test
    fun `the live zone shows the reference above the verse text`() {
        assertEquals(
            "John 3:16\nFor God so loved the world",
            stageCurrentText(Presenting.BIBLE, LyricSection(), listOf(verse())),
        )
    }

    @Test
    fun `a verse range is shown instead of the single verse number`() {
        assertEquals(
            "John 3:16-17\nFor God so loved the world",
            stageCurrentText(Presenting.BIBLE, LyricSection(), listOf(verse(verseRange = "16-17"))),
        )
    }

    @Test
    fun `only the first of several displayed verses reaches the live zone`() {
        val text = stageCurrentText(
            Presenting.BIBLE,
            LyricSection(),
            listOf(verse(verseNumber = 16), verse(verseNumber = 17, text = "For God sent not his Son")),
        )
        assertEquals("John 3:16\nFor God so loved the world", text)
    }

    @Test
    fun `Bible mode with nothing selected shows nothing rather than a stray reference`() {
        assertEquals("", stageCurrentText(Presenting.BIBLE, LyricSection(), emptyList()))
    }

    @Test
    fun `any other mode leaves the live text zone empty`() {
        listOf(Presenting.PICTURES, Presenting.MEDIA, Presenting.CANVAS, Presenting.NONE).forEach { mode ->
            assertEquals("", stageCurrentText(mode, section("ignored"), listOf(verse())), mode.name)
        }
    }

    // ── stageNextText ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the look-ahead shows the section after the one on screen`() {
        val sections = listOf(section("verse one"), section("chorus"), section("verse two"))
        assertEquals("chorus", stageNextText(Presenting.LYRICS, sections, 0, emptyList()))
        assertEquals("verse two", stageNextText(Presenting.LYRICS, sections, 1, emptyList()))
    }

    @Test
    fun `the last section has nothing after it`() {
        val sections = listOf(section("verse one"), section("chorus"))
        assertEquals("", stageNextText(Presenting.LYRICS, sections, 1, emptyList()))
    }

    @Test
    fun `an index outside the song is answered with nothing rather than an exception`() {
        val sections = listOf(section("only"))
        assertEquals("", stageNextText(Presenting.LYRICS, sections, 99, emptyList()))
        assertEquals("only", stageNextText(Presenting.LYRICS, sections, -1, emptyList()))
    }

    @Test
    fun `the look-ahead shows the next verse, not the secondary language of the current one`() {
        assertEquals(
            "John 3:17\nFor God sent not his Son",
            stageNextText(
                Presenting.BIBLE,
                emptyList(),
                0,
                listOf(verse(verseNumber = 17, text = "For God sent not his Son")),
            ),
        )
    }

    @Test
    fun `no next verse leaves the look-ahead empty`() {
        assertEquals("", stageNextText(Presenting.BIBLE, emptyList(), 0, emptyList()))
    }

    @Test
    fun `modes with no look-ahead leave it empty`() {
        listOf(Presenting.PRESENTATION, Presenting.WEBSITE, Presenting.NONE).forEach { mode ->
            assertEquals("", stageNextText(mode, listOf(section("a"), section("b")), 0, listOf(verse())), mode.name)
        }
    }
}

package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.app.churchpresenter.withoutAdds
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.server.ScheduleSongDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What an approved remote "project this" request actually does to this instance.
 *
 * A phone or a linked controller asks for an item, the operator approves it, and this is what
 * happens next: the item joins the schedule **and** goes on the output. Both halves matter — adding
 * without projecting leaves the operator wondering why nothing changed, and projecting without
 * adding loses the item from the service order. A row that is already in the service order is the
 * exception: it goes up as it is, because adding it again duplicates the row.
 *
 * Every content type has to set the *right* presenting mode. Sending a song and getting the Bible
 * renderer would put a blank or stale screen in front of the congregation, which is the failure this
 * covers. Each case is driven with a real [PresenterManager] and a fake [ScheduleActions] — the
 * latter is a data class of lambdas, so recording what it was asked to add needs no mock.
 *
 * Lived in `main.kt` until the extraction that added this file.
 */
class ExecuteProjectItemTest {

    private fun project(
        item: ScheduleItem,
        scheduleItems: List<ScheduleItem> = emptyList(),
    ): Pair<ScheduleActionsRecorder, PresenterManager> {
        val recorder = ScheduleActionsRecorder()
        val presenter = PresenterManager()
        executeProjectItem(item, recorder.actions(), presenter, scheduleItems)
        return recorder to presenter
    }

    // ── Put live from a phone ───────────────────────────────────────────────────

    @Test
    fun `a song a phone puts live goes up without being added to the schedule`() {
        val recorder = ScheduleActionsRecorder()
        val presenter = PresenterManager()
        val song = ScheduleItem.SongItem(
            id = "phone-1", songNumber = 7, title = "Holy", songbook = "Hymnal", songId = "Hymnal::7",
        )

        executeProjectItem(song, recorder.actions().withoutAdds(), presenter, emptyList())

        assertEquals(emptyList(), recorder.added, "projecting from a phone must not add a row")
        assertTrue(presenter.showPresenterWindow.value, "it still goes up")
    }

    @Test
    fun `a verse a phone puts live reaches the bible renderer without being added`() {
        val recorder = ScheduleActionsRecorder()
        val presenter = PresenterManager()
        val verse = ScheduleItem.BibleVerseItem(
            id = "phone-2", bookName = "John", chapter = 3, verseNumber = 16, verseText = "v",
        )

        executeProjectItem(verse, recorder.actions().withoutAdds(), presenter, emptyList())

        assertEquals(emptyList(), recorder.added)
        assertEquals(Presenting.BIBLE, presenter.slideContent.value)
    }

    @Test
    fun `a cue sent to be put live is not shown and not added`() {
        // A cue is fired, never projected as content.
        val recorder = ScheduleActionsRecorder()
        val presenter = PresenterManager()

        val cue = ScheduleItem.CueItem(id = "cue-1", action = "go")

        executeProjectItem(cue, recorder.actions(), presenter, emptyList())

        assertEquals(emptyList(), recorder.added)
        assertEquals(Presenting.NONE, presenter.slideContent.value)
    }

    @Test
    fun `without adds keeps every other action`() {
        var cleared = 0
        val actions = ScheduleActions(clearSchedule = { cleared++ }).withoutAdds()

        actions.clearSchedule()

        assertEquals(1, cleared)
    }

    // ── A row already in the schedule ───────────────────────────────────────────

    private val scheduledSong = ScheduleItem.SongItem(
        id = "row-1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal", songId = "Hymnal::42",
    )

    @Test
    fun `a row already in the schedule is put up without a second copy being added`() {
        val (recorder, presenter) = project(scheduledSong, scheduleItems = listOf(scheduledSong))

        assertEquals(emptyList(), recorder.added, "projecting a scheduled row must not duplicate it")
        assertTrue(presenter.showPresenterWindow.value, "it still goes up")
    }

    @Test
    fun `a verse already in the schedule still reaches the bible renderer`() {
        val verse = ScheduleItem.BibleVerseItem(
            id = "row-2", bookName = "John", chapter = 3, verseNumber = 16, verseText = "v",
        )

        val (recorder, presenter) = project(verse, scheduleItems = listOf(verse))

        assertEquals(emptyList(), recorder.added)
        assertEquals(Presenting.BIBLE, presenter.slideContent.value)
    }

    @Test
    fun `an item whose id is not a row is added as before`() {
        val (recorder, _) = project(scheduledSong.copy(id = "elsewhere"), scheduleItems = listOf(scheduledSong))

        assertEquals(listOf("song:42:Amazing Grace:Hymnal:Hymnal::42"), recorder.added)
    }

    @Test
    fun `a row is found by its id alone, and never by a blank one`() {
        val rows = listOf(scheduledSong, scheduledSong.copy(id = ""))

        assertEquals(scheduledSong, scheduledRowFor(scheduledSong.copy(title = "Edited on the phone"), rows))
        assertEquals(null, scheduledRowFor(scheduledSong.copy(id = ""), rows))
        assertFalse(isScheduledRow(scheduledSong.copy(id = "row-9"), rows))
    }

    @Test
    fun `select_song finds its row by id, then by number and songbook, then by title`() {
        val numbered = scheduledSong
        val other = scheduledSong.copy(id = "row-3", songbook = "Choruses")
        val unnumbered = ScheduleItem.SongItem(id = "row-4", songNumber = 0, title = "Ô Dieu", songbook = "")
        val rows = listOf(ScheduleItem.LabelItem(id = "l", text = "Welcome", textColor = "", backgroundColor = ""),
            numbered, other, unnumbered)

        assertEquals(other, scheduledSongFor(ScheduleSongDto("row-3", 0, "", ""), rows))
        assertEquals(other, scheduledSongFor(ScheduleSongDto("", 42, "Amazing Grace", "Choruses"), rows))
        assertEquals(unnumbered, scheduledSongFor(ScheduleSongDto("", 0, "ô dieu", ""), rows))
        assertEquals(null, scheduledSongFor(ScheduleSongDto("", 7, "Amazing Grace", "Hymnal"), rows))
    }

    // ── Each content type reaches its own renderer ──────────────────────────────

    @Test
    fun `a song is added to the schedule and the output opened, leaving the song to the Songs tab`() {
        val (recorder, presenter) = project(
            ScheduleItem.SongItem(
                id = "1",
                songNumber = 42,
                title = "Amazing Grace",
                songbook = "Hymnal",
                songId = "Hymnal::42",
            )
        )

        assertEquals(listOf("song:42:Amazing Grace:Hymnal:Hymnal::42"), recorder.added)
        assertTrue(presenter.showPresenterWindow.value, "projecting must open the output")
        // The Songs tab puts the song up, its first section in one push; a placeholder put up from
        // here ahead of it showed as a blank slide.
        assertEquals(Presenting.NONE, presenter.slideContent.value, "nothing goes on screen from here")
        assertTrue(presenter.lyricSection.value.lines.isEmpty() && presenter.lyricSection.value.title.isEmpty())
    }

    @Test
    fun `a bible verse is added and put on the bible renderer`() {
        val (recorder, presenter) = project(
            ScheduleItem.BibleVerseItem(
                id = "1", bookName = "John", chapter = 3, verseNumber = 16,
                verseText = "For God so loved the world.", verseRange = "16-18", bookId = 43,
            )
        )

        assertEquals(listOf("bible:John:3:16:For God so loved the world.:16-18:43"), recorder.added)
        assertEquals(Presenting.BIBLE, presenter.slideContent.value)
        assertTrue(presenter.showPresenterWindow.value)
    }

    @Test
    fun `a projected verse reaches the presenter with its reference intact`() {
        val (_, presenter) = project(
            ScheduleItem.BibleVerseItem(
                id = "1", bookName = "John", chapter = 3, verseNumber = 16,
                verseText = "For God so loved the world.", verseRange = "16-18",
            )
        )

        val verse = presenter.selectedVerses.value.firstOrNull()
        assertEquals("John", verse?.bookName)
        assertEquals(3, verse?.chapter)
        assertEquals(16, verse?.verseNumber)
        // The range is what makes it three verses rather than one on screen.
        assertEquals("16-18", verse?.verseRange)
    }

    @Test
    fun `a website is added and put on the website renderer`() {
        val (recorder, presenter) = project(
            ScheduleItem.WebsiteItem(id = "1", url = "https://example.org", title = "Notices")
        )

        assertEquals(listOf("website:https://example.org:Notices"), recorder.added)
        assertEquals(Presenting.WEBSITE, presenter.slideContent.value)
    }

    @Test
    fun `a picture folder is added with its count`() {
        val (recorder, _) = project(
            ScheduleItem.PictureItem(id = "1", folderPath = "/photos/advent", folderName = "Advent", imageCount = 12)
        )

        assertEquals(listOf("picture:/photos/advent:Advent:12"), recorder.added)
    }

    @Test
    fun `a presentation is added with its slide count and type`() {
        val (recorder, _) = project(
            ScheduleItem.PresentationItem(
                id = "1", filePath = "/decks/sermon.pptx", fileName = "sermon.pptx",
                slideCount = 24, fileType = "pptx",
            )
        )

        assertEquals(listOf("presentation:/decks/sermon.pptx:sermon.pptx:24:pptx"), recorder.added)
    }

    @Test
    fun `media is added with its url and type`() {
        val (recorder, _) = project(
            ScheduleItem.MediaItem(
                id = "1",
                mediaUrl = "https://example.org/clip.mp4",
                mediaTitle = "Clip",
                mediaType = "video",
            )
        )

        assertEquals(listOf("media:https://example.org/clip.mp4:Clip:video"), recorder.added)
    }

    // ── Types with nothing to project ───────────────────────────────────────────

    @Test
    fun `a label is not projectable and leaves the output as it was`() {
        val recorder = ScheduleActionsRecorder()
        val presenter = PresenterManager()
        // Put something real on the output first, so "unchanged" means it survived rather than
        // meaning nothing was ever set.
        executeProjectItem(
            ScheduleItem.BibleVerseItem(id = "0", bookName = "John", chapter = 3, verseNumber = 16, verseText = "v"),
            recorder.actions(),
            presenter,
            emptyList(),
        )
        val modeBefore = presenter.slideContent.value
        val addedBefore = recorder.added.size

        executeProjectItem(
            ScheduleItem.LabelItem(id = "1", text = "Welcome", textColor = "#FFF", backgroundColor = "#000"),
            recorder.actions(),
            presenter,
            emptyList(),
        )

        // A label is a divider in the service order, not content. Falling through the `when` has to
        // leave whatever is live alone rather than blanking the screen mid-service.
        assertEquals(addedBefore, recorder.added.size, "a label has nothing to add, got ${recorder.added}")
        assertEquals(modeBefore, presenter.slideContent.value, "and nothing to switch the output to")
        assertEquals("John", presenter.selectedVerses.value.single().bookName, "the live verse must still be live")
    }

    @Test
    fun `each projection sets exactly one mode`() {
        // Guards against a case that falls through into the next and leaves the output on the
        // previous renderer while the schedule says otherwise. A song is not here: the Songs tab
        // switches the output when it puts the song up.
        val cases = listOf(
            ScheduleItem.BibleVerseItem(
                id = "2",
                bookName = "John",
                chapter = 3,
                verseNumber = 16,
                verseText = "v",
            ) to Presenting.BIBLE,
            ScheduleItem.WebsiteItem(id = "3", url = "u", title = "t") to Presenting.WEBSITE,
        )

        cases.forEach { (item, expected) ->
            val (_, presenter) = project(item)
            assertEquals(
                expected,
                presenter.slideContent.value,
                "${item::class.simpleName} must reach its own renderer",
            )
        }
    }

    // ── Dictionary and announcements: projected without joining the schedule ────

    @Test
    fun `a dictionary entry reaches the renderer without joining the schedule`() {
        // The asymmetry is deliberate and is why `addScheduleItem` is a separate function rather than
        // something this one delegates to: projecting a word puts it on screen, it does not file it
        // in the service order. Merging the two would add a row every time one is projected.
        val (recorder, presenter) = project(
            ScheduleItem.DictionaryItem(
                id = "d", number = "G5485", word = "χάρις", transliteration = "charis", definition = "grace",
            )
        )

        assertTrue(
            recorder.added.isEmpty(),
            "a projected word must not be filed in the schedule, got ${recorder.added}",
        )
        assertEquals(Presenting.DICTIONARY, presenter.slideContent.value)
        assertTrue(presenter.showPresenterWindow.value)

        val shown = presenter.displayedDictionaryEntry.value
        assertEquals("G5485", shown?.number)
        assertEquals("χάρις", shown?.word)
        assertEquals("grace", shown?.definition)
    }

    @Test
    fun `a plain announcement puts its text on screen without joining the schedule`() {
        val (recorder, presenter) = project(
            ScheduleItem.AnnouncementItem(id = "a", text = "Service starts at 10")
        )

        assertTrue(recorder.added.isEmpty(), "got ${recorder.added}")
        assertEquals("Service starts at 10", presenter.announcementText.value)
        assertEquals(Presenting.ANNOUNCEMENTS, presenter.lastLive.value)
        assertTrue(presenter.showPresenterWindow.value)
    }

    @Test
    fun `a countdown announcement starts at the total the operator set`() {
        // The arithmetic is the point: hours*3600 + minutes*60 + seconds. Getting it wrong is a
        // countdown that reaches zero at the wrong moment, which is the one thing a countdown is for.
        val presenter = PresenterManager()
        try {
            executeProjectItem(
                ScheduleItem.AnnouncementItem(
                    id = "a", text = "", isTimer = true,
                    timerHours = 1, timerMinutes = 2, timerSeconds = 3,
                    timerMode = Constants.TIMER_MODE_DURATION, timerExpiredText = "Time!",
                ),
                ScheduleActionsRecorder().actions(),
                presenter,
                emptyList(),
            )

            assertEquals(3_723, presenter.timerRemainingSeconds.value, "1h 2m 3s is 3723 seconds")
            assertTrue(presenter.timerRunning.value)
            assertTrue(presenter.announcementTickerLive.value, "the ticker has to be live or the screen never updates")
            assertEquals(Presenting.ANNOUNCEMENTS, presenter.lastLive.value)
        } finally {
            presenter.pauseAnnouncementTimer()
        }
    }

    @Test
    fun `a count-up announcement starts from zero and runs`() {
        val presenter = PresenterManager()
        try {
            executeProjectItem(
                ScheduleItem.AnnouncementItem(
                    id = "a", text = "", isTimer = true, timerMode = Constants.TIMER_MODE_COUNT_UP,
                ),
                ScheduleActionsRecorder().actions(),
                presenter,
                emptyList(),
            )

            assertEquals(0, presenter.timerRemainingSeconds.value, "a count-up starts at nothing elapsed")
            assertTrue(presenter.timerRunning.value)
            assertTrue(presenter.announcementTickerLive.value)
        } finally {
            presenter.pauseAnnouncementTimer()
        }
    }

    @Test
    fun `the clock modes run a ticker without a countdown behind it`() {
        // Both clock modes are always-on displays rather than something counting down, so they leave
        // timerRunning false. They are covered together because what separates them — the text each
        // ticker writes — only appears on the next tick, and waiting a second for it would be
        // asserting on the clock rather than on the routing.
        listOf(Constants.TIMER_MODE_CLOCK, Constants.TIMER_MODE_CLOCK_DISPLAY).forEach { mode ->
            val presenter = PresenterManager()
            try {
                executeProjectItem(
                    ScheduleItem.AnnouncementItem(
                        id = "a", text = "", isTimer = true, timerMode = mode,
                        targetHour = 11, targetMinute = 0, targetSecond = 0, liveClockFormat = "HH:mm",
                    ),
                    ScheduleActionsRecorder().actions(),
                    presenter,
                    emptyList(),
                )

                assertFalse(presenter.timerRunning.value, "$mode is a display, not a countdown")
                assertTrue(presenter.announcementTickerLive.value, "$mode still has to drive the screen")
                assertEquals(Presenting.ANNOUNCEMENTS, presenter.lastLive.value)
            } finally {
                presenter.pauseAnnouncementTimer()
            }
        }
    }
}

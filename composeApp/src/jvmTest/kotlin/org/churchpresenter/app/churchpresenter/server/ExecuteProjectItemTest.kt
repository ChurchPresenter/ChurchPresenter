package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
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
 * adding loses the item from the service order.
 *
 * Every content type has to set the *right* presenting mode. Sending a song and getting the Bible
 * renderer would put a blank or stale screen in front of the congregation, which is the failure this
 * covers. Each case is driven with a real [PresenterManager] and a fake [ScheduleActions] — the
 * latter is a data class of lambdas, so recording what it was asked to add needs no mock.
 *
 * Lived in `main.kt` until the extraction that added this file.
 */
class ExecuteProjectItemTest {

    private fun project(item: ScheduleItem): Pair<ScheduleActionsRecorder, PresenterManager> {
        val recorder = ScheduleActionsRecorder()
        val presenter = PresenterManager()
        executeProjectItem(item, recorder.actions(), presenter)
        return recorder to presenter
    }

    // ── Each content type reaches its own renderer ──────────────────────────────

    @Test
    fun `a song is added to the schedule and put on the lyrics renderer`() {
        val (recorder, presenter) = project(
            ScheduleItem.SongItem(id = "1",
                songNumber = 42,
                title = "Amazing Grace",
                songbook = "Hymnal",
                songId = "Hymnal::42")
        )

        assertEquals(listOf("song:42:Amazing Grace:Hymnal:Hymnal::42"), recorder.added)
        assertEquals(Presenting.LYRICS, presenter.presentingMode.value)
        assertTrue(presenter.showPresenterWindow.value, "projecting must open the output")
    }

    @Test
    fun `a song carries its title and number onto the presenter`() {
        val (_, presenter) = project(
            ScheduleItem.SongItem(id = "1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")
        )

        val section = presenter.lyricSection.value
        assertEquals("Amazing Grace", section.title, "the output must name the song that was asked for")
        assertEquals(42, section.songNumber)
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
        assertEquals(Presenting.BIBLE, presenter.presentingMode.value)
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
        assertEquals(Presenting.WEBSITE, presenter.presentingMode.value)
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
            ScheduleItem.MediaItem(id = "1",
                mediaUrl = "https://example.org/clip.mp4",
                mediaTitle = "Clip",
                mediaType = "video")
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
            ScheduleItem.SongItem(id = "0", songNumber = 7, title = "Before", songbook = "B"),
            recorder.actions(),
            presenter,
        )
        val modeBefore = presenter.presentingMode.value
        val addedBefore = recorder.added.size

        executeProjectItem(
            ScheduleItem.LabelItem(id = "1", text = "Welcome", textColor = "#FFF", backgroundColor = "#000"),
            recorder.actions(),
            presenter,
        )

        // A label is a divider in the service order, not content. Falling through the `when` has to
        // leave whatever is live alone rather than blanking the screen mid-service.
        assertEquals(addedBefore, recorder.added.size, "a label has nothing to add, got ${recorder.added}")
        assertEquals(modeBefore, presenter.presentingMode.value, "and nothing to switch the output to")
        assertEquals("Before", presenter.lyricSection.value.title, "the live song must still be live")
    }

    @Test
    fun `each projection sets exactly one mode`() {
        // Guards against a case that falls through into the next and leaves the output on the
        // previous renderer while the schedule says otherwise.
        val cases = listOf(
            ScheduleItem.SongItem(id = "1", songNumber = 1, title = "T", songbook = "B") to Presenting.LYRICS,
            ScheduleItem.BibleVerseItem(id = "2",
                bookName = "John",
                chapter = 3,
                verseNumber = 16,
                verseText = "v") to Presenting.BIBLE,
            ScheduleItem.WebsiteItem(id = "3", url = "u", title = "t") to Presenting.WEBSITE,
        )

        cases.forEach { (item, expected) ->
            val (_, presenter) = project(item)
            assertEquals(expected,
                presenter.presentingMode.value,
                "${item::class.simpleName} must reach its own renderer")
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

        assertTrue(recorder.added.isEmpty(),
            "a projected word must not be filed in the schedule, got ${recorder.added}")
        assertEquals(Presenting.DICTIONARY, presenter.presentingMode.value)
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
        assertEquals(Presenting.ANNOUNCEMENTS, presenter.presentingMode.value)
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
            )

            assertEquals(3_723, presenter.timerRemainingSeconds.value, "1h 2m 3s is 3723 seconds")
            assertTrue(presenter.timerRunning.value)
            assertTrue(presenter.announcementTickerLive.value, "the ticker has to be live or the screen never updates")
            assertEquals(Presenting.ANNOUNCEMENTS, presenter.presentingMode.value)
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
                )

                assertFalse(presenter.timerRunning.value, "$mode is a display, not a countdown")
                assertTrue(presenter.announcementTickerLive.value, "$mode still has to drive the screen")
                assertEquals(Presenting.ANNOUNCEMENTS, presenter.presentingMode.value)
            } finally {
                presenter.pauseAnnouncementTimer()
            }
        }
    }
}

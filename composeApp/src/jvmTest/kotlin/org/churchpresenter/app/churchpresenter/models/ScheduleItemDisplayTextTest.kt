package org.churchpresenter.app.churchpresenter.models

import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one line each schedule row shows.
 *
 * `displayText` is a default constructor argument computed from the item's own fields, so it is
 * frozen at the moment the item is built and never recomputed. It is the only thing an operator
 * reads when finding their place in a service, and it is also what a phone shows over Instance
 * Link and the companion API — so it has to say enough to tell two rows apart, and it has to fit.
 *
 * The timer variants carry the most logic: five different shapes depending on how the countdown is
 * configured, formatted by hand.
 */
class ScheduleItemDisplayTextTest {

    // ── Songs ───────────────────────────────────────────────────────────────────

    @Test
    fun `a numbered song leads with its number`() {
        val song = ScheduleItem.SongItem(id = "1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")

        assertEquals("42 - Amazing Grace", song.displayText, "the number is how most operators find a song")
    }

    @Test
    fun `an unnumbered song shows just its title`() {
        val song = ScheduleItem.SongItem(id = "1", songNumber = 0, title = "Amazing Grace", songbook = "Hymnal")

        assertEquals("Amazing Grace", song.displayText, "a leading '0 - ' would be noise")
    }

    // ── Scripture ───────────────────────────────────────────────────────────────

    @Test
    fun `a single verse shows its reference`() {
        val verse = ScheduleItem.BibleVerseItem(
            id = "1", bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved…",
        )

        assertEquals("John 3:16", verse.displayText)
    }

    @Test
    fun `a verse range shows the range rather than the first verse`() {
        val range = ScheduleItem.BibleVerseItem(
            id = "1", bookName = "John", chapter = 3, verseNumber = 16, verseText = "…", verseRange = "16-18",
        )

        assertEquals("John 3:16-18", range.displayText, "the row must say how much will be read")
    }

    @Test
    fun `a discontinuous range is shown as written`() {
        val range = ScheduleItem.BibleVerseItem(
            id = "1", bookName = "Psalms", chapter = 23, verseNumber = 1, verseText = "…", verseRange = "1,4,6",
        )

        assertEquals("Psalms 23:1,4,6", range.displayText)
    }

    // ── The simple kinds ────────────────────────────────────────────────────────

    @Test
    fun `a label shows its own text`() {
        val label = ScheduleItem.LabelItem(id = "1", text = "Offering", textColor = "#FFF", backgroundColor = "#000")

        assertEquals("Offering", label.displayText)
    }

    @Test
    fun `a picture folder shows how many pictures are in it`() {
        val pictures = ScheduleItem.PictureItem(id = "1", folderPath = "/pics", folderName = "Baptism", imageCount = 24)

        assertEquals("Baptism (24 images)", pictures.displayText, "the count is how an operator spots the wrong folder")
    }

    @Test
    fun `a presentation shows how many slides it has`() {
        val deck = ScheduleItem.PresentationItem(
            id = "1", filePath = "/d.pptx", fileName = "Sermon.pptx", slideCount = 12, fileType = "pptx",
        )

        assertEquals("Sermon.pptx (12 slides)", deck.displayText)
    }

    @Test
    fun `media and lower thirds are marked so they stand out in the list`() {
        val media = ScheduleItem.MediaItem(id = "1", mediaUrl = "/clip.mp4", mediaTitle = "Baptism video", mediaType = "local")
        val lowerThird = ScheduleItem.LowerThirdItem(
            id = "2", presetId = "p", presetLabel = "Guest speaker", pauseAtFrame = false, pauseDurationMs = 0,
        )

        assertEquals("🎬 Baptism video", media.displayText)
        assertEquals("▼ Guest speaker", lowerThird.displayText)
    }

    @Test
    fun `a scene and a dictionary entry name themselves`() {
        val scene = ScheduleItem.SceneItem(id = "1", sceneId = "s", sceneName = "Welcome loop")
        val word = ScheduleItem.DictionaryItem(
            id = "2", number = "G26", word = "agape", transliteration = "agapē", definition = "love",
        )

        assertEquals("Scene: Welcome loop", scene.displayText)
        assertEquals("agape (G26)", word.displayText, "the number tells two senses of a word apart")
    }

    // ── Websites ────────────────────────────────────────────────────────────────

    @Test
    fun `a website shows its title`() {
        val site = ScheduleItem.WebsiteItem(id = "1", url = "https://example.org", title = "Giving page")

        assertEquals("Giving page", site.displayText)
    }

    @Test
    fun `a website with no title of its own falls back to the address`() {
        val site = ScheduleItem.WebsiteItem(id = "1", url = "https://example.org")

        assertEquals("https://example.org", site.displayText)
    }

    @Test
    fun `a very long website title is cut short`() {
        val long = "A".repeat(80)
        val site = ScheduleItem.WebsiteItem(id = "1", url = "https://example.org", title = long)

        assertEquals("A".repeat(60) + "…", site.displayText, "a whole paragraph would push every other column off")
        assertEquals(61, site.displayText.length)
    }

    @Test
    fun `a title that just fits is not cut`() {
        val exact = "A".repeat(60)
        val site = ScheduleItem.WebsiteItem(id = "1", url = "https://example.org", title = exact)

        assertEquals(exact, site.displayText, "an ellipsis on a title that fits would be a lie")
    }

    // ── Announcements ───────────────────────────────────────────────────────────

    private fun announcement(
        text: String = "",
        isTimer: Boolean = false,
        timerMode: String = Constants.TIMER_MODE_DURATION,
        hours: Int = 0,
        minutes: Int = 0,
        seconds: Int = 0,
        targetHour: Int = 0,
        targetMinute: Int = 0,
        targetSecond: Int = 0,
    ) = ScheduleItem.AnnouncementItem(
        id = "1",
        text = text,
        isTimer = isTimer,
        timerMode = timerMode,
        timerHours = hours,
        timerMinutes = minutes,
        timerSeconds = seconds,
        targetHour = targetHour,
        targetMinute = targetMinute,
        targetSecond = targetSecond,
    )

    @Test
    fun `an announcement shows its own words`() {
        assertEquals("Welcome to the 10am service", announcement(text = "Welcome to the 10am service").displayText)
    }

    @Test
    fun `a long announcement is cut short`() {
        val long = "W".repeat(70)

        assertEquals("W".repeat(50) + "…", announcement(text = long).displayText)
    }

    @Test
    fun `an announcement that just fits is not cut`() {
        val exact = "W".repeat(50)

        assertEquals(exact, announcement(text = exact).displayText)
    }

    @Test
    fun `a countdown under an hour shows minutes and seconds`() {
        val timer = announcement(isTimer = true, minutes = 5, seconds = 30)

        assertEquals("Timer 05:30", timer.displayText)
    }

    @Test
    fun `a countdown of an hour or more shows the hours too`() {
        val timer = announcement(isTimer = true, hours = 1, minutes = 2, seconds = 3)

        assertEquals("Timer 1:02:03", timer.displayText, "the hour is unpadded but the rest is not")
    }

    @Test
    fun `a countdown to a clock time says when`() {
        val timer = announcement(
            isTimer = true,
            timerMode = Constants.TIMER_MODE_CLOCK,
            targetHour = 9, targetMinute = 5, targetSecond = 0,
        )

        assertEquals("Until 09:05:00", timer.displayText, "'service starts at' is the whole point of the row")
    }

    @Test
    fun `a stopwatch and a clock name themselves`() {
        assertEquals(
            "Duration Timer",
            announcement(isTimer = true, timerMode = Constants.TIMER_MODE_COUNT_UP).displayText,
        )
        assertEquals(
            "Clock",
            announcement(isTimer = true, timerMode = Constants.TIMER_MODE_CLOCK_DISPLAY).displayText,
        )
    }

    @Test
    fun `a timer mode this build does not know shows as a countdown`() {
        val timer = announcement(isTimer = true, timerMode = "some_future_mode", minutes = 2)

        assertEquals("Timer 02:00", timer.displayText, "an unknown mode must still label the row")
    }

    @Test
    fun `text is ignored once the item is a timer`() {
        val timer = announcement(text = "Ignore me", isTimer = true, minutes = 1)

        assertEquals("Timer 01:00", timer.displayText, "a timer row shows the time, not the leftover text")
    }

    @Test
    fun `an empty countdown still reads as a timer`() {
        assertEquals("Timer 00:00", announcement(isTimer = true).displayText)
    }

    // ── Every kind at once ──────────────────────────────────────────────────────

    @Test
    fun `no schedule row is ever blank`() {
        // Whatever an item is, the row has to show something the operator can click on.
        val items = listOf(
            ScheduleItem.SongItem(id = "1", songNumber = 1, title = "Song", songbook = ""),
            ScheduleItem.BibleVerseItem(id = "2", bookName = "John", chapter = 3, verseNumber = 16, verseText = ""),
            ScheduleItem.LabelItem(id = "3", text = "Label", textColor = "#FFF", backgroundColor = "#000"),
            ScheduleItem.PictureItem(id = "4", folderPath = "/p", folderName = "Pics", imageCount = 0),
            ScheduleItem.PresentationItem(id = "5", filePath = "/d", fileName = "d.pptx", slideCount = 0, fileType = "pptx"),
            ScheduleItem.MediaItem(id = "6", mediaUrl = "/m", mediaTitle = "Clip", mediaType = "local"),
            ScheduleItem.LowerThirdItem(id = "7", presetId = "p", presetLabel = "Preset", pauseAtFrame = false, pauseDurationMs = 0),
            announcement(text = "Announcement"),
            ScheduleItem.WebsiteItem(id = "9", url = "https://example.org"),
            ScheduleItem.SceneItem(id = "10", sceneId = "s", sceneName = "Scene"),
            ScheduleItem.DictionaryItem(id = "11", number = "G26", word = "agape", transliteration = "", definition = ""),
        )

        items.forEach {
            assertTrue(it.displayText.isNotBlank(), "${it::class.simpleName} would render as an empty row")
        }
    }

    @Test
    fun `a row keeps the text it was built with`() {
        // displayText is a stored constructor default, not recomputed — the same reason renaming a
        // website after the fact leaves the row showing the old label.
        val song = ScheduleItem.SongItem(id = "1", songNumber = 1, title = "Original", songbook = "Hymnal")

        assertEquals("1 - Original", song.copy(title = "Renamed").displayText, "copy() carries the old label over")
    }
}

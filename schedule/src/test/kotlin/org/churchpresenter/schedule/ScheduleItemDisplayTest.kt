package org.churchpresenter.schedule

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.announcements
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.media
import org.churchpresenter.strings.generated.resources.pictures
import org.churchpresenter.strings.generated.resources.presentation
import org.churchpresenter.strings.generated.resources.display_lower_third
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.strings.generated.resources.schedule_kind_ministry
import org.churchpresenter.strings.generated.resources.schedule_kind_cue
import org.churchpresenter.strings.generated.resources.tab_canvas
import org.churchpresenter.strings.generated.resources.tab_dictionary
import org.churchpresenter.strings.generated.resources.tab_web
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Subtitles

/**
 * How schedule rows are labelled — the type icon, the grey detail line, and the timer preview.
 * The icon `when` was duplicated at two sites in ScheduleTab; the detail line and timer preview
 * carried real formatting (verse truncation, uppercase type tags, the count-up/clock "no preview"
 * rule) that was never tested.
 */
class ScheduleItemDisplayTest {

    // ── icon (exhaustive over the sealed type) ────────────────────────────────

    @Test
    fun `every schedule item type has its tab's icon`() {
        val cases = listOf(
            ScheduleItem.SongItem(id = "1", songNumber = 1, title = "t", songbook = "b") to Icons.Filled.MusicNote,
            ScheduleItem.BibleVerseItem(
                id = "2",
                bookName = "John",
                chapter = 3,
                verseNumber = 16,
                verseText = "x",
            ) to Icons.AutoMirrored.Filled.MenuBook,
            ScheduleItem.LabelItem(id = "3", text = "l", textColor = "#fff", backgroundColor = "#000")
                to Icons.AutoMirrored.Filled.Label,
            ScheduleItem.PictureItem(id = "4", folderPath = "/p", folderName = "p", imageCount = 1)
                to Icons.Filled.Image,
            ScheduleItem.PresentationItem(
                id = "5",
                filePath = "/d.pptx",
                fileName = "d",
                slideCount = 1,
                fileType = "pptx",
            ) to Icons.Filled.Slideshow,
            ScheduleItem.MediaItem(id = "6", mediaUrl = "/m.mp4", mediaTitle = "m", mediaType = "local")
                to Icons.Filled.Movie,
            ScheduleItem.LowerThirdItem(
                id = "7",
                presetId = "p",
                presetLabel = "p",
                pauseAtFrame = false,
                pauseDurationMs = 0L,
            ) to Icons.Filled.Subtitles,
            ScheduleItem.AnnouncementItem(id = "8", text = "a") to Icons.Filled.Campaign,
            ScheduleItem.WebsiteItem(id = "9", url = "https://x") to Icons.Filled.Language,
            ScheduleItem.SceneItem(id = "10", sceneId = "s", sceneName = "s") to Icons.Filled.Dashboard,
            ScheduleItem.DictionaryItem(
                id = "11",
                number = "1",
                word = "w",
                transliteration = "t",
                definition = "d",
            ) to Icons.Filled.Book,
        )
        for ((item, icon) in cases) {
            assertEquals(icon, scheduleItemIcon(item), item::class.simpleName)
        }
    }

    // ── palette index (exhaustive over the sealed type) ────────────────────────

    @Test
    fun `every schedule item type resolves to its declared palette slot`() {
        val cases = listOf(
            ScheduleItem.SongItem(id = "1", songNumber = 1, title = "t", songbook = "b") to 0,
            ScheduleItem.BibleVerseItem(
                id = "2",
                bookName = "John",
                chapter = 3,
                verseNumber = 16,
                verseText = "x",
            ) to 1,
            ScheduleItem.PresentationItem(
                id = "3",
                filePath = "/d.pptx",
                fileName = "d",
                slideCount = 1,
                fileType = "pptx",
            ) to 2,
            ScheduleItem.PictureItem(id = "4", folderPath = "/p", folderName = "p", imageCount = 1) to 3,
            ScheduleItem.MediaItem(id = "5", mediaUrl = "/m.mp4", mediaTitle = "m", mediaType = "local") to 0,
            ScheduleItem.LowerThirdItem(
                id = "6",
                presetId = "p",
                presetLabel = "p",
                pauseAtFrame = false,
                pauseDurationMs = 0L,
            ) to 1,
            ScheduleItem.AnnouncementItem(id = "7", text = "a") to 2,
            ScheduleItem.WebsiteItem(id = "8", url = "https://x") to 3,
            ScheduleItem.SceneItem(id = "9", sceneId = "s", sceneName = "s") to 0,
            ScheduleItem.DictionaryItem(
                id = "10",
                number = "1",
                word = "w",
                transliteration = "t",
                definition = "d",
            ) to 1,
            ScheduleItem.LabelItem(id = "11", text = "l", textColor = "#fff", backgroundColor = "#000") to 0,
        )
        for ((item, index) in cases) {
            assertEquals(index, scheduleItemPaletteIndex(item), item::class.simpleName)
        }
    }

    // ── kind label (exhaustive over the sealed type) ────────────────────────────

    @Test
    fun `every schedule item type maps to its own tab's string resource`() {
        val cases = listOf(
            ScheduleItem.SongItem(id = "1", songNumber = 1, title = "t", songbook = "b") to Res.string.songs,
            ScheduleItem.BibleVerseItem(
                id = "2",
                bookName = "John",
                chapter = 3,
                verseNumber = 16,
                verseText = "x",
            ) to Res.string.bible,
            ScheduleItem.PresentationItem(
                id = "3",
                filePath = "/d.pptx",
                fileName = "d",
                slideCount = 1,
                fileType = "pptx",
            ) to Res.string.presentation,
            ScheduleItem.PictureItem(
                id = "4",
                folderPath = "/p",
                folderName = "p",
                imageCount = 1,
            ) to Res.string.pictures,
            ScheduleItem.MediaItem(
                id = "5",
                mediaUrl = "/m.mp4",
                mediaTitle = "m",
                mediaType = "local",
            ) to Res.string.media,
            ScheduleItem.LowerThirdItem(
                id = "6",
                presetId = "p",
                presetLabel = "p",
                pauseAtFrame = false,
                pauseDurationMs = 0L,
            ) to Res.string.display_lower_third,
            ScheduleItem.AnnouncementItem(id = "7", text = "a") to Res.string.announcements,
            ScheduleItem.WebsiteItem(id = "8", url = "https://x") to Res.string.tab_web,
            ScheduleItem.SceneItem(id = "9", sceneId = "s", sceneName = "s") to Res.string.tab_canvas,
            ScheduleItem.DictionaryItem(
                id = "10",
                number = "1",
                word = "w",
                transliteration = "t",
                definition = "d",
            ) to Res.string.tab_dictionary,
        )
        for ((item, resource) in cases) {
            assertEquals(resource, scheduleItemKindLabel(item), item::class.simpleName)
        }
    }

    @Test
    fun `LabelItem's kind label is unused but still resolves without throwing`() =
        // LabelItem renders as a section header, never through this chip, but the function stays
        // exhaustive over the sealed type — this pins that the placeholder branch is at least
        // harmless if a future refactor ever did reach it.
        assertEquals(
            Res.string.songs,
            scheduleItemKindLabel(ScheduleItem.LabelItem(
                id = "1",
                text = "l",
                textColor = "#fff",
                backgroundColor = "#000",
            )),
        )

    // ── detail line ────────────────────────────────────────────────────────────

    @Test fun `a short bible verse shows in full`() =
        assertEquals("For God so loved", scheduleItemDetailText(
            ScheduleItem.BibleVerseItem(
                id = "1",
                bookName = "John",
                chapter = 3,
                verseNumber = 16,
                verseText = "For God so loved",
            )))

    @Test fun `a long bible verse is truncated to 100 chars with an ellipsis`() {
        val verse = "a".repeat(150)
        val detail = scheduleItemDetailText(
            ScheduleItem.BibleVerseItem(id = "1", bookName = "John", chapter = 3, verseNumber = 16, verseText = verse))
        assertEquals(103, detail!!.length, "100 chars + ellipsis")
        assertTrue(detail.endsWith("..."))
    }

    @Test fun `a presentation detail is its uppercased type and path`() =
        assertEquals("PPTX - /decks/a.pptx", scheduleItemDetailText(
            ScheduleItem.PresentationItem(
                id = "1",
                filePath = "/decks/a.pptx",
                fileName = "a",
                slideCount = 3,
                fileType = "pptx",
            )))

    @Test fun `a media detail is its uppercased type and url`() =
        assertEquals("LOCAL - /clips/a.mp4", scheduleItemDetailText(
            ScheduleItem.MediaItem(id = "1", mediaUrl = "/clips/a.mp4", mediaTitle = "a", mediaType = "local")))

    @Test fun `a type with no simple detail line returns null`() =
        assertNull(scheduleItemDetailText(ScheduleItem.SongItem(id = "1", songNumber = 1, title = "t", songbook = "b")))

    // ── announcement timer preview ─────────────────────────────────────────────

    private fun timer(mode: String) = ScheduleItem.AnnouncementItem(
        id = "1", text = "a", isTimer = true, timerMode = mode,
        timerMinutes = 5, timerSeconds = 9, targetHour = 9, targetMinute = 30, targetSecond = 5,
    )

    @Test fun `a clock-target timer previews the target time of day`() =
        assertEquals("09:30:05", announcementTimerSubtext(timer(Constants.TIMER_MODE_CLOCK)))

    @Test fun `a duration timer previews minutes and seconds`() =
        assertEquals("05:09", announcementTimerSubtext(timer(Constants.TIMER_MODE_DURATION)))

    @Test fun `a count-up timer has no fixed preview`() =
        assertNull(announcementTimerSubtext(timer(Constants.TIMER_MODE_COUNT_UP)))

    @Test fun `a live clock display has no fixed preview`() =
        assertNull(announcementTimerSubtext(timer(Constants.TIMER_MODE_CLOCK_DISPLAY)))

    @Test
    fun `a cue, a ministry and a label each name their own kind`() {
        assertEquals(Res.string.schedule_kind_cue, scheduleItemKindLabel(ScheduleItem.CueItem("c", "blank")))
        assertEquals(
            Res.string.schedule_kind_ministry,
            scheduleItemKindLabel(ScheduleItem.MinistryItem("m", "Violin", "Jake")),
        )
        assertEquals(Res.string.songs, scheduleItemKindLabel(ScheduleItem.LabelItem("l", "Welcome", "#FFF", "#000")))
    }

    @Test
    fun `a cue with neither a time nor a payload has an empty detail line`() {
        assertEquals("", scheduleItemDetailText(ScheduleItem.CueItem("c", "blank")))
    }

    @Test
    fun `a cue with only a payload shows what it puts on screen`() {
        val payload = ScheduleItem.AnnouncementItem(id = "a", text = "Welcome loop")
        assertEquals("Welcome loop", scheduleItemDetailText(ScheduleItem.CueItem("c", "show", payload = payload)))
    }
}

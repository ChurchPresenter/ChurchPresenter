package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [tabForScheduleItem] decides which tab clicking a schedule item jumps the operator to — the
 * routing half of `ScheduleTab`'s `onItemClick`, split out from the per-type state assignment that
 * stages the item for that tab. Every [ScheduleItem] subtype must land on its own tab so clicking a
 * song in the schedule never leaves the operator staring at, say, the Bible tab.
 *
 * [ScheduleItem.LabelItem] is the one deliberate exception: a label is a divider in the service
 * order, not presentable content, and clicking it opens the edit dialog in place rather than
 * switching tabs at all.
 */
class MainDesktopScheduleItemTabTest {

    @Test
    fun `a song routes to the Songs tab`() {
        val item = ScheduleItem.SongItem(id = "1", songNumber = 1, title = "Amazing Grace", songbook = "Hymnal")
        assertEquals(Tabs.SONGS, tabForScheduleItem(item))
    }

    @Test
    fun `a bible verse routes to the Bible tab`() {
        val item = ScheduleItem.BibleVerseItem(id = "1",
            bookName = "John",
            chapter = 3,
            verseNumber = 16,
            verseText = "v")
        assertEquals(Tabs.BIBLE, tabForScheduleItem(item))
    }

    @Test
    fun `a label does not route to any tab -- it opens its edit dialog in place`() {
        val item = ScheduleItem.LabelItem(id = "1", text = "Welcome", textColor = "#FFF", backgroundColor = "#000")
        assertNull(tabForScheduleItem(item))
    }

    @Test
    fun `a picture folder routes to the Pictures tab`() {
        val item = ScheduleItem.PictureItem(id = "1", folderPath = "/p", folderName = "Advent", imageCount = 5)
        assertEquals(Tabs.PICTURES, tabForScheduleItem(item))
    }

    @Test
    fun `a presentation routes to the Presentation tab`() {
        val item = ScheduleItem.PresentationItem(id = "1",
            filePath = "/f.pptx",
            fileName = "f.pptx",
            slideCount = 10,
            fileType = "pptx")
        assertEquals(Tabs.PRESENTATION, tabForScheduleItem(item))
    }

    @Test
    fun `media routes to the Media tab`() {
        val item = ScheduleItem.MediaItem(id = "1", mediaUrl = "u", mediaTitle = "t", mediaType = "local")
        assertEquals(Tabs.MEDIA, tabForScheduleItem(item))
    }

    @Test
    fun `a lower third routes to the Lower Third tab`() {
        val item = ScheduleItem.LowerThirdItem(id = "1",
            presetId = "p1",
            presetLabel = "Welcome",
            pauseAtFrame = false,
            pauseDurationMs = 0)
        assertEquals(Tabs.LOWER_THIRD, tabForScheduleItem(item))
    }

    @Test
    fun `an announcement routes to the Announcements tab`() {
        val item = ScheduleItem.AnnouncementItem(id = "1", text = "Welcome")
        assertEquals(Tabs.ANNOUNCEMENTS, tabForScheduleItem(item))
    }

    @Test
    fun `a website routes to the Web tab`() {
        val item = ScheduleItem.WebsiteItem(id = "1", url = "https://example.org")
        assertEquals(Tabs.WEB, tabForScheduleItem(item))
    }

    @Test
    fun `a canvas scene routes to the Canvas tab`() {
        val item = ScheduleItem.SceneItem(id = "1", sceneId = "s1", sceneName = "Intro")
        assertEquals(Tabs.CANVAS, tabForScheduleItem(item))
    }

    @Test
    fun `a dictionary entry routes to the Dictionary tab`() {
        val item = ScheduleItem.DictionaryItem(id = "1",
            number = "H430",
            word = "Elohim",
            transliteration = "el-o-heem",
            definition = "God")
        assertEquals(Tabs.DICTIONARY, tabForScheduleItem(item))
    }

    @Test
    fun `every content-bearing item type maps to a distinct tab`() {
        // Guards against a copy-paste mistake sending two different content types to the same
        // tab, which would look like clicking one silently loaded the other.
        val items: List<ScheduleItem> = listOf(
            ScheduleItem.SongItem(id = "1", songNumber = 1, title = "T", songbook = "B"),
            ScheduleItem.BibleVerseItem(id = "2", bookName = "John", chapter = 3, verseNumber = 16, verseText = "v"),
            ScheduleItem.PictureItem(id = "3", folderPath = "/p", folderName = "P", imageCount = 1),
            ScheduleItem.PresentationItem(id = "4", filePath = "/f", fileName = "f", slideCount = 1, fileType = "pdf"),
            ScheduleItem.MediaItem(id = "5", mediaUrl = "u", mediaTitle = "t", mediaType = "local"),
            ScheduleItem.LowerThirdItem(id = "6",
                presetId = "p",
                presetLabel = "l",
                pauseAtFrame = false,
                pauseDurationMs = 0),
            ScheduleItem.AnnouncementItem(id = "7", text = "t"),
            ScheduleItem.WebsiteItem(id = "8", url = "u"),
            ScheduleItem.SceneItem(id = "9", sceneId = "s", sceneName = "n"),
            ScheduleItem.DictionaryItem(id = "10", number = "n", word = "w", transliteration = "t", definition = "d"),
        )

        val tabs = items.map { tabForScheduleItem(it) }
        assertEquals(tabs.size, tabs.toSet().size, "every content item type must resolve to its own distinct tab")
    }
}

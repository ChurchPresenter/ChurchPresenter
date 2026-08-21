package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ScheduleActions] is the bag of callbacks that lets the toolbar, the menu, and every remote
 * "add to schedule" API call reach the real [org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel]
 * without any of those call sites holding the ViewModel itself. Every field is production wiring —
 * a typo in an argument order here would silently add the wrong song, verse, or picture from a
 * phone request, with no compiler error to catch it.
 *
 * The class has no logic of its own beyond the constructor, so what is worth proving is exactly
 * what a caller depends on: that every argument reaches the right callback, in the right order,
 * and that the untouched default is a genuine no-op rather than something that throws or has a
 * side effect.
 */
class ScheduleActionsTest {

    @Test
    fun `every default action is a safe no-op`() {
        val actions = ScheduleActions()
        actions.newSchedule()
        actions.openSchedule()
        actions.saveSchedule()
        actions.saveScheduleAs()
        actions.removeSelected()
        actions.removeById("id")
        actions.clearSchedule()
        actions.addSong(1, "title", "book", "id")
        actions.addBibleVerse("book", 1, 1, "text", "range", 1)
        actions.addPicture("path", "name", 1)
        actions.addPresentation("path", "name", 1, "pptx")
        actions.addMedia("url", "title", "video")
        actions.addScene("id", "name")
        actions.addDictionary("1", "word", "translit", "def")
        actions.addAnnouncement(ScheduleItem.AnnouncementItem(id = "1", text = "text"))
        actions.addWebsite("url", "title")
    }

    @Test
    fun `each no-argument file action is wired to its own callback`() {
        val fired = mutableListOf<String>()
        val actions = ScheduleActions(
            newSchedule = { fired += "new" },
            openSchedule = { fired += "open" },
            saveSchedule = { fired += "save" },
            saveScheduleAs = { fired += "saveAs" },
            removeSelected = { fired += "removeSelected" },
            clearSchedule = { fired += "clear" },
        )

        actions.newSchedule()
        actions.openSchedule()
        actions.saveSchedule()
        actions.saveScheduleAs()
        actions.removeSelected()
        actions.clearSchedule()

        assertEquals(listOf("new", "open", "save", "saveAs", "removeSelected", "clear"), fired)
    }

    @Test
    fun `removeById passes the exact id through, not the whole item`() {
        var received: String? = null
        val actions = ScheduleActions(removeById = { id -> received = id })

        actions.removeById("item-42")

        assertEquals("item-42", received)
    }

    @Test
    fun `addSong passes number, title, songbook and id in order`() {
        var received: List<Any>? = null
        val actions = ScheduleActions(
            addSong = { number, title, songbook, id -> received = listOf(number, title, songbook, id) },
        )

        actions.addSong(42, "Amazing Grace", "Hymnal", "Hymnal::42")

        assertEquals(listOf(42, "Amazing Grace", "Hymnal", "Hymnal::42"), received)
    }

    @Test
    fun `addBibleVerse passes book, chapter, verse, text, range and bookId in order`() {
        var received: List<Any>? = null
        val actions = ScheduleActions(
            addBibleVerse = { book, chapter, verse, text, range, bookId ->
                received = listOf(book, chapter, verse, text, range, bookId)
            },
        )

        actions.addBibleVerse("John", 3, 16, "For God so loved the world.", "16-18", 43)

        assertEquals(listOf("John", 3, 16, "For God so loved the world.", "16-18", 43), received)
    }

    @Test
    fun `addPicture passes folder path, name and image count in order`() {
        var received: List<Any>? = null
        val actions = ScheduleActions(
            addPicture = { path, name, count -> received = listOf(path, name, count) },
        )

        actions.addPicture("/photos/advent", "Advent", 12)

        assertEquals(listOf("/photos/advent", "Advent", 12), received)
    }

    @Test
    fun `addPresentation passes file path, name, slide count and type in order`() {
        var received: List<Any>? = null
        val actions = ScheduleActions(
            addPresentation = { path, name, slides, type -> received = listOf(path, name, slides, type) },
        )

        actions.addPresentation("/decks/sermon.pptx", "sermon.pptx", 24, "pptx")

        assertEquals(listOf("/decks/sermon.pptx", "sermon.pptx", 24, "pptx"), received)
    }

    @Test
    fun `addMedia passes url, title and type in order`() {
        var received: List<Any>? = null
        val actions = ScheduleActions(
            addMedia = { url, title, type -> received = listOf(url, title, type) },
        )

        actions.addMedia("https://example.org/clip.mp4", "Clip", "video")

        assertEquals(listOf("https://example.org/clip.mp4", "Clip", "video"), received)
    }

    @Test
    fun `addScene passes scene id and name in order`() {
        var received: List<Any>? = null
        val actions = ScheduleActions(
            addScene = { id, name -> received = listOf(id, name) },
        )

        actions.addScene("scene-1", "Welcome Scene")

        assertEquals(listOf("scene-1", "Welcome Scene"), received)
    }

    @Test
    fun `addDictionary passes number, word, transliteration and definition in order`() {
        var received: List<Any>? = null
        val actions = ScheduleActions(
            addDictionary = { number, word, translit, def -> received = listOf(number, word, translit, def) },
        )

        actions.addDictionary("H430", "Elohim", "el-o-heem", "God")

        assertEquals(listOf("H430", "Elohim", "el-o-heem", "God"), received)
    }

    @Test
    fun `addAnnouncement passes the whole item through untouched`() {
        var received: ScheduleItem.AnnouncementItem? = null
        val actions = ScheduleActions(addAnnouncement = { item -> received = item })
        val item = ScheduleItem.AnnouncementItem(id = "1", text = "Welcome", fontSize = 60)

        actions.addAnnouncement(item)

        assertEquals(item, received)
    }

    @Test
    fun `addWebsite passes url and title in order`() {
        var received: List<Any>? = null
        val actions = ScheduleActions(
            addWebsite = { url, title -> received = listOf(url, title) },
        )

        actions.addWebsite("https://example.org", "Notices")

        assertEquals(listOf("https://example.org", "Notices"), received)
    }

    @Test
    fun `copy replaces only the targeted callback and leaves the rest untouched`() {
        val fired = mutableListOf<String>()
        val base = ScheduleActions(
            newSchedule = { fired += "base-new" },
            saveSchedule = { fired += "base-save" },
        )
        val replaced = base.copy(saveSchedule = { fired += "replaced-save" })

        replaced.newSchedule()
        replaced.saveSchedule()

        assertEquals(listOf("base-new", "replaced-save"), fired)
    }

    @Test
    fun `two instances are equal only when built from the exact same lambda references`() {
        // Functions compare by reference, not by what they do, so this equality only holds because
        // the same lambda object is reused for both instances — callers must not rely on value
        // equality to detect "no actions changed" across two independently-built instances.
        val noop: () -> Unit = {}
        val first = ScheduleActions(newSchedule = noop)
        val second = ScheduleActions(newSchedule = noop)
        val third = ScheduleActions(newSchedule = { })

        assertTrue(first == second, "identical lambda references make two instances equal")
        assertTrue(first != third, "two separately-created lambdas are never equal, even if both are no-ops")
    }
}

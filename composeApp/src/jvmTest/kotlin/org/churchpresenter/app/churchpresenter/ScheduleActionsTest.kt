package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
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
        actions.addMedia("url", "title", "video", "")
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
            addMedia = { url, title, type, _ -> received = listOf(url, title, type) },
        )

        actions.addMedia("https://example.org/clip.mp4", "Clip", "video", "")

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

    // ── The calendar's own callbacks ────────────────────────────────────────────────────────────

    private val label = ScheduleItem.LabelItem(
        id = "l1",
        text = "Worship",
        textColor = "#FFFFFF",
        backgroundColor = "#000000",
    )

    /**
     * The nine added for the Calendar Manager, all defaulted and all reachable from a background
     * thread.
     *
     * Same reasoning as the no-op test above and worth repeating for these in particular: the
     * automation engine calls [ScheduleActions.currentTiming] every tick and the rest on whichever
     * tick a cue fires, with nowhere to report an exception. A default that threw would take the
     * tick down rather than doing nothing visible.
     */
    @Test
    fun `every calendar default is a safe no-op too`() {
        val actions = ScheduleActions()

        actions.addCue(ScheduleItem.CueItem(id = "c1", action = "show"))
        actions.addRow(label, null)
        actions.setServiceStart("10:30")
        actions.setServiceStart(null)
        actions.addLabel("Worship", "#FFFFFF", "#000000")
        actions.addLowerThird("preset-1", "Welcome", true, 2_000)
        actions.presentScene("scene-1")
        actions.playSlideshow(label, 1)
        actions.selectItem("row-1")
    }

    @Test
    fun `currentTiming reports an empty map rather than null`() {
        // The engine indexes into this by row id every tick, so a null would be a crash per tick
        // rather than a schedule that simply runs nothing.
        assertEquals(emptyMap<String, RowTiming>(), ScheduleActions().currentTiming())
    }

    @Test
    fun `addRow passes the item and its timing through together`() {
        // They travel as a pair on purpose: a planned row's timing was set on that very row, and
        // adding the item first and its timing afterwards is how the two came apart before.
        var received: Pair<ScheduleItem, RowTiming?>? = null
        val timing = RowTiming()
        val actions = ScheduleActions(addRow = { item, t -> received = item to t })

        actions.addRow(label, timing)

        assertEquals(label to timing, received)
    }

    @Test
    fun `addCue passes the whole cue through untouched`() {
        var received: ScheduleItem.CueItem? = null
        val actions = ScheduleActions(addCue = { received = it })
        val cue = ScheduleItem.CueItem(id = "c1", action = "show", absoluteTime = "09:45", payload = label)

        actions.addCue(cue)

        assertEquals(cue, received, "its payload and its time ride along with it")
    }

    @Test
    fun `setServiceStart passes a null through rather than dropping the call`() {
        // Null means "forget the start time", which is a real instruction and not an absent one.
        val received = mutableListOf<String?>()
        val actions = ScheduleActions(setServiceStart = { received += it })

        actions.setServiceStart("10:30")
        actions.setServiceStart(null)

        assertEquals(listOf("10:30", null), received)
    }

    @Test
    fun `addLabel passes text and both colours in order`() {
        var received: List<Any>? = null
        val actions = ScheduleActions(addLabel = { text, fg, bg -> received = listOf(text, fg, bg) })

        actions.addLabel("Worship", "#FFFFFF", "#101010")

        assertEquals(listOf("Worship", "#FFFFFF", "#101010"), received)
    }

    @Test
    fun `addLowerThird passes the preset, its label and both pause arguments in order`() {
        var received: List<Any>? = null
        val actions = ScheduleActions(
            addLowerThird = { id, label, pause, ms -> received = listOf(id, label, pause, ms) },
        )

        actions.addLowerThird("preset-1", "Welcome", true, 2_000)

        assertEquals(listOf("preset-1", "Welcome", true, 2_000L), received)
    }

    @Test
    fun `presentScene and selectItem each pass their own id`() {
        val received = mutableListOf<String>()
        val actions = ScheduleActions(
            presentScene = { received += "scene:$it" },
            selectItem = { received += "row:$it" },
        )

        actions.presentScene("scene-1")
        actions.selectItem("row-7")

        assertEquals(listOf("scene:scene-1", "row:row-7"), received)
    }

    @Test
    fun `playSlideshow passes the item and how many times it plays`() {
        var received: Pair<ScheduleItem, Int>? = null
        val actions = ScheduleActions(playSlideshow = { item, plays -> received = item to plays })

        // 0 is "until something else goes live", which is a distinct instruction from 1 and the
        // one most likely to be lost to a coerceAtLeast somewhere on the way.
        actions.playSlideshow(label, 0)

        assertEquals(label to 0, received)
    }

    @Test
    fun `a bundle with one calendar action wired leaves the rest at their defaults`() {
        // How it is really built: each site wires the few it owns and passes the rest through.
        var selected: String? = null
        val actions = ScheduleActions(selectItem = { selected = it })

        actions.selectItem("row-7")
        actions.presentScene("scene-1")

        assertEquals("row-7", selected)
        assertEquals(emptyMap(), actions.currentTiming(), "the ones it did not name are still the defaults")
    }
}

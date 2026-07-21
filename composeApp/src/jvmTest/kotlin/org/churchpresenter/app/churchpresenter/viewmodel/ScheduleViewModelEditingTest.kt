package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * In-place edits of items already on the schedule, and the dispatch that puts one on screen.
 *
 * These are the paths an operator hits mid-service: renaming a website tab once its real title
 * arrives, recolouring a section label, and double-clicking an item to go live. A wrong branch here
 * either silently drops the edit or presents the wrong kind of content.
 *
 * Same `user.home` isolation as [ScheduleViewModelTest] — the ViewModel resolves its autosave path
 * at construction and `newSchedule()` deletes that file.
 */
class ScheduleViewModelEditingTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private val created = mutableListOf<ScheduleViewModel>()
    private val notifications = mutableListOf<List<ScheduleItem>>()

    @BeforeTest
    fun isolateHome() {
        // Pin the JVM-wide log path to the real test home before swapping user.home below.
        TestSingletons.latchToTestHome()

        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-editing-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        notifications.clear()
    }

    @AfterTest
    fun restoreHome() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun newViewModel(): ScheduleViewModel =
        ScheduleViewModel(onScheduleChanged = { notifications.add(it) }).also { created.add(it) }

    private val ScheduleViewModel.website: ScheduleItem.WebsiteItem
        get() = scheduleItems.filterIsInstance<ScheduleItem.WebsiteItem>().single()

    private val ScheduleViewModel.label: ScheduleItem.LabelItem
        get() = scheduleItems.filterIsInstance<ScheduleItem.LabelItem>().single()

    // ── Website titles ──────────────────────────────────────────────────────────

    @Test
    fun `a website added without a title falls back to its url`() {
        val vm = newViewModel()
        vm.addWebsite("https://example.org", "")
        assertEquals("https://example.org", vm.website.title)
    }

    @Test
    fun `the real page title replaces a placeholder url title`() {
        val vm = newViewModel()
        vm.addWebsite("https://example.org", "")
        vm.updateWebsiteTitle("https://example.org", "Example Domain")
        assertEquals("Example Domain", vm.website.title)
    }

    @Test
    fun `a title the operator already set is never overwritten`() {
        val vm = newViewModel()
        vm.addWebsite("https://example.org", "Livestream")
        vm.updateWebsiteTitle("https://example.org", "Example Domain")
        assertEquals("Livestream", vm.website.title, "a page load must not clobber a deliberate name")
    }

    @Test
    fun `a blank incoming title is ignored`() {
        val vm = newViewModel()
        vm.addWebsite("https://example.org", "")
        val before = notifications.size
        vm.updateWebsiteTitle("https://example.org", "   ")
        assertEquals("https://example.org", vm.website.title)
        assertEquals(before, notifications.size, "a rejected edit must not notify listeners")
    }

    @Test
    fun `a title for a url that is not on the schedule changes nothing`() {
        val vm = newViewModel()
        vm.addWebsite("https://example.org", "")
        val before = notifications.size
        vm.updateWebsiteTitle("https://other.example", "Somewhere Else")
        assertEquals("https://example.org", vm.website.title)
        assertEquals(before, notifications.size)
    }

    @Test
    fun `updating a website title is undoable`() {
        val vm = newViewModel()
        vm.addWebsite("https://example.org", "")
        vm.updateWebsiteTitle("https://example.org", "Example Domain")
        vm.undo()
        assertEquals("https://example.org", vm.website.title)
    }

    /**
     * Documents CURRENT behaviour, not desired behaviour: `updateWebsiteTitle` uses `copy(title = …)`,
     * and `displayText` is a stored constructor property whose default was computed when the item was
     * created — so the schedule row keeps showing the raw URL even after the real title arrives.
     * Left unfixed here deliberately; this slate is tests only. If the copy is changed to recompute
     * `displayText`, this assertion should flip.
     */
    @Test
    fun `the schedule row keeps showing the url after a title update -- known bug`() {
        val vm = newViewModel()
        vm.addWebsite("https://example.org", "")
        vm.updateWebsiteTitle("https://example.org", "Example Domain")
        assertEquals("Example Domain", vm.website.title)
        assertEquals("https://example.org", vm.website.displayText, "current behaviour: the row label is stale")
    }

    // ── Labels ──────────────────────────────────────────────────────────────────

    @Test
    fun `editing a label rewrites its text and colours but keeps its id`() {
        val vm = newViewModel()
        vm.addLabel("Offering", "#FFFFFF", "#2196F3")
        val id = vm.label.id

        vm.updateLabel(id, "Communion", "#000000", "#FFEB3B")

        assertEquals(id, vm.label.id, "a regenerated id would orphan the item's note and selection")
        assertEquals("Communion", vm.label.text)
        assertEquals("#000000", vm.label.textColor)
        assertEquals("#FFEB3B", vm.label.backgroundColor)
        assertEquals("Communion", vm.label.displayText)
    }

    @Test
    fun `editing a label is undoable`() {
        val vm = newViewModel()
        vm.addLabel("Offering", "#FFFFFF", "#2196F3")
        vm.updateLabel(vm.label.id, "Communion", "#000000", "#FFEB3B")
        vm.undo()
        assertEquals("Offering", vm.label.text)
    }

    @Test
    fun `a label edit aimed at a non-label item is refused`() {
        val vm = newViewModel()
        vm.addSong(1, "Amazing Grace", "Hymnal")
        val songId = vm.scheduleItems.single().id
        val before = notifications.size

        vm.updateLabel(songId, "Hijacked", "#000000", "#FFFFFF")

        assertTrue(vm.scheduleItems.single() is ScheduleItem.SongItem, "a song must not be replaced by a label")
        assertEquals("Amazing Grace", (vm.scheduleItems.single() as ScheduleItem.SongItem).title)
        assertEquals(before, notifications.size)
    }

    @Test
    fun `a label edit for an unknown id changes nothing`() {
        val vm = newViewModel()
        vm.addLabel("Offering", "#FFFFFF", "#2196F3")
        val before = notifications.size
        vm.updateLabel("no-such-id", "Ghost", "#000000", "#FFFFFF")
        assertEquals("Offering", vm.label.text)
        assertEquals(before, notifications.size)
    }

    // ── Notes follow their item ─────────────────────────────────────────────────

    @Test
    fun `removing an item discards its note but leaves the others alone`() {
        val vm = newViewModel()
        vm.addSong(1, "A", "Hymnal")
        vm.addSong(2, "B", "Hymnal")
        val (first, second) = vm.scheduleItems.map { it.id }
        vm.setNote(first, "Key of G")
        vm.setNote(second, "Acapella")

        vm.removeItem(first)

        assertEquals("", vm.getNote(first), "a removed item's note must not linger and reattach to a new item")
        assertEquals("Acapella", vm.getNote(second))
    }

    @Test
    fun `undoing a removal brings the note back with the item`() {
        val vm = newViewModel()
        vm.addSong(1, "A", "Hymnal")
        val id = vm.scheduleItems.single().id
        vm.setNote(id, "Key of G")

        vm.removeItem(id)
        vm.undo()

        assertEquals(id, vm.scheduleItems.single().id)
        assertEquals("Key of G", vm.getNote(id), "an accidental delete must be fully recoverable")
    }

    @Test
    fun `clearSchedule drops every note and undo restores them all`() {
        val vm = newViewModel()
        vm.addSong(1, "A", "Hymnal")
        vm.addSong(2, "B", "Hymnal")
        val ids = vm.scheduleItems.map { it.id }
        ids.forEach { vm.setNote(it, "note for $it") }

        vm.clearSchedule()
        assertTrue(ids.all { vm.getNote(it).isEmpty() })

        vm.undo()
        assertTrue(ids.all { vm.getNote(it) == "note for $it" })
    }

    // ── Presenting ──────────────────────────────────────────────────────────────

    /** Records what [ScheduleViewModel.presentItem] dispatched, so each case can assert one outcome. */
    private class Dispatch {
        val presenting = mutableListOf<Presenting>()
        val items = mutableListOf<ScheduleItem>()
    }

    /** Presents [item] with every type callback wired, so a mis-routed item shows up as the wrong entry. */
    private fun ScheduleViewModel.presentWithAllCallbacks(item: ScheduleItem): Dispatch {
        val d = Dispatch()
        presentItem(
            item = item,
            onPresenting = { d.presenting.add(it) },
            onPresentSong = { d.items.add(it) },
            onPresentBible = { d.items.add(it) },
            onPresentPresentation = { d.items.add(it) },
            onPresentPictures = { d.items.add(it) },
            onPresentMedia = { d.items.add(it) },
            onPresentAnnouncement = { d.items.add(it) },
            onPresentLowerThird = { d.items.add(it) },
            onPresentWebsite = { d.items.add(it) },
            onPresentScene = { d.items.add(it) },
            onPresentDictionary = { d.items.add(it) }
        )
        return d
    }

    /** Presents [item] with only the tab-switch callback, exercising the fallback branches. */
    private fun ScheduleViewModel.presentBare(item: ScheduleItem): List<Presenting> {
        val seen = mutableListOf<Presenting>()
        presentItem(item = item, onPresenting = { seen.add(it) })
        return seen
    }

    private fun allItemTypes(): List<ScheduleItem> = listOf(
        ScheduleItem.SongItem(id = "song", songNumber = 1, title = "Song", songbook = "Hymnal"),
        ScheduleItem.BibleVerseItem(id = "bible", bookName = "John", chapter = 3, verseNumber = 16, verseText = "…"),
        ScheduleItem.PictureItem(id = "picture", folderPath = "/pics", folderName = "Pics", imageCount = 3),
        ScheduleItem.PresentationItem(id = "deck", filePath = "/d.pptx", fileName = "d.pptx", slideCount = 5, fileType = "pptx"),
        ScheduleItem.MediaItem(id = "media", mediaUrl = "/clip.mp4", mediaTitle = "Clip", mediaType = "local"),
        ScheduleItem.LowerThirdItem(id = "lt", presetId = "p", presetLabel = "Pastor", pauseAtFrame = false, pauseDurationMs = 2000L),
        ScheduleItem.AnnouncementItem(id = "announcement", text = "Welcome"),
        ScheduleItem.WebsiteItem(id = "website", url = "https://example.org"),
        ScheduleItem.SceneItem(id = "scene", sceneId = "s1", sceneName = "Scene"),
        ScheduleItem.DictionaryItem(id = "dict", number = "G26", word = "agathos", transliteration = "agathos", definition = "good")
    )

    @Test
    fun `every presentable type routes to its own handler and nothing else`() {
        val vm = newViewModel()
        allItemTypes().forEach { item ->
            val d = vm.presentWithAllCallbacks(item)
            assertEquals(listOf(item), d.items, "${item::class.simpleName} went to the wrong handler")
            assertTrue(d.presenting.isEmpty(), "${item::class.simpleName} should not fall back to a tab switch")
        }
    }

    @Test
    fun `without a type handler each item still switches to its own tab`() {
        val vm = newViewModel()
        val expected = mapOf(
            "song" to Presenting.LYRICS,
            "bible" to Presenting.BIBLE,
            "picture" to Presenting.PICTURES,
            "deck" to Presenting.PRESENTATION,
            "media" to Presenting.MEDIA,
            "announcement" to Presenting.ANNOUNCEMENTS,
            "website" to Presenting.WEBSITE,
            "scene" to Presenting.CANVAS,
            "dict" to Presenting.ANNOUNCEMENTS
        )
        allItemTypes().filter { it.id in expected }.forEach { item ->
            assertEquals(listOf(expected.getValue(item.id)), vm.presentBare(item), "wrong tab for ${item.id}")
        }
    }

    @Test
    fun `a lower third with no handler does nothing rather than switching tabs`() {
        val vm = newViewModel()
        val lowerThird = allItemTypes().single { it.id == "lt" }
        assertTrue(
            vm.presentBare(lowerThird).isEmpty(),
            "a lower third overlays the current output; it must not take the screen over"
        )
    }

    @Test
    fun `a label is not presentable`() {
        val vm = newViewModel()
        val label = ScheduleItem.LabelItem(id = "label", text = "Offering", textColor = "#FFF", backgroundColor = "#000")
        val d = vm.presentWithAllCallbacks(label)
        assertTrue(d.items.isEmpty(), "a section divider has nothing to show")
        assertTrue(d.presenting.isEmpty(), "clicking a divider must not blank or switch the output")
    }

    @Test
    fun `presenting does not change the schedule or the selection`() {
        val vm = newViewModel()
        vm.addSong(1, "A", "Hymnal")
        val item = vm.scheduleItems.single()
        val before = notifications.size

        vm.presentItem(item = item, onPresenting = {})

        assertEquals(1, vm.scheduleItems.size)
        assertEquals(before, notifications.size, "going live is not a schedule edit")
        assertNull(vm.selectedItemId)
    }
}

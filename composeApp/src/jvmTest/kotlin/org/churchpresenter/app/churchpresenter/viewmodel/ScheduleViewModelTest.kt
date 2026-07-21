package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The schedule is the spine of a service: every item the operator will present, in order, with
 * undo behind it. These tests cover the pure in-memory model only.
 *
 * IMPORTANT: [ScheduleViewModel] resolves its autosave path from `user.home` **at construction**,
 * and `newSchedule()` deletes that file. Every test therefore builds its ViewModel while
 * `user.home` points at a throwaway directory, so a test run can never delete the developer's
 * real `~/.churchpresenter/autosave_schedule.tmp`.
 */
class ScheduleViewModelTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private val created = mutableListOf<ScheduleViewModel>()

    /** Records every schedule the ViewModel pushed to its change callback. */
    private val notifications = mutableListOf<List<ScheduleItem>>()

    @BeforeTest
    fun isolateHome() {
        // Pin the JVM-wide log path to the real test home before swapping user.home below.
        TestSingletons.latchToTestHome()

        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-test").toFile()
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

    private fun ScheduleViewModel.addSongs(vararg titles: String) =
        titles.forEachIndexed { i, t -> addSong(songNumber = i + 1, title = t, songbook = "Hymnal") }

    private val ScheduleViewModel.titles: List<String>
        get() = scheduleItems.map { (it as ScheduleItem.SongItem).title }

    // ── Baseline ────────────────────────────────────────────────────────────────

    @Test
    fun `starts empty with nothing to undo or redo`() {
        val vm = newViewModel()
        assertTrue(vm.scheduleItems.isEmpty())
        assertFalse(vm.canUndo)
        assertFalse(vm.canRedo)
        assertNull(vm.selectedItemId)
    }

    // ── Adding ──────────────────────────────────────────────────────────────────

    @Test
    fun `adds items in order and notifies once per add`() {
        val vm = newViewModel()
        vm.addSongs("First", "Second", "Third")
        assertEquals(listOf("First", "Second", "Third"), vm.titles)
        assertEquals(3, notifications.size)
        assertEquals(3, notifications.last().size)
    }

    @Test
    fun `every added item gets a distinct id even when the content is identical`() {
        val vm = newViewModel()
        repeat(5) { vm.addSong(songNumber = 1, title = "Same", songbook = "Hymnal") }
        assertEquals(5, vm.scheduleItems.map { it.id }.toSet().size, "duplicate ids would break selection and removal")
    }

    @Test
    fun `mixed item types coexist in one schedule`() {
        val vm = newViewModel()
        vm.addSong(1, "Song", "Hymnal")
        vm.addBibleVerse("John", 3, 16, "For God so loved the world")
        vm.addLabel("Offering", "#FFFFFF", "#000000")
        vm.addWebsite("https://example.org", "Example")
        assertEquals(4, vm.scheduleItems.size)
        assertTrue(vm.scheduleItems[0] is ScheduleItem.SongItem)
        assertTrue(vm.scheduleItems[1] is ScheduleItem.BibleVerseItem)
        assertTrue(vm.scheduleItems[2] is ScheduleItem.LabelItem)
        assertTrue(vm.scheduleItems[3] is ScheduleItem.WebsiteItem)
    }

    // ── Removing ────────────────────────────────────────────────────────────────

    @Test
    fun `removes only the targeted item`() {
        val vm = newViewModel()
        vm.addSongs("A", "B", "C")
        vm.removeItem(vm.scheduleItems[1].id)
        assertEquals(listOf("A", "C"), vm.titles)
    }

    @Test
    fun `removing an unknown id leaves the schedule untouched`() {
        val vm = newViewModel()
        vm.addSongs("A", "B")
        vm.removeItem("no-such-id")
        assertEquals(listOf("A", "B"), vm.titles)
    }

    // ── Reordering ──────────────────────────────────────────────────────────────

    @Test
    fun `moves an item up and down one position, returning its new index`() {
        val vm = newViewModel()
        vm.addSongs("A", "B", "C")
        assertEquals(0, vm.moveItemUp(vm.scheduleItems[1].id))
        assertEquals(listOf("B", "A", "C"), vm.titles)
        assertEquals(2, vm.moveItemDown(vm.scheduleItems[1].id))
        assertEquals(listOf("B", "C", "A"), vm.titles)
    }

    @Test
    fun `moving past either end is a no-op`() {
        val vm = newViewModel()
        vm.addSongs("A", "B", "C")
        val before = vm.titles
        assertEquals(0, vm.moveItemUp(vm.scheduleItems.first().id), "top item stays at index 0")
        assertEquals(2, vm.moveItemDown(vm.scheduleItems.last().id), "bottom item stays at the last index")
        assertEquals(before, vm.titles)
    }

    @Test
    fun `moves an item to the top and to the bottom`() {
        val vm = newViewModel()
        vm.addSongs("A", "B", "C", "D")
        vm.moveItemToTop(vm.scheduleItems[2].id)
        assertEquals(listOf("C", "A", "B", "D"), vm.titles)
        vm.moveItemToBottom(vm.scheduleItems[0].id)
        assertEquals(listOf("A", "B", "D", "C"), vm.titles)
    }

    @Test
    fun `drag-reorder by index moves the item and shifts the rest`() {
        val vm = newViewModel()
        vm.addSongs("A", "B", "C", "D")
        vm.moveItem(from = 0, to = 2)
        assertEquals(listOf("B", "C", "A", "D"), vm.titles)
    }

    @Test
    fun `out-of-range or no-op index moves are ignored`() {
        val vm = newViewModel()
        vm.addSongs("A", "B")
        val before = vm.titles
        val notificationsBefore = notifications.size
        vm.moveItem(from = -1, to = 0)
        vm.moveItem(from = 0, to = 99)
        vm.moveItem(from = 5, to = 0)
        vm.moveItem(from = 1, to = 1)
        assertEquals(before, vm.titles)
        assertEquals(notificationsBefore, notifications.size, "a rejected move must not notify listeners")
    }

    // ── Undo / redo ─────────────────────────────────────────────────────────────

    @Test
    fun `undo reverses an add and redo reapplies it`() {
        val vm = newViewModel()
        vm.addSongs("A", "B")
        assertTrue(vm.canUndo)

        vm.undo()
        assertEquals(listOf("A"), vm.titles)
        assertTrue(vm.canRedo)

        vm.redo()
        assertEquals(listOf("A", "B"), vm.titles)
    }

    @Test
    fun `undo reverses removal and reordering too`() {
        val vm = newViewModel()
        vm.addSongs("A", "B", "C")
        vm.removeItem(vm.scheduleItems[0].id)
        assertEquals(listOf("B", "C"), vm.titles)
        vm.undo()
        assertEquals(listOf("A", "B", "C"), vm.titles)

        vm.moveItemToBottom(vm.scheduleItems[0].id)
        assertEquals(listOf("B", "C", "A"), vm.titles)
        vm.undo()
        assertEquals(listOf("A", "B", "C"), vm.titles)
    }

    @Test
    fun `undo walks back through the whole history`() {
        val vm = newViewModel()
        vm.addSongs("A", "B", "C")
        repeat(3) { vm.undo() }
        assertTrue(vm.scheduleItems.isEmpty())
        assertFalse(vm.canUndo, "nothing left to undo")
    }

    @Test
    fun `undo on an empty history is a harmless no-op`() {
        val vm = newViewModel()
        vm.undo()
        vm.redo()
        assertTrue(vm.scheduleItems.isEmpty())
        assertFalse(vm.canUndo)
        assertFalse(vm.canRedo)
    }

    @Test
    fun `a new edit after undo discards the redo branch`() {
        val vm = newViewModel()
        vm.addSongs("A", "B")
        vm.undo()
        assertTrue(vm.canRedo)

        vm.addSong(9, "C", "Hymnal") // diverge from the undone branch
        assertFalse(vm.canRedo, "redo must not resurrect an abandoned branch")
        assertEquals(listOf("A", "C"), vm.titles)
    }

    @Test
    fun `undo history is capped and the cap discards the oldest states`() {
        val vm = newViewModel()
        repeat(60) { vm.addSong(it, "Song $it", "Hymnal") } // cap is 50
        var undos = 0
        while (vm.canUndo) { vm.undo(); undos++ }
        assertEquals(50, undos, "undo depth should stop at the 50-snapshot cap")
        assertEquals(10, vm.scheduleItems.size, "the 10 oldest adds fall out of history and stay applied")
    }

    // ── Notes ───────────────────────────────────────────────────────────────────

    @Test
    fun `notes are stored per item and blank clears them`() {
        val vm = newViewModel()
        vm.addSongs("A")
        val id = vm.scheduleItems[0].id
        assertEquals("", vm.getNote(id), "absent note reads as empty, never null")

        vm.setNote(id, "Key of G")
        assertEquals("Key of G", vm.getNote(id))

        vm.setNote(id, "   ")
        assertEquals("", vm.getNote(id), "a blank note clears rather than storing whitespace")
    }

    /**
     * Documents a CURRENT BUG rather than desired behaviour: `setNote` calls neither
     * `notifyChanged()` nor `pushUndoSnapshot()`. Two consequences:
     *  - the schedule is never marked dirty, so the 60-second autosave loop skips a note-only
     *    change entirely -- type a note, crash before touching anything else, the note is gone;
     *  - the note change is not undoable, and an undo of an *unrelated* earlier edit will silently
     *    revert it, because undo restores the notes map wholesale from its snapshot.
     *
     * Left unfixed here deliberately -- this slate is tests only. If `setNote` is corrected to
     * notify (and optionally snapshot), this test should flip to asserting the notification.
     */
    @Test
    fun `setNote does not mark the schedule changed -- known bug`() {
        val vm = newViewModel()
        vm.addSongs("A")
        val id = vm.scheduleItems[0].id
        val before = notifications.size

        vm.setNote(id, "Key of G")
        assertEquals(before, notifications.size, "current behaviour: note edits notify nobody")

        vm.undo() // undoes the add; the note goes with it, having never been snapshotted
        assertEquals("", vm.getNote(id))
    }

    // ── Selection ───────────────────────────────────────────────────────────────

    @Test
    fun `selecting the same item twice toggles it off`() {
        val vm = newViewModel()
        vm.addSongs("A", "B")
        val id = vm.scheduleItems[0].id

        vm.selectItem(id)
        assertEquals(id, vm.selectedItemId)
        vm.selectItem(id)
        assertNull(vm.selectedItemId, "re-selecting the same item deselects it")

        vm.selectItem(id)
        vm.selectItem(vm.scheduleItems[1].id)
        assertEquals(vm.scheduleItems[1].id, vm.selectedItemId, "selecting a different item switches")

        vm.clearSelection()
        assertNull(vm.selectedItemId)
    }

    // ── Clearing ────────────────────────────────────────────────────────────────

    @Test
    fun `clearSchedule empties the list but stays undoable`() {
        val vm = newViewModel()
        vm.addSongs("A", "B")
        vm.clearSchedule()
        assertTrue(vm.scheduleItems.isEmpty())
        assertTrue(vm.canUndo)
        vm.undo()
        assertEquals(listOf("A", "B"), vm.titles, "an accidental clear must be recoverable")
    }

    @Test
    fun `newSchedule wipes history as well as items`() {
        val vm = newViewModel()
        vm.addSongs("A", "B")
        vm.newSchedule()
        assertTrue(vm.scheduleItems.isEmpty())
        assertFalse(vm.canUndo, "starting a new service must not leave the old one undo-reachable")
        assertFalse(vm.canRedo)
    }

    // ── Instance Link follower mode ─────────────────────────────────────────────

    @Test
    fun `while following a remote schedule local mutations are redirected, not applied`() {
        val vm = newViewModel()
        val pushed = mutableListOf<ScheduleItem>()
        vm.onPushToRemoteSchedule = { pushed.add(it) }

        vm.applyRemoteSchedule(emptyList())
        assertTrue(vm.isFollowingRemote)

        vm.addSong(1, "Requested", "Hymnal")
        assertTrue(vm.scheduleItems.isEmpty(), "a follower must not mutate its own schedule locally")
        assertEquals(1, pushed.size, "the add should be forwarded to the primary instead")
        assertEquals("Requested", (pushed[0] as ScheduleItem.SongItem).title)
    }

    @Test
    fun `while following a remote schedule reordering and undo are inert`() {
        val vm = newViewModel()
        vm.addSongs("A", "B")
        vm.applyRemoteSchedule(emptyList())

        vm.moveItem(0, 1)
        vm.clearSchedule()
        vm.undo()
        vm.newSchedule()
        assertTrue(vm.isFollowingRemote)
    }

    @Test
    fun `stopFollowingRemote restores local control`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(emptyList())
        assertTrue(vm.isFollowingRemote)

        vm.stopFollowingRemote()
        assertFalse(vm.isFollowingRemote)

        vm.addSong(1, "Local again", "Hymnal")
        assertEquals(listOf("Local again"), vm.titles)
    }
}

package org.churchpresenter.schedule

import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleViewModelFollowingTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private val created = mutableListOf<ScheduleViewModel>()
    private val notifications = mutableListOf<List<ScheduleItem>>()

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-following-test").toFile()
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

    private fun song(id: String, title: String) = ScheduleItem.SongItem(id, 1, title, "Hymnal")

    // ── Sync semantics ──────────────────────────────────────────────────────────

    @Test
    fun `a sync replaces the previous mirror rather than appending to it`() {
        val vm = newViewModel()
        vm.followRemoteSchedule(listOf(song("s1", "Old")))
        vm.followRemoteSchedule(listOf(song("s2", "New")))
        assertEquals(listOf("New"), vm.scheduleItems.map { (it as ScheduleItem.SongItem).title })
    }

    @Test
    fun `an empty broadcast clears the mirrored schedule`() {
        val vm = newViewModel()
        vm.followRemoteSchedule(listOf(song("s1", "Old")))
        vm.followRemoteSchedule(emptyList())
        assertTrue(vm.scheduleItems.isEmpty(), "the primary clearing its schedule must clear the follower's")
    }

    @Test
    fun `the first sync discards whatever the follower had locally`() {
        val vm = newViewModel()
        vm.addSong(1, "Local", "Hymnal")
        vm.followRemoteSchedule(listOf(song("s1", "Remote")))
        assertEquals(listOf("Remote"), vm.scheduleItems.map { (it as ScheduleItem.SongItem).title })
    }

    @Test
    fun `each sync notifies listeners with the mirrored schedule`() {
        val vm = newViewModel()
        val before = notifications.size
        vm.followRemoteSchedule(listOf(song("s1", "Remote")))
        assertEquals(before + 1, notifications.size, "the companion server and presenter need the new schedule")
        assertEquals(1, notifications.last().size)
    }

    // ── Local mutations while following ─────────────────────────────────────────

    @Test
    fun `every add type is forwarded to the primary instead of applied locally`() {
        val vm = newViewModel()
        val pushed = mutableListOf<ScheduleItem>()
        vm.onPushToRemoteSchedule = { pushed.add(it) }
        vm.followRemoteSchedule(emptyList())

        vm.addSong(1, "Song", "Hymnal")
        vm.addBibleVerse("John", 3, 16, "text")
        vm.addLabel("Offering", "#FFF", "#000")
        vm.addPicture("/pics", "Pics", 2)
        vm.addPresentation("/d.pptx", "d.pptx", 5, "pptx")
        vm.addMedia("/clip.mp4", "Clip", "local")
        vm.addLowerThird("p", "Pastor", false, 2000L)
        vm.addAnnouncement("Welcome")
        vm.addWebsite("https://example.org", "Site")
        vm.addScene("s1", "Scene")
        vm.addDictionary("G26", "agathos", "agathos", "good")

        assertEquals(11, pushed.size, "every add* method must funnel through the same push path")
        assertTrue(vm.scheduleItems.isEmpty(), "a follower must never mutate its own schedule locally")
    }

    @Test
    fun `an add with no push wiring is dropped silently rather than applied locally`() {
        val vm = newViewModel()
        vm.followRemoteSchedule(emptyList())
        vm.addSong(1, "Song", "Hymnal") // onPushToRemoteSchedule left null — pushing is disabled
        assertTrue(vm.scheduleItems.isEmpty())
        assertFalse(vm.canUndo, "a dropped add must not leave a phantom undo step")
    }

    @Test
    fun `a removal is forwarded to the primary by id`() {
        val vm = newViewModel()
        val removed = mutableListOf<String>()
        vm.onRemoveFromRemoteSchedule = { removed.add(it) }
        vm.followRemoteSchedule(listOf(song("s1", "Remote")))

        vm.removeItem("s1")

        assertEquals(listOf("s1"), removed)
        assertEquals(1, vm.scheduleItems.size, "the follower waits for the primary's next broadcast to drop it")
    }

    @Test
    fun `a removal with no push wiring leaves the mirror untouched`() {
        val vm = newViewModel()
        vm.followRemoteSchedule(listOf(song("s1", "Remote")))
        vm.removeItem("s1")
        assertEquals(1, vm.scheduleItems.size)
    }

    @Test
    fun `reordering while following is refused and reports no new index`() {
        val vm = newViewModel()
        vm.followRemoteSchedule(listOf(song("s1", "A"), song("s2", "B")))

        assertEquals(-1, vm.moveItemUp("s2"))
        assertEquals(-1, vm.moveItemDown("s1"))
        assertEquals(-1, vm.moveItemToTop("s2"))
        assertEquals(-1, vm.moveItemToBottom("s1"))
        vm.moveItem(0, 1)

        assertEquals(listOf("s1", "s2"), vm.scheduleItems.map { it.id }, "the mirror must stay in the primary's order")
    }

    @Test
    fun `in-place edits while following are refused`() {
        val vm = newViewModel()
        vm.followRemoteSchedule(listOf(
            ScheduleItem.LabelItem("l1", "Offering", "#FFF", "#000"),
            ScheduleItem.WebsiteItem("w1", "https://example.org", "https://example.org")
        ))

        vm.updateLabel("l1", "Communion", "#000000", "#FFEB3B")
        vm.updateWebsiteTitle("https://example.org", "Example Domain")

        assertEquals("Offering", (vm.scheduleItems[0] as ScheduleItem.LabelItem).text)
        assertEquals("https://example.org", (vm.scheduleItems[1] as ScheduleItem.WebsiteItem).title)
    }

    @Test
    fun `clearing and starting a new schedule while following are refused`() {
        val vm = newViewModel()
        vm.followRemoteSchedule(listOf(song("s1", "Remote")))

        vm.clearSchedule()
        assertEquals(1, vm.scheduleItems.size)

        vm.newSchedule()
        assertEquals(1, vm.scheduleItems.size, "a follower cannot start its own service")
    }

    @Test
    fun `selection stays local to the follower`() {
        val vm = newViewModel()
        vm.followRemoteSchedule(listOf(song("s1", "Remote")))

        vm.selectItem("s1")
        assertEquals("s1", vm.selectedItemId, "highlighting a row is a view concern, not a schedule edit")

        vm.clearSelection()
        assertNull(vm.selectedItemId)
    }

    @Test
    fun `handing control back keeps the mirrored items as the starting point`() {
        val vm = newViewModel()
        vm.followRemoteSchedule(listOf(song("s1", "Remote")))
        vm.stopFollowingRemote()

        vm.addSong(2, "Local", "Hymnal")

        assertEquals(
            listOf("Remote", "Local"),
            vm.scheduleItems.map { (it as ScheduleItem.SongItem).title },
            "a dropped link must leave the operator with what was on screen, not an empty list"
        )
    }

    @Test
    fun `a planned row with no push wiring is dropped while following`() {
        val vm = newViewModel()
        vm.followRemoteSchedule(emptyList())

        vm.addRow(song("planned", "Planned"), null)

        assertTrue(vm.scheduleItems.isEmpty())
    }
}

package org.churchpresenter.schedule

import androidx.compose.foundation.lazy.LazyListState
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleAutomationActionsTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private val created = mutableListOf<ScheduleViewModel>()

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-automation-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun newViewModel() = ScheduleViewModel().also { created.add(it) }

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, id, "Hymnal")

    private val noFiles = ScheduleFileCommands(open = {}, save = {}, saveAs = {})

    @Test
    fun `the file actions are the commands the tab was given`() {
        val calls = mutableListOf<String>()
        val actions = newViewModel().tabActions(
            ScheduleFileCommands(open = { calls += "open" }, save = { calls += "save" }, saveAs = { calls += "saveAs" })
        )
        actions.openSchedule()
        actions.saveSchedule()
        actions.saveScheduleAs()
        assertEquals(listOf("open", "save", "saveAs"), calls)
    }

    @Test
    fun `a planned row keeps its id and its timing`() {
        val vm = newViewModel()
        val actions = vm.tabActions(noFiles)
        val timing = RowTiming(startAt = "10:15")

        actions.addRow(song("planned"), timing)

        assertEquals("planned", vm.scheduleItems.single().id)
        assertEquals(timing, actions.currentTiming()["planned"])
    }

    @Test
    fun `a cue arrives under a fresh id with its payload`() {
        val vm = newViewModel()
        val cue = ScheduleItem.CueItem("template", CueAction.BLANK, label = "Blank", absoluteTime = "10:10")

        vm.tabActions(noFiles).addCue(cue)

        val added = vm.scheduleItems.single() as ScheduleItem.CueItem
        assertNotEquals("template", added.id)
        assertEquals("Blank", added.label)
        assertEquals("10:10", added.absoluteTime)
    }

    @Test
    fun `the automation selecting a row marks it live without toggling it off`() {
        val vm = newViewModel()
        vm.addRow(song("s1"), null)
        val actions = vm.tabActions(noFiles)

        actions.selectItem("s1")
        actions.selectItem("s1")

        assertEquals("s1", vm.selectedItemId)
        assertEquals("s1", vm.liveRowId)
    }

    @Test
    fun `the loaded service's start anchors the clock until the schedule is cleared`() {
        val vm = newViewModel()
        vm.addRow(song("s1"), null)

        vm.tabActions(noFiles).setServiceStart("10:00")
        assertEquals("10:00", vm.serviceStartTime)

        vm.clearSchedule()
        assertNull(vm.serviceStartTime)
    }

    @Test
    fun `the timing handed out is a copy the caller cannot change`() {
        val vm = newViewModel()
        vm.addRow(song("s1"), RowTiming(startAt = "09:30"))
        val snapshot = vm.tabActions(noFiles).currentTiming()

        vm.removeItem("s1")

        assertEquals("09:30", snapshot["s1"]?.startAt)
        assertTrue(vm.timing.isEmpty())
    }

    @Test
    fun `presenting a row tells the listener which row went live`() {
        val vm = newViewModel()
        val presented = mutableListOf<ScheduleItem>()
        vm.onItemPresented = { presented += it }
        val item = song("s1")

        vm.presentItem(item, onPresenting = {})

        assertEquals(listOf<ScheduleItem>(item), presented)
        assertEquals("s1", vm.liveRowId)
    }

    @Test
    fun `presenting a label tells the listener but puts nothing live`() {
        val vm = newViewModel()
        val modes = mutableListOf<Presenting>()
        val label = ScheduleItem.LabelItem("l1", "Welcome", "#FFFFFF", "#000000")

        vm.presentItem(label, onPresenting = { modes += it })

        assertNull(vm.liveRowId)
        assertTrue(modes.isEmpty())
    }

    @Test
    fun `a row dropped on the delete zone is removed along with its selection`() {
        val vm = newViewModel()
        vm.addRow(song("a"), null)
        vm.addRow(song("b"), null)
        vm.selectItem("b")

        vm.finishRowDrag(index = 1, overDeleteZone = true, targetIndex = null)

        assertEquals(listOf("a"), vm.scheduleItems.map { it.id })
        assertNull(vm.selectedItemId)
    }

    @Test
    fun `dropping an unselected row on the delete zone keeps the selection`() {
        val vm = newViewModel()
        vm.addRow(song("a"), null)
        vm.addRow(song("b"), null)
        vm.selectItem("a")

        vm.finishRowDrag(index = 1, overDeleteZone = true, targetIndex = null)

        assertEquals("a", vm.selectedItemId)
    }

    @Test
    fun `a row dropped over another slot moves there`() {
        val vm = newViewModel()
        listOf("a", "b", "c").forEach { vm.addRow(song(it), null) }

        vm.finishRowDrag(index = 0, overDeleteZone = false, targetIndex = 2)

        assertEquals(listOf("b", "c", "a"), vm.scheduleItems.map { it.id })
    }

    @Test
    fun `a row put down where it started, or over no slot, stays put`() {
        val vm = newViewModel()
        listOf("a", "b").forEach { vm.addRow(song(it), null) }
        val undoBefore = vm.canUndo

        vm.finishRowDrag(index = 1, overDeleteZone = false, targetIndex = null)
        vm.finishRowDrag(index = 0, overDeleteZone = false, targetIndex = 0)

        assertEquals(listOf("a", "b"), vm.scheduleItems.map { it.id })
        assertEquals(undoBefore, vm.canUndo)
    }

    @Test
    fun `the delete zone with no row under the drag removes nothing`() {
        val vm = newViewModel()
        vm.addRow(song("a"), null)

        vm.finishRowDrag(index = 5, overDeleteZone = true, targetIndex = null)

        assertEquals(listOf("a"), vm.scheduleItems.map { it.id })
    }

    @Test
    fun `a lifted row off screen is held where the pointer is, and put down clean`() {
        val drag = ScheduleDragState()

        drag.arm(index = 3, itemInfo = null, fallbackY = 120f)

        assertTrue(drag.isActive)
        assertEquals(3, drag.fromIndex)
        assertEquals(3, drag.targetIndex)
        assertEquals(120f, drag.cursorY)

        drag.end(index = 3)

        assertFalse(drag.isActive)
        assertEquals(-1, drag.fromIndex)
        assertNull(drag.targetIndex)
        assertEquals(0f, drag.cursorY)
    }

    @Test
    fun `putting down a different row leaves the lifted one's index alone`() {
        val drag = ScheduleDragState()
        drag.arm(index = 2, itemInfo = null, fallbackY = 0f)

        drag.end(index = 4)

        assertEquals(2, drag.fromIndex)
        assertFalse(drag.isActive)
    }

    @Test
    fun `carrying a row to the bottom edge arms the delete zone and keeps its slot`() {
        val drag = ScheduleDragState()
        val geometry = ScheduleDragGeometry(
            listState = LazyListState(),
            thresholdPx = 4f,
            deleteZonePx = 50f,
            listHeightPx = { 400 },
            onDropped = {},
        )
        drag.arm(index = 1, itemInfo = null, fallbackY = 100f)

        drag.moveBy(320f, geometry)

        assertTrue(drag.overDeleteZone)
        assertEquals(1, drag.targetIndex)

        drag.moveBy(-300f, geometry)

        assertFalse(drag.overDeleteZone)
        assertEquals(1, drag.targetIndex, "no slot under the pointer leaves the last target in place")
    }
}

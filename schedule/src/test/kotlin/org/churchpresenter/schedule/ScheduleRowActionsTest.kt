package org.churchpresenter.schedule

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.MediaCommand
import org.churchpresenter.sharedui.models.Presenting
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
 * What a row does when it goes live (`docs/SHOW_CONTROL.md`, Cue actions): kept beside the rows
 * like notes and timing, saved with the file, undone with everything else, and handed over once
 * the row's content is up.
 */
class ScheduleRowActionsTest {

    private lateinit var home: File
    private var realHome: String? = null
    private val created = mutableListOf<ScheduleViewModel>()

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-schedule-actions").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        created.forEach { runCatching { it.dispose() } }
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val song = ScheduleItem.SongItem("s1", 1, "Amazing Grace", "Hymns", "Hymns::1")
    private val label = ScheduleItem.LabelItem("h", "Word", "#FFFFFF", "#000000")
    private val obs = listOf(Action.ObsScene("Pulpit"), Action.Wait(2.0))

    private fun vm(): ScheduleViewModel = ScheduleViewModel().also { vm ->
        created += vm
        vm.addRow(song, null)
        vm.addRow(label, null)
    }

    @Test
    fun `a row's actions are set, read back, and taken away by an empty list`() {
        val vm = vm()
        assertEquals(emptyList(), vm.actionsFor("s1"))
        vm.setActions("s1", obs)
        assertEquals(obs, vm.actionsFor("s1"))
        assertEquals(mapOf("s1" to obs), vm.actions)
        vm.setActions("s1", emptyList())
        assertFalse("s1" in vm.actions)
    }

    @Test
    fun `setting them is an edit that undoes, and setting them unchanged is no edit`() {
        val vm = vm()
        vm.setActions("s1", obs)
        val undoable = vm.canUndo
        vm.undo()
        assertTrue(undoable)
        assertEquals(emptyList(), vm.actionsFor("s1"))
        vm.redo()
        assertEquals(obs, vm.actionsFor("s1"))
        vm.undo()
        vm.redo()
        val depth = vm.undoStack.size
        vm.setActions("s1", obs)
        assertEquals(depth, vm.undoStack.size, "Save with nothing changed pushes no undo step")
    }

    @Test
    fun `removing the row, or clearing the schedule, takes its actions with it`() {
        val vm = vm()
        vm.setActions("s1", obs)
        vm.removeItem("s1")
        assertTrue(vm.actions.isEmpty())
        vm.undo()
        assertEquals(obs, vm.actionsFor("s1"), "undoing the removal brings them back")
        vm.clearSchedule()
        assertTrue(vm.actions.isEmpty())
    }

    @Test
    fun `actions are saved with the file and read back, and a file without any writes none`() {
        val vm = vm()
        val every = listOf(
            Action.Timer(TimerModes.CLOCK, until = "10:30"),
            Action.Media(MediaCommand.STOP),
            Action.GoLive(rowId = "h"),
        )
        vm.setActions("s1", every)
        val reopened = vm()
        val written = vm.json.encodeToString(ScheduleFileV2.serializer(), vm.scheduleFileDocument())
        reopened.replaceSchedule(reopened.decodeSchedule(written), null)
        assertEquals(every, reopened.actionsFor("s1"))

        val plain = vm()
        val none = plain.json.encodeToString(ScheduleFileV2.serializer(), plain.scheduleFileDocument())
        assertFalse("actions" in none, "a schedule with no actions still opens in a build from before them")
    }

    @Test
    fun `a field a newer build added is skipped, not a reason to lose the schedule`() {
        val vm = vm()
        val timing = vm.json.encodeToString(RowTiming.serializer(), RowTiming.DEFAULT)
        val text = """{"version":2,"items":[],"notes":{"s1":"hi"},"timing":{"s1":$timing},"lighting":{"s1":"red"}}"""
        val decoded = vm.decodeSchedule(text)
        assertEquals(mapOf("s1" to "hi"), decoded.notes)
    }

    @Test
    fun `a row's actions are handed over after its content, and a row without any hands none`() {
        val vm = vm()
        vm.setActions("s1", obs)
        val seen = mutableListOf<String>()
        vm.onRowActions = { item, actions -> seen += "actions:${item.id}:${actions.size}" }
        vm.presentItem(song, onPresenting = { seen += "content:$it" })
        vm.presentItem(label, onPresenting = { seen += "content:$it" })
        assertEquals(listOf("content:${Presenting.LYRICS}", "actions:s1:2"), seen)
    }

    @Test
    fun `the tab's actions read every row's actions`() {
        val vm = vm()
        vm.setActions("s1", obs)
        assertEquals(mapOf("s1" to obs), vm.tabActions(ScheduleFileCommands({}, {}, {})).currentActions())
        assertNull(ScheduleTabActions().currentActions()["s1"])
    }
}

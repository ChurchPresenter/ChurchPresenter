package org.churchpresenter.schedule

import androidx.compose.ui.geometry.Offset
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.utils.FileListTransferable
import org.churchpresenter.sharedui.utils.ScheduleItemTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
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
 * A drop between two rows: where it lands ([dropSlot]), the view model putting rows there
 * ([insertingAt]) and the panel's target taking a row from another tab or files from outside.
 */
class ScheduleDropTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private lateinit var viewModel: ScheduleViewModel

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-drop-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        viewModel = ScheduleViewModel()
    }

    @AfterTest
    fun restoreHome() {
        runCatching { viewModel.dispose() }
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun song(id: String) = ScheduleItem.SongItem(id = id, songNumber = 1, title = id, songbook = "Hymnal")

    private fun ids() = viewModel.scheduleItems.map { it.id }

    private fun seed(vararg ids: String) = ids.forEach { viewModel.addOrPush(song(it)) }

    // ── Where a drop lands ──────────────────────────────────────────────────────

    private val rows = listOf(
        DragItemGeometry(index = 0, offset = 0, size = 40),
        DragItemGeometry(index = 1, offset = 40, size = 40),
        DragItemGeometry(index = 2, offset = 80, size = 40),
    )

    @Test
    fun `a drop on the top half of a row goes in front of it`() {
        assertEquals(DropSlot(1, 40f), dropSlot(cursorY = 50f, visibleItems = rows))
    }

    @Test
    fun `a drop on the bottom half of a row goes after it`() {
        assertEquals(DropSlot(2, 80f), dropSlot(cursorY = 70f, visibleItems = rows))
    }

    @Test
    fun `a drop above the first row goes at the top`() {
        assertEquals(DropSlot(0, 0f), dropSlot(cursorY = -5f, visibleItems = rows))
    }

    @Test
    fun `a drop below every row goes after the last`() {
        assertEquals(DropSlot(3, 120f), dropSlot(cursorY = 500f, visibleItems = rows))
    }

    @Test
    fun `an empty list has the one slot at the top`() {
        assertEquals(DropSlot(0, 0f), dropSlot(cursorY = 200f, visibleItems = emptyList()))
    }

    @Test
    fun `a scrolled list counts from the first row it shows`() {
        val scrolled = listOf(DragItemGeometry(7, 0, 40), DragItemGeometry(8, 40, 40))
        assertEquals(DropSlot(7, 0f), dropSlot(cursorY = 10f, visibleItems = scrolled))
    }

    @Test
    fun `a drop point is the window units times the display scale`() {
        assertEquals(Offset(200f, 90f), scaledDropPoint(100, 45, 2.0))
        assertEquals(Offset(150f, 67.5f), scaledDropPoint(100, 45, 1.5))
    }

    // ── Inserting at a slot ─────────────────────────────────────────────────────

    @Test
    fun `rows added while inserting go in at the slot, in order`() {
        seed("a", "b")
        viewModel.insertingAt(1) {
            viewModel.addOrPush(song("x"))
            viewModel.addOrPush(song("y"))
        }
        assertEquals(listOf("a", "x", "y", "b"), ids())
    }

    @Test
    fun `rows added after inserting go on the end again`() {
        seed("a", "b")
        viewModel.insertingAt(0) { viewModel.addOrPush(song("x")) }
        viewModel.addOrPush(song("z"))
        assertEquals(listOf("x", "a", "b", "z"), ids())
    }

    @Test
    fun `no slot adds at the end`() {
        seed("a")
        viewModel.insertingAt(null) { viewModel.addOrPush(song("x")) }
        assertEquals(listOf("a", "x"), ids())
    }

    @Test
    fun `a slot past the end is the end`() {
        seed("a")
        viewModel.insertingAt(9) { viewModel.addOrPush(song("x")) }
        assertEquals(listOf("a", "x"), ids())
    }

    @Test
    fun `a block that throws still puts the next add on the end`() {
        seed("a", "b")
        runCatching { viewModel.insertingAt(0) { error("drop failed") } }
        viewModel.addOrPush(song("z"))
        assertEquals(listOf("a", "b", "z"), ids())
    }

    @Test
    fun `an insert is undone as one more add`() {
        seed("a", "b")
        viewModel.insertingAt(1) { viewModel.addOrPush(song("x")) }
        viewModel.undo()
        assertEquals(listOf("a", "b"), ids())
    }

    @Test
    fun `a follower sends the slot to the primary, one further for each row`() {
        val pushed = mutableListOf<Pair<String, Int?>>()
        viewModel.onPushToRemoteSchedule = { item, index -> pushed += item.id to index }
        viewModel.followRemoteSchedule(listOf(song("a"), song("b"), song("c")))

        viewModel.insertingAt(1) {
            viewModel.addOrPush(song("x"))
            viewModel.addOrPush(song("y"))
        }
        viewModel.addOrPush(song("z"))

        assertEquals(listOf("x" to 1, "y" to 2, "z" to null), pushed)
        assertEquals(listOf("a", "b", "c"), ids(), "a follower's mirror changes only when the primary says")
    }

    // ── The panel's drop target ─────────────────────────────────────────────────

    private fun dropTarget(slot: Int?) = ScheduleFileDrop { viewModel }.apply {
        locate = { position -> slot?.let { DropSlot(it, position.y) } }
    }

    @Test
    fun `a row from another tab goes in where it was let go`() {
        seed("a", "b")
        val drop = dropTarget(slot = 1)
        assertTrue(drop.drop(ScheduleItemTransferable(song("dragged")), Offset(0f, 50f)))
        assertEquals(listOf("a", "dragged", "b"), ids())
    }

    @Test
    fun `a drop with no position goes on the end`() {
        seed("a", "b")
        val drop = dropTarget(slot = 0)
        assertTrue(drop.drop(ScheduleItemTransferable(song("dragged")), position = null))
        assertEquals(listOf("a", "b", "dragged"), ids())
    }

    @Test
    fun `files from outside go in where they were let go`() {
        seed("a", "b")
        val clip = File(tempHome, "Welcome.mp4").apply { writeText("") }
        val drop = dropTarget(slot = 1)
        assertTrue(drop.drop(FileListTransferable(listOf(clip)), Offset(0f, 50f)))
        val added = viewModel.scheduleItems[1]
        assertTrue(added is ScheduleItem.MediaItem && added.mediaUrl == clip.absolutePath)
        assertEquals(3, viewModel.scheduleItems.size)
    }

    @Test
    fun `nothing it can read is not taken`() {
        seed("a")
        val drop = dropTarget(slot = 0)
        assertFalse(drop.drop(null, Offset.Zero))
        assertFalse(drop.drop(Unreadable, Offset.Zero))
        assertEquals(listOf("a"), ids())
    }

    @Test
    fun `the panel takes rows and files and nothing else`() {
        val drop = dropTarget(slot = 0)
        assertTrue(drop.accepts(ScheduleItemTransferable(song("a"))))
        assertTrue(drop.accepts(FileListTransferable(emptyList())))
        assertFalse(drop.accepts(Unreadable))
        assertFalse(drop.accepts(null as Transferable?))
    }

    @Test
    fun `the slot is where the pointer is, and none without a list or a position`() {
        assertEquals(DropSlot(2, 30f), dropTarget(slot = 2).slotAt(Offset(0f, 30f)))
        assertNull(dropTarget(slot = 2).slotAt(null))
        assertNull(ScheduleFileDrop { viewModel }.slotAt(Offset(0f, 30f)))
        val broken = ScheduleFileDrop { viewModel }.apply { locate = { error("no layout yet") } }
        assertNull(broken.slotAt(Offset.Zero))
    }

    @Test
    fun `a drop clears the slot and the highlight`() {
        val drop = dropTarget(slot = 0).apply {
            dragOver = true
            slot = DropSlot(0, 0f)
        }
        drop.drop(ScheduleItemTransferable(song("a")), Offset.Zero)
        assertFalse(drop.dragOver)
        assertNull(drop.slot)
    }

    /** A drag whose data says it carries rows but throws when asked for them. */
    private object Unreadable : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = emptyArray()
        override fun isDataFlavorSupported(flavor: DataFlavor) = false
        override fun getTransferData(flavor: DataFlavor): Any = error("unreadable")
    }
}

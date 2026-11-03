package org.churchpresenter.sharedui.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What a tab hands the schedule when it is dragged: a row, or files. */
class ScheduleDragTest {

    private val song = ScheduleItem.SongItem(id = "s1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")

    @Test
    fun `a dragged row comes back as the same row`() {
        val drag = ScheduleItemTransferable(song)
        assertTrue(drag.carriesScheduleItem())
        assertSame(song, drag.scheduleItemOrNull())
        assertEquals(listOf(ScheduleItemFlavor), drag.transferDataFlavors.toList())
    }

    @Test
    fun `a row is not offered as anything else`() {
        val drag = ScheduleItemTransferable(song)
        assertFalse(drag.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        assertFailsWith<UnsupportedFlavorException> { drag.getTransferData(DataFlavor.stringFlavor) }
    }

    @Test
    fun `the row flavor stays inside the app`() {
        assertEquals(DataFlavor.javaJVMLocalObjectMimeType, ScheduleItemFlavor.mimeType.substringBefore(';'))
        assertEquals(ScheduleItem::class.java, ScheduleItemFlavor.representationClass)
    }

    @Test
    fun `dragged files are a file list, as from Finder or Explorer`() {
        val files = listOf(File("sunrise.jpg"))
        val drag = FileListTransferable(files)
        assertTrue(drag.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        assertEquals(files, drag.getTransferData(DataFlavor.javaFileListFlavor))
        assertFalse(drag.carriesScheduleItem())
        assertNull(drag.scheduleItemOrNull())
        assertFailsWith<UnsupportedFlavorException> { drag.getTransferData(ScheduleItemFlavor) }
    }

    @Test
    fun `a drag of something else carries no row`() {
        val text = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.stringFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.stringFlavor
            override fun getTransferData(flavor: DataFlavor): Any = "hello"
        }
        assertNull(text.scheduleItemOrNull())
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `either drag is a copy, never a move`() {
        // What each carries is the transferable above; Compose wraps it in a type it keeps internal.
        assertEquals(listOf(DragAndDropTransferAction.Copy), scheduleItemDragData(song).supportedActions.toList())
        assertEquals(listOf(DragAndDropTransferAction.Copy), fileDragData(emptyList()).supportedActions.toList())
    }
}

package org.churchpresenter.sharedui.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File

/**
 * What a tab drags onto the schedule from inside the app: a ready-made row. JVM-local, so it never
 * leaves the app — another program offered it sees nothing it can read.
 */
val ScheduleItemFlavor: DataFlavor = DataFlavor(
    "${DataFlavor.javaJVMLocalObjectMimeType};class=${ScheduleItem::class.java.name}",
    null,
    ScheduleItem::class.java.classLoader,
)

/** A drag carrying one schedule row, [item]. */
class ScheduleItemTransferable(private val item: ScheduleItem) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(ScheduleItemFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == ScheduleItemFlavor
    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return item
    }
}

/** A drag carrying [files], the same flavor a drag from Finder or Explorer carries. */
class FileListTransferable(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor
    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return files
    }
}

/**
 * True when this drag carries a schedule row. A flavor check only: asked mid-drag, when macOS
 * refuses `getTransferData`.
 */
fun Transferable.carriesScheduleItem(): Boolean = isDataFlavorSupported(ScheduleItemFlavor)

/** The schedule row this drag carries, or null when it carries none. Asked at the drop. */
fun Transferable.scheduleItemOrNull(): ScheduleItem? =
    takeIf { it.carriesScheduleItem() }?.getTransferData(ScheduleItemFlavor) as? ScheduleItem

/** What `dragAndDropSource` hands over for a schedule row, [item]. */
@OptIn(ExperimentalComposeUiApi::class)
fun scheduleItemDragData(item: ScheduleItem): DragAndDropTransferData =
    DragAndDropTransferData(
        transferable = DragAndDropTransferable(ScheduleItemTransferable(item)),
        supportedActions = listOf(DragAndDropTransferAction.Copy),
    )

/** What `dragAndDropSource` hands over for [files]: the schedule takes them as a file drop. */
@OptIn(ExperimentalComposeUiApi::class)
fun fileDragData(files: List<File>): DragAndDropTransferData =
    DragAndDropTransferData(
        transferable = DragAndDropTransferable(FileListTransferable(files)),
        supportedActions = listOf(DragAndDropTransferAction.Copy),
    )

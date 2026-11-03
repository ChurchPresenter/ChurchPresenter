package org.churchpresenter.schedule

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.geometry.Offset
import org.churchpresenter.sharedui.utils.carriesScheduleItem
import org.churchpresenter.sharedui.utils.scheduleItemOrNull
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.Component
import java.awt.Point
import java.awt.datatransfer.Transferable

/**
 * Compose's own drop target, attached to the panel's node so it cannot aim at the wrong window.
 * It takes files from outside the app and rows dragged from the app's own tabs, and puts either
 * where it was let go.
 */
@Stable
internal class ScheduleFileDrop(private val viewModel: () -> ScheduleViewModel) : DragAndDropTarget {
    /** True while a file, or a row from another tab, is being dragged over this panel. */
    var dragOver by mutableStateOf(false)

    /** The files the last drop could not use, named so the message can say which. */
    var skippedFiles by mutableStateOf(emptyList<String>())

    /** The slot under the pointer while a drag is over the panel; null when there is none. */
    var slot by mutableStateOf<DropSlot?>(null)

    /** Turns a pointer position in the window into a slot in the list. Set by the row list. */
    var locate: (Offset) -> DropSlot? = { null }

    override fun onEntered(event: DragAndDropEvent) {
        dragOver = true
        slot = slotAt(event.dropPosition())
    }
    override fun onMoved(event: DragAndDropEvent) { slot = slotAt(event.dropPosition()) }
    override fun onExited(event: DragAndDropEvent) { clear() }
    override fun onEnded(event: DragAndDropEvent) { clear() }

    override fun onDrop(event: DragAndDropEvent): Boolean = drop(event.transferableOrNull(), event.dropPosition())

    fun accepts(event: DragAndDropEvent): Boolean = accepts(event.transferableOrNull())

    /** Whether [data] is something this panel takes: files, or a row from another tab. */
    fun accepts(data: Transferable?): Boolean =
        data != null && (data.carriesFileList() || data.carriesScheduleItem())

    /**
     * Puts what [data] carries into the schedule at the slot under [position] — at the end when
     * there is none. A row from another tab goes in as it is; files go in the way the file picker
     * would add them. False when [data] carries neither.
     */
    fun drop(data: Transferable?, position: Offset?): Boolean {
        val at = slotAt(position)?.index
        clear()
        val vm = viewModel()
        readOrNull { data?.scheduleItemOrNull() }?.let { item ->
            vm.insertingAt(at) { vm.addOrPush(item) }
            return true
        }
        val files = readOrNull { data?.fileListOrNull() } ?: return false
        vm.insertingAt(at) { skippedFiles = handleDroppedFiles(files, vm) }
        return true
    }

    /** The slot under [position], in the window's pixels; null with no position or no list. */
    fun slotAt(position: Offset?): DropSlot? = position?.let { readOrNull { locate(it) } }

    private fun clear() {
        dragOver = false
        slot = null
    }
}

/**
 * Data that cannot be read is data this panel does not take, and there is nothing to tell the
 * operator at that point -- so a read that throws answers null.
 */
@Suppress("SwallowedException", "TooGenericExceptionCaught")
private inline fun <T> readOrNull(read: () -> T?): T? = try {
    read()
} catch (_: Exception) {
    null
}

/**
 * The AWT data behind a drag: the one part of a drop a test cannot reach, so it is all this does.
 * Everything that reads it ([ScheduleFileDrop.accepts], [ScheduleFileDrop.drop]) takes a plain
 * [Transferable], which a test hands a stand-in.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.transferableOrNull(): Transferable? = readOrNull { awtTransferable }

/** Where this drag is, in the window's pixels; the other half a test cannot reach. */
@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.dropPosition(): Offset? = readOrNull {
    when (val e = nativeEvent) {
        is DropTargetDragEvent -> windowPixels(e.location, e.dropTargetContext?.component)
        is DropTargetDropEvent -> windowPixels(e.location, e.dropTargetContext?.component)
        else -> null
    }
}

private fun windowPixels(point: Point, component: Component?): Offset =
    scaledDropPoint(point.x, point.y, component?.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0)

/**
 * A drop point in the window's pixels -- the space `positionInRoot()` measures the rows in. AWT
 * gives it in the window's units, at [x], [y]; Compose multiplies by the display's [scale] the same
 * way (its own `positionInRoot` for a drop is internal on the desktop).
 */
internal fun scaledDropPoint(x: Int, y: Int, scale: Double): Offset =
    Offset((x * scale).toFloat(), (y * scale).toFloat())

/**
 * Runs [block] with every row it adds landing at [index] and on, in order, instead of at the end —
 * a drop between two rows. A null [index] adds at the end, as ever.
 */
internal fun ScheduleViewModel.insertingAt(index: Int?, block: () -> Unit) {
    insertionIndex = index
    try {
        block()
    } finally {
        insertionIndex = null
    }
}

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.presenter.overlapsOf
import org.churchpresenter.app.churchpresenter.presenter.rectIn
import org.churchpresenter.settings.TextBox
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic
import java.awt.Cursor
import kotlin.math.abs

private val BOX_HANDLE = 9.dp
private const val BOX_PICKED_BORDER = 2
private const val SNAP_PERCENT = 1.2f
private const val HALF = 0.5f

/**
 * One edge-or-corner handle of a box: which edges it moves, each `-1` for the left or top edge, `1`
 * for the right or bottom, `0` for neither.
 */
internal enum class BoxGrip(val dx: Int, val dy: Int) {
    TOP_LEFT(-1, -1), TOP(0, -1), TOP_RIGHT(1, -1), RIGHT(1, 0),
    BOTTOM_RIGHT(1, 1), BOTTOM(0, 1), BOTTOM_LEFT(-1, 1), LEFT(-1, 0),
}

/** [box] moved by [dx] × [dy] percent, kept inside its area. */
internal fun TextBox.movedBy(dx: Float, dy: Float): TextBox = copy(
    xPercent = (xPercent + dx).coerceIn(0f, TextBox.FULL_PERCENT - widthPercent),
    yPercent = (yPercent + dy).coerceIn(0f, TextBox.FULL_PERCENT - heightPercent),
)

/**
 * [this] with the edges [grip] names dragged by [dx] × [dy] percent, never smaller than a sliver nor
 * outside its area.
 */
internal fun TextBox.resizedBy(grip: BoxGrip, dx: Float, dy: Float): TextBox {
    val min = TextBox.MIN_SIZE_PERCENT
    val full = TextBox.FULL_PERCENT
    var left = xPercent
    var top = yPercent
    var right = rightPercent
    var bottom = bottomPercent
    when (grip.dx) {
        -1 -> left = (left + dx).coerceIn(0f, right - min)
        1 -> right = (right + dx).coerceIn(left + min, full)
    }
    when (grip.dy) {
        -1 -> top = (top + dy).coerceIn(0f, bottom - min)
        1 -> bottom = (bottom + dy).coerceIn(top + min, full)
    }
    return copy(xPercent = left, yPercent = top, widthPercent = right - left, heightPercent = bottom - top)
}

/**
 * [box] with its edges pulled onto the nearest of [lines] -- percent positions across and down: the
 * area's edges and centre, and the other boxes' edges -- when one is within a snap's reach. Its left,
 * centre and right are each tried across, its top, middle and bottom down; the closest pull wins.
 */
internal fun TextBox.snappedTo(across: List<Float>, down: List<Float>): TextBox {
    fun pull(edges: List<Float>, lines: List<Float>): Float? = edges.flatMap { edge -> lines.map { it - edge } }
        .filter { abs(it) <= SNAP_PERCENT }
        .minByOrNull { abs(it) }
    val dx = pull(listOf(xPercent, xPercent + widthPercent * HALF, rightPercent), across) ?: 0f
    val dy = pull(listOf(yPercent, yPercent + heightPercent * HALF, bottomPercent), down) ?: 0f
    return movedBy(dx, dy)
}

/** The lines a box snaps to: its area's edges and centre, and every other box's edges and centre. */
internal fun snapLines(others: List<TextBox>): Pair<List<Float>, List<Float>> {
    val full = TextBox.FULL_PERCENT
    val across = listOf(0f, full * HALF, full) + others.flatMap {
        listOf(it.xPercent, it.xPercent + it.widthPercent * HALF, it.rightPercent)
    }
    val down = listOf(0f, full * HALF, full) + others.flatMap {
        listOf(it.yPercent, it.yPercent + it.heightPercent * HALF, it.bottomPercent)
    }
    return across to down
}

/**
 * The page's text boxes over the preview, in [area] (the overlay's dp): each outlined -- red where
 * it overlaps another -- and clicked to point the Text rows at its item; the one they point at
 * solidly outlined, dragged by its body to move it and by its eight handles to resize it.
 *
 * [scale] is preview dp per output pixel, what every drag is handed back in. A dragged box snaps to
 * guides while the page asks it to, unless Alt is held.
 */
@Composable
internal fun BoxHandles(targets: BoxTargets, area: Rect, scale: Float) {
    val colors = MaterialTheme.semantic
    val error = MaterialTheme.colorScheme.error
    val rects = targets.handles.map { it.box.rectIn(area) }
    // A page whose rows do not say which box they point at leaves the pick to the preview itself.
    var pickedHere by remember { mutableStateOf<String?>(null) }
    val selected = targets.selected ?: pickedHere
    targets.handles.forEachIndexed { index, handle ->
        val rect = rects[index]
        val overlaps = rect.overlapsOf(rects.filterIndexed { j, _ -> j != index }).isNotEmpty()
        val picked = handle.key == selected
        val color = if (overlaps) error else colors.adjustAccent
        Box(
            Modifier
                .offset(rect.left.dp, rect.top.dp)
                .size(rect.width.dp, rect.height.dp)
                .then(
                    if (picked || overlaps) {
                        Modifier.border(BOX_PICKED_BORDER.dp, color, AppShape(2.dp))
                    } else {
                        Modifier.dashedBorder(color, 2.dp)
                    },
                )
                .then(
                    if (picked) {
                        Modifier
                    } else {
                        Modifier.clickable {
                            pickedHere = handle.key
                            handle.onPick()
                        }
                    },
                )
                .testTag(adjustBoxTag(handle.key)),
        )
    }
    val picked = targets.handles.firstOrNull { it.key == selected } ?: return
    val others = targets.handles.filter { it !== picked }.map { it.box }
    MovableBox(picked, area, scale, others, targets.options.snap)
}

/** The picked box: its body dragged to move it, its handles to resize it. */
@Composable
private fun MovableBox(handle: BoxHandle, area: Rect, scale: Float, others: List<TextBox>, snap: Boolean) {
    val rect = handle.box.rectIn(area)
    var from by remember { mutableStateOf(handle.box) }
    val windowInfo = LocalWindowInfo.current
    // Output pixels to percent of the area, on each axis.
    fun percent(total: Offset) = Offset(
        total.x * scale / area.width * TextBox.FULL_PERCENT,
        total.y * scale / area.height * TextBox.FULL_PERCENT,
    )
    val (across, down) = snapLines(others)
    fun snapped(box: TextBox) =
        if (snap && !windowInfo.keyboardModifiers.isAltPressed) box.snappedTo(across, down) else box
    Box(
        Modifier
            .offset(rect.left.dp, rect.top.dp)
            .size(rect.width.dp, rect.height.dp)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR)))
            .adjustDrag(scale, onStart = { from = handle.box }, onDrag = { total ->
                val move = percent(total)
                handle.onChange(snapped(from.movedBy(move.x, move.y)))
            })
            .testTag(ADJUST_BOX_MOVE_TAG),
    )
    BoxGrip.entries.forEach { grip ->
        val x = rect.left + rect.width * (grip.dx + 1) * HALF
        val y = rect.top + rect.height * (grip.dy + 1) * HALF
        Box(
            Modifier
                .offset(x.dp - BOX_HANDLE / 2, y.dp - BOX_HANDLE / 2)
                .size(BOX_HANDLE)
                .background(MaterialTheme.semantic.adjustAccent, AppShape(2.dp))
                .pointerHoverIcon(PointerIcon(Cursor(gripCursor(grip))))
                .adjustDrag(scale, onStart = { from = handle.box }, onDrag = { total ->
                    val move = percent(total)
                    handle.onChange(from.resizedBy(grip, move.x, move.y))
                })
                .testTag(adjustBoxGripTag(grip)),
        )
    }
}

/** The resize cursor each handle shows. */
private fun gripCursor(grip: BoxGrip): Int = when (grip) {
    BoxGrip.TOP_LEFT -> Cursor.NW_RESIZE_CURSOR
    BoxGrip.TOP -> Cursor.N_RESIZE_CURSOR
    BoxGrip.TOP_RIGHT -> Cursor.NE_RESIZE_CURSOR
    BoxGrip.RIGHT -> Cursor.E_RESIZE_CURSOR
    BoxGrip.BOTTOM_RIGHT -> Cursor.SE_RESIZE_CURSOR
    BoxGrip.BOTTOM -> Cursor.S_RESIZE_CURSOR
    BoxGrip.BOTTOM_LEFT -> Cursor.SW_RESIZE_CURSOR
    BoxGrip.LEFT -> Cursor.W_RESIZE_CURSOR
}

/** Test handles for the box handles. */
internal fun adjustBoxTag(key: String): String = "profile_adjust_box_$key"
internal fun adjustBoxGripTag(grip: BoxGrip): String = "profile_adjust_box_grip_${grip.name}"
internal const val ADJUST_BOX_MOVE_TAG = "profile_adjust_box_move"

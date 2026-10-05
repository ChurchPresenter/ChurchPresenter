package org.churchpresenter.profiles

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.presenter.PresentedBlock
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic
import java.awt.Cursor
import kotlin.math.roundToInt

private const val UNPICKED_ALPHA = 0.55f

/** Where [block] was drawn, in the overlay's own dp, or null while it has not been laid out. */
internal fun Map<PresentedBlock, Rect>.frameOf(block: PresentedBlock, origin: Offset, density: Float): AdjustFrame? =
    this[block]?.let { r ->
        AdjustFrame(
            left = ((r.left - origin.x) / density).dp,
            top = ((r.top - origin.y) / density).dp,
            right = ((r.right - origin.x) / density).dp,
            bottom = ((r.bottom - origin.y) / density).dp,
        )
    }

/**
 * The translation or language blocks: each one faintly outlined, and a click picks it as the target;
 * the picked one solidly outlined. Dragged, a block is picked and moves on its own -- the block
 * itself is the handle. Drawn under every other handle, since a block is large and a handle over it
 * is small.
 */
@Composable
internal fun BlockOutlines(targets: BlockTargets, frames: List<AdjustFrame?>, scale: Float) {
    val semantic = MaterialTheme.semantic
    // A lone block has nothing to be picked from -- unless the reference is, when the verse is, or
    // it can be moved, when it is the handle that moves it.
    val referencePicked = targets.reference?.picked == true
    val shift by rememberUpdatedState(targets.shift)
    if (frames.size > 1 || referencePicked || targets.shift != null) frames.forEachIndexed { index, frame ->
        if (frame == null) return@forEachIndexed
        // With the reference picked, its verse's block is a click away from being picked again.
        val picked = index == targets.selected && !referencePicked
        // Where the drag began and what the block's move was then, noted on the first frame the
        // picked block's move is to hand -- a block picked by the drag itself only gets one after.
        var from by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var base by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .offset(frame.left, frame.top)
                .size(frame.width, frame.height)
                .then(
                    if (picked) {
                        Modifier.border(2.dp, semantic.adjustHandle, AppShape(4.dp))
                    } else {
                        Modifier
                            .dashedBorder(semantic.adjustHandle.copy(alpha = UNPICKED_ALPHA), 4.dp)
                            .clickable { targets.onSelect(index) }
                    },
                )
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR)))
                .adjustDrag(
                    scale,
                    onStart = {
                        if (!picked) targets.onSelect(index)
                        from = null
                    },
                    onDrag = { total ->
                        val move = shift ?: return@adjustDrag
                        val start = from ?: move.value.also { from = it; base = total }
                        val x = (start.first + total.x - base.x).roundToInt()
                        move.onChange(x to (start.second + total.y - base.y).roundToInt())
                    },
                )
                .testTag(adjustBlockTag(index)),
        )
    }
}

/**
 * On the Bible page, the reference outlined in orange, which is dragged anywhere on its own. Drawn
 * over every other handle: it is small and must win where it overlaps a larger one.
 */
@Composable
internal fun BlockGrips(
    targets: BlockTargets,
    reference: AdjustFrame?,
    referenceBounds: AdjustFrame,
    scale: Float,
) {
    val ref = targets.reference
    if (ref != null && reference != null) ReferenceHandle(ref, reference, referenceBounds, scale)
}

/**
 * The reference, outlined in orange -- solid while the Text rows point at it. Clicked, it is picked;
 * dragged, it is picked and moves anywhere on its own, from where its position puts it -- but never
 * out of [bounds], its translation's cell, which would clip it out of sight and its handle with it.
 */
@Composable
private fun ReferenceHandle(reference: ReferenceTarget, frame: AdjustFrame, bounds: AdjustFrame, scale: Float) {
    var from by remember { mutableStateOf(reference.shift.value) }
    var room by remember { mutableStateOf(DragRoom.NONE) }
    val accent = MaterialTheme.semantic.adjustAccent
    Box(
        Modifier
            .offset(frame.left, frame.top)
            .size(frame.width, frame.height)
            .then(
                if (reference.picked) Modifier.border(2.dp, accent, AppShape(4.dp))
                else Modifier.dashedBorder(accent, 3.dp),
            )
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR)))
            .clickable { reference.onPick() }
            .adjustDrag(
                scale,
                onStart = {
                    from = reference.shift.value
                    room = DragRoom.within(frame, bounds, scale)
                    if (!reference.picked) reference.onPick()
                },
                onDrag = { total ->
                    val x = (from.first + total.x.coerceIn(room.left, room.right)).roundToInt()
                    reference.shift.onChange(x to (from.second + total.y.coerceIn(room.up, room.down)).roundToInt())
                },
            )
            .testTag(ADJUST_REFERENCE_TAG),
    )
}

/**
 * How far a block may be dragged each way, in output pixels, before it leaves the frame it must stay
 * in. Each is at least nothing: a block already at or past an edge may still be dragged back.
 */
internal data class DragRoom(val left: Float, val right: Float, val up: Float, val down: Float) {
    companion object {
        val NONE = DragRoom(0f, 0f, 0f, 0f)

        /** The room [frame] has inside [bounds], at [scale] preview dp per output pixel. */
        fun within(frame: AdjustFrame, bounds: AdjustFrame, scale: Float): DragRoom = DragRoom(
            left = minOf(0f, (bounds.left - frame.left).value / scale),
            right = maxOf(0f, (bounds.right - frame.right).value / scale),
            up = minOf(0f, (bounds.top - frame.top).value / scale),
            down = maxOf(0f, (bounds.bottom - frame.bottom).value / scale),
        )
    }
}

/** Test handles for the block handles. */
internal fun adjustBlockTag(index: Int): String = "profile_adjust_block_$index"
internal const val ADJUST_REFERENCE_TAG = "profile_adjust_reference"

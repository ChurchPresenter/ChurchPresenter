package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.presenter.PresentedBlock
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic
import java.awt.Cursor
import kotlin.math.roundToInt

private val MOVE_DOT = 12.dp
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
 * the picked one solidly outlined. Drawn under every other handle, since a block is large and a
 * handle over it is small.
 */
@Composable
internal fun BlockOutlines(targets: BlockTargets, frames: List<AdjustFrame?>) {
    val semantic = MaterialTheme.semantic
    // A lone block has nothing to be picked from -- unless the reference is, when the verse is.
    val referencePicked = targets.reference?.picked == true
    if (frames.size > 1 || referencePicked) frames.forEachIndexed { index, frame ->
        if (frame == null) return@forEachIndexed
        // With the reference picked, its verse's block is a click away from being picked again.
        val picked = index == targets.selected && !referencePicked
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
                .testTag(adjustBlockTag(index)),
        )
    }
}

/**
 * The picked block's blue dot at its top left, which moves it on its own, and -- on the Bible page --
 * the reference outlined in orange, which is dragged anywhere on its own. Drawn over every other
 * handle: both are small and must win where they overlap a larger one.
 */
@Composable
internal fun BlockGrips(targets: BlockTargets, frames: List<AdjustFrame?>, reference: AdjustFrame?, scale: Float) {
    val shift = targets.shift
    val pickedFrame = targets.selected?.let { frames.getOrNull(it) }
    val ref = targets.reference
    if (ref != null && reference != null) ReferenceHandle(ref, reference, scale)
    // The block's dot is left off while the reference is picked: the reference is what moves then.
    if (shift != null && pickedFrame != null && ref?.picked != true) MoveDot(shift, pickedFrame, scale)
}

/** The picked block's own move, at its top left. */
@Composable
private fun MoveDot(shift: Adjustable<Pair<Int, Int>>, frame: AdjustFrame, scale: Float) {
    var from by remember { mutableStateOf(shift.value) }
    Box(
        Modifier
            .offset(frame.left - MOVE_DOT / 2, frame.top - MOVE_DOT / 2)
            .size(MOVE_DOT)
            .background(MaterialTheme.semantic.adjustHandle, CircleShape)
            .adjustDrag(scale, onStart = { from = shift.value }, onDrag = { total ->
                shift.onChange((from.first + total.x).roundToInt() to (from.second + total.y).roundToInt())
            })
            .testTag(ADJUST_BLOCK_MOVE_TAG),
    )
}

/**
 * The reference, outlined in orange -- solid while the Text rows point at it. Clicked, it is picked;
 * dragged, it is picked and moves anywhere on its own, from where its position puts it.
 */
@Composable
private fun ReferenceHandle(reference: ReferenceTarget, frame: AdjustFrame, scale: Float) {
    var from by remember { mutableStateOf(reference.shift.value) }
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
                    if (!reference.picked) reference.onPick()
                },
                onDrag = { total ->
                    val x = (from.first + total.x).roundToInt()
                    reference.shift.onChange(x to (from.second + total.y).roundToInt())
                },
            )
            .testTag(ADJUST_REFERENCE_TAG),
    )
}

/** Test handles for the block handles. */
internal fun adjustBlockTag(index: Int): String = "profile_adjust_block_$index"
internal const val ADJUST_BLOCK_MOVE_TAG = "profile_adjust_block_move"
internal const val ADJUST_REFERENCE_TAG = "profile_adjust_reference"

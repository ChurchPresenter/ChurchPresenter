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
private const val REFERENCE_DRAG_MIN_PX = 8f

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
    // A lone translation or language has nothing to be picked from.
    if (frames.size > 1) frames.forEachIndexed { index, frame ->
        if (frame == null) return@forEachIndexed
        val picked = index == targets.selected
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
 * the reference outlined in orange, dragged up to go above its verse or down to go after it. Drawn
 * over every other handle: both are small and must win where they overlap a larger one.
 */
@Composable
internal fun BlockGrips(targets: BlockTargets, frames: List<AdjustFrame?>, reference: AdjustFrame?, scale: Float) {
    val shift = targets.shift
    val pickedFrame = targets.selected?.let { frames.getOrNull(it) }
    if (shift != null && pickedFrame != null) MoveDot(shift, pickedFrame, scale)
    val above = targets.referenceAbove
    if (above != null && reference != null) ReferenceHandle(above, reference, scale)
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

/** The reference, outlined in orange: dragged up it goes above its verse, dragged down after it. */
@Composable
private fun ReferenceHandle(above: Adjustable<Boolean>, frame: AdjustFrame, scale: Float) {
    Box(
        Modifier
            .offset(frame.left, frame.top)
            .size(frame.width, frame.height)
            .dashedBorder(MaterialTheme.semantic.adjustAccent, 3.dp)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
            .adjustDrag(scale, onStart = {}, onDrag = {}, onEnd = { total ->
                when {
                    total.y < -REFERENCE_DRAG_MIN_PX -> above.onChange(true)
                    total.y > REFERENCE_DRAG_MIN_PX -> above.onChange(false)
                }
            })
            .testTag(ADJUST_REFERENCE_TAG),
    )
}

/** Test handles for the block handles. */
internal fun adjustBlockTag(index: Int): String = "profile_adjust_block_$index"
internal const val ADJUST_BLOCK_MOVE_TAG = "profile_adjust_block_move"
internal const val ADJUST_REFERENCE_TAG = "profile_adjust_reference"

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.theme.semantic
import kotlin.math.roundToInt

private val MOVE_HANDLE = 26.dp

/**
 * The circle at the centre of the frame: dragging it moves the block, with the three guide lines
 * shown while it moves; released near one, the block snaps to that alignment.
 */
@Composable
internal fun MoveHandle(model: AdjustModel, frame: AdjustFrame, scale: Float) {
    var dragged by remember { mutableStateOf<Offset?>(null) }
    var from by remember { mutableStateOf(model.region?.value) }
    val restY = frame.top + frame.height * restingFraction(model.verticalAlignment)
    val regionHeightPx = frame.height.value / scale
    val regionWidthPx = frame.width.value / scale
    dragged?.let { SnapGuides(frame) }
    val shift = dragged ?: Offset.Zero
    Box(
        Modifier
            .offset(
                frame.left + frame.width / 2 - MOVE_HANDLE / 2 + (shift.x * scale).dp,
                restY - MOVE_HANDLE / 2 + (shift.y * scale).dp,
            )
            .size(MOVE_HANDLE)
            .background(MaterialTheme.semantic.adjustHandle, CircleShape)
            .adjustDrag(
                scale,
                onStart = {
                    from = model.region?.value
                    dragged = Offset.Zero
                },
                onDrag = { total ->
                    dragged = total
                    from?.let { model.region?.onChange?.invoke(it.movedBy(
                        total,
                        regionWidthPx,
                        regionHeightPx,
                        vertical = false,
                    )) }
                },
                onEnd = { total ->
                    dragged = null
                    val snap = snapFor(restingFraction(model.verticalAlignment) + total.y / regionHeightPx)
                    val region = from
                    when {
                        snap != null -> model.onSnap(snap)
                        region != null -> model.region?.onChange?.invoke(region.movedBy(
                            total,
                            regionWidthPx,
                            regionHeightPx,
                            true,
                        ))
                    }
                },
            )
            .testTag(ADJUST_MOVE_TAG),
    ) {
        Icon(
            Icons.Filled.OpenWith,
            contentDescription = null,
            tint = MaterialTheme.semantic.onAdjustHandle,
            modifier = Modifier.padding(5.dp),
        )
    }
}

/**
 * [this] moved by [total] output pixels over a region [width] by [height]: an offset of 100 puts the
 * box against the far edge, so the whole free width is 200 points of it. Vertically only when
 * [vertical] -- while a block is being dragged its height is left to the snap on release.
 */
private fun ContentRegion.movedBy(total: Offset, width: Float, height: Float, vertical: Boolean): ContentRegion {
    fun moved(start: Int, delta: Float, extent: Float) =
        (start + delta / extent * FULL_PERCENT * 2).roundToInt().coerceIn(ContentRegion.OFFSET_RANGE)
    return copy(
        xOffsetPercent = moved(xOffsetPercent, total.x, width),
        yOffsetPercent = if (vertical) moved(yOffsetPercent, total.y, height) else yOffsetPercent,
    )
}

/** The three dashed lines a moving block snaps to. */
@Composable
private fun SnapGuides(frame: AdjustFrame) {
    SNAP_GUIDES.forEach { (fraction, _) ->
        Box(
            Modifier
                .offset(frame.left, frame.top + frame.height * fraction)
                .width(frame.width)
                .height(1.dp)
                .dashedBorder(MaterialTheme.semantic.adjustAccent, 0.dp),
        )
    }
}

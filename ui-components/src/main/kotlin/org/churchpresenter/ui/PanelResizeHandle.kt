package org.churchpresenter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.awt.Cursor

private const val GRIP_DOT_COUNT = 3

/** The strip's width, and the size of its grip dots. */
private val HANDLE_WIDTH = 16.dp
private val DOT_SIZE = 3.dp

/**
 * The new width a panel takes after a drag of [dragAmount] pixels.
 *
 * [invert] is for a panel on the *right*: dragging left has to make it wider, so the amount is
 * subtracted rather than added. The floor is `minOf(minPx, maxPx)`, not `minPx` — on a window too
 * narrow to give the panel its minimum, the cap wins and `coerceIn` would otherwise be handed a
 * reversed range and throw.
 */
fun resizedPanelWidth(
    currentPx: Float,
    dragAmount: Float,
    invert: Boolean,
    minPx: Float,
    maxPx: Float,
): Float {
    val moved = if (invert) currentPx - dragAmount else currentPx + dragAmount
    return moved.coerceIn(minOf(minPx, maxPx), maxPx)
}

/**
 * The draggable strip between a side panel and the content beside it: grip dots, a resize cursor,
 * and the button that collapses the panel.
 *
 * Extracted from `MainDesktop` so the gesture can be driven by a test. Both splitters — schedule on
 * the left, preview on the right — were inline in a 2,000-line composable that only runs under a
 * real display, which left the app's two most-used drag handles with no coverage at all. They are
 * also where a drag-handle regression has already happened once, hence the keying note below.
 *
 * [onResize] is called with the raw horizontal drag amount and is expected to apply
 * [resizedPanelWidth]; [onResizeEnd] fires once when the gesture finishes, which is where the
 * caller persists the width.
 */
@Composable
fun PanelResizeHandle(
    collapsed: Boolean,
    onResize: (dragAmount: Float) -> Unit,
    onResizeEnd: () -> Unit,
    onToggleCollapsed: () -> Unit,
    icon: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(HANDLE_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Keyed only on `collapsed` -- never on the width being dragged. The caller persists
            // that width in onResizeEnd, so keying on it would tear this coroutine down and
            // relaunch it at the end of every gesture, which is what made the second drag onward
            // unreliable before. Matches SongsTab's column-resize handles.
            .pointerInput(collapsed) {
                if (!collapsed) {
                    detectHorizontalDragGestures(onDragEnd = onResizeEnd) { _, amount ->
                        onResize(amount)
                    }
                }
            }
            .pointerHoverIcon(
                if (collapsed) PointerIcon.Default else PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!collapsed) {
            val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            GripDots(dotColor, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            GripDots(dotColor, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
        IconButton(onClick = onToggleCollapsed, modifier = Modifier.wrapContentHeight()) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GripDots(color: Color, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { repeat(GRIP_DOT_COUNT) { Box(Modifier.size(DOT_SIZE).background(color, CircleShape)) } }
}

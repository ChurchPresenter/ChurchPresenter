package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
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

private const val GRIP_ALPHA = 0.16f
private const val HOVER_ALPHA = 0.05f

/** The strip's width, and the size of its two grips. */
private val HANDLE_WIDTH = 16.dp
private val GRIP_WIDTH = 3.dp
private val GRIP_LENGTH = 40.dp

/**
 * The new width a panel takes after a drag of [dragAmount] pixels.
 *
 * [invert] is for a panel on the *right*: dragging left has to make it wider, so the amount is
 * subtracted rather than added. The floor is `minOf(minPx, maxPx)`, not `minPx` — on a window too
 * narrow to give the panel its minimum, the cap wins and `coerceIn` would otherwise be handed a
 * reversed range and throw.
 */
internal fun resizedPanelWidth(
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
 * The draggable strip between a side panel and the content beside it: two grips, a resize cursor,
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
internal fun PanelResizeHandle(
    collapsed: Boolean,
    onResize: (dragAmount: Float) -> Unit,
    onResizeEnd: () -> Unit,
    onToggleCollapsed: () -> Unit,
    icon: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .width(HANDLE_WIDTH)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .background(
                if (hovered && !collapsed) MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_ALPHA)
                else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .hoverable(interaction)
            // Keyed only on `collapsed` -- never on the width being dragged. The caller persists
            // that width in onResizeEnd, so keying on it would tear this coroutine down and
            // relaunch it at the end of every gesture, which is what made the second drag onward
            // unreliable before. Matches SongsTab's column-resize handles.
            .pointerInput(collapsed) {
                if (!collapsed) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false; onResizeEnd() },
                        onDragCancel = { dragging = false },
                    ) { _, amount ->
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
            val gripColor = if (dragging) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = GRIP_ALPHA)
            Grip(gripColor, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            Grip(gripColor, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
        KeyIconButton(onClick = onToggleCollapsed, modifier = Modifier.wrapContentHeight()) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Grip(color: Color, modifier: Modifier) {
    Box(modifier.size(width = GRIP_WIDTH, height = GRIP_LENGTH).background(color, RoundedCornerShape(3.dp)))
}

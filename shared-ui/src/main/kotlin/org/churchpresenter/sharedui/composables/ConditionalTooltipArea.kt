package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Wraps [TooltipArea] but suppresses the tooltip when the component is not fully
 * within the window's visible bounds (e.g. partially scrolled off-screen).
 *
 * Where the component sits is only worked out when its tooltip is about to show, and in root
 * coordinates: `boundsInWindow` on every layout pass asks AWT for the window's place on screen,
 * a native call that stalls the event thread on Windows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConditionalTooltipArea(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.ComponentRect(
        anchor = Alignment.BottomCenter,
        offset = DpOffset(0.dp, 4.dp)
    ),
    content: @Composable () -> Unit
) {
    // Not state: storing the coordinates must not recompose anything on each layout pass.
    val anchor = remember { TooltipAnchor() }
    val windowInfo = LocalWindowInfo.current

    TooltipArea(
        tooltip = { if (anchor.isFullyVisibleIn(windowInfo.containerSize)) tooltip() },
        tooltipPlacement = tooltipPlacement,
        modifier = modifier.onGloballyPositioned { anchor.coordinates = it }
    ) {
        content()
    }
}

/** The last place a tooltip's component was laid out, read only when the tooltip shows. */
internal class TooltipAnchor {
    var coordinates: LayoutCoordinates? = null

    /** Whether the component lies wholly inside a window of [containerSize]; true before any layout. */
    fun isFullyVisibleIn(containerSize: IntSize): Boolean {
        val coords = coordinates?.takeIf { it.isAttached } ?: return true
        val root = coords.findRootCoordinates()
        return isFullyVisibleInWindow(
            bounds = clampToRoot(coords.boundsInRoot(), root.size.width.toFloat(), root.size.height.toFloat()),
            windowWidth = containerSize.width.toFloat(),
            windowHeight = containerSize.height.toFloat(),
        )
    }
}

/**
 * [bounds] cut to the root, and [Rect.Zero] when nothing of it is left — what `boundsInWindow`
 * does before it moves the rectangle into window coordinates, so the visibility rule is unchanged.
 */
internal fun clampToRoot(bounds: Rect, rootWidth: Float, rootHeight: Float): Rect {
    val left = bounds.left.coerceIn(0f, rootWidth)
    val top = bounds.top.coerceIn(0f, rootHeight)
    val right = bounds.right.coerceIn(0f, rootWidth)
    val bottom = bounds.bottom.coerceIn(0f, rootHeight)
    return if (left == right || top == bottom) Rect.Zero else Rect(left, top, right, bottom)
}

internal fun isFullyVisibleInWindow(bounds: Rect, windowWidth: Float, windowHeight: Float): Boolean =
    bounds.left >= 0f &&
        bounds.top >= 0f &&
        bounds.right <= windowWidth &&
        bounds.bottom <= windowHeight

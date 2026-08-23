package org.churchpresenter.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Wraps [TooltipArea] but suppresses the tooltip when the component is not fully
 * within the window's visible bounds (e.g. partially scrolled off-screen).
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
    var fullyVisible by remember { mutableStateOf(true) }
    val windowInfo = LocalWindowInfo.current

    TooltipArea(
        tooltip = { if (fullyVisible) tooltip() },
        tooltipPlacement = tooltipPlacement,
        modifier = modifier.onGloballyPositioned { coords ->
            fullyVisible = isFullyVisibleInWindow(
                bounds = coords.boundsInWindow(),
                windowWidth = windowInfo.containerSize.width.toFloat(),
                windowHeight = windowInfo.containerSize.height.toFloat(),
            )
        }
    ) {
        content()
    }
}

fun isFullyVisibleInWindow(bounds: Rect, windowWidth: Float, windowHeight: Float): Boolean =
    bounds.left >= 0f &&
        bounds.top >= 0f &&
        bounds.right <= windowWidth &&
        bounds.bottom <= windowHeight

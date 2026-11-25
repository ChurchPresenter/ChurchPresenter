package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import org.churchpresenter.hosanna.HosannaRadius
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.defaults.PositionMetrics
import org.churchpresenter.hosanna.composables.defaults.PositionPickerDefaults
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.hosanna.composables.model.GRID
import org.churchpresenter.hosanna.composables.model.ScreenPosition
import org.churchpresenter.hosanna.composables.model.column
import org.churchpresenter.hosanna.composables.model.movePosition
import org.churchpresenter.hosanna.composables.model.positionAt
import org.churchpresenter.hosanna.composables.model.row

/**
 * A small screen with nine spots to pick where something goes on the output. Arrows move between spots.
 *
 * @param position the picked spot.
 * @param onPositionChange called with the spot picked.
 * @param label the picker's screen-reader name.
 * @param positionName each spot's screen-reader name.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param compact uses the smaller screen.
 * @param enabled whether a spot can be picked.
 */
@Composable
fun PositionPicker(
    position: ScreenPosition,
    onPositionChange: (ScreenPosition) -> Unit,
    label: String,
    positionName: (ScreenPosition) -> String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = HosannaTheme.colors
    val typography = HosannaTheme.typography
    val metrics = PositionPickerDefaults.metrics(compact)
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = HosannaTheme.shapes.m
    val ink = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PositionPickerDefaults.LabelGap)) {
        BasicText(label, style = typography.subtitle.copy(color = ink))
        Box(
            modifier = Modifier
                .size(metrics.width, metrics.height)
                .then(
                    if (focused && enabled) {
                        Modifier.focusRing(
                            colors.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING),
                            HosannaRadius.M,
                        )
                    } else {
                        Modifier
                    },
                )
                .onKeyEvent { event ->
                    if (!enabled) return@onKeyEvent false
                    movePosition(position, event)?.let(onPositionChange) != null
                }
                .focusable(enabled, interactionSource)
                .semantics { contentDescription = label }
                .alpha(if (enabled) 1f else HosannaStateAlpha.DISABLED_CONTENT)
                .clip(shape)
                .background(colors.screen)
                .border(FieldDefaults.Border, colors.outline, shape)
                .drawBehind { drawGrid(colors.onScreen.copy(alpha = HosannaStateAlpha.HOVER)) }
                .selectableGroup(),
        ) {
            Column(Modifier.fillMaxSize()) {
                repeat(GRID) { row ->
                    Row(Modifier.weight(1f)) {
                        repeat(GRID) { column ->
                            val cell = positionAt(row, column)
                            PositionCell(
                                name = positionName(cell),
                                selected = cell == position,
                                enabled = enabled,
                                metrics = metrics,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            ) { onPositionChange(cell) }
                        }
                    }
                }
            }
        }
        BasicText(positionName(position), style = typography.bodyStrong.copy(color = ink))
    }
}

private fun DrawScope.drawGrid(line: Color) {
    val stroke = PositionPickerDefaults.Line.toPx()
    for (i in 1 until GRID) {
        val x = size.width * i / GRID
        val y = size.height * i / GRID
        drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
        drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
    }
}

@Composable
private fun PositionCell(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    metrics: PositionMetrics,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = HosannaTheme.colors
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val active = enabled && !selected && (hovered || pressed)
    val shape = RoundedCornerShape(metrics.radius)
    val tint = when {
        selected -> colors.primary
        active && pressed -> colors.primary.copy(alpha = HosannaStateAlpha.PRESSED)
        active -> colors.primary.copy(alpha = HosannaStateAlpha.HOVER)
        else -> Color.Transparent
    }
    val dot = when {
        selected -> colors.onPrimary
        active -> colors.primary
        else -> colors.onScreen.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
    }
    Box(
        modifier = modifier
            .selectable(selected, source, null, enabled, Role.RadioButton, onClick)
            .semantics { contentDescription = name }
            .padding(metrics.inset)
            .background(tint, shape)
            .then(if (active) Modifier.dashedOutline(colors.primary, metrics.radius) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) metrics.selectedDot else metrics.dot)
                .background(dot, HosannaTheme.shapes.full),
        )
    }
}

private fun Modifier.dashedOutline(color: Color, radius: Dp): Modifier = drawBehind {
    val stroke = PositionPickerDefaults.Dash.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            stroke,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(PositionPickerDefaults.DashOn.toPx(), PositionPickerDefaults.DashOff.toPx()),
            ),
        ),
    )
}

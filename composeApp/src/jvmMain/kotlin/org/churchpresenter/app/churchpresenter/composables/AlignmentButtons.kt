package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_align_bottom
import churchpresenter.composeapp.generated.resources.ic_align_center
import churchpresenter.composeapp.generated.resources.ic_align_left
import churchpresenter.composeapp.generated.resources.ic_align_middle
import churchpresenter.composeapp.generated.resources.ic_align_right
import churchpresenter.composeapp.generated.resources.ic_align_top
import churchpresenter.composeapp.generated.resources.align_left
import churchpresenter.composeapp.generated.resources.align_center
import churchpresenter.composeapp.generated.resources.align_right
import churchpresenter.composeapp.generated.resources.align_top
import churchpresenter.composeapp.generated.resources.align_middle
import churchpresenter.composeapp.generated.resources.align_bottom
import churchpresenter.composeapp.generated.resources.position_above_desc
import churchpresenter.composeapp.generated.resources.position_below_desc
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.sunken
import androidx.compose.ui.graphics.graphicsLayer

private val TRACK_INSET = 2.dp
private const val ICON_FRACTION = 0.7f
private const val SEGMENT_HOVER_ALPHA = 0.08f
private val SEGMENT_HOVER_SHIFT = 1.dp

private class IconChoice(
    val value: String,
    val painter: Painter,
    val tooltip: String?,
    val contentDescription: String?,
)

/**
 * A group of icon buttons for horizontal alignment (Left, Center, Right)
 */
@Composable
fun HorizontalAlignmentButtons(
    selectedAlignment: String,
    onAlignmentChange: (String) -> Unit,
    leftValue: String,
    centerValue: String,
    rightValue: String,
    buttonSize: Dp = 28.dp,
    cornerRadius: Dp = 4.dp
) {
    IconChoiceTrack(
        choices = listOf(
            IconChoice(
                rightValue,
                painterResource(Res.drawable.ic_align_right),
                stringResource(Res.string.align_right),
                null,
            ),
            IconChoice(
                centerValue,
                painterResource(Res.drawable.ic_align_center),
                stringResource(Res.string.align_center),
                null,
            ),
            IconChoice(
                leftValue,
                painterResource(Res.drawable.ic_align_left),
                stringResource(Res.string.align_left),
                null,
            ),
        ),
        selected = selectedAlignment,
        onSelect = onAlignmentChange,
        buttonSize = buttonSize,
        cornerRadius = cornerRadius,
    )
}

/**
 * A group of icon buttons for vertical alignment (Top, Middle, Bottom)
 */
@Composable
fun VerticalAlignmentButtons(
    selectedAlignment: String,
    onAlignmentChange: (String) -> Unit,
    topValue: String,
    middleValue: String,
    bottomValue: String,
    buttonSize: Dp = 28.dp,
    cornerRadius: Dp = 4.dp
) {
    val bottom = stringResource(Res.string.align_bottom)
    val middle = stringResource(Res.string.align_middle)
    val top = stringResource(Res.string.align_top)
    IconChoiceTrack(
        choices = listOf(
            IconChoice(
                bottomValue,
                painterResource(Res.drawable.ic_align_bottom),
                bottom,
                bottom,
            ),
            IconChoice(
                middleValue,
                painterResource(Res.drawable.ic_align_middle),
                middle,
                middle,
            ),
            IconChoice(
                topValue,
                painterResource(Res.drawable.ic_align_top),
                top,
                top,
            ),
        ),
        selected = selectedAlignment,
        onSelect = onAlignmentChange,
        buttonSize = buttonSize,
        cornerRadius = cornerRadius,
    )
}

/**
 * A group of 2 icon buttons for position (Above/Below)
 */
@Composable
fun PositionButtons(
    selectedPosition: String,
    onPositionChange: (String) -> Unit,
    aboveValue: String,
    belowValue: String,
    buttonSize: Dp = 28.dp,
    cornerRadius: Dp = 4.dp
) {
    IconChoiceTrack(
        choices = listOf(
            IconChoice(
                aboveValue,
                painterResource(Res.drawable.ic_align_top),
                null,
                stringResource(Res.string.position_above_desc),
            ),
            IconChoice(
                belowValue,
                painterResource(Res.drawable.ic_align_bottom),
                null,
                stringResource(Res.string.position_below_desc),
            ),
        ),
        selected = selectedPosition,
        onSelect = onPositionChange,
        buttonSize = buttonSize,
        cornerRadius = cornerRadius,
    )
}

/**
 * The choices sit in one sunken track, the chosen one raised. The track keeps the footprint the
 * flat row of [buttonSize] squares had, so every caller keeps its layout.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconChoiceTrack(
    choices: List<IconChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    buttonSize: Dp,
    cornerRadius: Dp,
) {
    val palette = elevationPalette()
    val iconSize = (buttonSize.value * ICON_FRACTION).dp.coerceIn(14.dp, 20.dp)
    val segmentWidth = buttonSize - (TRACK_INSET * 2 + TRACK_INSET * (choices.size - 1)) / choices.size
    val segmentHeight = buttonSize - TRACK_INSET * 2
    val trackRadius = maxOf(cornerRadius, 6.dp)
    val segmentShape = RoundedCornerShape(trackRadius - TRACK_INSET)
    Row(
        modifier = Modifier.sunken(RoundedCornerShape(trackRadius), palette).padding(TRACK_INSET),
        horizontalArrangement = Arrangement.spacedBy(TRACK_INSET),
    ) {
        choices.forEach { choice ->
            val isSelected = choice.value == selected
            val segment: @Composable () -> Unit = {
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val tint = when {
                    isSelected -> palette.selected.ink
                    hovered -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(segmentWidth, segmentHeight)
                        .then(
                            when {
                                isSelected -> Modifier.raised(
                                    segmentShape,
                                    palette.selected,
                                    palette,
                                    hovered = hovered,
                                    lift = 2.dp,
                                )
                                hovered -> Modifier.graphicsLayer { translationY = -SEGMENT_HOVER_SHIFT.toPx() }
                                    .clip(segmentShape)
                                    .background(tint.copy(alpha = SEGMENT_HOVER_ALPHA))
                                else -> Modifier.clip(segmentShape)
                            }
                        )
                        .hoverable(interaction)
                        .selectable(
                            selected = isSelected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClick = { onSelect(choice.value) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = choice.painter,
                        contentDescription = choice.contentDescription,
                        modifier = Modifier.size(iconSize),
                        colorFilter = ColorFilter.tint(tint),
                    )
                }
            }
            val tooltip = choice.tooltip
            if (tooltip == null) {
                segment()
            } else {
                TooltipArea(
                    tooltip = {
                        Surface(
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shape = MaterialTheme.shapes.extraSmall,
                            tonalElevation = 4.dp,
                        ) {
                            Text(
                                tooltip,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    },
                    tooltipPlacement = TooltipPlacement.ComponentRect(
                        anchor = Alignment.BottomCenter,
                        offset = DpOffset(0.dp, 4.dp),
                    ),
                ) {
                    segment()
                }
            }
        }
    }
}

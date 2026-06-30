package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
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

/**
 * A group of icon buttons for horizontal alignment (Left, Center, Right)
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val iconSize = (buttonSize.value * 0.7f).dp.coerceIn(14.dp, 20.dp)
    Row {
        // Left align button
        TooltipArea(
            tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.align_left), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } },
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
        ) {
            OutlinedButton(
                onClick = { onAlignmentChange(leftValue) },
                modifier = Modifier.size(buttonSize),
                shape = RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius, topEnd = 0.dp, bottomEnd = 0.dp),
                border = BorderStroke(1.dp, if (selectedAlignment == leftValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedAlignment == leftValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(0.dp)
            ) {
                Image(painter = painterResource(Res.drawable.ic_align_left), contentDescription = null, modifier = Modifier.size(iconSize), colorFilter = ColorFilter.tint(if (selectedAlignment == leftValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface))
            }
        }

        // Center align button
        TooltipArea(
            tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.align_center), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } },
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
        ) {
            OutlinedButton(
                onClick = { onAlignmentChange(centerValue) },
                modifier = Modifier.size(buttonSize),
                shape = RoundedCornerShape(0.dp),
                border = BorderStroke(1.dp, if (selectedAlignment == centerValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedAlignment == centerValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(0.dp)
            ) {
                Image(painter = painterResource(Res.drawable.ic_align_center), contentDescription = null, modifier = Modifier.size(iconSize), colorFilter = ColorFilter.tint(if (selectedAlignment == centerValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface))
            }
        }

        // Right align button
        TooltipArea(
            tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.align_right), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } },
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
        ) {
            OutlinedButton(
                onClick = { onAlignmentChange(rightValue) },
                modifier = Modifier.size(buttonSize),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = cornerRadius, bottomEnd = cornerRadius),
                border = BorderStroke(1.dp, if (selectedAlignment == rightValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedAlignment == rightValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(0.dp)
            ) {
                Image(painter = painterResource(Res.drawable.ic_align_right), contentDescription = null, modifier = Modifier.size(iconSize), colorFilter = ColorFilter.tint(if (selectedAlignment == rightValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface))
            }
        }
    }
}

/**
 * A group of icon buttons for vertical alignment (Top, Middle, Bottom)
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val iconSize = (buttonSize.value * 0.7f).dp.coerceIn(14.dp, 20.dp)
    Row {
        TooltipArea(
            tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.align_top), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } },
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
        ) {
            OutlinedButton(
                onClick = { onAlignmentChange(topValue) },
                modifier = Modifier.size(buttonSize),
                shape = RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius, topEnd = 0.dp, bottomEnd = 0.dp),
                border = BorderStroke(1.dp, if (selectedAlignment == topValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedAlignment == topValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(0.dp)
            ) {
                Image(painter = painterResource(Res.drawable.ic_align_top), contentDescription = stringResource(Res.string.align_top), modifier = Modifier.size(iconSize), colorFilter = ColorFilter.tint(if (selectedAlignment == topValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface))
            }
        }
        TooltipArea(
            tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.align_middle), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } },
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
        ) {
            OutlinedButton(
                onClick = { onAlignmentChange(middleValue) },
                modifier = Modifier.size(buttonSize),
                shape = RoundedCornerShape(0.dp),
                border = BorderStroke(1.dp, if (selectedAlignment == middleValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedAlignment == middleValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(0.dp)
            ) {
                Image(painter = painterResource(Res.drawable.ic_align_middle), contentDescription = stringResource(Res.string.align_middle), modifier = Modifier.size(iconSize), colorFilter = ColorFilter.tint(if (selectedAlignment == middleValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface))
            }
        }
        TooltipArea(
            tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.align_bottom), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } },
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
        ) {
            OutlinedButton(
                onClick = { onAlignmentChange(bottomValue) },
                modifier = Modifier.size(buttonSize),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = cornerRadius, bottomEnd = cornerRadius),
                border = BorderStroke(1.dp, if (selectedAlignment == bottomValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedAlignment == bottomValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(0.dp)
            ) {
                Image(painter = painterResource(Res.drawable.ic_align_bottom), contentDescription = stringResource(Res.string.align_bottom), modifier = Modifier.size(iconSize), colorFilter = ColorFilter.tint(if (selectedAlignment == bottomValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface))
            }
        }
    }
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
    val iconSize = (buttonSize.value * 0.7f).dp.coerceIn(14.dp, 20.dp)
    Row {
        OutlinedButton(
            onClick = { onPositionChange(aboveValue) },
            modifier = Modifier.size(buttonSize),
            shape = RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius, topEnd = 0.dp, bottomEnd = 0.dp),
            border = BorderStroke(1.dp, if (selectedPosition == aboveValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedPosition == aboveValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = PaddingValues(0.dp)
        ) {
            Image(painter = painterResource(Res.drawable.ic_align_top), contentDescription = stringResource(Res.string.position_above_desc), modifier = Modifier.size(iconSize), colorFilter = ColorFilter.tint(if (selectedPosition == aboveValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface))
        }
        OutlinedButton(
            onClick = { onPositionChange(belowValue) },
            modifier = Modifier.size(buttonSize),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = cornerRadius, bottomEnd = cornerRadius),
            border = BorderStroke(1.dp, if (selectedPosition == belowValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedPosition == belowValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = PaddingValues(0.dp)
        ) {
            Image(painter = painterResource(Res.drawable.ic_align_bottom), contentDescription = stringResource(Res.string.position_below_desc), modifier = Modifier.size(iconSize), colorFilter = ColorFilter.tint(if (selectedPosition == belowValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface))
        }
    }
}


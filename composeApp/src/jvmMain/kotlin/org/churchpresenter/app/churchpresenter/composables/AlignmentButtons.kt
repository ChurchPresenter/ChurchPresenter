package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource

/**
 * A group of icon buttons for horizontal alignment (Left, Center, Right)
 */
@Composable
fun HorizontalAlignmentButtons(
    selectedAlignment: String,
    onAlignmentChange: (String) -> Unit,
    leftValue: String,
    centerValue: String,
    rightValue: String
) {
    Row {
        // Left align button
        OutlinedButton(
            onClick = { onAlignmentChange(leftValue) },
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            border = BorderStroke(
                1.dp,
                if (selectedAlignment == leftValue) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedAlignment == leftValue)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_align_left),
                contentDescription = "Align Left",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (selectedAlignment == leftValue)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )
        }

        // Center align button
        OutlinedButton(
            onClick = { onAlignmentChange(centerValue) },
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(0.dp),
            border = BorderStroke(
                1.dp,
                if (selectedAlignment == centerValue) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedAlignment == centerValue)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_align_center),
                contentDescription = "Align Center",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (selectedAlignment == centerValue)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )
        }

        // Right align button
        OutlinedButton(
            onClick = { onAlignmentChange(rightValue) },
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 4.dp),
            border = BorderStroke(
                1.dp,
                if (selectedAlignment == rightValue) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedAlignment == rightValue)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_align_right),
                contentDescription = "Align Right",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (selectedAlignment == rightValue)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
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
    bottomValue: String
) {
    Row {
        // Top align button
        OutlinedButton(
            onClick = { onAlignmentChange(topValue) },
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            border = BorderStroke(
                1.dp,
                if (selectedAlignment == topValue) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedAlignment == topValue)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_align_top),
                contentDescription = "Align Top",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (selectedAlignment == topValue)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )
        }

        // Middle align button
        OutlinedButton(
            onClick = { onAlignmentChange(middleValue) },
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(0.dp),
            border = BorderStroke(
                1.dp,
                if (selectedAlignment == middleValue) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedAlignment == middleValue)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_align_middle),
                contentDescription = "Align Middle",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (selectedAlignment == middleValue)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )
        }

        // Bottom align button
        OutlinedButton(
            onClick = { onAlignmentChange(bottomValue) },
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 4.dp),
            border = BorderStroke(
                1.dp,
                if (selectedAlignment == bottomValue) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedAlignment == bottomValue)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_align_bottom),
                contentDescription = "Align Bottom",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (selectedAlignment == bottomValue)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )
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
    belowValue: String
) {
    Row {
        // Above button
        OutlinedButton(
            onClick = { onPositionChange(aboveValue) },
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            border = BorderStroke(
                1.dp,
                if (selectedPosition == aboveValue) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedPosition == aboveValue)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_align_top),
                contentDescription = "Above",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (selectedPosition == aboveValue)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )
        }

        // Below button
        OutlinedButton(
            onClick = { onPositionChange(belowValue) },
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 4.dp),
            border = BorderStroke(
                1.dp,
                if (selectedPosition == belowValue) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedPosition == belowValue)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_align_bottom),
                contentDescription = "Below",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (selectedPosition == belowValue)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}


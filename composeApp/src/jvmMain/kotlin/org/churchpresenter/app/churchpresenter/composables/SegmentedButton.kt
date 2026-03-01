package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SegmentedButtonItem<T>(
    val value: T,
    val label: String,
    val tooltip: String? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> SegmentedButton(
    items: List<SegmentedButtonItem<T>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    buttonWidth: Dp = 40.dp,
    buttonHeight: Dp = 40.dp,
    fontSize: TextUnit = 16.sp
) {
    require(items.isNotEmpty()) { "SegmentedButton requires at least one item" }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedValue == item.value
            val shape = when {
                items.size == 1 -> RoundedCornerShape(4.dp)
                index == 0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                index == items.lastIndex -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                else -> RoundedCornerShape(0.dp)
            }

            val button: @Composable () -> Unit = {
                OutlinedButton(
                    onClick = { onValueChange(item.value) },
                    modifier = Modifier
                        .height(buttonHeight)
                        .width(buttonWidth),
                    shape = shape,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.onSecondary
                        else
                            Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Text(
                        text = item.label,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (item.tooltip != null) {
                TooltipArea(
                    tooltip = {
                        Surface(
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shape = MaterialTheme.shapes.extraSmall,
                            tonalElevation = 4.dp
                        ) {
                            Text(
                                text = item.tooltip,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    tooltipPlacement = TooltipPlacement.CursorPoint(
                        offset = DpOffset(0.dp, 16.dp)
                    )
                ) {
                    button()
                }
            } else {
                button()
            }
        }
    }
}


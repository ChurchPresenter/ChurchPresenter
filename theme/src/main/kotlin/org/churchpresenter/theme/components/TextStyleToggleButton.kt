package org.churchpresenter.theme.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val OUTLINE_ALPHA = 0.5f
private const val LABEL_SIZE_FRACTION = 0.36f

/**
 * One square toggle of the text-style row — B, I, U and friends: the letter set in the style it
 * stands for, filled with the primary colour while on and outlined while off, with [tooltip]
 * under the pointer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextStyleToggleButton(
    label: String,
    tooltip: String,
    isActive: Boolean,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    textDecoration: TextDecoration? = null,
    buttonSize: Dp = 28.dp,
    onClick: () -> Unit,
) {
    val activeBackground = MaterialTheme.colorScheme.primary
    val inactiveBackground = MaterialTheme.colorScheme.surfaceVariant
    val activeContent = MaterialTheme.colorScheme.onPrimary
    val inactiveContent = MaterialTheme.colorScheme.onSurfaceVariant

    TooltipArea(
        tooltip = { ControlTooltip(tooltip) },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        ),
    ) {
        Surface(
            modifier = Modifier
                .size(buttonSize)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = if (isActive) {
                        activeBackground
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = OUTLINE_ALPHA)
                    },
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable { onClick() },
            color = if (isActive) activeBackground else inactiveBackground,
            shape = RoundedCornerShape(8.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    fontSize = (buttonSize.value * LABEL_SIZE_FRACTION).sp,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration,
                    color = if (isActive) activeContent else inactiveContent,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The small inverse-surface tooltip the app's controls show under the pointer. */
@Composable
fun ControlTooltip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 4.dp,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

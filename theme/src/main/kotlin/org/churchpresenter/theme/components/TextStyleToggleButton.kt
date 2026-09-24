package org.churchpresenter.theme.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState

private const val LABEL_SIZE_FRACTION = 0.36f

/**
 * One square toggle of the text-style row — B, I, U and friends: the letter set in the style it
 * stands for, a raised key that turns the accent colour while on, with [tooltip]
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
    TooltipArea(
        tooltip = { ControlTooltip(tooltip) },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        ),
    ) {
        val palette = elevationPalette()
        val fill = if (isActive) palette.accent else palette.key
        val shape = RoundedCornerShape(8.dp)
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val hovered by interaction.collectIsHoveredAsState()
        Box(
            modifier = Modifier
                .size(buttonSize)
                .raised(shape, fill, palette, pressed = pressed, hovered = hovered, lift = 2.dp)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = (buttonSize.value * LABEL_SIZE_FRACTION).sp,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration,
                color = fill.ink,
                maxLines = 1,
            )
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

package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.hoverable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.dialogs.LiveTool
import org.churchpresenter.dialogs.LiveToolChip
import org.churchpresenter.sharedui.composables.ConditionalTooltipArea
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.keyboardFocusRing
import org.churchpresenter.theme.raised

/** Whether the live tools show their names beside their chips -- only where the sidebar has room. */
internal val LocalLiveToolLabels = compositionLocalOf { false }

/** The live tools' raised strip: one key holding every tool, so they read as one set. */
@Composable
internal fun LiveToolStrip(content: @Composable RowScope.() -> Unit) {
    val palette = elevationPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .raised(STRIP_SHAPE, palette.key, palette, moves = false, lift = 1.dp)
            .padding(4.dp),
        content = content,
    )
}

/** The hairline in the strip between the tools that open a dialog and the ones that act on air. */
@Composable
internal fun LiveToolDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(22.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * One tool in the strip: its colored chip, and its name where there is room -- otherwise the name
 * is the tooltip. [onAir] reddens the chip while what it puts up is up; [selected] lights a toggle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList")
internal fun LiveToolButton(
    tool: LiveTool,
    painter: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAir: Boolean = false,
    selected: Boolean? = null,
) {
    val showLabel = LocalLiveToolLabels.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val on = selected == true
    val accent = MaterialTheme.colorScheme.primary
    val background = when {
        on && hovered -> accent.copy(alpha = SELECTED_HOVER_ALPHA)
        on -> accent.copy(alpha = SELECTED_ALPHA)
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_ALPHA)
        else -> Color.Transparent
    }
    val button: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = modifier
                .height(BUTTON_HEIGHT)
                .keyboardFocusRing(BUTTON_SHAPE)
                .clip(BUTTON_SHAPE)
                .background(background)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = label
                    if (selected != null) this.selected = on
                }
                .padding(start = 6.dp, end = if (showLabel) 10.dp else 6.dp),
        ) {
            LiveToolChip(tool, painter, onAir = onAir)
            if (showLabel) {
                Text(
                    label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
    if (showLabel) {
        button()
    } else {
        ConditionalTooltipArea(tooltip = { ToolTooltip(label) }) { button() }
    }
}

@Composable
private fun ToolTooltip(text: String) {
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

private val STRIP_SHAPE = RoundedCornerShape(13.dp)
private val BUTTON_SHAPE = RoundedCornerShape(10.dp)
private val BUTTON_HEIGHT = 38.dp
private const val HOVER_ALPHA = 0.06f
private const val SELECTED_ALPHA = 0.14f
private const val SELECTED_HOVER_ALPHA = 0.2f

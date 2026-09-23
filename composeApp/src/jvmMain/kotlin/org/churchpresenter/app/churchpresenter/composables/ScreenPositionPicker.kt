package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.position_showing_at
import org.churchpresenter.theme.components.ControlTooltip
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.stringResource

private const val GRID_COLUMNS = 3
private const val SCREEN_ASPECT = 16f / 9f
private const val HOVER_ALPHA = 0.16f
private const val DOT_ALPHA = 0.5f
private val SCREEN_WIDTH = 150.dp
private val SCREEN_PADDING = 5.dp
private val CELL_GAP = 4.dp
private val DOT_HEIGHT = 5.dp
private val DOT_WIDTH = 5.dp
private val MARKER_WIDTH = 20.dp

/**
 * Where on the output something is shown, picked on a miniature screen: a sunken 16:9 panel holding
 * a 3x3 grid of spots. The chosen spot is a raised accent marker; its name is written beside the
 * screen rather than on the spot, so a long name never wraps inside a tile.
 *
 * [positions] are value-to-name pairs in reading order, top-left first.
 */
@Composable
fun ScreenPositionPicker(
    positions: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = elevationPalette()
    // Stacked, not side by side: the tab's side panel is narrow, and a name beside the screen ran
    // off its right edge.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            modifier = Modifier
                .width(SCREEN_WIDTH)
                .aspectRatio(SCREEN_ASPECT)
                .sunken(RoundedCornerShape(10.dp), palette)
                .padding(SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            positions.chunked(GRID_COLUMNS).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                ) {
                    row.forEach { (value, name) ->
                        PositionCell(
                            name = name,
                            isSelected = value == selected,
                            onClick = { onSelect(value) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.position_showing_at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = positions.firstOrNull { it.first == selected }?.second ?: selected,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PositionCell(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val palette = elevationPalette()
    val shape = RoundedCornerShape(6.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dotWidth by animateDpAsState(if (isSelected) MARKER_WIDTH else DOT_WIDTH)
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DOT_ALPHA)
    TooltipArea(
        tooltip = { ControlTooltip(name) },
        modifier = modifier,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .then(
                    when {
                        isSelected -> Modifier.raised(shape, palette.accent, palette, lift = 2.dp)
                        hovered -> Modifier.clip(shape).background(palette.key.ink.copy(alpha = HOVER_ALPHA))
                        else -> Modifier.clip(shape)
                    }
                )
                .hoverable(interaction)
                .selectable(
                    selected = isSelected,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .semantics { contentDescription = name },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = dotWidth, height = DOT_HEIGHT)
                    .clip(CircleShape)
                    .background(if (isSelected) palette.accent.ink else dotColor)
            )
        }
    }
}

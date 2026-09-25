package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_star
import churchpresenter.composeapp.generated.resources.ic_star_filled
import churchpresenter.composeapp.generated.resources.recent_pin
import churchpresenter.composeapp.generated.resources.recent_unpin
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.semantic
import org.churchpresenter.theme.components.KeyIconButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val RECENT_CHIP_HEIGHT = 30.dp

/**
 * One recent file or folder: a raised chip, lit in the selected fill while it is the open one, with
 * its pin star inside -- gold when pinned. Shared by the Pictures, Presentation and Media recents.
 */
@Composable
fun RecentChip(
    name: String,
    isActive: Boolean,
    isPinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    height: Dp = RECENT_CHIP_HEIGHT,
) {
    val palette = elevationPalette()
    val fill = if (isActive) palette.selected else palette.key
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    RecentChipTooltip(name) {
    Row(
        modifier = Modifier
            .height(height)
            .raised(shape, fill, palette, pressed = pressed, hovered = hovered, lift = 2.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = fill.ink,
            maxLines = 1,
            // Capped, and ellipsised rather than clipped. `maxLines = 1` alone stops a name wrapping
            // but not a chip growing: one long file name -- or a streaming URL, which is a whole
            // path -- took the entire row and pushed every other recent out of sight, with no
            // indication that there were any. The tooltip below carries the full name.
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = RECENT_CHIP_MAX_TEXT),
        )
        KeyIconButton(onClick = onTogglePin, modifier = Modifier.size(24.dp)) {
            Icon(
                painter = painterResource(if (isPinned) Res.drawable.ic_star_filled else Res.drawable.ic_star),
                contentDescription = stringResource(if (isPinned) Res.string.recent_unpin else Res.string.recent_pin),
                modifier = Modifier.size(13.dp),
                tint = if (isPinned) MaterialTheme.semantic.favorite else fill.ink.copy(alpha = STAR_OFF_ALPHA)
            )
        }
    }
    }
}

/**
 * The chip's full name on hover, for a name the chip had to cut short.
 *
 * Only for those: a tooltip that repeats what is already fully readable is noise on a row the
 * operator passes the mouse over constantly. The threshold is the same character budget the width
 * cap allows, so the two cannot disagree about which names are cut.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentChipTooltip(name: String, content: @Composable () -> Unit) {
    if (name.length <= RECENT_CHIP_NAME_BUDGET) {
        content()
        return
    }
    ConditionalTooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        content = content,
    )
}

private const val STAR_OFF_ALPHA = 0.45f

/**
 * As much of a name as one chip shows before it is ellipsised.
 *
 * Roughly a dozen chips of this width fit a maximised window's recents row, and a name this long is
 * already unusual -- the cap is there for the file called
 * `Sunday Morning Service 2026-09-21 Full Recording Final.mp4`, and for a streaming URL, which is a
 * whole path and used to take the row on its own.
 */
private val RECENT_CHIP_MAX_TEXT = 180.dp

/** [RECENT_CHIP_MAX_TEXT] in characters, near enough, for deciding whether to offer the tooltip. */
private const val RECENT_CHIP_NAME_BUDGET = 28

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.customize_screen_geometry
import churchpresenter.composeapp.generated.resources.customize_screen_geometry_unknown
import churchpresenter.composeapp.generated.resources.customize_screen_heading
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.display_stage_monitor
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The Customize dialog's left rail: the category list, and the card naming the screen it edits.
 *
 * Split out of `ProjectionCustomizeDialog.kt` when the dialog grew its third column — the rail, the
 * body and the shell are three separate pictures, and one file holding all of them was past
 * detekt's `TooManyFunctions` threshold as well as past what is comfortable to read.
 */

private val RAIL_WIDTH = 176.dp

private val OVERRIDDEN_DOT = 6.dp

private val CAPTION_SIZE = 10.sp

private val CAPTION_TRACKING = 0.9.sp

/** The left rail: one row per category, dotted where this output has settings of its own. */
@Composable
internal fun CustomizeRail(
    panes: List<CustomizePane>,
    selected: CustomizePane,
    assignment: ScreenAssignment,
    screenLabel: String,
    onSelect: (CustomizePane) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        panes.forEach { pane ->
            CustomizeRailRow(
                pane = pane,
                selected = pane == selected,
                overridden = pane.isOverridden(assignment),
                onSelect = { onSelect(pane) },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        CustomizeScreenCard(assignment, screenLabel)
    }
}

@Composable
private fun CustomizeRailRow(
    pane: CustomizePane,
    selected: Boolean,
    overridden: Boolean,
    onSelect: () -> Unit,
) {
    val ink = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp)
            .testTag(railTag(pane.name)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(pane.icon, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
        Text(
            text = pane.label(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // A screen with settings of its own for this category is worth seeing without opening it,
        // which is what the dot is.
        if (overridden) {
            Box(
                modifier = Modifier
                    .size(OVERRIDDEN_DOT)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/**
 * Which screen this dialog is editing, at the foot of the rail.
 *
 * The header already carries the name, but the header is a long way from the controls and reads as
 * the dialog's title rather than as a fact about the output. The geometry and the display mode are
 * the two things that decide what the controls above even mean — a lower third has a band, a full
 * screen does not — so they are worth stating where they can be glanced at.
 */
@Composable
private fun CustomizeScreenCard(assignment: ScreenAssignment, screenLabel: String) {
    val mode = displayModeLabel(assignment.displayMode)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CustomizeCaption(stringResource(Res.string.customize_screen_heading))
        Text(
            text = screenLabel,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // A DeckLink device and an unassigned slot report no bounds, so they name the mode
            // alone rather than claiming a 0 × 0 screen.
            text = if (assignment.targetBoundsW > 0 && assignment.targetBoundsH > 0) {
                stringResource(
                    Res.string.customize_screen_geometry,
                    assignment.targetBoundsW,
                    assignment.targetBoundsH,
                    mode,
                )
            } else {
                stringResource(Res.string.customize_screen_geometry_unknown, mode)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun displayModeLabel(mode: String): String = when (shownDisplayMode(mode)) {
    Constants.DISPLAY_MODE_STAGE_MONITOR -> stringResource(Res.string.display_stage_monitor)
    Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL -> stringResource(Res.string.display_lower_third)
    else -> stringResource(Res.string.display_fullscreen)
}

/** The small uppercase accent caption the dialog's sections are titled with. */
@Composable
internal fun CustomizeCaption(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        fontSize = CAPTION_SIZE,
        letterSpacing = CAPTION_TRACKING,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

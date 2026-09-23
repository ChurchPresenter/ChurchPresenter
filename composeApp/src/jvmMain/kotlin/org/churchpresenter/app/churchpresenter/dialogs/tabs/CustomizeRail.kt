package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.display_stage_monitor
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The Profiles tab's left rail: the category list.
 *
 * Split out of `ProjectionCustomizeDialog.kt` when the dialog grew its third column — the rail, the
 * body and the shell are three separate pictures, and one file holding all of them was past
 * detekt's `TooManyFunctions` threshold as well as past what is comfortable to read. It used to
 * also carry a card naming the profile and its display mode, at the foot of the rail -- dropped as
 * a duplicate of what `ProfileHeader` already states at the top of the tab.
 */

private val RAIL_WIDTH = 176.dp

private val CAPTION_SIZE = 10.sp

private val CAPTION_TRACKING = 0.9.sp

/** The left rail: one row per category. */
@Composable
internal fun CustomizeRail(
    panes: List<CustomizePane>,
    selected: CustomizePane,
    onSelect: (CustomizePane) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        panes.forEach { pane ->
            CustomizeRailRow(
                pane = pane,
                selected = pane == selected,
                onSelect = { onSelect(pane) },
            )
        }
    }
}

@Composable
private fun CustomizeRailRow(
    pane: CustomizePane,
    selected: Boolean,
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

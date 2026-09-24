package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.output_profile_badge_full
import churchpresenter.composeapp.generated.resources.output_profile_badge_lower_third
import churchpresenter.composeapp.generated.resources.output_profile_badge_stage
import churchpresenter.composeapp.generated.resources.output_profile_name_hint
import churchpresenter.composeapp.generated.resources.output_profile_unassigned
import churchpresenter.composeapp.generated.resources.output_profile_used_by
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

private val NAME_FIELD_WIDTH = 260.dp
private const val BADGE_FILL_ALPHA = 0.16f

/** The name, and what uses the profile. */
@Composable
internal fun ProfileHeader(
    profile: OutputProfile,
    usedBy: List<String>,
    onRename: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsTextField(
            value = profile.name,
            onValueChange = onRename,
            label = stringResource(Res.string.output_profile_name_hint),
            placeholder = { Text(profile.id) },
            modifier = Modifier.width(NAME_FIELD_WIDTH),
            fillWidth = true,
        )
        Text(
            text = usageText(usedBy),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** "Used by Screen 1, NDI 1", or that nothing is using it. */
@Composable
internal fun usageText(usedBy: List<String>): String =
    if (usedBy.isEmpty()) stringResource(Res.string.output_profile_unassigned)
    else stringResource(Res.string.output_profile_used_by, usedBy.joinToString(", "))

/** The colour a display mode is marked with, in the list and on its badge. */
@Composable
internal fun profileModeColor(displayMode: String): Color = when (shownDisplayMode(displayMode)) {
    Constants.DISPLAY_MODE_STAGE_MONITOR -> MaterialTheme.semantic.success
    Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL -> MaterialTheme.semantic.warning
    else -> MaterialTheme.colorScheme.primary
}

/** FULL / LOWER 3RD / STAGE, tinted with the mode's own colour. */
@Composable
internal fun ProfileModeBadge(displayMode: String) {
    val color = profileModeColor(displayMode)
    val label = when (shownDisplayMode(displayMode)) {
        Constants.DISPLAY_MODE_STAGE_MONITOR -> stringResource(Res.string.output_profile_badge_stage)
        Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL -> stringResource(Res.string.output_profile_badge_lower_third)
        else -> stringResource(Res.string.output_profile_badge_full)
    }
    Text(
        text = label.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = BADGE_FILL_ALPHA), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** A small dot in the mode's colour, leading a row of the profile list. */
@Composable
internal fun ProfileModeDot(displayMode: String) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(profileModeColor(displayMode), CircleShape),
    )
}

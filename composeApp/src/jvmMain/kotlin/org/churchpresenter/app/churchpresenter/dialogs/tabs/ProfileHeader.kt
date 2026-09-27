package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.output_profile_badge_full
import churchpresenter.composeapp.generated.resources.output_profile_badge_lower_third
import churchpresenter.composeapp.generated.resources.output_profile_badge_stage
import churchpresenter.composeapp.generated.resources.profile_assign_output
import churchpresenter.composeapp.generated.resources.profile_detail_advanced
import churchpresenter.composeapp.generated.resources.profile_detail_basic
import churchpresenter.composeapp.generated.resources.profile_link_standalone
import churchpresenter.composeapp.generated.resources.profile_not_in_use
import churchpresenter.composeapp.generated.resources.profile_page_title
import churchpresenter.composeapp.generated.resources.profile_shown_on
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

private const val BADGE_FILL_ALPHA = 0.16f

/** A corner of half the height: the chips are pills. */
private const val PILL_PERCENT = 50

/**
 * The top of the settings column: which profile and page this is, how the profile is linked, which
 * outputs show it, and Basic / Advanced.
 *
 * [linkState] is the start of the second line -- "Standalone profile" until a profile can be
 * linked -- and [usedBy] the outputs following it, each a chip; "Assign output" opens the Outputs
 * page, where one is picked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfilePageHeader(
    profile: OutputProfile,
    page: ProfilePage,
    usedBy: List<String>,
    detail: SettingsDetail,
    onDetailChange: (SettingsDetail) -> Unit,
    onAssignOutput: () -> Unit,
    modifier: Modifier = Modifier,
    linkState: @Composable RowScope.() -> Unit = { StandaloneLinkState() },
) {
    val palette = profilesPalette()
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(Res.string.profile_page_title, profile.displayName(), page.label()),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).testTag(PROFILE_PAGE_TITLE_TAG),
                )
                ProfileModeBadge(profile.displayMode)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    linkState()
                }
                Text("|", fontSize = 12.sp, color = palette.faintText)
                Text(
                    stringResource(Res.string.profile_shown_on),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                usedBy.forEach { OutputChip(it) }
                AssignOutputChip(onAssignOutput)
            }
        }
        if (page.hasDetailSwitch) {
            RowSegmented(
                options = listOf(
                    RowOption(SettingsDetail.BASIC, stringResource(Res.string.profile_detail_basic), DETAIL_BASIC_TAG),
                    RowOption(
                        SettingsDetail.ADVANCED,
                        stringResource(Res.string.profile_detail_advanced),
                        DETAIL_ADVANCED_TAG,
                    ),
                ),
                selected = detail,
                onSelect = onDetailChange,
            )
        }
    }
}

/** "Standalone profile", with its icon -- a profile linked to nothing. */
@Composable
internal fun StandaloneLinkState() {
    HeaderLinkLine(Icons.Filled.LinkOff, stringResource(Res.string.profile_link_standalone))
}

/** The icon and words at the start of the header's second line. */
@Composable
internal fun HeaderLinkLine(icon: ImageVector, text: String) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(15.dp),
    )
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

/** One output following the profile: a pill with a green dot. */
@Composable
internal fun OutputChip(label: String) {
    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(AppShape(PILL_PERCENT))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(MaterialTheme.semantic.success, CircleShape))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

@Composable
private fun AssignOutputChip(onClick: () -> Unit) {
    val palette = profilesPalette()
    Row(
        modifier = Modifier
            .height(22.dp)
            .dashedBorder(MaterialTheme.colorScheme.outline, 11.dp)
            .clip(AppShape(PILL_PERCENT))
            .clickable(onClick = onClick)
            .testTag(ASSIGN_OUTPUT_TAG)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = palette.faintText, modifier = Modifier.size(12.dp))
        Text(
            stringResource(Res.string.profile_assign_output),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The profile's name, or its id when it has none. */
internal fun OutputProfile.displayName(): String = name.ifBlank { id }

/** The outputs using a profile, "Screen 1, NDI 2" -- or that none are. */
@Composable
internal fun usageText(usedBy: List<String>): String =
    if (usedBy.isEmpty()) stringResource(Res.string.profile_not_in_use) else usedBy.joinToString(", ")

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
            .background(color.copy(alpha = BADGE_FILL_ALPHA), AppShape(4.dp))
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

/** Test handle for the page title. */
internal const val PROFILE_PAGE_TITLE_TAG = "profile_page_title"

/** Test handles for the Basic / Advanced segments. */
internal const val DETAIL_BASIC_TAG = "profile_detail_basic"
internal const val DETAIL_ADVANCED_TAG = "profile_detail_advanced"

/** Test handle for the "Assign output" chip. */
internal const val ASSIGN_OUTPUT_TAG = "profile_assign_output"

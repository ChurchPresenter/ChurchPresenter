package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.output_profile_delete
import org.churchpresenter.strings.generated.resources.output_profile_duplicate
import org.churchpresenter.strings.generated.resources.profile_actions
import org.churchpresenter.strings.generated.resources.profile_delete_sub
import org.churchpresenter.strings.generated.resources.profile_display_mode
import org.churchpresenter.strings.generated.resources.profile_duplicate_sub
import org.churchpresenter.strings.generated.resources.profile_nav_profile
import org.churchpresenter.strings.generated.resources.profile_mode_full
import org.churchpresenter.strings.generated.resources.profile_mode_lower_third
import org.churchpresenter.strings.generated.resources.profile_mode_stage
import org.churchpresenter.strings.generated.resources.profile_name
import org.churchpresenter.settings.DISPLAY_MODE_PATH
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource

/**
 * General: the profile's name and what kind of screen it is for, and what can be done to it.
 *
 * The name was the header's field and the display mode the first row of the old editor; both are
 * set once and belong on the page about the profile itself, not above every page of its styling.
 *
 * [extraGroups] are the groups later pages of work add here -- the linking card, and what happens
 * on the output screen -- placed between the profile card and the actions.
 */
@Composable
internal fun ProfileGeneralPage(
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
    modeLocked: Boolean = false,
    modeSub: String? = null,
    extraGroups: @Composable () -> Unit = {},
    extraActions: @Composable () -> Unit = {},
    deleteBlockedNote: String? = null,
) {
    SettingsGroup(stringResource(Res.string.profile_nav_profile), key = "profile") {
        SettingsRow(stringResource(Res.string.profile_name)) {
            SettingsTextField(
                value = profile.name,
                onValueChange = onRename,
                placeholder = { Text(profile.id) },
                modifier = Modifier.width(NAME_FIELD_WIDTH).testTag(PROFILE_NAME_FIELD_TAG),
                fillWidth = true,
            )
        }
        SettingsRow(stringResource(Res.string.profile_display_mode), sub = modeSub, paths = listOf(DISPLAY_MODE_PATH)) {
            DisplayModeSegments(profile, onProfileChange, enabled = !modeLocked)
        }
    }
    extraGroups()
    SettingsGroup(stringResource(Res.string.profile_actions), key = "actions") {
        SettingsRow(
            stringResource(Res.string.output_profile_duplicate),
            sub = stringResource(Res.string.profile_duplicate_sub),
        ) {
            ActionKey(Icons.Filled.ContentCopy, stringResource(Res.string.output_profile_duplicate), onDuplicate)
        }
        extraActions()
        SettingsRow(
            stringResource(Res.string.output_profile_delete),
            sub = deleteBlockedNote ?: stringResource(Res.string.profile_delete_sub),
        ) {
            ActionKey(
                Icons.Filled.Delete,
                stringResource(Res.string.output_profile_delete),
                onRequestDelete,
                enabled = deleteBlockedNote == null,
                danger = true,
            )
        }
    }
}

private val NAME_FIELD_WIDTH = 240.dp

/** Full screen / Lower third / Stage monitor. A vertical lower third stays vertical when re-picked. */
@Composable
internal fun DisplayModeSegments(
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    enabled: Boolean = true,
) {
    RowSegmented(
        options = listOf(
            RowOption(Constants.DISPLAY_MODE_FULLSCREEN, stringResource(Res.string.profile_mode_full)),
            RowOption(
                Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                stringResource(Res.string.profile_mode_lower_third),
            ),
            RowOption(Constants.DISPLAY_MODE_STAGE_MONITOR, stringResource(Res.string.profile_mode_stage)),
        ),
        selected = shownDisplayMode(profile.displayMode),
        onSelect = { picked ->
            if (enabled) onProfileChange(profile.copy(displayMode = pickedDisplayMode(picked, profile.displayMode)))
        },
    )
}

/** A key with an icon and a word, as the page's actions are drawn. */
@Composable
internal fun ActionKey(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val tint = if (danger && enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    KeyButton(
        onClick = onClick,
        enabled = enabled,
        shape = AppShape(7.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.testTag(actionKeyTag(label)),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text("  $label", fontSize = 12.sp, color = tint, maxLines = 1)
    }
}

/** Test handle for the name field on General. */
internal const val PROFILE_NAME_FIELD_TAG = "profile_name_field"

/** Test handle for an action key, by its label. */
internal fun actionKeyTag(label: String): String = "profile_action_$label"

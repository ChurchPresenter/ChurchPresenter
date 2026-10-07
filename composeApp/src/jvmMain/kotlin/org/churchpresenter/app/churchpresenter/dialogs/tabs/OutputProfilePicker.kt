package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.profiles.HintTooltip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.output_profile_blank
import org.churchpresenter.strings.generated.resources.output_profile_dont_use
import org.churchpresenter.strings.generated.resources.projection_merged_by_profile
import org.churchpresenter.settings.BLANK_OUTPUT_PROFILE_ID
import org.churchpresenter.settings.OutputProfile
import org.jetbrains.compose.resources.stringResource

/**
 * The one thing left to choose per output: which [OutputProfile] it follows.
 *
 * Everything about how an output looks and what it shows now lives on the profile, not the
 * output itself -- see `ScreenAssignment.activeProfileId`. Shared by the Screen/Browser
 * Source/NDI rows on the Projection tab and the Live Preview sidebar's swap menu, since all three
 * do exactly the same job: write `activeProfileId`.
 *
 * [onDontUse], given only for a screen row that drives a monitor, adds **Don't use** under Blank:
 * the monitor is marked unused and the row set to None -- see `ProjectionSettings.withScreenUnused`.
 * The network outputs have no monitor, so they never pass it.
 */
@Composable
internal fun OutputProfilePicker(
    profiles: List<OutputProfile>,
    activeProfileId: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** The profile merging this output into its picture, which then decides it -- see OutputMerge.kt. */
    mergedBy: OutputProfile? = null,
    onDontUse: (() -> Unit)? = null,
) {
    if (mergedBy != null) {
        val note = stringResource(Res.string.projection_merged_by_profile, mergedBy.name.ifBlank { mergedBy.id })
        HintTooltip(note) {
            KeyButton(
                shape = AppShape(6.dp),
                onClick = {},
                enabled = false,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = modifier,
            ) {
                Text(note, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    val active = profiles.find { it.id == activeProfileId }
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        KeyButton(
            shape = AppShape(6.dp),
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (activeProfileId == BLANK_OUTPUT_PROFILE_ID) stringResource(Res.string.output_profile_blank)
                else active?.let { it.name.ifBlank { it.id } }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(Res.string.output_profile_blank), style = MaterialTheme.typography.bodySmall)
                },
                onClick = { expanded = false; onPick(BLANK_OUTPUT_PROFILE_ID) },
            )
            if (onDontUse != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.output_profile_dont_use),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    onClick = { expanded = false; onDontUse() },
                )
            }
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name.ifBlank { profile.id }, style = MaterialTheme.typography.bodySmall) },
                    onClick = { expanded = false; onPick(profile.id) },
                )
            }
        }
    }
}

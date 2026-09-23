package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.output_profile_none
import org.churchpresenter.settings.OutputProfile
import org.jetbrains.compose.resources.stringResource

/**
 * The one thing left to choose per output: which [OutputProfile] it follows.
 *
 * Everything about how an output looks and what it shows now lives on the profile, not the
 * output itself -- see `ScreenAssignment.activeProfileId`. Shared by the Screen/Browser
 * Source/NDI rows on the Projection tab and the Live Preview sidebar's swap menu, since all three
 * do exactly the same job: write `activeProfileId`.
 */
@Composable
internal fun OutputProfilePicker(
    profiles: List<OutputProfile>,
    activeProfileId: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = profiles.find { it.id == activeProfileId }
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            shape = RoundedCornerShape(6.dp),
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = active?.name?.ifBlank { active.id } ?: stringResource(Res.string.output_profile_none),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(Res.string.output_profile_none), style = MaterialTheme.typography.bodySmall)
                },
                onClick = { expanded = false; onPick(null) },
            )
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name.ifBlank { profile.id }, style = MaterialTheme.typography.bodySmall) },
                    onClick = { expanded = false; onPick(profile.id) },
                )
            }
        }
    }
}

package org.churchpresenter.liveoutput.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.output_profile_blank
import org.churchpresenter.strings.generated.resources.output_profile_swap_menu_tooltip
import org.churchpresenter.strings.generated.resources.collapse_preview
import org.churchpresenter.strings.generated.resources.expand_preview
import org.churchpresenter.settings.BLANK_OUTPUT_PROFILE_ID
import org.churchpresenter.settings.OutputProfile
import org.jetbrains.compose.resources.stringResource

// The line above each preview: the output's name and mode, the fold caret and the profile picker.

/**
 * The row above one preview: the output's display mode, and a caret that folds the picture away.
 *
 * The mode chip used to be drawn for Stage Monitor and Lower Third only, so the ordinary full-screen
 * outputs -- the majority of them -- were the ones with nothing written above them. Every mode names
 * itself now, which is also what gives every preview a row to click.
 *
 * [outputLabel] is drawn **only while collapsed**, and against the far edge. Open, the output
 * already names itself in the corner of its own picture and repeating it here would say the same
 * thing twice in one glance; collapsed, that corner is gone and a stack of rows reading "Full
 * Screen" three times over could not be told apart. Pushing it to the trailing edge keeps it in the
 * column the in-picture label sits in, so the name does not jump across the row as a preview folds.
 */
@Composable
internal fun PreviewHeader(
    modeLabel: String,
    outputLabel: String,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
    profiles: List<OutputProfile> = emptyList(),
    activeProfileId: String? = null,
    onPickProfile: (String) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape(3.dp))
            .then(if (collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (collapsible) Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) stringResource(Res.string.collapse_preview)
                                 else stringResource(Res.string.expand_preview),
            modifier = Modifier.size(14.dp).rotate(if (expanded) 0f else CARET_CLOSED_DEGREES),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = modeLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
        )
        // Quick-swap: which Output Profile this output follows, right where the operator is
        // already watching it live. Only shown once a profile exists to swap to -- an empty menu
        // would just be clutter on every tile -- and only while the tile is open, where there is
        // room for it; the collapsed row is one line reserved for the label below.
        if (expanded && profiles.isNotEmpty()) {
            OutputProfileSwapMenu(profiles = profiles, activeProfileId = activeProfileId, onPick = onPickProfile)
        }
        if (!expanded) {
            Spacer(Modifier.weight(1f))
            Text(
                text = outputLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The header's profile picker: an icon button that opens a menu of every saved [OutputProfile]. */
@Composable
private fun OutputProfileSwapMenu(
    profiles: List<OutputProfile>,
    activeProfileId: String?,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        KeyIconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(18.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = stringResource(Res.string.output_profile_swap_menu_tooltip),
                modifier = Modifier.size(14.dp),
                tint = if (activeProfileId != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(Res.string.output_profile_blank),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (activeProfileId == BLANK_OUTPUT_PROFILE_ID) FontWeight.Bold
                        else FontWeight.Normal,
                    )
                },
                onClick = { expanded = false; onPick(BLANK_OUTPUT_PROFILE_ID) },
            )
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Text(
                            profile.name.ifBlank { profile.id },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (profile.id == activeProfileId) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = { expanded = false; onPick(profile.id) },
                )
            }
        }
    }
}

/** How far the caret turns when the preview is folded away; the same quarter turn the tray uses. */
private const val CARET_CLOSED_DEGREES = -90f

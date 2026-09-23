package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.ndi_output_numbered
import churchpresenter.composeapp.generated.resources.output_profile_create_default
import churchpresenter.composeapp.generated.resources.output_profile_delete
import churchpresenter.composeapp.generated.resources.output_profile_duplicate
import churchpresenter.composeapp.generated.resources.output_profile_empty_state
import churchpresenter.composeapp.generated.resources.output_profile_new
import churchpresenter.composeapp.generated.resources.output_profile_new_name_default
import churchpresenter.composeapp.generated.resources.output_profile_usage_count
import churchpresenter.composeapp.generated.resources.screen_number
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.deleteOutputProfile
import org.churchpresenter.settings.duplicateOutputProfile
import org.churchpresenter.settings.newOutputProfile
import org.churchpresenter.settings.outputProfileUsageCount
import org.churchpresenter.settings.renameOutputProfile
import org.churchpresenter.settings.updateOutputProfile
import org.jetbrains.compose.resources.stringResource

/**
 * The Profiles tab: every named [OutputProfile] in one place, with a full editing surface for the
 * one selected.
 *
 * An output only ever *assigns* a profile now (see the pickers on the Projection tab and in
 * `LivePreviewPanel`'s sidebar) -- there is nothing left to edit per-output, so this is the only
 * place a profile's styling is changed. The shipped "Default" profile is not protected: it is
 * exactly as editable and deletable as one made here, which is why [output_profile_create_default]
 * exists -- a plain, explicit way to get a fresh factory-default profile back as a starting point
 * or a backup, rather than a special-cased entry nothing else can touch.
 */
@Composable
internal fun ProfilesSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val proj = settings.projectionSettings
    var selectedId by remember { mutableStateOf(proj.outputProfiles.firstOrNull()?.id) }
    // Clamped rather than stored: a profile can disappear out from under the stored id (deleted
    // here, or by another operator on a shared document), and this must fall back the moment that
    // happens rather than pointing at nothing.
    val effectiveId = selectedId?.takeIf { id -> proj.outputProfiles.any { it.id == id } }
        ?: proj.outputProfiles.firstOrNull()?.id
    val profile = proj.outputProfiles.find { it.id == effectiveId }

    // Set by either the rail's own Delete button or the editor's, so one dialog and one confirm
    // path covers deleting the open profile and deleting any other one without selecting it first.
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val pendingDelete = proj.outputProfiles.find { it.id == pendingDeleteId }

    fun updateProjection(transform: (ProjectionSettings) -> ProjectionSettings) {
        onSettingsChange { s -> s.copy(projectionSettings = transform(s.projectionSettings)) }
    }

    val defaultProfileName = stringResource(Res.string.output_profile_new_name_default)

    Row(modifier = Modifier.fillMaxSize()) {
        ProfilesRail(
            profiles = proj.outputProfiles,
            selectedId = effectiveId,
            usageCountOf = { id -> proj.outputProfileUsageCount(id) },
            onSelect = { selectedId = it },
            onNew = {
                val fresh = newOutputProfile(proj.outputProfiles)
                updateProjection { it.copy(outputProfiles = it.outputProfiles + fresh) }
                selectedId = fresh.id
            },
            onCreateDefault = {
                val fresh = newOutputProfile(proj.outputProfiles, defaultProfileName)
                updateProjection { it.copy(outputProfiles = it.outputProfiles + fresh) }
                selectedId = fresh.id
            },
            onDuplicate = { id ->
                val source = proj.outputProfiles.find { it.id == id } ?: return@ProfilesRail
                val copyName = duplicateName(source.name.ifBlank { defaultProfileName })
                updateProjection { it.duplicateOutputProfile(id, copyName) }
            },
            onRequestDelete = { pendingDeleteId = it },
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (profile != null) {
            ProfileEditor(
                settings = settings,
                profile = profile,
                onSettingsChange = onSettingsChange,
                onProfileChange = { updated ->
                    updateProjection { it.updateOutputProfile(profile.id) { updated } }
                },
                onRename = { name -> updateProjection { it.renameOutputProfile(profile.id, name) } },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.output_profile_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (pendingDelete != null) {
        DeleteProfileDialog(
            profileName = pendingDelete.name.ifBlank { pendingDelete.id },
            userLabels = profileUserLabels(proj, pendingDelete.id),
            onConfirm = {
                updateProjection { it.deleteOutputProfile(pendingDelete.id) }
                if (selectedId == pendingDelete.id) selectedId = null
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

/** "Foyer TV" → "Foyer TV copy", "Foyer TV copy" → "Foyer TV copy copy": no de-duplication attempted. */
private fun duplicateName(name: String): String = "$name copy"

/** Every output currently following [id], labeled the way its own card labels it. */
@Composable
private fun profileUserLabels(proj: ProjectionSettings, id: String): List<String> {
    val labels = mutableListOf<String>()
    proj.screenAssignments.forEachIndexed { index, assignment ->
        if (assignment.activeProfileId == id) {
            labels += proj.screenLabelOr(assignment, stringResource(Res.string.screen_number, index + 1))
        }
    }
    proj.browserSourceOutputs.forEachIndexed { index, output ->
        if (output.activeProfileId == id) {
            labels += output.browserSourceLabelOr(
                stringResource(Res.string.browser_source_output_label, index + 1),
            )
        }
    }
    proj.ndiOutputs.forEachIndexed { index, output ->
        if (output.activeProfileId == id) {
            labels += output.ndiLabelOr(stringResource(Res.string.ndi_output_numbered, index + 1))
        }
    }
    return labels
}

/** The left rail: every profile, New/Create Default above it. */
@Composable
private fun ProfilesRail(
    profiles: List<OutputProfile>,
    selectedId: String?,
    usageCountOf: (String) -> Int,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onCreateDefault: () -> Unit,
    onDuplicate: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.width(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(Res.string.output_profile_new), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RailIconTextButton(
            icon = Icons.Filled.RestartAlt,
            label = stringResource(Res.string.output_profile_create_default),
            onClick = onCreateDefault,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                profiles.forEach { profile ->
                    ProfileRailRow(
                        profile = profile,
                        selected = profile.id == selectedId,
                        usageCount = usageCountOf(profile.id),
                        onSelect = { onSelect(profile.id) },
                        onDuplicate = { onDuplicate(profile.id) },
                        onRequestDelete = { onRequestDelete(profile.id) },
                    )
                }
            }
            SettingsScrollbar(scrollState)
        }
    }
}

// Wide enough for a name, its usage count and the Duplicate/Delete icons without crushing the
// name down to a couple of characters before it ellipsizes.
private val RAIL_WIDTH = 260.dp

@Composable
private fun RailIconTextButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.width(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One rail row: the profile's name, its usage count, and Duplicate/Delete actions. */
@Composable
private fun ProfileRailRow(
    profile: OutputProfile,
    selected: Boolean,
    usageCount: Int,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val ink = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name.ifBlank { profile.id },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (usageCount > 0) {
                Text(
                    text = stringResource(Res.string.output_profile_usage_count, usageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDuplicate, modifier = Modifier.width(28.dp)) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(Res.string.output_profile_duplicate),
                modifier = Modifier.width(14.dp),
                tint = ink,
            )
        }
        IconButton(onClick = onRequestDelete, modifier = Modifier.width(28.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(Res.string.output_profile_delete),
                modifier = Modifier.width(14.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}


package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.ndi_output_numbered
import churchpresenter.composeapp.generated.resources.output_profile_empty_state
import churchpresenter.composeapp.generated.resources.output_profile_new_name_default
import churchpresenter.composeapp.generated.resources.screen_number
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.deleteOutputProfile
import org.churchpresenter.settings.duplicateOutputProfile
import org.churchpresenter.settings.moveOutputProfile
import org.churchpresenter.settings.newOutputProfile
import org.churchpresenter.settings.renameOutputProfile
import org.churchpresenter.settings.updateOutputProfile
import org.jetbrains.compose.resources.stringResource

/**
 * The Profiles tab: every named [OutputProfile] down the left, and the one selected in four
 * columns -- the list, its pages, the page's settings and the preview.
 *
 * An output only ever *assigns* a profile (on the Projection tab, in the live preview's sidebar, or
 * on the Outputs page here), so this is the only place a profile's styling is changed. A profile is
 * a reusable style, not a display: several outputs of different sizes can share one, so the list
 * says what uses each profile rather than naming a connection or a resolution.
 */
@Composable
internal fun ProfilesSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /** Shows each display's number on screen -- the Projection tab's Identify. */
    onIdentify: () -> Unit = {},
) {
    val proj = settings.projectionSettings
    var selectedId by remember { mutableStateOf(proj.outputProfiles.firstOrNull()?.id) }
    // Clamped rather than stored: a profile can disappear out from under the stored id, and this
    // must fall back the moment that happens rather than pointing at nothing.
    val effectiveId = selectedId?.takeIf { id -> proj.outputProfiles.any { it.id == id } }
        ?: proj.outputProfiles.firstOrNull()?.id
    val profile = proj.outputProfiles.find { it.id == effectiveId }
    // Held here rather than in the editor, so moving between profiles keeps the page -- comparing
    // two profiles' Bible styling is one of the reasons to move between them.
    var page by remember { mutableStateOf<ProfilePage>(ProfilePage.General) }

    // Set by the Delete action, so one dialog and one confirm path covers every way of deleting.
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val pendingDelete = proj.outputProfiles.find { it.id == pendingDeleteId }

    fun updateProjection(transform: (ProjectionSettings) -> ProjectionSettings) {
        onSettingsChange { s -> s.copy(projectionSettings = transform(s.projectionSettings)) }
    }

    val defaultProfileName = stringResource(Res.string.output_profile_new_name_default)
    val usage = proj.outputProfiles.associate { it.id to profileUserLabels(proj, it.id) }

    Row(modifier = Modifier.fillMaxSize()) {
        ProfilesList(
            profiles = proj.outputProfiles,
            selectedId = effectiveId,
            usageOf = { id -> usage[id].orEmpty() },
            actions = ProfileListActions(
                onSelect = { selectedId = it },
                onNew = {
                    // Unnamed: General opens on it, with its name field waiting to be filled in.
                    val fresh = newOutputProfile(proj.outputProfiles)
                    updateProjection { it.copy(outputProfiles = it.outputProfiles + fresh) }
                    selectedId = fresh.id
                    page = ProfilePage.General
                },
                onMove = { id, to -> updateProjection { it.moveOutputProfile(id, to) } },
                onRename = { id, name -> updateProjection { it.renameOutputProfile(id, name) } },
                onDuplicate = { id ->
                    val source = proj.outputProfiles.find { it.id == id }
                    if (source != null) {
                        val copyName = duplicateName(source.name.ifBlank { defaultProfileName })
                        updateProjection { it.duplicateOutputProfile(id, copyName) }
                    }
                },
                onDelete = { pendingDeleteId = it },
            ),
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (profile != null) {
            ProfileEditor(
                settings = settings,
                profile = profile,
                usedBy = usage[profile.id].orEmpty(),
                page = page,
                onPageChange = { page = it },
                onSettingsChange = onSettingsChange,
                onProfileChange = { updated -> updateProjection { it.updateOutputProfile(profile.id) { updated } } },
                onRename = { name -> updateProjection { it.renameOutputProfile(profile.id, name) } },
                onDuplicate = {
                    val copyName = duplicateName(profile.name.ifBlank { defaultProfileName })
                    updateProjection { it.duplicateOutputProfile(profile.id, copyName) }
                },
                onRequestDelete = { pendingDeleteId = profile.id },
                onIdentify = onIdentify,
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
            userLabels = usage[pendingDelete.id].orEmpty(),
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
internal fun duplicateName(name: String): String = "$name copy"

/** Every output currently following [id], labeled the way its own card labels it. */
@Composable
internal fun profileUserLabels(proj: ProjectionSettings, id: String): List<String> {
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

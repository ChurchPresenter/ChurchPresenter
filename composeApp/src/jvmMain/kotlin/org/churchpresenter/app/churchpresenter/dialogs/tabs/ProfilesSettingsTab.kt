package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.ndi_output_numbered
import churchpresenter.composeapp.generated.resources.output_profile_empty_state
import churchpresenter.composeapp.generated.resources.output_profile_list_header
import churchpresenter.composeapp.generated.resources.output_profile_new_name_default
import churchpresenter.composeapp.generated.resources.profile_list_hint
import churchpresenter.composeapp.generated.resources.profile_list_new
import churchpresenter.composeapp.generated.resources.screen_number
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.deleteOutputProfile
import org.churchpresenter.settings.duplicateOutputProfile
import org.churchpresenter.settings.newOutputProfile
import org.churchpresenter.settings.renameOutputProfile
import org.churchpresenter.settings.updateOutputProfile
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyIconButton
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
            onSelect = { selectedId = it },
            onNew = {
                // Unnamed: General opens on it, with its name field waiting to be filled in.
                val fresh = newOutputProfile(proj.outputProfiles)
                updateProjection { it.copy(outputProfiles = it.outputProfiles + fresh) }
                selectedId = fresh.id
                page = ProfilePage.General
            },
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

/** The profile list is this wide: a name, its badge, and what uses it under them. */
internal val PROFILE_LIST_WIDTH = 250.dp

/** The left column: every profile in order, with "+" above them and a hint below. */
@Composable
private fun ProfilesList(
    profiles: List<OutputProfile>,
    selectedId: String?,
    usageOf: (String) -> List<String>,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
) {
    val palette = profilesPalette()
    Column(
        modifier = Modifier
            .width(PROFILE_LIST_WIDTH)
            .fillMaxHeight()
            .background(palette.rail),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupCaption(stringResource(Res.string.output_profile_list_header), Modifier.weight(1f))
            KeyIconButton(onClick = onNew, modifier = Modifier.size(30.dp).testTag(NEW_PROFILE_TAG)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.profile_list_new),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                profiles.forEach { profile ->
                    ProfileListRow(
                        profile = profile,
                        selected = profile.id == selectedId,
                        usedBy = usageOf(profile.id),
                        onSelect = { onSelect(profile.id) },
                    )
                }
            }
            SettingsScrollbar(scrollState)
        }
        Text(
            text = stringResource(Res.string.profile_list_hint),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = palette.faintText,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
        )
    }
}

private const val HOVER_WASH_ALPHA = 0.06f

/** One profile: its mode's dot, its name, what uses it, and its mode's badge. */
@Composable
private fun ProfileListRow(
    profile: OutputProfile,
    selected: Boolean,
    usedBy: List<String>,
    onSelect: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val background = when {
        selected -> scheme.secondaryContainer
        hovered -> scheme.onSurface.copy(alpha = HOVER_WASH_ALPHA)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape(9.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(onClick = onSelect)
            .testTag(profileRowTag(profile.id))
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProfileModeDot(profile.displayMode)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName(),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = usageText(usedBy),
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(2.dp))
        ProfileModeBadge(profile.displayMode)
    }
}

/** Test handle for one row of the profile list. */
internal fun profileRowTag(id: String): String = "profile_row_$id"

/** Test handle for the "+" that makes a new profile. */
internal const val NEW_PROFILE_TAG = "profile_new"

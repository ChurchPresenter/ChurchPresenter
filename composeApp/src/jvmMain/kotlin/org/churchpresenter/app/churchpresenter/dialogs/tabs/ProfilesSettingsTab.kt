package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.ndi_output_numbered
import churchpresenter.composeapp.generated.resources.output_profile_create_default
import churchpresenter.composeapp.generated.resources.output_profile_create_default_tooltip
import churchpresenter.composeapp.generated.resources.output_profile_delete
import churchpresenter.composeapp.generated.resources.output_profile_duplicate
import churchpresenter.composeapp.generated.resources.output_profile_empty_state
import churchpresenter.composeapp.generated.resources.output_profile_identify_tooltip
import churchpresenter.composeapp.generated.resources.output_profile_list_header
import churchpresenter.composeapp.generated.resources.output_profile_new
import churchpresenter.composeapp.generated.resources.output_profile_new_name_default
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
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.KeyIconButton
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
 *
 * A profile is a reusable style, not a display: several outputs of different sizes can share one,
 * so the list says what uses each profile rather than naming a connection or a resolution.
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
    // Clamped rather than stored: a profile can disappear out from under the stored id (deleted
    // here, or by another operator on a shared document), and this must fall back the moment that
    // happens rather than pointing at nothing.
    val effectiveId = selectedId?.takeIf { id -> proj.outputProfiles.any { it.id == id } }
        ?: proj.outputProfiles.firstOrNull()?.id
    val profile = proj.outputProfiles.find { it.id == effectiveId }

    // Set by the list's own Delete button, so one dialog and one confirm path covers deleting the
    // open profile and deleting any other one without selecting it first.
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val pendingDelete = proj.outputProfiles.find { it.id == pendingDeleteId }

    // Held here rather than in the editor, so it stays as the operator left it when they move
    // between profiles -- comparing what two profiles show is one of the reasons to open it.
    var contentOpen by remember { mutableStateOf(false) }

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
            onIdentify = onIdentify,
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
                val source = proj.outputProfiles.find { it.id == id } ?: return@ProfilesList
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
                usedBy = usage[profile.id].orEmpty(),
                onSettingsChange = onSettingsChange,
                onProfileChange = { updated ->
                    updateProjection { it.updateOutputProfile(profile.id) { updated } }
                },
                onRename = { name -> updateProjection { it.renameOutputProfile(profile.id, name) } },
                contentOpen = contentOpen,
                onContentOpenChange = { contentOpen = it },
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

// Wide enough for a name and its badge side by side, with the usage line under it.
private val LIST_WIDTH = 248.dp
private const val UNASSIGNED_ALPHA = 0.7f

/** The left column: every profile, with Identify, New and Create Default above them. */
@Composable
private fun ProfilesList(
    profiles: List<OutputProfile>,
    selectedId: String?,
    usageOf: (String) -> List<String>,
    onSelect: (String) -> Unit,
    onIdentify: () -> Unit,
    onNew: () -> Unit,
    onCreateDefault: () -> Unit,
    onDuplicate: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(LIST_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CustomizeCaption(stringResource(Res.string.output_profile_list_header))
            Spacer(modifier = Modifier.weight(1f))
            WithTooltip(stringResource(Res.string.output_profile_identify_tooltip)) {
                SmallKey(label = stringResource(Res.string.identify_screen), onClick = onIdentify)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyButton(
                onClick = onNew,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    stringResource(Res.string.output_profile_new),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WithTooltip(stringResource(Res.string.output_profile_create_default_tooltip)) {
                ListIconKey(
                    icon = Icons.Filled.RestartAlt,
                    description = stringResource(Res.string.output_profile_create_default),
                    onClick = onCreateDefault,
                )
            }
        }
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
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
                        onDuplicate = { onDuplicate(profile.id) },
                        onRequestDelete = { onRequestDelete(profile.id) },
                    )
                }
            }
            SettingsScrollbar(scrollState)
        }
    }
}

/**
 * One profile: a dot in its display mode's colour, its name, what uses it and its mode's badge.
 * Duplicate and Delete appear under the pointer, and stay on the selected row.
 */
@Composable
private fun ProfileListRow(
    profile: OutputProfile,
    selected: Boolean,
    usedBy: List<String>,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val background = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_WASH_ALPHA)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProfileModeDot(profile.displayMode)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name.ifBlank { profile.id },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = usageText(usedBy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (usedBy.isEmpty()) UNASSIGNED_ALPHA else 1f),
            )
        }
        if (hovered || selected) {
            ListIconKey(
                icon = Icons.Filled.ContentCopy,
                description = stringResource(Res.string.output_profile_duplicate),
                onClick = onDuplicate,
                size = ROW_KEY_SIZE,
            )
            ListIconKey(
                icon = Icons.Filled.Delete,
                description = stringResource(Res.string.output_profile_delete),
                onClick = onRequestDelete,
                size = ROW_KEY_SIZE,
                tint = MaterialTheme.colorScheme.error,
            )
        } else {
            ProfileModeBadge(profile.displayMode)
        }
    }
}

private const val HOVER_WASH_ALPHA = 0.06f
private val ROW_KEY_SIZE = 26.dp

@Composable
private fun ListIconKey(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp = 32.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    KeyIconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun SmallKey(label: String, onClick: () -> Unit) {
    KeyButton(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithTooltip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

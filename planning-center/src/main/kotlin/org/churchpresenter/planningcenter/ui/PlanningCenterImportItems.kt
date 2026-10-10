package org.churchpresenter.planningcenter.ui

import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.strings.generated.resources.planning_center_import_button
import org.churchpresenter.strings.generated.resources.cancel
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.minimumInteractiveComponentSize
import org.churchpresenter.planningcenter.PcoItemType
import org.churchpresenter.theme.components.toggleRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.RaisedCheckbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.planning_center_disconnect
import org.churchpresenter.strings.generated.resources.planning_center_import_add_song
import org.churchpresenter.strings.generated.resources.planning_center_import_deselect_all
import org.churchpresenter.strings.generated.resources.planning_center_import_file_count
import org.churchpresenter.strings.generated.resources.planning_center_import_items
import org.churchpresenter.strings.generated.resources.planning_center_import_matched
import org.churchpresenter.strings.generated.resources.planning_center_import_select_all
import org.churchpresenter.strings.generated.resources.planning_center_import_select_plan
import org.churchpresenter.strings.generated.resources.planning_center_import_service_type
import org.churchpresenter.strings.generated.resources.planning_center_status_connected
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.sharedui.composables.LabeledCheckbox
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.settings.PlanningCenterSettings
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.RowScope

/** The service type and plan pickers, and who is connected with a way to disconnect. */
@Composable
internal fun PcoImportHeader(
    viewModel: PlanningCenterImportViewModel,
    settings: PlanningCenterSettings,
    onDisconnect: () -> Unit,
) {
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    DropdownSelector(
        label = stringResource(Res.string.planning_center_import_service_type),
        value = viewModel.selectedServiceTypeId,
        options = viewModel.serviceTypes.map { it.id to it.name },
        onValueChange = { id -> viewModel.selectServiceType(id) },
        modifier = Modifier.weight(1f).guideTarget(GuideTargets.PCO_SERVICE_TYPE)
    )
    if (viewModel.plans.isNotEmpty() && !viewModel.isLoadingPlans) {
        Spacer(Modifier.width(12.dp))
        DropdownSelector(
            label = stringResource(Res.string.planning_center_import_select_plan),
            value = viewModel.selectedPlanId ?: "",
            options = viewModel.plans.map { plan ->
                plan.id to "${plan.title}${if (plan.dates.isNotBlank()) " — ${plan.dates}" else ""}"
            },
            onValueChange = { viewModel.selectPlan(it) },
            modifier = Modifier.weight(1f).guideTarget(GuideTargets.PCO_PLAN)
        )
    }
    Spacer(Modifier.width(12.dp))
    Box(
        modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.semantic.success)
    )
    Spacer(Modifier.width(6.dp))
    Text(
        stringResource(
            Res.string.planning_center_status_connected,
            settings.connectedPersonName.ifBlank { "?" }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.semantic.success
    )
    Spacer(Modifier.width(10.dp))
    KeyButton(
        onClick = onDisconnect,
        shape = AppShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            stringResource(Res.string.planning_center_disconnect),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
}

/** The items heading, with select all. */
@Composable
internal fun PcoItemsHeader(viewModel: PlanningCenterImportViewModel) {
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        stringResource(Res.string.planning_center_import_items),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.width(12.dp))
    val selectAllInteraction = remember { MutableInteractionSource() }
    val canSelect = viewModel.planItems.isNotEmpty()
    Row(
        modifier = Modifier.toggleRow(
            checked = viewModel.allSelected,
            onCheckedChange = { viewModel.setAllSelected(it) },
            interaction = selectAllInteraction,
            role = Role.Checkbox,
            enabled = canSelect,
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RaisedCheckbox(
            checked = viewModel.allSelected,
            onCheckedChange = null,
            enabled = canSelect,
            interactionSource = selectAllInteraction,
            modifier = Modifier.minimumInteractiveComponentSize(),
        )
        Text(
            if (viewModel.allSelected) {
                stringResource(Res.string.planning_center_import_deselect_all)
            } else {
                stringResource(Res.string.planning_center_import_select_all)
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}
}

/**
 * One plan item: its row by type, the scripture it mentions, and -- expanded -- its attachments.
 * [onAddSong] asks to add a song the library does not have yet.
 */
@Composable
internal fun PcoPlanItemRow(
    entry: PlanningCenterImportViewModel.ImportPlanItem,
    viewModel: PlanningCenterImportViewModel,
    isFetchingArrangement: String?,
    onAddSong: (PlanningCenterClient.PlanItem) -> Unit,
) {
val pco = entry.pco
// Scoped to the whole row (not just the button's branch) so the
// attachments list below can also check it — a real accordion.
var expanded by remember(pco.id) { mutableStateOf(false) }
val hasScripture = viewModel.detectedScripturesByItemId[pco.id]?.isNotEmpty() == true
val isExpandable = pco.itemType == PcoItemType.ITEM && !hasScripture &&
    viewModel.attachmentsByItemId.containsKey(pco.id) &&
    (viewModel.attachmentsByItemId[pco.id]?.size ?: 0) > 0
Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(
                if (isExpandable) Modifier.clickable { expanded = !expanded } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (pco.itemType) {
            PcoItemType.SONG -> SongItemCells(entry, viewModel, isFetchingArrangement, onAddSong)
            PcoItemType.HEADER -> {
                RaisedCheckbox(
                    checked = entry.selected,
                    onCheckedChange = { viewModel.toggleItemSelected(pco.id) }
                )
                PlanItemTypeIcon(
                    Icons.AutoMirrored.Filled.Label,
                    tint = MaterialTheme.semantic.warning
                )
                Text(
                    pco.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            PcoItemType.MEDIA -> {
                // PCO "media" items reference video/audio that lives
                // in the Media Library, not the Attachments API — the
                // "attachments" this endpoint would return are incidental
                // files, not the actual media, so there's nothing usable
                // to import. Show the row disabled — a greyed,
                // uncheckable checkbox and muted title (e.g. YouTube/
                // Vimeo videos, which are external links, not files).
                // Non-null (no-op) onCheckedChange keeps the same
                // minimumInteractiveComponentSize footprint as the
                // interactive rows so the checkbox stays aligned.
                RaisedCheckbox(checked = false, enabled = false, onCheckedChange = {})
                PlanItemTypeIcon(
                    Icons.Filled.PlayCircle,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    pco.title,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textDecoration = TextDecoration.LineThrough
                )
            }
            else -> GenericItemCells(entry, viewModel, hasScripture)
        }
    }

    ItemScriptures(pco, viewModel)

    if (expanded && pco.itemType == PcoItemType.ITEM) ItemAttachments(pco, viewModel)
}
}

/** A song row: matched to the library, or offering to add it. */
@Composable
private fun RowScope.SongItemCells(
    entry: PlanningCenterImportViewModel.ImportPlanItem,
    viewModel: PlanningCenterImportViewModel,
    isFetchingArrangement: String?,
    onAddSong: (PlanningCenterClient.PlanItem) -> Unit,
) {
    val pco = entry.pco
    RaisedCheckbox(
        checked = entry.selected && entry.matchedSongId != null,
        enabled = entry.matchedSongId != null,
        onCheckedChange = { viewModel.toggleItemSelected(pco.id) }
    )
    PlanItemTypeIcon(Icons.Filled.MusicNote)
    Text(pco.songTitle ?: pco.title, modifier = Modifier.weight(1f))
    if (entry.matchedSongId != null) {
        MatchedTag()
    } else if (isFetchingArrangement == pco.id) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
    } else {
        RaisedButton(
            shape = AppShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp),
            onClick = { onAddSong(pco) }
        ) {
            Text(
                stringResource(Res.string.planning_center_import_add_song),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** A generic plan item: imported as an announcement, or by its scripture or attachments. */
@Composable
private fun RowScope.GenericItemCells(
    entry: PlanningCenterImportViewModel.ImportPlanItem,
    viewModel: PlanningCenterImportViewModel,
    hasScripture: Boolean,
) {
    val pco = entry.pco
    // Generic "item" rows: if scripture references were
    // detected (see below), that accordion IS the selection
    // mechanism — no separate announcement checkbox needed.
    // Otherwise fall back to a plain checkbox (import the
    // title as an announcement). hasScripture is hoisted above.
    if (!hasScripture) {
        RaisedCheckbox(
            checked = entry.selected,
            onCheckedChange = { viewModel.toggleItemSelected(pco.id) }
        )
    } else {
        Spacer(Modifier.width(40.dp))
    }
    PlanItemTypeIcon(
        if (hasScripture) Icons.Filled.MenuBook else Icons.Filled.Campaign
    )
    Text(
        pco.title,
        modifier = Modifier.weight(1f),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    // Attachments are now loaded eagerly (see selectPlan),
    // so the button only appears once we actually know
    // there's something to show — never a dead-end click.
    val attachmentsLoaded = viewModel.attachmentsByItemId.containsKey(pco.id)
    val fileCount = viewModel.attachmentsByItemId[pco.id]?.size ?: 0
    if (!attachmentsLoaded) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
    } else if (fileCount > 0) {
        // Plain badge — the whole row is the click target
        // for expand/collapse (see isExpandable above).
        Box(
            modifier = Modifier
                .clip(AppShape(PILL_CORNER_PERCENT))
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    AppShape(PILL_CORNER_PERCENT)
                )
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                stringResource(
                    Res.string.planning_center_import_file_count,
                    fileCount
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The scripture references detected in an item, each with its own checkbox. */
@Composable
private fun ItemScriptures(pco: PlanningCenterClient.PlanItem, viewModel: PlanningCenterImportViewModel) {
val scriptures = viewModel.detectedScripturesByItemId[pco.id].orEmpty()
if (scriptures.isNotEmpty()) {
    val selectedScriptureIdx = viewModel.selectedScriptureIndices[pco.id].orEmpty()
    scriptures.forEachIndexed { index, verse ->
        LabeledCheckbox(
            checked = index in selectedScriptureIdx,
            onCheckedChange = { viewModel.toggleScriptureSelected(pco.id, index) },
            label = verse.displayReference,
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
            style = MaterialTheme.typography.bodySmall,
            spacing = 8.dp,
        )
    }
}
}

/** An expanded item's attachments, each with its own checkbox; unsupported files are struck through. */
@Composable
private fun ItemAttachments(pco: PlanningCenterClient.PlanItem, viewModel: PlanningCenterImportViewModel) {
val attachments = viewModel.attachmentsByItemId[pco.id].orEmpty()
val selectedIds = viewModel.selectedAttachmentIds[pco.id].orEmpty()
attachments.forEach { att ->
    val ext = att.filename.substringAfterLast('.', "").lowercase()
    val supported = isSupportedAttachment(att.filename)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RaisedCheckbox(
            checked = supported && att.id in selectedIds,
            enabled = supported,
            onCheckedChange = { viewModel.toggleAttachmentSelected(pco.id, att.id) }
        )
        val thumbUrl = att.thumbnailUrl
        if (thumbUrl != null && isImageExtension(ext)) {
            AttachmentThumbnail(thumbUrl, viewModel)
        } else {
            PlanItemTypeIcon(attachmentExtensionIcon(ext))
        }
        Text(
            att.filename,
            style = MaterialTheme.typography.bodySmall,
            color = if (supported) {
                Color.Unspecified
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            },
            textDecoration = if (supported) null else TextDecoration.LineThrough
        )
    }
}
}

/** A green "✓ Matched" tag shown on song rows already matched to the local library. */
@Composable
private fun MatchedTag() {
    Box(
        modifier = Modifier
            .clip(AppShape(PILL_CORNER_PERCENT))
            .background(MaterialTheme.semantic.successContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        // The string already carries a leading "✓".
        Text(
            stringResource(Res.string.planning_center_import_matched),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.semantic.onSuccessContainer
        )
    }
}

/** Row-leading type icon inside a small tinted rounded badge (matches the design's item tree). */
@Composable
private fun PlanItemTypeIcon(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(AppShape(7.dp))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

private const val PILL_CORNER_PERCENT = 50

/** Cancel, and import -- with a spinner while the import runs. */
@Composable
internal fun PcoImportFooter(
    isImporting: Boolean,
    canImport: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyButton(
            onClick = onDismiss,
            shape = AppShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(stringResource(Res.string.cancel))
        }
        RaisedButton(
            shape = AppShape(6.dp),
            enabled = !isImporting && canImport,
            onClick = onImport,
            modifier = Modifier.guideTarget(GuideTargets.PCO_IMPORT),
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(Res.string.planning_center_import_button))
        }
    }
}

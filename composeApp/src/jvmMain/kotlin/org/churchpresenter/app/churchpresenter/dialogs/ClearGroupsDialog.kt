package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.liveoutput.CLEARABLE_LAYERS
import org.churchpresenter.liveoutput.knownLayers
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.clear_group_clear
import org.churchpresenter.strings.generated.resources.clear_groups_add
import org.churchpresenter.strings.generated.resources.clear_groups_empty
import org.churchpresenter.strings.generated.resources.clear_groups_layers
import org.churchpresenter.strings.generated.resources.clear_groups_name
import org.churchpresenter.strings.generated.resources.clear_groups_save
import org.churchpresenter.strings.generated.resources.clear_groups_title
import org.churchpresenter.strings.generated.resources.clear_layer_announcements
import org.churchpresenter.strings.generated.resources.clear_layer_captions
import org.churchpresenter.strings.generated.resources.clear_layer_graphics
import org.churchpresenter.strings.generated.resources.clear_layer_media
import org.churchpresenter.strings.generated.resources.clear_layer_messages
import org.churchpresenter.strings.generated.resources.clear_layer_props
import org.churchpresenter.strings.generated.resources.clear_layer_slide
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.strings.generated.resources.delete_saved_string
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ProvideUiFontScale
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Keeps the clear groups (`docs/SHOW_CONTROL.md`, Clear groups): named sets of layers cleared together. */
@Composable
fun ClearGroupsDialog(
    isVisible: Boolean,
    groups: List<ClearGroup>,
    onGroupsChange: (List<ClearGroup>) -> Unit,
    onClearGroup: (ClearGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(LocalMainWindowState.current, CLEAR_DIALOG_WIDTH, CLEAR_DIALOG_HEIGHT),
        width = CLEAR_DIALOG_WIDTH,
        height = CLEAR_DIALOG_HEIGHT,
    )
    val title = stringResource(Res.string.clear_groups_title)
    DialogWindow(onCloseRequest = onDismiss, state = dialogState, title = title) {
        ProvideUiFontScale {
            ClearGroupsDialogContent(groups, onGroupsChange, onClearGroup, onDismiss)
        }
    }
}

/** The dialog's body, apart from its window so it can be drawn headless. */
@Composable
internal fun ClearGroupsDialogContent(
    groups: List<ClearGroup>,
    onGroupsChange: (List<ClearGroup>) -> Unit,
    onClearGroup: (ClearGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<ClearGroup?>(null) }
    val labels = CLEARABLE_LAYERS.associate { it.name to stringResource(it.clearLabel) }
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(stringResource(Res.string.clear_groups_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.padding(top = 12.dp))
            HorizontalDivider()
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (groups.isEmpty() && editing == null) {
                    Text(stringResource(Res.string.clear_groups_empty), style = MaterialTheme.typography.bodyMedium)
                }
                groups.forEach { group ->
                    ClearGroupRow(group, editing?.id == group.id, { onClearGroup(group) }) { editing = group }
                }
                editing?.let { draft ->
                    HorizontalDivider()
                    ClearGroupEditor(draft) { editing = it }
                }
            }
            HorizontalDivider()
            Spacer(Modifier.padding(top = 12.dp))
            ClearGroupButtons(
                editing = editing,
                isSaved = editing?.let { draft -> groups.any { it.id == draft.id } } == true,
                onAdd = { editing = ClearGroup(id = newClearGroupId(groups), name = "") },
                onSave = { draft ->
                    val named = draft.layers.mapNotNull(labels::get).joinToString(" + ")
                    val saved = draft.copy(name = draft.name.ifBlank { named })
                    val isNew = groups.none { it.id == saved.id }
                    onGroupsChange(if (isNew) groups + saved else groups.map { if (it.id == saved.id) saved else it })
                    editing = null
                },
                onDelete = { draft ->
                    onGroupsChange(groups.filterNot { it.id == draft.id })
                    editing = null
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/** One group: its Clear button, then its name and layers; clicking the name opens it for editing. */
@Composable
private fun ClearGroupRow(group: ClearGroup, editing: Boolean, onClear: () -> Unit, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RaisedButton(
            shape = AppShape(6.dp),
            onClick = onClear,
            modifier = Modifier.testTag(clearGroupButtonTag(group.id)),
        ) { Text(stringResource(Res.string.clear_group_clear)) }
        Spacer(Modifier.width(12.dp))
        val layers = group.knownLayers().map { stringResource(it.clearLabel) }
        FilterChip(
            selected = editing,
            onClick = onEdit,
            label = { Text(group.name + "  ·  " + layers.joinToString(" + ")) },
            modifier = Modifier.testTag(clearGroupEditTag(group.id)),
        )
    }
}

/** The name and the layers of the group being added or edited. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClearGroupEditor(draft: ClearGroup, onChange: (ClearGroup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = stringResource(Res.string.clear_groups_name),
            fillWidth = true,
            modifier = Modifier.fillMaxWidth().testTag(CLEAR_GROUP_NAME_TAG),
        )
        Text(stringResource(Res.string.clear_groups_layers), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CLEARABLE_LAYERS.forEach { layer ->
                val selected = layer.name in draft.layers
                FilterChip(
                    selected = selected,
                    onClick = {
                        val names = if (selected) draft.layers - layer.name else draft.layers + layer.name
                        onChange(draft.copy(layers = CLEARABLE_LAYERS.map { it.name }.filter { it in names }))
                    },
                    label = { Text(stringResource(layer.clearLabel)) },
                    modifier = Modifier.testTag(clearGroupLayerTag(layer)),
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ClearGroupButtons(
    editing: ClearGroup?,
    isSaved: Boolean,
    onAdd: () -> Unit,
    onSave: (ClearGroup) -> Unit,
    onDelete: (ClearGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton(shape = AppShape(6.dp), onClick = onAdd, modifier = Modifier.testTag(CLEAR_GROUP_ADD_TAG)) {
            Text(stringResource(Res.string.clear_groups_add))
        }
        if (editing != null && isSaved) {
            GhostButton(shape = AppShape(6.dp), onClick = { onDelete(editing) }) {
                Text(stringResource(Res.string.delete_saved_string))
            }
        }
        Spacer(Modifier.weight(1f))
        GhostButton(shape = AppShape(6.dp), onClick = onDismiss) { Text(stringResource(Res.string.close)) }
        if (editing != null) {
            RaisedButton(
                shape = AppShape(6.dp),
                onClick = { onSave(editing) },
                enabled = editing.layers.isNotEmpty(),
                modifier = Modifier.testTag(CLEAR_GROUP_SAVE_TAG),
            ) {
                Text(stringResource(Res.string.clear_groups_save))
            }
        }
    }
}

/** What a layer is called where an operator clears it. */
internal val Layer.clearLabel: StringResource
    get() = when (this) {
        Layer.MEDIA -> Res.string.clear_layer_media
        Layer.CAPTIONS -> Res.string.clear_layer_captions
        Layer.GRAPHICS -> Res.string.clear_layer_graphics
        Layer.PROPS -> Res.string.clear_layer_props
        Layer.ANNOUNCEMENTS -> Res.string.clear_layer_announcements
        Layer.MESSAGES -> Res.string.clear_layer_messages
        else -> Res.string.clear_layer_slide
    }

/** An id no group has. */
internal fun newClearGroupId(groups: List<ClearGroup>): String =
    generateSequence(groups.size + 1) { it + 1 }.map { "clear$it" }.first { id -> groups.none { it.id == id } }

internal const val CLEAR_GROUP_NAME_TAG = "clear_group_name"
internal const val CLEAR_GROUP_ADD_TAG = "clear_group_add"
internal const val CLEAR_GROUP_SAVE_TAG = "clear_group_save"

internal fun clearGroupButtonTag(id: String) = "clear_group_button_$id"

internal fun clearGroupEditTag(id: String) = "clear_group_edit_$id"

internal fun clearGroupLayerTag(layer: Layer) = "clear_group_layer_${layer.name}"

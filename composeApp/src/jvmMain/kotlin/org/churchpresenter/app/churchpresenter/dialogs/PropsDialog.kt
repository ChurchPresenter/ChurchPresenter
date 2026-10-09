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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.launch
import org.churchpresenter.settings.PropCorner
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.PropKind
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.strings.generated.resources.delete_saved_string
import org.churchpresenter.strings.generated.resources.props_add
import org.churchpresenter.strings.generated.resources.props_badge_text
import org.churchpresenter.strings.generated.resources.props_choose_picture
import org.churchpresenter.strings.generated.resources.props_corner_bottom_left
import org.churchpresenter.strings.generated.resources.props_corner_bottom_right
import org.churchpresenter.strings.generated.resources.props_corner_top_left
import org.churchpresenter.strings.generated.resources.props_corner_top_right
import org.churchpresenter.strings.generated.resources.props_countdown_to
import org.churchpresenter.strings.generated.resources.props_empty
import org.churchpresenter.strings.generated.resources.props_kind_badge
import org.churchpresenter.strings.generated.resources.props_kind_clock
import org.churchpresenter.strings.generated.resources.props_kind_countdown
import org.churchpresenter.strings.generated.resources.props_kind_image
import org.churchpresenter.strings.generated.resources.props_name
import org.churchpresenter.strings.generated.resources.props_picture
import org.churchpresenter.strings.generated.resources.props_save
import org.churchpresenter.strings.generated.resources.props_size
import org.churchpresenter.strings.generated.resources.props_title
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ProvideUiFontScale
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Switches props on and off air, and keeps them (`docs/SHOW_CONTROL.md`, Props): a logo bug, a
 * clock, a countdown or a badge, each in a corner, staying up while the content changes under it.
 */
@Composable
@Suppress("LongParameterList")
fun PropsDialog(
    isVisible: Boolean,
    props: List<PropDefinition>,
    onPropsChange: (List<PropDefinition>) -> Unit,
    onAir: Set<String>,
    onSwitch: (id: String, on: Boolean) -> Unit,
    onChoosePicture: suspend () -> String?,
    onDismiss: () -> Unit,
    /** The window it opens in -- see [DialogFrame]. */
    frame: DialogFrame = appDialogFrame,
) {
    if (!isVisible) return
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(LocalMainWindowState.current, PROPS_DIALOG_WIDTH, PROPS_DIALOG_HEIGHT),
        width = PROPS_DIALOG_WIDTH,
        height = PROPS_DIALOG_HEIGHT,
    )
    frame(
        DialogFrameSpec(
            onClose = onDismiss,
            state = dialogState,
            title = stringResource(Res.string.props_title),
        ),
    ) {
        ProvideUiFontScale {
            PropsDialogContent(props, onPropsChange, onAir, onSwitch, onChoosePicture, onDismiss)
        }
    }
}

/** The dialog's body, apart from its window so it can be drawn headless. */
@Composable
@Suppress("LongParameterList")
internal fun PropsDialogContent(
    props: List<PropDefinition>,
    onPropsChange: (List<PropDefinition>) -> Unit,
    onAir: Set<String>,
    onSwitch: (id: String, on: Boolean) -> Unit,
    onChoosePicture: suspend () -> String?,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<PropDefinition?>(null) }
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(stringResource(Res.string.props_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.padding(top = 12.dp))
            HorizontalDivider()
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (props.isEmpty() && editing == null) {
                    Text(stringResource(Res.string.props_empty), style = MaterialTheme.typography.bodyMedium)
                }
                props.forEach { prop ->
                    PropRow(prop, prop.id in onAir, editing?.id == prop.id, { onSwitch(prop.id, it) }) {
                        editing = prop
                    }
                }
                editing?.let { draft ->
                    HorizontalDivider()
                    PropEditor(draft, onChange = { editing = it }, onChoosePicture = onChoosePicture)
                }
            }
            HorizontalDivider()
            Spacer(Modifier.padding(top = 12.dp))
            PropButtons(
                editing = editing,
                isSaved = editing?.let { draft -> props.any { it.id == draft.id } } == true,
                onAdd = { editing = PropDefinition(id = newPropId(props), name = "") },
                onSave = { draft ->
                    val saved = draft.copy(name = draft.name.ifBlank { draft.kind.name.lowercase() })
                    onPropsChange(props.filterNot { it.id == saved.id } + saved)
                    editing = null
                },
                onDelete = { draft ->
                    if (draft.id in onAir) onSwitch(draft.id, false)
                    onPropsChange(props.filterNot { it.id == draft.id })
                    editing = null
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/** One prop: its on-air switch, its name and kind; clicking the name opens it for editing. */
@Composable
private fun PropRow(
    prop: PropDefinition,
    on: Boolean,
    editing: Boolean,
    onSwitch: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Switch(checked = on, onCheckedChange = onSwitch, modifier = Modifier.testTag(propSwitchTag(prop.id)))
        Spacer(Modifier.width(12.dp))
        FilterChip(
            selected = editing,
            onClick = onEdit,
            label = { Text(prop.name + "  ·  " + stringResource(prop.kind.label)) },
            modifier = Modifier.testTag(propEditTag(prop.id)),
        )
    }
}

/** The fields of the prop being added or edited. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropEditor(
    draft: PropDefinition,
    onChange: (PropDefinition) -> Unit,
    onChoosePicture: suspend () -> String?,
) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = stringResource(Res.string.props_name),
            fillWidth = true,
            modifier = Modifier.fillMaxWidth().testTag(PROP_NAME_TAG),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PropKind.entries.forEach { kind ->
                FilterChip(
                    selected = draft.kind == kind,
                    onClick = { onChange(draft.copy(kind = kind)) },
                    label = { Text(stringResource(kind.label)) },
                    modifier = Modifier.testTag(propKindTag(kind)),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PropCorner.entries.forEach { corner ->
                FilterChip(
                    selected = draft.corner == corner,
                    onClick = { onChange(draft.copy(corner = corner)) },
                    label = { Text(stringResource(corner.label)) },
                    modifier = Modifier.testTag(propCornerTag(corner)),
                )
            }
        }
        SettingsTextField(
            value = draft.sizePercent.toString(),
            onValueChange = { typed ->
                typed.filter(Char::isDigit).take(2).toIntOrNull()?.let { onChange(draft.copy(sizePercent = it)) }
            },
            label = stringResource(Res.string.props_size),
            modifier = Modifier.width(SIZE_FIELD_WIDTH).testTag(PROP_SIZE_TAG),
        )
        when (draft.kind) {
            PropKind.IMAGE -> Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsTextField(
                    value = draft.imagePath,
                    onValueChange = { onChange(draft.copy(imagePath = it)) },
                    label = stringResource(Res.string.props_picture),
                    modifier = Modifier.weight(1f).testTag(PROP_PICTURE_TAG),
                )
                Spacer(Modifier.width(8.dp))
                GhostButton(shape = AppShape(6.dp), onClick = {
                    scope.launch { onChoosePicture()?.let { onChange(draft.copy(imagePath = it)) } }
                }) { Text(stringResource(Res.string.props_choose_picture)) }
            }
            PropKind.BADGE -> SettingsTextField(
                value = draft.text,
                onValueChange = { onChange(draft.copy(text = it)) },
                label = stringResource(Res.string.props_badge_text),
                modifier = Modifier.width(TEXT_FIELD_WIDTH).testTag(PROP_TEXT_TAG),
            )
            PropKind.COUNTDOWN -> SettingsTextField(
                value = draft.countdownTo,
                onValueChange = { onChange(draft.copy(countdownTo = it.take(TIME_CHARS))) },
                label = stringResource(Res.string.props_countdown_to),
                modifier = Modifier.width(TEXT_FIELD_WIDTH).testTag(PROP_COUNTDOWN_TAG),
            )
            PropKind.CLOCK -> Unit
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun PropButtons(
    editing: PropDefinition?,
    isSaved: Boolean,
    onAdd: () -> Unit,
    onSave: (PropDefinition) -> Unit,
    onDelete: (PropDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton(shape = AppShape(6.dp), onClick = onAdd, modifier = Modifier.testTag(PROP_ADD_TAG)) {
            Text(stringResource(Res.string.props_add))
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
                modifier = Modifier.testTag(PROP_SAVE_TAG),
            ) {
                Text(stringResource(Res.string.props_save))
            }
        }
    }
}

private val PropKind.label: StringResource
    get() = when (this) {
        PropKind.IMAGE -> Res.string.props_kind_image
        PropKind.CLOCK -> Res.string.props_kind_clock
        PropKind.COUNTDOWN -> Res.string.props_kind_countdown
        PropKind.BADGE -> Res.string.props_kind_badge
    }

private val PropCorner.label: StringResource
    get() = when (this) {
        PropCorner.TOP_LEFT -> Res.string.props_corner_top_left
        PropCorner.TOP_RIGHT -> Res.string.props_corner_top_right
        PropCorner.BOTTOM_LEFT -> Res.string.props_corner_bottom_left
        PropCorner.BOTTOM_RIGHT -> Res.string.props_corner_bottom_right
    }

/** An id no prop has. */
internal fun newPropId(props: List<PropDefinition>): String =
    generateSequence(props.size + 1) { it + 1 }.map { "prop$it" }.first { id -> props.none { it.id == id } }

internal const val PROP_NAME_TAG = "prop_name"
internal const val PROP_SIZE_TAG = "prop_size"
internal const val PROP_PICTURE_TAG = "prop_picture"
internal const val PROP_TEXT_TAG = "prop_text"
internal const val PROP_COUNTDOWN_TAG = "prop_countdown"
internal const val PROP_ADD_TAG = "prop_add"
internal const val PROP_SAVE_TAG = "prop_save"

internal fun propSwitchTag(id: String) = "prop_switch_$id"

internal fun propEditTag(id: String) = "prop_edit_$id"

internal fun propKindTag(kind: PropKind) = "prop_kind_${kind.name}"

internal fun propCornerTag(corner: PropCorner) = "prop_corner_${corner.name}"

private val SIZE_FIELD_WIDTH = 200.dp
private val TEXT_FIELD_WIDTH = 260.dp
private const val TIME_CHARS = 5

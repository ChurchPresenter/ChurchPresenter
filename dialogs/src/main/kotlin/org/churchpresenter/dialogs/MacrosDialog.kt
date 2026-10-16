package org.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.macros_purpose
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.schedule.ActionListEditor
import org.churchpresenter.settings.Macro
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.strings.generated.resources.control_open
import org.churchpresenter.strings.generated.resources.delete_saved_string
import org.churchpresenter.strings.generated.resources.macros_add
import org.churchpresenter.strings.generated.resources.macros_empty
import org.churchpresenter.strings.generated.resources.macros_hint
import org.churchpresenter.strings.generated.resources.macros_name
import org.churchpresenter.strings.generated.resources.macros_run
import org.churchpresenter.strings.generated.resources.macros_save
import org.churchpresenter.strings.generated.resources.macros_title
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ProvideUiFontScale
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource

/** Keeps the macros (`docs/SHOW_CONTROL.md`, Macros): named action lists, run from here, a key or the API. */
@Composable
fun MacrosDialog(
    isVisible: Boolean,
    macros: List<Macro>,
    rows: List<ScheduleItem>,
    onMacrosChange: (List<Macro>) -> Unit,
    onRun: (Macro) -> Unit,
    onDismiss: () -> Unit,
    onOpenControl: (() -> Unit)? = null,
    /** The window it opens in -- see [DialogFrame]. */
    frame: DialogFrame = appDialogFrame,
) {
    if (!isVisible) return
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(LocalMainWindowState.current, MACROS_DIALOG_WIDTH, MACROS_DIALOG_HEIGHT),
        width = MACROS_DIALOG_WIDTH,
        height = MACROS_DIALOG_HEIGHT,
    )
    frame(
        DialogFrameSpec(
            onClose = onDismiss,
            state = dialogState,
            title = stringResource(Res.string.macros_title),
        ),
    ) {
        ProvideUiFontScale {
            MacrosDialogContent(macros, rows, onMacrosChange, onRun, onDismiss, onOpenControl)
        }
    }
}

/** The dialog's body, apart from its window so it can be drawn headless. */
@Composable
internal fun MacrosDialogContent(
    macros: List<Macro>,
    rows: List<ScheduleItem>,
    onMacrosChange: (List<Macro>) -> Unit,
    onRun: (Macro) -> Unit,
    onDismiss: () -> Unit,
    onOpenControl: (() -> Unit)? = null,
) {
    var editing by remember { mutableStateOf<Macro?>(null) }
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            LiveToolDialogHeader(
                tool = LiveTool.MACROS,
                painter = rememberVectorPainter(Icons.Outlined.PlayCircle),
                title = stringResource(Res.string.macros_title),
                purpose = stringResource(Res.string.macros_purpose),
                onClose = onDismiss,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LIVE_TOOL_BODY_PADDING, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (macros.isEmpty() && editing == null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().liveToolPanel().padding(14.dp),
                    ) {
                        Text(stringResource(Res.string.macros_empty), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(Res.string.macros_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                macros.forEachIndexed { index, macro ->
                    MacroRow(index + 1, macro, editing?.id == macro.id, { onRun(macro) }) { editing = macro }
                }
                editing?.let { draft ->
                    Spacer(Modifier.height(8.dp))
                    MacroEditor(draft, rows) { editing = it }
                }
            }
            LiveToolDialogFooter {
                MacroButtons(
                    editing = editing,
                    isSaved = editing?.let { draft -> macros.any { it.id == draft.id } } == true,
                    onAdd = { editing = Macro(id = newMacroId(macros), name = "") },
                    onSave = { draft ->
                        val saved = draft.copy(name = draft.name.trim().ifBlank { draft.id })
                        val isNew = macros.none { it.id == saved.id }
                        onMacrosChange(
                            if (isNew) macros + saved else macros.map { if (it.id == saved.id) saved else it },
                        )
                        editing = null
                    },
                    onDelete = { draft ->
                        onMacrosChange(macros.filterNot { it.id == draft.id })
                        editing = null
                    },
                    onOpenControl = onOpenControl,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/**
 * One macro, as a panel: its number -- the Run macro key that runs it -- its name, which opens it
 * for editing, and its Run.
 */
@Composable
private fun MacroRow(number: Int, macro: Macro, editing: Boolean, onRun: () -> Unit, onEdit: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val tint = LiveTool.MACROS.tint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(MACRO_ROW_HEIGHT)
            .liveToolPanel()
            .then(if (editing) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .padding(start = 10.dp, end = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Text(
                number.toString(),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            macro.name,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(vertical = 8.dp)
                .testTag(macroEditTag(macro.id)),
        )
        RaisedButton(
            shape = AppShape(8.dp),
            onClick = onRun,
            enabled = macro.actions.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = tint.chip, contentColor = tint.icon),
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier.height(28.dp).testTag(macroRunTag(macro.id)),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.macros_run), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The name and the actions of the macro being added or edited. */
@Composable
private fun MacroEditor(draft: Macro, rows: List<ScheduleItem>, onChange: (Macro) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = stringResource(Res.string.macros_name),
            fillWidth = true,
            modifier = Modifier.fillMaxWidth().testTag(MACRO_NAME_TAG),
        )
        ActionListEditor(draft.actions, rows, onChange = { onChange(draft.copy(actions = it)) })
    }
}

@Composable
@Suppress("LongParameterList")
private fun RowScope.MacroButtons(
    editing: Macro?,
    isSaved: Boolean,
    onAdd: () -> Unit,
    onSave: (Macro) -> Unit,
    onDelete: (Macro) -> Unit,
    onOpenControl: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    GhostButton(shape = AppShape(9.dp), onClick = onAdd, modifier = Modifier.testTag(MACRO_ADD_TAG)) {
        Text(stringResource(Res.string.macros_add), color = MaterialTheme.colorScheme.primary)
    }
    if (onOpenControl != null) {
        GhostButton(
            shape = AppShape(9.dp),
            onClick = onOpenControl,
            modifier = Modifier.testTag(MACRO_CONTROL_TAG),
        ) { Text(stringResource(Res.string.control_open)) }
    }
    if (editing != null && isSaved) {
        GhostButton(shape = AppShape(9.dp), onClick = { onDelete(editing) }) {
            Text(stringResource(Res.string.delete_saved_string))
        }
    }
    Spacer(Modifier.weight(1f))
    KeyButton(shape = AppShape(10.dp), onClick = onDismiss) { Text(stringResource(Res.string.close)) }
    if (editing != null) {
        RaisedButton(
            shape = AppShape(10.dp),
            onClick = { onSave(editing) },
            modifier = Modifier.testTag(MACRO_SAVE_TAG),
        ) {
            Text(stringResource(Res.string.macros_save), fontWeight = FontWeight.Bold)
        }
    }
}

/** An id no macro has. */
internal fun newMacroId(macros: List<Macro>): String =
    generateSequence(macros.size + 1) { it + 1 }.map { "macro$it" }.first { id -> macros.none { it.id == id } }

internal const val MACRO_NAME_TAG = "macro_name"
internal const val MACRO_ADD_TAG = "macro_add"
internal const val MACRO_SAVE_TAG = "macro_save"
internal const val MACRO_CONTROL_TAG = "macro_control"

internal fun macroRunTag(id: String) = "macro_run_$id"

internal fun macroEditTag(id: String) = "macro_edit_$id"

private val MACROS_DIALOG_WIDTH = 640.dp
private val MACROS_DIALOG_HEIGHT = 600.dp
private val MACRO_ROW_HEIGHT = 44.dp

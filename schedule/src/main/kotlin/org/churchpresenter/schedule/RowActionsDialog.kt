package org.churchpresenter.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.action_unknown
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.strings.generated.resources.row_actions_add
import org.churchpresenter.strings.generated.resources.row_actions_empty
import org.churchpresenter.strings.generated.resources.row_actions_for
import org.churchpresenter.strings.generated.resources.row_actions_hint
import org.churchpresenter.strings.generated.resources.row_actions_save
import org.churchpresenter.strings.generated.resources.row_actions_title
import org.churchpresenter.strings.generated.resources.tooltip_move_down
import org.churchpresenter.strings.generated.resources.tooltip_move_up
import org.churchpresenter.strings.generated.resources.tooltip_remove
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ProvideUiFontScale
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/**
 * Edits what a schedule row does when it goes live (`docs/SHOW_CONTROL.md`, Cue actions): a list of
 * show-control actions, run in order. Nothing is kept until Save.
 */
@Composable
internal fun RowActionsDialog(
    row: ScheduleItem,
    actions: List<Action>,
    rows: List<ScheduleItem>,
    onSave: (List<Action>) -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(LocalMainWindowState.current, ROW_ACTIONS_WIDTH, ROW_ACTIONS_HEIGHT),
        width = ROW_ACTIONS_WIDTH,
        height = ROW_ACTIONS_HEIGHT,
    )
    val title = stringResource(Res.string.row_actions_title)
    DialogWindow(onCloseRequest = onDismiss, state = dialogState, title = title) {
        ProvideUiFontScale {
            RowActionsDialogContent(row, actions, rows, LocalActionChoices.current, onSave, onDismiss)
        }
    }
}

/** The dialog's body, apart from its window so it can be drawn headless. */
@Composable
internal fun RowActionsDialogContent(
    row: ScheduleItem,
    actions: List<Action>,
    rows: List<ScheduleItem>,
    choices: ActionChoices,
    onSave: (List<Action>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(row.id, actions) { mutableStateOf(actions) }
    LaunchedEffect(Unit) { choices.onOpen() }
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                stringResource(Res.string.row_actions_for, row.displayText),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                stringResource(Res.string.row_actions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = 12.dp))
            HorizontalDivider()
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (draft.isEmpty()) {
                    Text(stringResource(Res.string.row_actions_empty), style = MaterialTheme.typography.bodyMedium)
                }
                draft.forEachIndexed { index, action ->
                    RowActionCard(
                        index = index,
                        action = action,
                        last = index == draft.lastIndex,
                        choices = choices,
                        rows = rows,
                        onChange = { changed -> draft = draft.toMutableList().also { it[index] = changed } },
                        onMove = { by -> draft = draft.moved(index, index + by) },
                        onRemove = { draft = draft.filterIndexed { i, _ -> i != index } },
                    )
                }
            }
            HorizontalDivider()
            Spacer(Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AddActionButton { kind -> draft = draft + newAction(kind, choices, rows) }
                Spacer(Modifier.weight(1f))
                GhostButton(shape = AppShape(6.dp), onClick = onDismiss) { Text(stringResource(Res.string.close)) }
                RaisedButton(
                    shape = AppShape(6.dp),
                    onClick = {
                        onSave(draft)
                        onDismiss()
                    },
                    modifier = Modifier.testTag(ROW_ACTIONS_SAVE_TAG),
                ) { Text(stringResource(Res.string.row_actions_save)) }
            }
        }
    }
}

/** One action in the list: its number and kind, its fields, and the buttons that move or remove it. */
@Composable
private fun RowActionCard(
    index: Int,
    action: Action,
    last: Boolean,
    choices: ActionChoices,
    rows: List<ScheduleItem>,
    onChange: (Action) -> Unit,
    onMove: (by: Int) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(rowActionCardTag(index))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), AppShape(8.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}.  " + stringResource(action.kind?.label ?: Res.string.action_unknown),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            CardButton(IconRes.drawable.ic_arrow_up, Res.string.tooltip_move_up, enabled = index > 0) { onMove(-1) }
            CardButton(IconRes.drawable.ic_arrow_down, Res.string.tooltip_move_down, enabled = !last) { onMove(1) }
            CardButton(IconRes.drawable.ic_close, Res.string.tooltip_remove, error = true, onClick = onRemove)
        }
        RowActionFields(action, choices, rows, onChange)
    }
}

@Composable
private fun CardButton(
    icon: DrawableResource,
    tooltip: StringResource,
    enabled: Boolean = true,
    error: Boolean = false,
    onClick: () -> Unit,
) {
    TooltipIconButton(
        painter = painterResource(icon),
        text = stringResource(tooltip),
        onClick = onClick,
        enabled = enabled,
        buttonSize = CARD_BUTTON_SIZE,
        iconSize = CARD_ICON_SIZE,
        iconTint = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
            error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/** Add action, and the menu of every kind it can add. */
@Composable
private fun AddActionButton(onAdd: (ActionKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GhostButton(
            shape = AppShape(6.dp),
            onClick = { open = true },
            modifier = Modifier.testTag(ROW_ACTIONS_ADD_TAG),
        ) {
            Text(stringResource(Res.string.row_actions_add))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ActionKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(stringResource(kind.label)) },
                    onClick = {
                        open = false
                        onAdd(kind)
                    },
                    modifier = Modifier.testTag(addActionKindTag(kind)),
                )
            }
        }
    }
}

/** [this] with the entry at [from] moved to [to], or unchanged when [to] is off either end. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (to !in indices) return this
    return toMutableList().also { it.add(to, it.removeAt(from)) }
}

internal const val ROW_ACTIONS_SAVE_TAG = "row_actions_save"
internal const val ROW_ACTIONS_ADD_TAG = "row_actions_add"

internal fun rowActionCardTag(index: Int) = "row_action_card_$index"

internal fun addActionKindTag(kind: ActionKind) = "row_action_add_${kind.name}"

private val ROW_ACTIONS_WIDTH = 620.dp
private val ROW_ACTIONS_HEIGHT = 560.dp
private val CARD_BUTTON_SIZE = 28.dp
private val CARD_ICON_SIZE = 14.dp
private const val DISABLED_ALPHA = 0.3f

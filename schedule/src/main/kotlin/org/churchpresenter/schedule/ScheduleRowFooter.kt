package org.churchpresenter.schedule

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.strings.generated.resources.tooltip_row_actions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_edit
import org.churchpresenter.icons.generated.resources.ic_check
import org.churchpresenter.strings.generated.resources.schedule_note_placeholder
import org.churchpresenter.strings.generated.resources.tooltip_note
import org.churchpresenter.strings.generated.resources.tooltip_note_clear
import org.churchpresenter.strings.generated.resources.tooltip_note_done
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette

/**
 * What sits under a row's title: the chip listing its cue actions, then its note -- closed as a
 * chip, or open in its editor.
 */
@Composable
internal fun ColumnScope.ScheduleRowFooter(
    note: RowNote,
    onNoteText: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onNoteExpanded: (Boolean) -> Unit,
    actions: List<Action>,
    rows: List<ScheduleItem>,
    onEditActions: () -> Unit,
) {
    if (actions.isNotEmpty()) {
        ScheduleRowActionsChip(actions, rows, onEdit = onEditActions)
    }
    if (note.text.isNotEmpty() && !note.expanded) {
        ScheduleRowNoteChip(note = note.text, onEdit = { onNoteExpanded(true) })
    }
    AnimatedVisibility(visible = note.expanded) {
        ScheduleRowNoteEditor(
            noteText = note.typing,
            onNoteTextChange = onNoteText,
            onCommit = onNoteChanged,
            onClose = { onNoteExpanded(false) },
        )
    }
}

/** What the row does when it goes live, in one line under its title; the pencil opens the editor. */
@Composable
private fun ScheduleRowActionsChip(actions: List<Action>, rows: List<ScheduleItem>, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SCHEDULE_ROW_ACTIONS_CHIP_TAG)
            .padding(start = 38.dp, end = 8.dp, bottom = 7.dp)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f), AppShape(6.dp))
            .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            rememberVectorPainter(Icons.Outlined.Bolt),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = actions.map { actionSummary(it, rows) }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp)
        )
        ScheduleRowActionButton(
            painter = painterResource(IconRes.drawable.ic_edit),
            text = stringResource(Res.string.tooltip_row_actions),
            onClick = onEdit,
            iconSize = 11.dp,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

internal const val SCHEDULE_ROW_ACTIONS_BUTTON_TAG = "schedule_row_actions_button"
internal const val SCHEDULE_ROW_ACTIONS_CHIP_TAG = "schedule_row_actions_chip"

/** A note that is written but not being edited: the text, and a pencil to open it. */
@Composable
private fun ScheduleRowNoteChip(note: String, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 38.dp, end = 8.dp, bottom = 7.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f), AppShape(6.dp))
            .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f).padding(top = 2.dp, bottom = 2.dp)
        )
        ScheduleRowActionButton(
            painter = painterResource(IconRes.drawable.ic_edit),
            text = stringResource(Res.string.tooltip_note),
            onClick = onEdit,
            iconSize = 11.dp,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
        )
    }
}

/**
 * The note being edited.
 *
 * [onCommit] is the row's `onNoteChanged`, and is called as the text changes rather than on close:
 * a note half-typed when the app goes down is still a note somebody wrote.
 */
@Composable
private fun ScheduleRowNoteEditor(
    noteText: String,
    onNoteTextChange: (String) -> Unit,
    onCommit: (String) -> Unit,
    onClose: () -> Unit,
) {
    val noteInteractionSource = remember { MutableInteractionSource() }
    val noteFieldFocused by noteInteractionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 38.dp, end = 8.dp, bottom = 7.dp)
            .sunken(
                AppShape(7.dp),
                elevationPalette(),
                rim = if (noteFieldFocused) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
            .hoverTint(AppShape(7.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = noteText,
            onValueChange = onNoteTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 3,
            interactionSource = noteInteractionSource,
            decorationBox = { innerTextField ->
                Box {
                    if (noteText.isEmpty()) {
                        Text(
                            stringResource(Res.string.schedule_note_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            }
        )
        TooltipIconButton(
            painter = painterResource(IconRes.drawable.ic_check),
            text = stringResource(Res.string.tooltip_note_done),
            onClick = {
                onCommit(noteText)
                onClose()
            },
            buttonSize = 32.dp,
            iconSize = 15.dp,
            iconTint = MaterialTheme.colorScheme.primary
        )
        TooltipIconButton(
            painter = painterResource(IconRes.drawable.ic_close),
            text = stringResource(Res.string.tooltip_note_clear),
            onClick = {
                onNoteTextChange("")
                onCommit("")
            },
            modifier = Modifier.padding(end = 4.dp),
            buttonSize = 32.dp,
            iconSize = 15.dp,
            iconTint = MaterialTheme.colorScheme.error
        )
    }
}

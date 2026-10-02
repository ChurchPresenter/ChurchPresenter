package org.churchpresenter.qa

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.sharedui.composables.BibleListRowShape
import org.churchpresenter.sharedui.composables.bibleRowColors
import org.churchpresenter.sharedui.composables.rememberRowHover
import org.churchpresenter.sharedui.composables.rowPad
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.qa_denied
import org.churchpresenter.strings.generated.resources.qa_done
import org.churchpresenter.strings.generated.resources.qa_edit_question_hint
import org.churchpresenter.strings.generated.resources.qa_submitter_device
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.tooltip_edit
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.semantic
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DISPLAYED_ROW_ALPHA = 0.35f
private const val HINT_ALPHA = 0.5f
private const val QUESTION_MAX_LINES = 3
private const val EDIT_MAX_LINES = 5

/** What a question's buttons do; each acts on the one question the row shows. */
internal class QuestionActions(
    val onApprove: () -> Unit,
    val onDeny: () -> Unit,
    val onMarkDone: () -> Unit,
    val onEdit: (String) -> Unit,
    val onDisplay: () -> Unit,
    val onDelete: () -> Unit,
)

/** A row's own state: the text being edited, and which of its prompts is open. */
@Stable
internal class QuestionRowState(text: String) {
    var editing by mutableStateOf(false)
    var editText by mutableStateOf(text)
    var confirmGoLive by mutableStateOf(false)
    var confirmDelete by mutableStateOf(false)
}

@Composable
internal fun QuestionRow(
    question: Question,
    isDisplayed: Boolean,
    isHistory: Boolean = false,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onMarkDone: () -> Unit = {},
    onEdit: (String) -> Unit,
    onDisplay: () -> Unit,
    onDelete: () -> Unit,
) {
    val (hover, hovered) = rememberRowHover()
    val bgColor = if (isDisplayed) MaterialTheme.semantic.successContainer.copy(alpha = DISPLAYED_ROW_ALPHA)
    else bibleRowColors(selected = false, hovered = hovered).background
    val statusColor = when (question.status) {
        QuestionStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        QuestionStatus.APPROVED -> MaterialTheme.colorScheme.inverseSurface
        QuestionStatus.DENIED -> MaterialTheme.colorScheme.error
        QuestionStatus.DONE -> MaterialTheme.colorScheme.secondary
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val state = remember(question.text) { QuestionRowState(question.text) }
    val actions = QuestionActions(onApprove, onDeny, onMarkDone, onEdit, onDisplay, onDelete)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BibleListRowShape)
            .background(bgColor)
            .hoverable(hover)
            .padding(horizontal = rowPad(12.dp), vertical = rowPad(8.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(AppShape(4.dp)).background(statusColor))
            Spacer(Modifier.width(8.dp))
            Text(
                text = timeFormat.format(Date(question.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(50.dp)
            )

            if (!state.editing) {
                VoteBadges(question)
                StatusLabel(question.status, statusColor)
                SubmitterText(question, Modifier.weight(1f))
            } else {
                Spacer(Modifier.width(12.dp))
            }

            if (!isHistory) {
                EditButtons(question, state, onEdit)
                if (!state.editing) {
                    StatusActions(question, isDisplayed, state, actions)
                    DeleteControl(state, onDelete)
                }
            }
        }

        if (state.editing) {
            EditField(state)
        }
    }
}

/** Upvote and downvote counts, when a question has any. */
@Composable
private fun VoteBadges(question: Question) {
    if (question.upvotes <= 0 && question.downvotes <= 0) return
    Row(modifier = Modifier.padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (question.upvotes > 0) {
            Surface(shape = AppShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = "▲ ${question.upvotes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        if (question.downvotes > 0) {
            Surface(shape = AppShape(4.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    text = "▼ ${question.downvotes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/** "Done" or "Denied" beside a question that is either; nothing otherwise. */
@Composable
private fun StatusLabel(status: QuestionStatus, color: Color) {
    val label = when (status) {
        QuestionStatus.DONE -> stringResource(Res.string.qa_done)
        QuestionStatus.DENIED -> stringResource(Res.string.qa_denied)
        else -> return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(end = 8.dp)
    )
}

/** Who sent the question, if they said, and the question itself; the device is in the tooltip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubmitterText(question: Question, modifier: Modifier) {
    TooltipArea(
        tooltip = { SubmitterTooltip(question) },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomStart,
            offset = DpOffset(0.dp, 4.dp)
        ),
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (question.submitterName.isNotBlank()) {
                Text(
                    text = question.submitterName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = question.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                maxLines = QUESTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SubmitterTooltip(question: Question) {
    if (question.submitterName.isBlank() && question.submitterDeviceId.isBlank()) {
        Box {}
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (question.submitterName.isNotBlank()) {
                Text(
                    text = question.submitterName,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (question.submitterDeviceId.isNotBlank()) {
                Text(
                    text = stringResource(Res.string.qa_submitter_device, question.submitterDeviceId),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** Edit, or Save and Cancel while the question is being edited. */
@Composable
private fun EditButtons(question: Question, state: QuestionRowState, onEdit: (String) -> Unit) = with(state) {
    val strSave = stringResource(Res.string.save)
    val strEdit = stringResource(Res.string.tooltip_edit)
    val strCancel = stringResource(Res.string.cancel)
    QAIconButton(
        tooltip = if (editing) strSave else strEdit,
        onClick = {
            if (editing) {
                if (editText.isNotBlank() && editText.trim() != question.text) onEdit(editText)
                editing = false
            } else {
                editText = question.text
                editing = true
            }
        }
    ) {
        Icon(
            imageVector = if (editing) Icons.Default.Check else Icons.Default.Edit,
            contentDescription = if (editing) strSave else strEdit,
            tint = if (editing) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.tertiary
        )
    }
    if (editing) {
        QAIconButton(tooltip = strCancel, onClick = { editText = question.text; editing = false }) {
            Icon(Icons.Default.Close, strCancel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The question's text, being edited in place. */
@Composable
private fun EditField(state: QuestionRowState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 50.dp, top = 4.dp)
            .sunken(AppShape(8.dp), elevationPalette())
            .hoverTint(AppShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        BasicTextField(
            value = state.editText,
            onValueChange = { state.editText = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = EDIT_MAX_LINES,
            decorationBox = { innerTextField ->
                if (state.editText.isEmpty()) {
                    Text(
                        stringResource(Res.string.qa_edit_question_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HINT_ALPHA)
                    )
                }
                innerTextField()
            }
        )
    }
}

package org.churchpresenter.qa

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.sharedui.composables.GoLiveButton
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.delete_saved_string
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.strings.generated.resources.qa_approve
import org.churchpresenter.strings.generated.resources.qa_back_to_incoming
import org.churchpresenter.strings.generated.resources.qa_confirm_delete_prompt
import org.churchpresenter.strings.generated.resources.qa_confirm_go_live
import org.churchpresenter.strings.generated.resources.qa_confirm_go_live_prompt
import org.churchpresenter.strings.generated.resources.qa_deny
import org.churchpresenter.strings.generated.resources.qa_done_clear
import org.churchpresenter.strings.generated.resources.qa_mark_done
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedIconButton
import org.jetbrains.compose.resources.stringResource

private const val QA_DISABLED_ALPHA = 0.38f

/** Approve, deny, done and Go Live, as fit a question that is pending, approved, done or denied. */
@Composable
internal fun StatusActions(
    question: Question,
    isDisplayed: Boolean,
    state: QuestionRowState,
    actions: QuestionActions,
) = with(actions) {
    val strApprove = stringResource(Res.string.qa_approve)
    val strDeny = stringResource(Res.string.qa_deny)
    when (question.status) {
        QuestionStatus.PENDING -> {
            ApproveButton(strApprove, onApprove)
            DenyButton(strDeny, onDeny)
        }
        QuestionStatus.APPROVED -> {
            if (!isDisplayed) GoLiveButton(onClick = onDisplay, tooltipText = stringResource(Res.string.go_live))
            val strDone = stringResource(if (isDisplayed) Res.string.qa_done_clear else Res.string.qa_mark_done)
            QAIconButton(tooltip = strDone, onClick = onMarkDone) {
                Icon(Icons.Default.Done, contentDescription = strDone, tint = MaterialTheme.colorScheme.secondary)
            }
            DenyButton(strDeny, onDeny)
        }
        QuestionStatus.DONE -> {
            val strBack = stringResource(Res.string.qa_back_to_incoming)
            QAIconButton(tooltip = strBack, onClick = onApprove) {
                Icon(Icons.Default.Refresh, strBack, tint = MaterialTheme.colorScheme.tertiary)
            }
            ConfirmGoLive(state, actions, MaterialTheme.colorScheme.secondary, dimmed = false)
        }
        QuestionStatus.DENIED -> {
            ApproveButton(strApprove, onApprove)
            ConfirmGoLive(state, actions, MaterialTheme.colorScheme.tertiary, dimmed = true)
        }
    }
}

@Composable
private fun ApproveButton(tooltip: String, onApprove: () -> Unit) {
    QAIconButton(tooltip = tooltip, onClick = onApprove) {
        Icon(Icons.Default.Check, tooltip, tint = MaterialTheme.colorScheme.inverseSurface)
    }
}

@Composable
private fun DenyButton(tooltip: String, onDeny: () -> Unit) {
    QAIconButton(tooltip = tooltip, onClick = onDeny) {
        Icon(Icons.Default.Close, tooltip, tint = MaterialTheme.colorScheme.error)
    }
}

/** Go Live for a question already retired, which approves it first, behind a confirmation. */
@Composable
private fun ConfirmGoLive(
    state: QuestionRowState,
    actions: QuestionActions,
    promptColor: Color,
    dimmed: Boolean,
) = with(state) {
    val strGoLive = stringResource(Res.string.go_live)
    if (!confirmGoLive) {
        GoLiveButton(onClick = { confirmGoLive = true }, tooltipText = strGoLive, dimmed = dimmed)
        return@with
    }
    val strCancel = stringResource(Res.string.cancel)
    Text(
        stringResource(Res.string.qa_confirm_go_live_prompt),
        style = MaterialTheme.typography.labelSmall,
        color = promptColor,
        modifier = Modifier.padding(end = 4.dp)
    )
    GoLiveButton(
        onClick = {
            confirmGoLive = false
            actions.onApprove()
            actions.onDisplay()
        },
        tooltipText = stringResource(Res.string.qa_confirm_go_live)
    )
    QAIconButton(tooltip = strCancel, onClick = { confirmGoLive = false }) {
        Icon(Icons.Default.Close, strCancel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Delete, behind a confirmation. */
@Composable
internal fun DeleteControl(state: QuestionRowState, onDelete: () -> Unit) = with(state) {
    val strCancel = stringResource(Res.string.cancel)
    val strDelete = stringResource(Res.string.delete_saved_string)
    if (!confirmDelete) {
        QAIconButton(tooltip = strDelete, onClick = { confirmDelete = true }) {
            Icon(Icons.Default.Delete, strDelete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return@with
    }
    Text(
        stringResource(Res.string.qa_confirm_delete_prompt),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(end = 4.dp)
    )
    QAIconButton(tooltip = strDelete, onClick = { confirmDelete = false; onDelete() }) {
        Icon(Icons.Default.Delete, strDelete, tint = MaterialTheme.colorScheme.error)
    }
    QAIconButton(tooltip = strCancel, onClick = { confirmDelete = false }) {
        Icon(Icons.Default.Close, strCancel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QAIconButton(
    tooltip: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = tooltip,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp)
        )
    ) {
        RaisedIconButton(
            onClick = onClick,
            modifier = Modifier.size(30.dp),
            shape = AppShape(5.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = QA_DISABLED_ALPHA)
            )
        ) {
            content()
        }
    }
}

package org.churchpresenter.qa

import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import org.churchpresenter.theme.components.RaisedIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.strings.generated.resources.qa_add_question_hint
import org.churchpresenter.strings.generated.resources.qa_clear_all_questions
import org.churchpresenter.strings.generated.resources.qa_clear_question_text
import org.churchpresenter.strings.generated.resources.qa_displaying
import org.churchpresenter.strings.generated.resources.qa_finished
import org.churchpresenter.strings.generated.resources.qa_hide_qr
import org.churchpresenter.strings.generated.resources.qa_history
import org.churchpresenter.strings.generated.resources.qa_incoming
import org.churchpresenter.strings.generated.resources.qa_new_session
import org.churchpresenter.strings.generated.resources.qa_resume
import org.churchpresenter.strings.generated.resources.qa_server_not_running
import org.churchpresenter.strings.generated.resources.qa_show_qr
import org.churchpresenter.strings.generated.resources.qa_stop_session
import org.churchpresenter.strings.generated.resources.tooltip_clear_display
import org.churchpresenter.strings.generated.resources.tooltip_qa_remote
import org.churchpresenter.strings.generated.resources.qa_add
import org.churchpresenter.strings.generated.resources.qa_filter_label
import org.churchpresenter.strings.generated.resources.qa_sort_label
import org.churchpresenter.strings.generated.resources.qa_sort_least_votes
import org.churchpresenter.strings.generated.resources.qa_sort_most_votes
import org.churchpresenter.strings.generated.resources.qa_sort_newest
import org.churchpresenter.strings.generated.resources.qa_sort_oldest
import org.churchpresenter.strings.generated.resources.qa_voting_disabled
import org.churchpresenter.strings.generated.resources.qa_voting_enabled
import org.churchpresenter.strings.generated.resources.qa_all
import org.churchpresenter.strings.generated.resources.qa_approved
import org.churchpresenter.strings.generated.resources.qa_denied
import org.churchpresenter.strings.generated.resources.qa_done
import org.churchpresenter.strings.generated.resources.qa_incoming_approved
import org.churchpresenter.sharedui.composables.ActionIconButton
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.sharedui.models.Presenting
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken
import androidx.compose.foundation.layout.RowScope

/** Session, counts, the display and voting toggles and Clear All, above the questions. */
@Composable
internal fun QATabScope.TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The session controls take the leftover width, so the trailing badges and
        // buttons keep their intrinsic size instead of being squeezed to one glyph.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SessionButtons(this@TopBar)
        }

        val pendingCount = questions.count { it.status == QuestionStatus.PENDING }
        val doneCount = questions.count { it.status == QuestionStatus.DONE }
        val deniedCount = questions.count { it.status == QuestionStatus.DENIED }
        StatBadge(stringResource(Res.string.qa_incoming), pendingCount, MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(8.dp))
        StatBadge(
            stringResource(Res.string.qa_finished),
            doneCount + deniedCount,
            MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.width(16.dp))
        DisplayButtons()

        Spacer(Modifier.width(16.dp))

        // Clear all questions
        ClearAllButton()
    }
}

/** Start or stop the session -- or, with no server, why neither is possible. */
@Composable
private fun RowScope.SessionButtons(tab: QATabScope) = with(tab) {
    if (!isServerRunning) {
        Text(
            stringResource(Res.string.qa_server_not_running),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    } else if (sessionActive) {
        RaisedButton(
            onClick = {
                qaManager.toggleSession()
                output.setDisplayedQuestion(null)
                output.setShowQRCodeOnDisplay(false)
                presenting(Presenting.NONE)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = AppShape(8.dp)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.qa_stop_session))
        }
    } else {
        RaisedButton(
            onClick = { qaManager.toggleSession() },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ),
            shape = AppShape(8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.qa_new_session))
        }
        if (qaManager.history.isNotEmpty()) {
            KeyButton(onClick = { qaManager.restoreFromHistory() },
                shape = AppShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.qa_resume, qaManager.history.size))
            }
        }
    }
}

/** Remote access, the join QR code on screen, and voting on or off. */
@Composable
private fun QATabScope.DisplayButtons() {
    ActionIconButton(
        onClick = { showRemoteDialog = true },
        tooltipText = stringResource(Res.string.tooltip_qa_remote),
        modifier = Modifier.guideTarget(GuideTargets.QA_REMOTE),
        icon = Icons.Default.SettingsRemote,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    Spacer(Modifier.width(8.dp))

    ActionIconButton(
        onClick = {
            if (showQROnDisplay && isQALocked) return@ActionIconButton
            qaManager.toggleQRCodeDisplay()
            output.setShowQRCodeOnDisplay(qaManager.showQRCodeOnDisplay)
            if (qaManager.showQRCodeOnDisplay) {
                output.setDisplayedQuestion(null)
                presenting(Presenting.QA)
            } else if (qaManager.displayedQuestion == null) {
                presenting(Presenting.NONE)
            }
        },
        enabled = !(showQROnDisplay && isQALocked),
        tooltipText = stringResource(if (showQROnDisplay) Res.string.qa_hide_qr else Res.string.qa_show_qr),
        icon = Icons.Default.Tv,
        containerColor = if (showQROnDisplay) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (showQROnDisplay) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    )

    Spacer(Modifier.width(8.dp))

    ActionIconButton(
        onClick = {
            onSettingsChange { s ->
                s.copy(qaSettings = s.qaSettings.copy(votingEnabled = !s.qaSettings.votingEnabled))
            }
        },
        tooltipText = stringResource(
            if (qaSettings.votingEnabled) Res.string.qa_voting_enabled else Res.string.qa_voting_disabled
        ),
        icon = Icons.Default.HowToVote,
        containerColor = if (qaSettings.votingEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (qaSettings.votingEnabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    )
}

@Composable
private fun QATabScope.ClearAllButton() {
    if (questions.isNotEmpty()) {
        KeyButton(
            onClick = { showClearConfirm = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            shape = AppShape(8.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(Res.string.qa_clear_all_questions),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Which questions are listed and in what order, and the way into the history. */
@Composable
internal fun QATabScope.FilterBar() {
    val pendingCount = questions.count { it.status == QuestionStatus.PENDING }
    val approvedCount = questions.count { it.status == QuestionStatus.APPROVED }
    val incomingApprovedCount = questions.count {
        it.status == QuestionStatus.PENDING || it.status == QuestionStatus.APPROVED
    }
    val doneCount = questions.count { it.status == QuestionStatus.DONE }
    val deniedCount = questions.count { it.status == QuestionStatus.DENIED }
    val historyCount = qaManager.history.size
    val allCount = questions.size

    // Filter options: 0=All, 1=Incoming(pending only), 2=Approved, 3=Incoming+Approved, 4=Done, 5=Denied
    // History is separate (selectedFilter = 6)
val filterLabels = listOf(
    stringResource(Res.string.qa_all),
    stringResource(Res.string.qa_incoming),
    stringResource(Res.string.qa_approved),
    stringResource(Res.string.qa_incoming_approved),
    stringResource(Res.string.qa_done),
    stringResource(Res.string.qa_denied)
)
val filterCounts = listOf(allCount, pendingCount, approvedCount, incomingApprovedCount, doneCount, deniedCount)

    val sortLabels = listOf(
        stringResource(Res.string.qa_sort_newest),
        stringResource(Res.string.qa_sort_oldest),
        stringResource(Res.string.qa_sort_most_votes),
        stringResource(Res.string.qa_sort_least_votes)
    )
    val filterItemsWithCount = filterLabels.mapIndexed { i, label -> "$label (${filterCounts[i]})" }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DropdownSelector(
            label = stringResource(Res.string.qa_filter_label),
            items = filterItemsWithCount,
            selected = if (selectedFilter < 6) {
                filterItemsWithCount[selectedFilter]
            } else {
                filterItemsWithCount[0]
            },
            onSelectedChange = { sel -> selectedFilter = filterItemsWithCount.indexOf(sel).coerceAtLeast(0) }
        )

        DropdownSelector(
            label = stringResource(Res.string.qa_sort_label),
            items = sortLabels,
            selected = sortLabels[sortMode],
            onSelectedChange = { sel -> sortMode = sortLabels.indexOf(sel).coerceAtLeast(0) }
        )

        Spacer(Modifier.weight(1f))

        // History button
        KeyButton(
            onClick = { selectedFilter = 6 },
            modifier = Modifier.height(42.dp),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            colors = if (selectedFilter == 6) ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) else ButtonDefaults.outlinedButtonColors(),
            shape = AppShape(8.dp)
        ) {
            Text(
                "${stringResource(Res.string.qa_history)} ($historyCount)",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** The operator's own question, typed in and added to the list. */
@Composable
internal fun QATabScope.AddQuestionField() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .sunken(AppShape(8.dp), elevationPalette())
                .hoverTint(AppShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                BasicTextField(
                    value = addQuestionText,
                    onValueChange = { addQuestionText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (addQuestionText.isEmpty()) {
                            Text(
                                stringResource(Res.string.qa_add_question_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                )
            }
            if (addQuestionText.isNotEmpty()) {
                RaisedIconButton(
                    onClick = { addQuestionText = "" },
                    modifier = Modifier.size(30.dp),
                    shape = AppShape(5.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        painter = painterResource(IconRes.drawable.ic_close),
                        contentDescription = stringResource(Res.string.qa_clear_question_text),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        RaisedButton(
            onClick = {
                qaManager.addQuestion(addQuestionText)
                addQuestionText = ""
            },
            enabled = addQuestionText.isNotBlank(),
            shape = AppShape(8.dp)
        ) {
            Text(stringResource(Res.string.qa_add))
        }
    }
}

/** What is on screen now, and the button that takes it down. */
@Composable
internal fun QATabScope.DisplayingBar(displayedQuestion: Question) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(Res.string.qa_displaying, displayedQuestion.text.take(DISPLAY_PREVIEW_CHARS)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        KeyButton(
            onClick = {
                qaManager.clearDisplay()
                output.setDisplayedQuestion(null)
                output.setShowQRCodeOnDisplay(false)
                presenting(Presenting.NONE)
            },
            shape = AppShape(8.dp)
        ) {
            Text(stringResource(Res.string.tooltip_clear_display))
        }
    }
}

@Composable
private fun StatBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

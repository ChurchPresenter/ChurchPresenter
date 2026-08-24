package org.churchpresenter.qa

import androidx.compose.foundation.ExperimentalFoundationApi
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.diagnostics.CrashReporter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.cancel
import org.churchpresenter.resources.generated.resources.go_live
import org.churchpresenter.resources.generated.resources.ic_close
import org.churchpresenter.resources.generated.resources.qa_add_question_hint
import org.churchpresenter.resources.generated.resources.qa_approve
import org.churchpresenter.resources.generated.resources.qa_back_to_incoming
import org.churchpresenter.resources.generated.resources.qa_clear_all_questions
import org.churchpresenter.resources.generated.resources.qa_clear_question_text
import org.churchpresenter.resources.generated.resources.qa_confirm_go_live
import org.churchpresenter.resources.generated.resources.qa_confirm_go_live_prompt
import org.churchpresenter.resources.generated.resources.qa_delete_all_history
import org.churchpresenter.resources.generated.resources.qa_delete_question
import org.churchpresenter.resources.generated.resources.qa_deny
import org.churchpresenter.resources.generated.resources.qa_displaying
import org.churchpresenter.resources.generated.resources.qa_done_clear
import org.churchpresenter.resources.generated.resources.qa_edit_question_hint
import org.churchpresenter.resources.generated.resources.qa_export_dialog_title
import org.churchpresenter.resources.generated.resources.qa_export_to_file
import org.churchpresenter.resources.generated.resources.qa_finished
import org.churchpresenter.resources.generated.resources.qa_hide_qr
import org.churchpresenter.resources.generated.resources.qa_history
import org.churchpresenter.resources.generated.resources.qa_import_dialog_title
import org.churchpresenter.resources.generated.resources.qa_import_from_file
import org.churchpresenter.resources.generated.resources.qa_incoming
import org.churchpresenter.resources.generated.resources.qa_mark_done
import org.churchpresenter.resources.generated.resources.qa_new_session
import org.churchpresenter.resources.generated.resources.qa_no_approved
import org.churchpresenter.resources.generated.resources.qa_no_denied
import org.churchpresenter.resources.generated.resources.qa_no_finished
import org.churchpresenter.resources.generated.resources.qa_no_history
import org.churchpresenter.resources.generated.resources.qa_resume
import org.churchpresenter.resources.generated.resources.qa_server_not_running
import org.churchpresenter.resources.generated.resources.qa_show_qr
import org.churchpresenter.resources.generated.resources.qa_start_session_hint
import org.churchpresenter.resources.generated.resources.qa_stop_session
import org.churchpresenter.resources.generated.resources.qa_waiting
import org.churchpresenter.resources.generated.resources.save
import org.churchpresenter.resources.generated.resources.qa_confirm_delete_prompt
import org.churchpresenter.resources.generated.resources.qa_submitter_device
import org.churchpresenter.resources.generated.resources.tooltip_clear_display
import org.churchpresenter.resources.generated.resources.tooltip_edit
import org.churchpresenter.resources.generated.resources.tooltip_qa_remote
import org.churchpresenter.resources.generated.resources.qa_add
import org.churchpresenter.resources.generated.resources.qa_clear
import org.churchpresenter.resources.generated.resources.qa_clear_all_confirm_message
import org.churchpresenter.resources.generated.resources.qa_export_clear
import org.churchpresenter.resources.generated.resources.qa_filter_label
import org.churchpresenter.resources.generated.resources.qa_sort_label
import org.churchpresenter.resources.generated.resources.qa_sort_least_votes
import org.churchpresenter.resources.generated.resources.qa_sort_most_votes
import org.churchpresenter.resources.generated.resources.qa_sort_newest
import org.churchpresenter.resources.generated.resources.qa_sort_oldest
import org.churchpresenter.resources.generated.resources.qa_voting_disabled
import org.churchpresenter.resources.generated.resources.qa_voting_enabled
import org.churchpresenter.resources.generated.resources.qa_all
import org.churchpresenter.resources.generated.resources.qa_approved
import org.churchpresenter.resources.generated.resources.qa_denied
import org.churchpresenter.resources.generated.resources.qa_done
import org.churchpresenter.resources.generated.resources.qa_incoming_approved
import org.churchpresenter.ui.ActionIconButton
import org.churchpresenter.ui.GoLiveButton
import org.churchpresenter.ui.DropdownSelector
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.companionserver.TunnelStatus
import org.churchpresenter.theme.semantic
import java.io.IOException
import java.nio.file.Path
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SORT_BY_VOTES = 3
private const val DISPLAY_PREVIEW_CHARS = 50
private const val FILTER_QUEUED = 3
private const val FILTER_FINISHED = 4
private const val FILTER_DENIED = 5
private const val FILTER_HISTORY = 6

/** What the export dialog opens with; the operator renames it or does not. */
private const val EXPORT_FILE_NAME = "questions.txt"

/** Every icon button on a question row is this square. Was a defaulted parameter no caller set. */
private val QA_ICON_BUTTON_SIZE = 30.dp

// Pre-existing, and baselined in `:composeApp` before this file moved: the tab is one long Compose tree; splitting it
// would only move the length.
@Suppress("LongMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QATab(
    modifier: Modifier = Modifier,
    qaManager: QAManager,
    output: QaOutput,
    serverUrl: String,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    tunnelStatus: TunnelStatus = TunnelStatus.Idle,
    tunnelUrl: String = "",
    onStartTunnel: () -> Unit = {},
    onStopTunnel: () -> Unit = {},
    qaDisplayUrl: String = "",
    onQaDisplayUrlChanged: (String) -> Unit = {},
    /** A device id → the name the operator sees for it elsewhere, or "" when it has none.
     *  A lambda rather than the manager itself, so the tab keeps no dependency on it. */
    resolveDeviceName: (String) -> String = { "" },
    /**
     * Asks the operator where to write the export, returning `null` when they cancel.
     *
     * A parameter rather than a call into a file dialog, because a dialog cannot be opened in a
     * test: everything around this — building the export text, writing it, and the rule that
     * cancelling aborts a clear rather than losing the questions — is this module's own and is
     * exercised by handing it a temp path. The default stands for "cancelled".
     */
    chooseExportFile: suspend (suggestedName: String, title: String) -> Path? = { _, _ -> null },
    /** Asks the operator which file to import, returning `null` when they cancel. See above. */
    chooseImportFile: suspend (title: String) -> Path? = { null },
) {
    // The handful of controls here that write straight to QASettings go through one updater
    // rather than restating the two nested copies each time.
    val updateQa: (QASettings.() -> QASettings) -> Unit = { change ->
        onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.change()) }
    }
    val sessionActive = qaManager.sessionActive
    val questions = qaManager.questions
    val displayedQuestion = qaManager.displayedQuestion
    val showQROnDisplay = qaManager.showQRCodeOnDisplay
    val outputIsClear = output.outputIsClear
    val isQALocked = output.lockedToQa

    // Reset QA display state when the output is cleared (e.g. via Escape or Clear Display).
    // Keyed on the boolean rather than on what is live: the body only ever acts on "the screen went
    // empty", so a move between two other kinds of content is not a wake-up this tab needs.
    LaunchedEffect(outputIsClear) {
        val hasQAContentUp = showQROnDisplay || displayedQuestion != null
        if (outputIsClear && !isQALocked && hasQAContentUp) {
            qaManager.clearDisplay()
        }
    }

    val isServerRunning = serverUrl.isNotEmpty()

    var selectedFilter by remember { mutableStateOf(0) }
    var sortMode by remember { mutableStateOf(0) } // 0=newest, 1=oldest, 2=most votes, 3=least votes
    var showClearConfirm by remember { mutableStateOf(false) }
    var addQuestionText by remember { mutableStateOf("") }
    var showRemoteDialog by remember { mutableStateOf(false) }

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

    fun <T> List<T>.applySortMode(sortMode: Int, timestamp: (T) -> Long, voteCount: (T) -> Int): List<T> {
        return when (sortMode) {
            0 -> sortedByDescending { timestamp(it) }
            1 -> sortedBy { timestamp(it) }
            2 -> sortedByDescending { voteCount(it) }
            SORT_BY_VOTES -> sortedBy { voteCount(it) }
            else -> this
        }
    }

    val filteredQuestions = remember(questions.toList(), selectedFilter, qaManager.history.toList(), sortMode) {
        val sorted = { list: List<Question> -> list.applySortMode(sortMode, { it.timestamp }, { it.voteCount }) }
        when (selectedFilter) {
            0 -> sorted(questions.toList())
            1 -> sorted(questions.filter { it.status == QuestionStatus.PENDING })
            2 -> sorted(questions.filter { it.status == QuestionStatus.APPROVED })
            3 -> sorted(
                questions.filter { it.status == QuestionStatus.PENDING || it.status == QuestionStatus.APPROVED }
            )
            4 -> sorted(questions.filter { it.status == QuestionStatus.DONE })
            5 -> sorted(questions.filter { it.status == QuestionStatus.DENIED })
            6 -> qaManager.history
            else -> questions
        }
    }

    val qaSettings = appSettings.qaSettings

    // Hoist strings needed inside non-composable lambdas
    val strExportTitle = stringResource(Res.string.qa_export_dialog_title)
    val strImportTitle = stringResource(Res.string.qa_import_dialog_title)

    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // ── Question List ────────────────────────────────
            // Top bar: session + clear
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isServerRunning) {
                    Text(
                        stringResource(Res.string.qa_server_not_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (sessionActive) {
                    Button(
                        onClick = {
                            qaManager.toggleSession()
                            output.setDisplayedQuestion(null)
                            output.setShowQrCode(false)
                            output.clear()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.qa_stop_session))
                    }
                } else {
                    Button(
                        onClick = { qaManager.toggleSession() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.qa_new_session))
                    }
                    if (qaManager.history.isNotEmpty()) {
                        OutlinedButton(onClick = { qaManager.restoreFromHistory() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.qa_resume, qaManager.history.size))
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                StatBadge(stringResource(Res.string.qa_incoming), pendingCount, MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                StatBadge(
                    stringResource(Res.string.qa_finished),
                    doneCount + deniedCount,
                    MaterialTheme.colorScheme.secondary,
                )

                Spacer(Modifier.width(16.dp))

                ActionIconButton(
                    onClick = { showRemoteDialog = true },
                    tooltipText = stringResource(Res.string.tooltip_qa_remote),
                    icon = Icons.Default.SettingsRemote,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(Modifier.width(8.dp))

                ActionIconButton(
                    onClick = {
                        if (showQROnDisplay && isQALocked) return@ActionIconButton
                        qaManager.toggleQRCodeDisplay()
                        output.setShowQrCode(qaManager.showQRCodeOnDisplay)
                        if (qaManager.showQRCodeOnDisplay) { output.setDisplayedQuestion(null); output.goLive() }
                        else if (qaManager.displayedQuestion == null) { output.clear() }
                    },
                    enabled = !(showQROnDisplay && isQALocked),
                    tooltipText = stringResource(if (showQROnDisplay) Res.string.qa_hide_qr else Res.string.qa_show_qr),
                    icon = Icons.Default.Tv,
                    containerColor = if (showQROnDisplay) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (showQROnDisplay) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(Modifier.width(8.dp))

                ActionIconButton(
                    onClick = { updateQa { copy(votingEnabled = !votingEnabled) } },
                    tooltipText = stringResource(
                        if (qaSettings.votingEnabled) Res.string.qa_voting_enabled
                        else Res.string.qa_voting_disabled,
                    ),
                    icon = Icons.Default.HowToVote,
                    containerColor = if (qaSettings.votingEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (qaSettings.votingEnabled) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(Modifier.width(16.dp))

                // Clear all questions
                if (questions.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.qa_clear_all_questions))
                    }
                }
            }

            // Filter: dropdown for question views + History tab
            val sortLabels = listOf(
                stringResource(Res.string.qa_sort_newest),
                stringResource(Res.string.qa_sort_oldest),
                stringResource(Res.string.qa_sort_most_votes),
                stringResource(Res.string.qa_sort_least_votes)
            )
            val filterItemsWithCount = filterLabels.mapIndexed { i, label -> "$label (${filterCounts[i]})" }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownSelector(
                    label = stringResource(Res.string.qa_filter_label),
                    items = filterItemsWithCount,
                    selected = if (selectedFilter < 6) filterItemsWithCount[selectedFilter]
                        else filterItemsWithCount[0],
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
                OutlinedButton(
                    onClick = { selectedFilter = 6 },
                    modifier = Modifier.height(42.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    colors = if (selectedFilter == 6) ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) else ButtonDefaults.outlinedButtonColors(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${stringResource(Res.string.qa_history)} ($historyCount)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // Add question input
            if (sessionActive) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            BasicTextField(
                                value = addQuestionText,
                                onValueChange = { addQuestionText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                                .copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (addQuestionText.isEmpty()) {
                                        Text(
                                            stringResource(Res.string.qa_add_question_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            maxLines = 1,
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                        if (addQuestionText.isNotEmpty()) {
                            FilledIconButton(
                                onClick = { addQuestionText = "" },
                                modifier = Modifier.size(30.dp),
                                shape = RoundedCornerShape(5.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_close),
                                    contentDescription = stringResource(Res.string.qa_clear_question_text),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            qaManager.addQuestion(addQuestionText)
                            addQuestionText = ""
                        },
                        enabled = addQuestionText.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(Res.string.qa_add))
                    }
                }
            }

            // Clear display bar
            if (displayedQuestion != null) {
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
                    OutlinedButton(
                        onClick = {
                            qaManager.clearDisplay()
                            output.setDisplayedQuestion(null)
                            output.setShowQrCode(false)
                            output.clear()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(Res.string.tooltip_clear_display))
                    }
                }
            }

            if (filteredQuestions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when (selectedFilter) {
                            0 -> if (sessionActive) stringResource(Res.string.qa_waiting)
                                else stringResource(Res.string.qa_start_session_hint)
                            1 -> if (sessionActive) stringResource(Res.string.qa_waiting)
                                else stringResource(Res.string.qa_start_session_hint)
                            2 -> stringResource(Res.string.qa_no_approved)
                            FILTER_QUEUED -> if (sessionActive) stringResource(Res.string.qa_waiting)
                                else stringResource(Res.string.qa_start_session_hint)
                            FILTER_FINISHED -> stringResource(Res.string.qa_no_finished)
                            FILTER_DENIED -> stringResource(Res.string.qa_no_denied)
                            FILTER_HISTORY -> stringResource(Res.string.qa_no_history)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // History tab actions bar
                if (selectedFilter == FILTER_HISTORY && filteredQuestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = {
                            coroutineScope.launch {
                                val path = chooseExportFile(EXPORT_FILE_NAME, strExportTitle)
                                if (path != null) {
                                    val export = filteredQuestions.joinToString("\n") { q ->
                                        val status = q.status.name.lowercase().replaceFirstChar { it.uppercase() }
                                        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                            .format(Date(q.timestamp))
                                        "[$time] [$status] ${q.text}"
                                    }
                                    try {
                                        withContext(Dispatchers.IO) { path.toFile().writeText(export) }
                                    } catch (e: IOException) {
                                        CrashReporter.reportException(e, context = "QATab.exportQuestions")
                                    }
                                }
                            }
                        },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                stringResource(Res.string.qa_export_to_file),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        OutlinedButton(onClick = {
                            coroutineScope.launch {
                                val path = chooseImportFile(strImportTitle)
                                if (path != null) {
                                    val lines = try {
                                        withContext(Dispatchers.IO) { path.toFile().readLines() }
                                    } catch (e: IOException) {
                                        CrashReporter.reportException(e, context = "QATab.importQuestions")
                                        emptyList()
                                    }
                                    for (line in lines) {
                                        val text = line.replace(Regex("^\\[.*?\\]\\s*\\[.*?\\]\\s*"), "").trim()
                                        if (text.isNotBlank()) qaManager.addQuestion(text)
                                    }
                                }
                            }
                        },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                stringResource(Res.string.qa_import_from_file),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        OutlinedButton(
                            onClick = { qaManager.clearHistory() },
                            colors = ButtonDefaults
                                .outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.qa_delete_all_history))
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredQuestions.distinctBy { it.id }, key = { it.id }) { question ->
                        QuestionRow(
                            question = question,
                            deviceName = resolveDeviceName(question.submitterDeviceId),
                            isDisplayed = displayedQuestion?.id == question.id,
                            isHistory = selectedFilter == 6,
                            onApprove = { qaManager.approveQuestion(question.id) },
                            onDeny = { qaManager.denyQuestion(question.id) },
                            onMarkDone = {
                                qaManager.markDone(question.id)
                                if (displayedQuestion?.id == question.id) {
                                    output.setDisplayedQuestion(null)
                                    output.clear()
                                }
                            },
                            onEdit = { newText ->
                                qaManager.editQuestion(question.id, newText)
                                val updated = qaManager.findQuestion(question.id)
                                if (updated != null && displayedQuestion?.id == question.id) {
                                    output.setDisplayedQuestion(updated)
                                }
                            },
                            onDisplay = {
                                qaManager.displayQuestion(question.id)
                                val current = qaManager.findQuestion(question.id) ?: question
                                output.setDisplayedQuestion(current)
                                output.setShowQrCode(false)
                                output.goLive()
                            },
                            onDelete = {
                                qaManager.deleteQuestion(question.id)
                                if (displayedQuestion?.id == question.id) {
                                    output.setDisplayedQuestion(null)
                                    output.clear()
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }

        // ── Clear All Confirmation Dialog ─────────────────────────────
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text(stringResource(Res.string.qa_clear_all_questions)) },
                text = { Text(stringResource(Res.string.qa_clear_all_confirm_message)) },
                confirmButton = {
                    TextButton(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                        qaManager.clearAll()
                        output.setDisplayedQuestion(null)
                        output.setShowQrCode(false)
                        output.clear()
                        showClearConfirm = false
                    }) { Text(stringResource(Res.string.qa_clear), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = {
                            // Close the confirm dialog before the save dialog opens and snapshot
                            // the questions so a concurrent clear cannot empty the export
                            showClearConfirm = false
                            val toExport = questions.toList()
                            coroutineScope.launch {
                                val path = chooseExportFile(EXPORT_FILE_NAME, strExportTitle)
                                // Cancelling the save dialog aborts the clear — never delete unexported questions
                                if (path != null) {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            path.toFile().writeText(
                                                toExport.joinToString("\n") { q ->
                                                    val at = SimpleDateFormat("HH:mm", Locale.getDefault())
                                                        .format(Date(q.timestamp))
                                                    "[$at] [${q.status}] ${q.text}"
                                                }
                                            )
                                        }
                                    } catch (e: IOException) {
                                        CrashReporter.reportException(e, context = "QATab.exportAndClear")
                                        return@launch
                                    }
                                    qaManager.clearAll()
                                    output.setDisplayedQuestion(null)
                                    output.setShowQrCode(false)
                                    output.clear()
                                }
                            }
                        }) { Text(stringResource(Res.string.qa_export_clear)) }
                        TextButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { showClearConfirm = false },
                        ) { Text(stringResource(Res.string.cancel)) }
                    }
                }
            )
        }
    }

    if (showRemoteDialog) {
        QARemoteDialog(
            serverUrl = serverUrl,
            qaDisplayUrl = qaDisplayUrl,
            onQaDisplayUrlChanged = onQaDisplayUrlChanged,
            apiKeyEnabled = appSettings.serverSettings.apiKeyEnabled,
            apiKey = appSettings.serverSettings.apiKey,
            tunnelStatus = tunnelStatus,
            tunnelUrl = tunnelUrl,
            onStartTunnel = onStartTunnel,
            onStopTunnel = onStopTunnel,
            qaSettings = qaSettings,
            onSettingsChange = onSettingsChange,
            onDismiss = { showRemoteDialog = false }
        )
    }
}

// Pre-existing, and baselined in `:composeApp` before this file moved: one row, with every moderation control it can
// offer.
@Suppress("LongMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuestionRow(
    question: Question,
    /** What this device is called, or "" — then the id stands on its own, as it always did. */
    deviceName: String,
    isDisplayed: Boolean,
    isHistory: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onMarkDone: () -> Unit,
    onEdit: (String) -> Unit,
    onDisplay: () -> Unit,
    onDelete: () -> Unit,
) {
    val bgColor = if (isDisplayed) MaterialTheme.semantic.successContainer.copy(alpha = 0.35f) else Color.Transparent
    val statusColor = when (question.status) {
        QuestionStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        QuestionStatus.APPROVED -> MaterialTheme.colorScheme.inverseSurface
        QuestionStatus.DENIED -> MaterialTheme.colorScheme.error
        QuestionStatus.DONE -> MaterialTheme.colorScheme.secondary
    }
    val strDone = stringResource(Res.string.qa_done)
    val strDenied = stringResource(Res.string.qa_denied)
    val statusLabel = when (question.status) {
        QuestionStatus.DONE -> strDone
        QuestionStatus.DENIED -> strDenied
        else -> null
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var editing by remember { mutableStateOf(false) }
    var confirmGoLive by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editText by remember(question.text) { mutableStateOf(question.text) }

    // Strings resolved in composable scope for use in icon contentDescriptions
    val strSave = stringResource(Res.string.save)
    val strEdit = stringResource(Res.string.tooltip_edit)
    val strCancel = stringResource(Res.string.cancel)
    val strApprove = stringResource(Res.string.qa_approve)
    val strDeny = stringResource(Res.string.qa_deny)
    val strGoLive = stringResource(Res.string.go_live)
    val strMarkDone = stringResource(Res.string.qa_mark_done)
    val strDoneClear = stringResource(Res.string.qa_done_clear)
    val strBackToIncoming = stringResource(Res.string.qa_back_to_incoming)
    val strConfirmGoLive = stringResource(Res.string.qa_confirm_go_live)
    val strDelete = stringResource(Res.string.qa_delete_question)
    val strConfirmDelete = stringResource(Res.string.qa_confirm_delete_prompt)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(statusColor))
            Spacer(Modifier.width(8.dp))
            Text(
                text = timeFormat.format(Date(question.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(50.dp)
            )

            if (!editing) {
                if (question.upvotes > 0 || question.downvotes > 0) {
                    Row(modifier = Modifier.padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (question.upvotes > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = "\u25B2 ${question.upvotes}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (question.downvotes > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    text = "\u25BC ${question.downvotes}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                val hasSubmitterInfo = question.submitterName.isNotBlank() || question.submitterDeviceId.isNotBlank()
                TooltipArea(
                    tooltip = {
                        if (hasSubmitterInfo) {
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
                                        // The name if the device has one — the same name the
                                        // approval prompt and Server settings show it by.
                                        Text(
                                            text = stringResource(
                                                Res.string.qa_submitter_device,
                                                deviceName.ifBlank { question.submitterDeviceId },
                                            ),
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (deviceName.isNotBlank()) {
                                            Text(
                                                text = question.submitterDeviceId,
                                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box {}
                        }
                    },
                    tooltipPlacement = TooltipPlacement.ComponentRect(
                        anchor = Alignment.BottomStart,
                        offset = DpOffset(0.dp, 4.dp),
                    ),
                    modifier = Modifier.weight(1f)
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
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }

            if (!isHistory) {
                // Edit / Save button
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
                        tint = if (editing) MaterialTheme.colorScheme.inverseSurface
                            else MaterialTheme.colorScheme.tertiary
                    )
                }

                if (editing) {
                    QAIconButton(tooltip = strCancel, onClick = { editText = question.text; editing = false }) {
                        Icon(Icons.Default.Close, strCancel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (!editing) {
                    when (question.status) {
                        QuestionStatus.PENDING -> {
                            QAIconButton(tooltip = strApprove, onClick = onApprove) {
                                Icon(Icons.Default.Check, strApprove, tint = MaterialTheme.colorScheme.inverseSurface)
                            }
                            QAIconButton(tooltip = strDeny, onClick = onDeny) {
                                Icon(Icons.Default.Close, strDeny, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        QuestionStatus.APPROVED -> {
                            if (!isDisplayed) {
                                GoLiveButton(onClick = onDisplay, tooltipText = strGoLive)
                            }
                            QAIconButton(
                                tooltip = if (isDisplayed) strDoneClear else strMarkDone,
                                onClick = onMarkDone,
                            ) {
                                Icon(
                                    Icons.Default.Done,
                                    contentDescription = if (isDisplayed) strDoneClear else strMarkDone,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            QAIconButton(tooltip = strDeny, onClick = onDeny) {
                                Icon(Icons.Default.Close, strDeny, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        QuestionStatus.DONE -> {
                            QAIconButton(tooltip = strBackToIncoming, onClick = onApprove) {
                                Icon(
                                    Icons.Default.Refresh,
                                    strBackToIncoming,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            if (confirmGoLive) {
                                Text(
                                    stringResource(Res.string.qa_confirm_go_live_prompt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                GoLiveButton(
                                    onClick = { confirmGoLive = false; onApprove(); onDisplay() },
                                    tooltipText = strConfirmGoLive,
                                )
                                QAIconButton(tooltip = strCancel, onClick = { confirmGoLive = false }) {
                                    Icon(
                                        Icons.Default.Close,
                                        strCancel,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                GoLiveButton(onClick = { confirmGoLive = true }, tooltipText = strGoLive)
                            }
                        }
                        QuestionStatus.DENIED -> {
                            QAIconButton(tooltip = strApprove, onClick = onApprove) {
                                Icon(Icons.Default.Check, strApprove, tint = MaterialTheme.colorScheme.inverseSurface)
                            }
                            if (confirmGoLive) {
                                Text(
                                    stringResource(Res.string.qa_confirm_go_live_prompt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                GoLiveButton(
                                    onClick = { confirmGoLive = false; onApprove(); onDisplay() },
                                    tooltipText = strConfirmGoLive,
                                )
                                QAIconButton(tooltip = strCancel, onClick = { confirmGoLive = false }) {
                                    Icon(
                                        Icons.Default.Close,
                                        strCancel,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                GoLiveButton(onClick = { confirmGoLive = true }, tooltipText = strGoLive, dimmed = true)
                            }
                        }
                    }

                    if (confirmDelete) {
                        Text(
                            strConfirmDelete,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        QAIconButton(tooltip = strDelete, onClick = { confirmDelete = false; onDelete() }) {
                            Icon(Icons.Default.Delete, strDelete, tint = MaterialTheme.colorScheme.error)
                        }
                        QAIconButton(tooltip = strCancel, onClick = { confirmDelete = false }) {
                            Icon(Icons.Default.Close, strCancel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        QAIconButton(tooltip = strDelete, onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, strDelete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Edit text field
        if (editing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 50.dp, top = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        if (editText.isEmpty()) {
                            Text(
                                stringResource(Res.string.qa_edit_question_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QAIconButton(
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
            offset = DpOffset(0.dp, 4.dp),
        )
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(QA_ICON_BUTTON_SIZE),
            shape = RoundedCornerShape(5.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        ) {
            content()
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

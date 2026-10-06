package org.churchpresenter.qa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.diagnostics.CrashReporter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.file_filter_text
import org.churchpresenter.strings.generated.resources.qa_clear_all_questions
import org.churchpresenter.strings.generated.resources.qa_delete_all_history
import org.churchpresenter.strings.generated.resources.qa_export_to_file
import org.churchpresenter.strings.generated.resources.qa_import_from_file
import org.churchpresenter.strings.generated.resources.qa_no_approved
import org.churchpresenter.strings.generated.resources.qa_no_denied
import org.churchpresenter.strings.generated.resources.qa_no_finished
import org.churchpresenter.strings.generated.resources.qa_no_history
import org.churchpresenter.strings.generated.resources.qa_start_session_hint
import org.churchpresenter.strings.generated.resources.qa_waiting
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.qa_clear
import org.churchpresenter.strings.generated.resources.qa_clear_all_confirm_message
import org.churchpresenter.strings.generated.resources.qa_export_clear
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.sharedui.models.Presenting
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.IOException
import javax.swing.filechooser.FileNameExtensionFilter
import org.churchpresenter.sharedui.composables.rowPad

/** What an empty list says, which depends on the filter and on whether a session is running. */
@Composable
internal fun QATabScope.EmptyNotice() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            when (selectedFilter) {
                0, 1, FILTER_QUEUED -> stringResource(
                    if (sessionActive) Res.string.qa_waiting else Res.string.qa_start_session_hint
                )
                2 -> stringResource(Res.string.qa_no_approved)
                FILTER_FINISHED -> stringResource(Res.string.qa_no_finished)
                FILTER_DENIED -> stringResource(Res.string.qa_no_denied)
                FILTER_HISTORY -> stringResource(Res.string.qa_no_history)
                else -> ""
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Export, import and delete, over the history. */
@Composable
internal fun QATabScope.HistoryActions(filteredQuestions: List<Question>) {
    val strTextFiles = stringResource(Res.string.file_filter_text)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KeyButton(onClick = {
            coroutineScope.launch {
                val path = FileChooser.platformInstance.save(
                    location = null,
                    suggestedName = "questions.txt",
                    filters = listOf(FileNameExtensionFilter(strTextFiles, "txt")),
                    title = strExportTitle
                )
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
            shape = AppShape(8.dp)
        ) {
            Text(
                stringResource(Res.string.qa_export_to_file),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        KeyButton(onClick = {
            coroutineScope.launch {
                val path = FileChooser.platformInstance.chooseSingle(
                    path = null,
                    filters = listOf(FileNameExtensionFilter(strTextFiles, "txt")),
                    title = strImportTitle,
                    selectDirectory = false
                )
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
            shape = AppShape(8.dp)
        ) {
            Text(
                stringResource(Res.string.qa_import_from_file),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        KeyButton(
            onClick = { qaManager.clearHistory() },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            shape = AppShape(8.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.qa_delete_all_history))
        }
    }
}

/** The questions, each with the actions its state allows. */
@Composable
internal fun QATabScope.QuestionList(filteredQuestions: List<Question>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(rowPad(2.dp)),
    ) {
        items(filteredQuestions.distinctBy { it.id }, key = { it.id }) { question ->
            // What was on screen when the row was drawn: marking it done or deleting it clears the
            // manager's own copy before the handler asks whether it was the one showing.
            val wasDisplayed = displayedQuestion?.id == question.id
            QuestionRow(
                question = question,
                isDisplayed = wasDisplayed,
                isHistory = selectedFilter == 6,
                onApprove = { qaManager.approveQuestion(question.id) },
                onDeny = { qaManager.denyQuestion(question.id) },
                onMarkDone = {
                    qaManager.markDone(question.id)
                    if (wasDisplayed) {
                        output.setDisplayedQuestion(null)
                        presenting(Presenting.NONE)
                    }
                },
                onEdit = { newText ->
                    qaManager.editQuestion(question.id, newText)
                    val updated = qaManager.findQuestion(question.id)
                    if (updated != null && wasDisplayed) {
                        output.setDisplayedQuestion(updated)
                    }
                },
                onDisplay = {
                    qaManager.displayQuestion(question.id)
                    val current = qaManager.findQuestion(question.id) ?: question
                    output.setDisplayedQuestion(current)
                    output.setShowQRCodeOnDisplay(false)
                    presenting(Presenting.QA)
                },
                onDelete = {
                    qaManager.deleteQuestion(question.id)
                    if (wasDisplayed) {
                        output.setDisplayedQuestion(null)
                        presenting(Presenting.NONE)
                    }
                }
            )
        }
    }
}

/** Clear All, with the chance to export the questions first. */
@Composable
internal fun QATabScope.ClearAllDialog() {
    val strTextFiles = stringResource(Res.string.file_filter_text)
    AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        title = { Text(stringResource(Res.string.qa_clear_all_questions)) },
        text = { Text(stringResource(Res.string.qa_clear_all_confirm_message)) },
        confirmButton = {
            GhostButton(
                shape = AppShape(6.dp),
                onClick = {
                qaManager.clearAll()
                output.setDisplayedQuestion(null)
                output.setShowQRCodeOnDisplay(false)
                presenting(Presenting.NONE)
                showClearConfirm = false
            }) { Text(stringResource(Res.string.qa_clear), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    shape = AppShape(6.dp),
                    onClick = {
                    // Close the confirm dialog before the save dialog opens and snapshot
                    // the questions so a concurrent clear cannot empty the export
                    showClearConfirm = false
                    val toExport = questions.toList()
                    coroutineScope.launch {
                        val path = FileChooser.platformInstance.save(
                            location = null,
                            suggestedName = "questions.txt",
                            filters = listOf(FileNameExtensionFilter(strTextFiles, "txt")),
                            title = strExportTitle
                        )
                        // Cancelling the save dialog aborts the clear — never delete unexported questions
                        if (path != null) {
                            try {
                                withContext(Dispatchers.IO) {
                                    path.toFile().writeText(
                                        toExport.joinToString("\n") { q ->
                                            val time = SimpleDateFormat("HH:mm", Locale.getDefault())
                                                .format(Date(q.timestamp))
                                            "[$time] [${q.status}] ${q.text}"
                                        }
                                    )
                                }
                            } catch (e: IOException) {
                                CrashReporter.reportException(e, context = "QATab.exportAndClear")
                                return@launch
                            }
                            qaManager.clearAll()
                            output.setDisplayedQuestion(null)
                            output.setShowQRCodeOnDisplay(false)
                            presenting(Presenting.NONE)
                        }
                    }
                }) { Text(stringResource(Res.string.qa_export_clear)) }
                GhostButton(shape = AppShape(6.dp), onClick = { showClearConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        }
    )
}

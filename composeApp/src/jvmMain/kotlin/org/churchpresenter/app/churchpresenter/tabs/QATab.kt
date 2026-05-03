package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.qa_admin_panel
import churchpresenter.composeapp.generated.resources.qa_admin_password
import churchpresenter.composeapp.generated.resources.qa_approve
import churchpresenter.composeapp.generated.resources.qa_back_to_incoming
import churchpresenter.composeapp.generated.resources.qa_background_color
import churchpresenter.composeapp.generated.resources.qa_clear_all_questions
import churchpresenter.composeapp.generated.resources.qa_confirm_go_live
import churchpresenter.composeapp.generated.resources.qa_confirm_go_live_prompt
import churchpresenter.composeapp.generated.resources.qa_cooldown_label
import churchpresenter.composeapp.generated.resources.qa_delete_all_history
import churchpresenter.composeapp.generated.resources.qa_delete_question
import churchpresenter.composeapp.generated.resources.qa_deny
import churchpresenter.composeapp.generated.resources.qa_display_styling
import churchpresenter.composeapp.generated.resources.qa_displaying
import churchpresenter.composeapp.generated.resources.qa_done_clear
import churchpresenter.composeapp.generated.resources.qa_edit_question_hint
import churchpresenter.composeapp.generated.resources.qa_export_dialog_title
import churchpresenter.composeapp.generated.resources.qa_export_to_file
import churchpresenter.composeapp.generated.resources.qa_finished
import churchpresenter.composeapp.generated.resources.qa_finished_tab
import churchpresenter.composeapp.generated.resources.qa_font
import churchpresenter.composeapp.generated.resources.qa_hide_qr
import churchpresenter.composeapp.generated.resources.qa_history
import churchpresenter.composeapp.generated.resources.qa_history_tab
import churchpresenter.composeapp.generated.resources.qa_import_dialog_title
import churchpresenter.composeapp.generated.resources.qa_import_from_file
import churchpresenter.composeapp.generated.resources.qa_incoming
import churchpresenter.composeapp.generated.resources.qa_incoming_tab
import churchpresenter.composeapp.generated.resources.qa_mark_done
import churchpresenter.composeapp.generated.resources.qa_new_session
import churchpresenter.composeapp.generated.resources.qa_no_finished
import churchpresenter.composeapp.generated.resources.qa_no_history
import churchpresenter.composeapp.generated.resources.qa_no_password
import churchpresenter.composeapp.generated.resources.qa_position
import churchpresenter.composeapp.generated.resources.qa_resume
import churchpresenter.composeapp.generated.resources.qa_server_hint
import churchpresenter.composeapp.generated.resources.qa_server_not_running
import churchpresenter.composeapp.generated.resources.qa_settings_section
import churchpresenter.composeapp.generated.resources.qa_show_qr
import churchpresenter.composeapp.generated.resources.qa_size
import churchpresenter.composeapp.generated.resources.qa_start_session_hint
import churchpresenter.composeapp.generated.resources.qa_stop_session
import churchpresenter.composeapp.generated.resources.qa_submit_questions
import churchpresenter.composeapp.generated.resources.qa_transparent
import churchpresenter.composeapp.generated.resources.qa_waiting
import churchpresenter.composeapp.generated.resources.save
import churchpresenter.composeapp.generated.resources.tooltip_clear_display
import churchpresenter.composeapp.generated.resources.tooltip_edit
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.Question
import org.churchpresenter.app.churchpresenter.models.QuestionStatus
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.generateQRCodeBitmap
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun QATab(
    modifier: Modifier = Modifier,
    qaManager: QAManager,
    presenterManager: PresenterManager,
    serverUrl: String,
    presenting: (Presenting) -> Unit,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    tunnelStatus: TunnelStatus = TunnelStatus.Idle,
    tunnelUrl: String = "",
    onStartTunnel: () -> Unit = {},
    onStopTunnel: () -> Unit = {},
    qaDisplayUrl: String = "",
    onQaDisplayUrlChanged: (String) -> Unit = {},
) {
    val sessionActive = qaManager.sessionActive
    val questions = qaManager.questions
    val displayedQuestion = qaManager.displayedQuestion
    val showQROnDisplay = qaManager.showQRCodeOnDisplay
    val presentingMode by presenterManager.presentingMode

    // Reset QA display state when display is cleared (e.g. via Escape or Clear Display)
    LaunchedEffect(presentingMode) {
        if (presentingMode == Presenting.NONE && (showQROnDisplay || displayedQuestion != null)) {
            qaManager.clearDisplay()
        }
    }

    val effectiveBaseUrl = qaDisplayUrl.ifEmpty { serverUrl }
    val submissionUrl = if (effectiveBaseUrl.isNotEmpty()) "$effectiveBaseUrl/qa" else ""
    var adminUseTunnel by remember { mutableStateOf(false) }
    val adminBaseUrl = if (adminUseTunnel && tunnelUrl.isNotEmpty()) tunnelUrl else serverUrl
    val adminDisplayUrl = if (adminBaseUrl.isNotEmpty()) "$adminBaseUrl/qa/admin" else ""
    val adminQrUrl = if (adminBaseUrl.isNotEmpty()) {
        val pw = appSettings.qaSettings.adminPassword
        if (pw.isNotEmpty()) "$adminBaseUrl/qa/admin?password=${java.net.URLEncoder.encode(pw, "UTF-8")}"
        else "$adminBaseUrl/qa/admin"
    } else ""

    var selectedFilter by remember { mutableStateOf(0) }

    val incomingCount = questions.count { it.status == QuestionStatus.PENDING || it.status == QuestionStatus.APPROVED }
    val finishedCount = questions.count { it.status == QuestionStatus.DONE || it.status == QuestionStatus.DENIED }
    val historyCount = qaManager.history.size

    val filteredQuestions = remember(questions.toList(), selectedFilter, qaManager.history.toList()) {
        when (selectedFilter) {
            0 -> questions.filter { it.status == QuestionStatus.PENDING || it.status == QuestionStatus.APPROVED }
                .sortedBy { it.timestamp }
            1 -> questions.filter { it.status == QuestionStatus.DONE || it.status == QuestionStatus.DENIED }
            2 -> qaManager.history
            else -> questions
        }
    }

    val qaSettings = appSettings.qaSettings
    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }

    // Hoist strings needed inside non-composable lambdas
    val strExportTitle = stringResource(Res.string.qa_export_dialog_title)
    val strImportTitle = stringResource(Res.string.qa_import_dialog_title)

    Row(modifier = modifier.fillMaxSize()) {
        // ── Left Panel: Question List ────────────────────────────────
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Top bar: session + clear
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (serverUrl.isEmpty()) {
                    Text(
                        stringResource(Res.string.qa_server_not_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE53935)
                    )
                } else if (sessionActive) {
                    Button(
                        onClick = {
                            qaManager.toggleSession()
                            presenterManager.setDisplayedQuestion(null)
                            presenterManager.setShowQRCodeOnDisplay(false)
                            presenting(Presenting.NONE)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.qa_stop_session))
                    }
                } else {
                    Button(
                        onClick = { qaManager.toggleSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.qa_new_session))
                    }
                    if (qaManager.history.isNotEmpty()) {
                        OutlinedButton(onClick = { qaManager.restoreFromHistory() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.qa_resume, qaManager.history.size))
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                StatBadge(stringResource(Res.string.qa_incoming), incomingCount, Color(0xFFFFA726))
                Spacer(Modifier.width(8.dp))
                StatBadge(stringResource(Res.string.qa_finished), finishedCount, Color(0xFF42A5F5))

                Spacer(Modifier.width(16.dp))

                // Clear all questions
                if (questions.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            qaManager.clearAll()
                            presenterManager.setDisplayedQuestion(null)
                            presenterManager.setShowQRCodeOnDisplay(false)
                            presenting(Presenting.NONE)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.qa_clear_all_questions))
                    }
                }
            }

            // Filter tabs
            PrimaryTabRow(selectedTabIndex = selectedFilter) {
                Tab(
                    selected = selectedFilter == 0,
                    onClick = { selectedFilter = 0 },
                    text = { Text(stringResource(Res.string.qa_incoming_tab, incomingCount)) }
                )
                Tab(
                    selected = selectedFilter == 1,
                    onClick = { selectedFilter = 1 },
                    text = { Text(stringResource(Res.string.qa_finished_tab, finishedCount)) }
                )
                Tab(
                    selected = selectedFilter == 2,
                    onClick = { selectedFilter = 2 },
                    text = { Text(stringResource(Res.string.qa_history_tab, historyCount)) }
                )
            }

            // Clear display bar
            if (displayedQuestion != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF43A047).copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.qa_displaying, displayedQuestion.text.take(50)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedButton(
                        onClick = {
                            qaManager.clearDisplay()
                            presenterManager.setDisplayedQuestion(null)
                            presenterManager.setShowQRCodeOnDisplay(false)
                            presenting(Presenting.NONE)
                        }
                    ) {
                        Text(stringResource(Res.string.tooltip_clear_display))
                    }
                }
            }

            if (filteredQuestions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when (selectedFilter) {
                            0 -> if (sessionActive) stringResource(Res.string.qa_waiting) else stringResource(Res.string.qa_start_session_hint)
                            1 -> stringResource(Res.string.qa_no_finished)
                            2 -> stringResource(Res.string.qa_no_history)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // History tab actions bar
                if (selectedFilter == 2 && filteredQuestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = {
                            val chooser = javax.swing.JFileChooser().apply {
                                dialogTitle = strExportTitle
                                selectedFile = java.io.File("questions.txt")
                                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Text files", "txt")
                            }
                            if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                val file = chooser.selectedFile
                                val export = filteredQuestions.joinToString("\n") { q ->
                                    val status = q.status.name.lowercase().replaceFirstChar { it.uppercase() }
                                    val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(q.timestamp))
                                    "[$time] [$status] ${q.text}"
                                }
                                file.writeText(export)
                            }
                        }) {
                            Text(stringResource(Res.string.qa_export_to_file), color = MaterialTheme.colorScheme.onSurface)
                        }
                        OutlinedButton(onClick = {
                            val chooser = javax.swing.JFileChooser().apply {
                                dialogTitle = strImportTitle
                                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Text files", "txt")
                            }
                            if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                val lines = chooser.selectedFile.readLines()
                                for (line in lines) {
                                    val text = line.replace(Regex("^\\[.*?\\]\\s*\\[.*?\\]\\s*"), "").trim()
                                    if (text.isNotBlank()) qaManager.addQuestion(text)
                                }
                            }
                        }) {
                            Text(stringResource(Res.string.qa_import_from_file), color = MaterialTheme.colorScheme.onSurface)
                        }
                        OutlinedButton(
                            onClick = { qaManager.clearHistory() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.qa_delete_all_history))
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredQuestions, key = { it.id }) { question ->
                        QuestionRow(
                            question = question,
                            isDisplayed = displayedQuestion?.id == question.id,
                            isHistory = selectedFilter == 2,
                            onApprove = { qaManager.approveQuestion(question.id) },
                            onDeny = { qaManager.denyQuestion(question.id) },
                            onMarkDone = {
                                qaManager.markDone(question.id)
                                if (displayedQuestion?.id == question.id) {
                                    presenterManager.setDisplayedQuestion(null)
                                    presenting(Presenting.NONE)
                                }
                            },
                            onEdit = { newText ->
                                qaManager.editQuestion(question.id, newText)
                                val updated = qaManager.findQuestion(question.id)
                                if (updated != null && displayedQuestion?.id == question.id) {
                                    presenterManager.setDisplayedQuestion(updated)
                                }
                            },
                            onDisplay = {
                                qaManager.displayQuestion(question.id)
                                val current = qaManager.findQuestion(question.id) ?: question
                                presenterManager.setDisplayedQuestion(current)
                                presenterManager.setShowQRCodeOnDisplay(false)
                                presenting(Presenting.QA)
                            },
                            onDelete = {
                                qaManager.deleteQuestion(question.id)
                                if (displayedQuestion?.id == question.id) {
                                    presenterManager.setDisplayedQuestion(null)
                                    presenting(Presenting.NONE)
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        // ── Right Panel: QR Codes, Styling & Settings ────────────────
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (submissionUrl.isNotEmpty()) {
                Text(stringResource(Res.string.qa_submit_questions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                val submissionQR = remember(submissionUrl) { generateQRCodeBitmap(submissionUrl, 256) }
                if (submissionQR != null) {
                    Image(bitmap = submissionQR, contentDescription = stringResource(Res.string.qa_submit_questions), modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)))
                }
                Text(submissionUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        qaManager.toggleQRCodeDisplay()
                        presenterManager.setShowQRCodeOnDisplay(qaManager.showQRCodeOnDisplay)
                        if (qaManager.showQRCodeOnDisplay) { presenterManager.setDisplayedQuestion(null); presenting(Presenting.QA) }
                        else if (qaManager.displayedQuestion == null) { presenting(Presenting.NONE) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(if (showQROnDisplay) Res.string.qa_hide_qr else Res.string.qa_show_qr), fontSize = 12.sp)
                }

            } else {
                Text(stringResource(Res.string.qa_server_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 32.dp))
            }

            // ── Public Access (Tunnel) ──────────────────────────────
            if (serverUrl.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                Text(
                    "Public Access",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Let users submit questions via mobile data without joining your WiFi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                when (tunnelStatus) {
                    TunnelStatus.Idle -> {
                        Button(onClick = onStartTunnel, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Enable Public Access", fontSize = 12.sp)
                        }
                    }
                    TunnelStatus.Downloading -> {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.height(4.dp))
                            Text("Downloading tunnel client...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    TunnelStatus.Starting -> {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.height(4.dp))
                            Text("Starting tunnel...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    is TunnelStatus.Connected -> {
                        Button(
                            onClick = {
                                onStopTunnel()
                                onQaDisplayUrlChanged(serverUrl)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Disable Public Access", fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("QR Code shows:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            val isLocal = qaDisplayUrl.isEmpty() || qaDisplayUrl == serverUrl
                            OutlinedButton(
                                onClick = { onQaDisplayUrlChanged(serverUrl) },
                                modifier = Modifier.weight(1f),
                                colors = if (isLocal) ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("Local", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onQaDisplayUrlChanged(tunnelUrl) },
                                modifier = Modifier.weight(1f),
                                colors = if (!isLocal) ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("Public", fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            tunnelStatus.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF43A047),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is TunnelStatus.Error -> {
                        Text(
                            tunnelStatus.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Button(onClick = onStartTunnel, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Admin QR Code ──────────────────────────────────────
            if (adminDisplayUrl.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                Text(stringResource(Res.string.qa_admin_panel), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))

                if (tunnelStatus is TunnelStatus.Connected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { adminUseTunnel = false },
                            modifier = Modifier.weight(1f),
                            colors = if (!adminUseTunnel) ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("Local", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { adminUseTunnel = true },
                            modifier = Modifier.weight(1f),
                            colors = if (adminUseTunnel) ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("Public", fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val adminQR = remember(adminQrUrl) { generateQRCodeBitmap(adminQrUrl, 256) }
                if (adminQR != null) {
                    Image(bitmap = adminQR, contentDescription = stringResource(Res.string.qa_admin_panel), modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)))
                }
                Text(adminDisplayUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Text(stringResource(Res.string.qa_display_styling), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(color = qaSettings.textColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(textColor = it)) } })
                TextStyleButtons(
                    bold = qaSettings.bold, italic = qaSettings.italic, underline = qaSettings.underline, shadow = qaSettings.shadow,
                    onBoldChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(bold = it)) } },
                    onItalicChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(italic = it)) } },
                    onUnderlineChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(underline = it)) } },
                    onShadowChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(shadow = it)) } }
                )
            }

            AnimatedVisibility(visible = qaSettings.shadow) {
                ShadowDetailRow(
                    shadowColor = qaSettings.shadowColor, shadowSize = qaSettings.shadowSize, shadowOpacity = qaSettings.shadowOpacity,
                    onColorChange = { c -> onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(shadowColor = c)) } },
                    onSizeChange = { v -> onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(shadowSize = v)) } },
                    onOpacityChange = { v -> onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(shadowOpacity = v)) } },
                )
            }

            Spacer(Modifier.height(8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(modifier = Modifier.width(140.dp)) {
                    Text(stringResource(Res.string.qa_font), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    FontSettingsDropdown(value = qaSettings.fontType, fonts = availableFonts, onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(fontType = it)) } }, modifier = Modifier.fillMaxWidth())
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.qa_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    NumberSettingsTextField(initialText = qaSettings.fontSize, range = 8..200, onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(fontSize = it)) } })
                }
            }

            Spacer(Modifier.height(8.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(stringResource(Res.string.qa_background_color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                if (qaSettings.backgroundColor == "transparent") {
                    Button(onClick = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundColor = "#1E1E2E")) } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) { Text(stringResource(Res.string.qa_transparent), style = MaterialTheme.typography.labelMedium) }
                } else {
                    ColorPickerField(color = qaSettings.backgroundColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundColor = it)) } })
                    Button(onClick = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundColor = "transparent")) } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) { Text(stringResource(Res.string.qa_transparent), style = MaterialTheme.typography.labelMedium) }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.qa_position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            val positions = listOf(
                Constants.TOP_LEFT to "TL", Constants.TOP_CENTER to "TC", Constants.TOP_RIGHT to "TR",
                Constants.CENTER_LEFT to "CL", Constants.CENTER to "C", Constants.CENTER_RIGHT to "CR",
                Constants.BOTTOM_LEFT to "BL", Constants.BOTTOM_CENTER to "BC", Constants.BOTTOM_RIGHT to "BR"
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                positions.chunked(3).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        rowItems.forEach { (posConst, posLabel) ->
                            val isSelected = qaSettings.position == posConst
                            Box(
                                modifier = Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(3.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(position = posConst)) } },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(posLabel, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Text(stringResource(Res.string.qa_settings_section), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            Text(stringResource(Res.string.qa_cooldown_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            NumberSettingsTextField(
                initialText = qaSettings.rateLimitCooldownSeconds,
                range = 0..600,
                onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(rateLimitCooldownSeconds = it)) } }
            )

            Spacer(Modifier.height(12.dp))

            Text(stringResource(Res.string.qa_admin_password), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = qaSettings.adminPassword,
                onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(adminPassword = it)) } },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.qa_no_password), style = MaterialTheme.typography.bodySmall) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuestionRow(
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
    val bgColor = if (isDisplayed) Color(0xFF43A047).copy(alpha = 0.1f) else Color.Transparent
    val statusColor = when (question.status) {
        QuestionStatus.PENDING -> Color(0xFFFFA726)
        QuestionStatus.APPROVED -> Color(0xFF66BB6A)
        QuestionStatus.DENIED -> Color(0xFFEF5350)
        QuestionStatus.DONE -> Color(0xFF42A5F5)
    }
    val statusLabel = when (question.status) {
        QuestionStatus.DONE -> "Done"
        QuestionStatus.DENIED -> "Denied"
        else -> null
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var editing by remember { mutableStateOf(false) }
    var confirmGoLive by remember { mutableStateOf(false) }
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
                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(end = 8.dp)
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
                        tint = if (editing) Color(0xFF43A047) else Color(0xFFFF9800)
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
                                Icon(Icons.Default.Check, strApprove, tint = Color(0xFF43A047))
                            }
                            QAIconButton(tooltip = strDeny, onClick = onDeny) {
                                Icon(Icons.Default.Close, strDeny, tint = Color(0xFFE53935))
                            }
                        }
                        QuestionStatus.APPROVED -> {
                            if (!isDisplayed) {
                                QAIconButton(tooltip = strGoLive, onClick = onDisplay) {
                                    Icon(Icons.Default.Tv, strGoLive, tint = Color(0xFF1E88E5))
                                }
                            }
                            QAIconButton(tooltip = if (isDisplayed) strDoneClear else strMarkDone, onClick = onMarkDone) {
                                Icon(
                                    Icons.Default.Done,
                                    contentDescription = if (isDisplayed) strDoneClear else strMarkDone,
                                    tint = Color(0xFF42A5F5)
                                )
                            }
                            QAIconButton(tooltip = strDeny, onClick = onDeny) {
                                Icon(Icons.Default.Close, strDeny, tint = Color(0xFFE53935))
                            }
                        }
                        QuestionStatus.DONE -> {
                            QAIconButton(tooltip = strBackToIncoming, onClick = onApprove) {
                                Icon(Icons.Default.Refresh, strBackToIncoming, tint = Color(0xFFFFA726))
                            }
                            if (confirmGoLive) {
                                Text(stringResource(Res.string.qa_confirm_go_live_prompt), style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E88E5), modifier = Modifier.padding(end = 4.dp))
                                QAIconButton(tooltip = strConfirmGoLive, onClick = { confirmGoLive = false; onApprove(); onDisplay() }) {
                                    Icon(Icons.Default.Tv, strConfirmGoLive, tint = Color(0xFF1E88E5))
                                }
                                QAIconButton(tooltip = strCancel, onClick = { confirmGoLive = false }) {
                                    Icon(Icons.Default.Close, strCancel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                QAIconButton(tooltip = strGoLive, onClick = { confirmGoLive = true }) {
                                    Icon(Icons.Default.Tv, strGoLive, tint = Color(0xFF1E88E5))
                                }
                            }
                        }
                        QuestionStatus.DENIED -> {
                            if (confirmGoLive) {
                                Text(stringResource(Res.string.qa_confirm_go_live_prompt), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA726), modifier = Modifier.padding(end = 4.dp))
                                QAIconButton(tooltip = strConfirmGoLive, onClick = { confirmGoLive = false; onApprove(); onDisplay() }) {
                                    Icon(Icons.Default.Tv, strConfirmGoLive, tint = Color(0xFF1E88E5))
                                }
                                QAIconButton(tooltip = strCancel, onClick = { confirmGoLive = false }) {
                                    Icon(Icons.Default.Close, strCancel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                QAIconButton(tooltip = strGoLive, onClick = { confirmGoLive = true }) {
                                    Icon(Icons.Default.Tv, strGoLive, tint = Color(0xFF1E88E5).copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    QAIconButton(tooltip = strDelete, onClick = onDelete) {
                        Icon(Icons.Default.Delete, strDelete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Edit text field
        if (editing) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth().padding(start = 50.dp, top = 4.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = false,
                maxLines = 5,
                placeholder = { Text(stringResource(Res.string.qa_edit_question_hint)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QAIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp))
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            content()
        }
    }
}

@Composable
private fun StatBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

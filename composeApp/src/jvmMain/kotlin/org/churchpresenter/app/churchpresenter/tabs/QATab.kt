package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.Question
import org.churchpresenter.app.churchpresenter.models.QuestionStatus
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.generateQRCodeBitmap
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import java.awt.GraphicsEnvironment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QATab(
    modifier: Modifier = Modifier,
    qaManager: QAManager,
    presenterManager: PresenterManager,
    serverUrl: String,
    presenting: (Presenting) -> Unit,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
) {
    val sessionActive = qaManager.sessionActive
    val questions = qaManager.questions
    val displayedQuestion = qaManager.displayedQuestion
    val showQROnDisplay = qaManager.showQRCodeOnDisplay

    val submissionUrl = if (serverUrl.isNotEmpty()) "$serverUrl/qa" else ""
    val adminUrl = if (serverUrl.isNotEmpty()) "$serverUrl/qa/admin" else ""

    var selectedFilter by remember { mutableStateOf(0) } // 0=Incoming, 1=Done+Denied, 2=History

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

    Row(modifier = modifier.fillMaxSize()) {
        // ── Left Panel: Question List ────────────────────────────────
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            // Top bar: session + clear
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Session controls
                if (serverUrl.isEmpty()) {
                    Text(
                        "Server not running — start the companion server in Settings to enable Q&A",
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
                        Text("Stop Session")
                    }
                } else {
                    Button(
                        onClick = { qaManager.toggleSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Session")
                    }
                    if (qaManager.history.isNotEmpty()) {
                        OutlinedButton(onClick = { qaManager.restoreFromHistory() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume (${qaManager.history.size})")
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Stats
                StatBadge("Incoming", incomingCount, Color(0xFFFFA726))
                Spacer(Modifier.width(8.dp))
                StatBadge("Finished", finishedCount, Color(0xFF42A5F5))

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
                        Text("Clear All Questions")
                    }
                }
            }

            // Filter tabs
            TabRow(selectedTabIndex = selectedFilter) {
                Tab(
                    selected = selectedFilter == 0,
                    onClick = { selectedFilter = 0 },
                    text = { Text("Incoming ($incomingCount)") }
                )
                Tab(
                    selected = selectedFilter == 1,
                    onClick = { selectedFilter = 1 },
                    text = { Text("Finished ($finishedCount)") }
                )
                Tab(
                    selected = selectedFilter == 2,
                    onClick = { selectedFilter = 2 },
                    text = { Text("History ($historyCount)") }
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
                        "Displaying: ${displayedQuestion.text.take(50)}...",
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
                            presenting(Presenting.NONE)
                        }
                    ) {
                        Text("Clear Display")
                    }
                }
            }

            if (filteredQuestions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (selectedFilter) {
                            0 -> if (sessionActive) "Waiting for questions..." else "Start a session to receive questions"
                            1 -> "No finished questions"
                            2 -> "No history from previous sessions"
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
                                dialogTitle = "Export Questions"
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
                            Text("Export to File", color = MaterialTheme.colorScheme.onSurface)
                        }
                        OutlinedButton(onClick = {
                            val chooser = javax.swing.JFileChooser().apply {
                                dialogTitle = "Import Questions"
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
                            Text("Import from File", color = MaterialTheme.colorScheme.onSurface)
                        }
                        OutlinedButton(
                            onClick = { qaManager.clearHistory() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete All History")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
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
                // Submission QR
                Text("Submit Questions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                val submissionQR = remember(submissionUrl) { generateQRCodeBitmap(submissionUrl, 256) }
                if (submissionQR != null) {
                    Image(bitmap = submissionQR, contentDescription = "Submission QR Code", modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)))
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
                    Text(if (showQROnDisplay) "Hide QR from Display" else "Show QR on Display", fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))

                // Admin QR
                Text("Admin Panel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                val adminQR = remember(adminUrl) { generateQRCodeBitmap(adminUrl, 256) }
                if (adminQR != null) {
                    Image(bitmap = adminQR, contentDescription = "Admin QR Code", modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)))
                }
                Text(adminUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text("Start the companion server to enable Q&A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 32.dp))
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // ── Display Styling ──────────────────────────────────
            Text("Display Styling", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            // Color + style buttons
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

            // Font type + size
            Spacer(Modifier.height(8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(modifier = Modifier.width(140.dp)) {
                    Text("Font", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    FontSettingsDropdown(value = qaSettings.fontType, fonts = availableFonts, onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(fontType = it)) } }, modifier = Modifier.fillMaxWidth())
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    NumberSettingsTextField(initialText = qaSettings.fontSize, range = 8..200, onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(fontSize = it)) } })
                }
            }

            // Background color
            Spacer(Modifier.height(8.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text("Background Color", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                if (qaSettings.backgroundColor == "transparent") {
                    Button(onClick = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundColor = "#1E1E2E")) } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) { Text("Transparent", style = MaterialTheme.typography.labelMedium) }
                } else {
                    ColorPickerField(color = qaSettings.backgroundColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundColor = it)) } })
                    Button(onClick = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundColor = "transparent")) } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) { Text("Transparent", style = MaterialTheme.typography.labelMedium) }
                }
            }

            // Position on screen
            Spacer(Modifier.height(8.dp))
            Text("Position", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
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

            // ── Settings ─────────────────────────────────────────
            Text("Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            // Rate limit cooldown
            Text("Cooldown between questions (seconds)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            NumberSettingsTextField(
                initialText = qaSettings.rateLimitCooldownSeconds,
                range = 0..600,
                onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(rateLimitCooldownSeconds = it)) } }
            )

            Spacer(Modifier.height(12.dp))

            // Admin password (at the bottom)
            Text("Admin Password", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            OutlinedTextField(
                value = qaSettings.adminPassword,
                onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(adminPassword = it)) } },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                placeholder = { Text("No password", style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))

            // Time
            Text(
                text = timeFormat.format(Date(question.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(50.dp)
            )

            if (!editing) {
                // Submitter name
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
                // Status label for finished/history tab
                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                // Question text
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
                // Edit button
                IconButton(onClick = {
                    if (editing) {
                        if (editText.isNotBlank() && editText.trim() != question.text) {
                            onEdit(editText)
                        }
                        editing = false
                    } else {
                        editText = question.text
                        editing = true
                    }
                }) {
                    Icon(
                        imageVector = if (editing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (editing) "Save" else "Edit",
                        tint = if (editing) Color(0xFF43A047) else Color(0xFFFF9800)
                    )
                }

                if (editing) {
                    IconButton(onClick = {
                        editText = question.text
                        editing = false
                    }) {
                        Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Action buttons (hidden while editing)
                if (!editing) {
                    when (question.status) {
                        QuestionStatus.PENDING -> {
                            IconButton(onClick = onApprove) {
                                Icon(Icons.Default.Check, "Approve", tint = Color(0xFF43A047))
                            }
                            IconButton(onClick = onDeny) {
                                Icon(Icons.Default.Close, "Deny", tint = Color(0xFFE53935))
                            }
                        }
                        QuestionStatus.APPROVED -> {
                            if (!isDisplayed) {
                                IconButton(onClick = onDisplay) {
                                    Icon(Icons.Default.Tv, "Go Live", tint = Color(0xFF1E88E5))
                                }
                            }
                            IconButton(onClick = onMarkDone) {
                                Icon(
                                    Icons.Default.Done,
                                    contentDescription = if (isDisplayed) "Done & Clear" else "Mark Done",
                                    tint = Color(0xFF42A5F5)
                                )
                            }
                            IconButton(onClick = onDeny) {
                                Icon(Icons.Default.Close, "Deny", tint = Color(0xFFE53935))
                            }
                        }
                        QuestionStatus.DONE -> {
                            IconButton(onClick = onApprove) {
                                Icon(Icons.Default.Refresh, "Back to Incoming", tint = Color(0xFFFFA726))
                            }
                            if (confirmGoLive) {
                                Text("Go Live?", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E88E5), modifier = Modifier.padding(end = 4.dp))
                                IconButton(onClick = { confirmGoLive = false; onApprove(); onDisplay() }) {
                                    Icon(Icons.Default.Tv, "Confirm Go Live", tint = Color(0xFF1E88E5))
                                }
                                IconButton(onClick = { confirmGoLive = false }) {
                                    Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                IconButton(onClick = { confirmGoLive = true }) {
                                    Icon(Icons.Default.Tv, "Go Live", tint = Color(0xFF1E88E5))
                                }
                            }
                        }
                        QuestionStatus.DENIED -> {
                            if (confirmGoLive) {
                                Text("Go Live?", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA726), modifier = Modifier.padding(end = 4.dp))
                                IconButton(onClick = { confirmGoLive = false; onApprove(); onDisplay() }) {
                                    Icon(Icons.Default.Tv, "Confirm Go Live", tint = Color(0xFF1E88E5))
                                }
                                IconButton(onClick = { confirmGoLive = false }) {
                                    Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                IconButton(onClick = { confirmGoLive = true }) {
                                    Icon(Icons.Default.Tv, "Go Live", tint = Color(0xFF1E88E5).copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Edit text field
        if (editing) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 50.dp, top = 4.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = false,
                maxLines = 5,
                placeholder = { Text("Edit question text...") },
            )
        }
    }
}

@Composable
private fun StatBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

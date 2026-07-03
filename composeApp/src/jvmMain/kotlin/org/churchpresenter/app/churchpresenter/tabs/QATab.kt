package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Cursor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
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
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.qa_admin_panel
import churchpresenter.composeapp.generated.resources.qa_add_question_hint
import churchpresenter.composeapp.generated.resources.qa_admin_password
import churchpresenter.composeapp.generated.resources.qa_qr_message_default
import churchpresenter.composeapp.generated.resources.qa_qr_message_label
import churchpresenter.composeapp.generated.resources.qa_qr_message_reset
import churchpresenter.composeapp.generated.resources.qa_approve
import churchpresenter.composeapp.generated.resources.qa_back_to_incoming
import churchpresenter.composeapp.generated.resources.qa_background_color
import churchpresenter.composeapp.generated.resources.qa_text_color
import churchpresenter.composeapp.generated.resources.qa_qr_fg_color
import churchpresenter.composeapp.generated.resources.qa_qr_bg_color
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
import churchpresenter.composeapp.generated.resources.qa_font
import churchpresenter.composeapp.generated.resources.qa_hide_qr
import churchpresenter.composeapp.generated.resources.qa_history
import churchpresenter.composeapp.generated.resources.qa_import_dialog_title
import churchpresenter.composeapp.generated.resources.qa_import_from_file
import churchpresenter.composeapp.generated.resources.qa_incoming
import churchpresenter.composeapp.generated.resources.qa_mark_done
import churchpresenter.composeapp.generated.resources.qa_new_session
import churchpresenter.composeapp.generated.resources.qa_no_approved
import churchpresenter.composeapp.generated.resources.qa_no_denied
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
import churchpresenter.composeapp.generated.resources.qa_waiting
import churchpresenter.composeapp.generated.resources.qa_public_access
import churchpresenter.composeapp.generated.resources.qa_public_access_description
import churchpresenter.composeapp.generated.resources.qa_enable_public_access
import churchpresenter.composeapp.generated.resources.qa_disable_public_access
import churchpresenter.composeapp.generated.resources.qa_downloading_tunnel
import churchpresenter.composeapp.generated.resources.qa_starting_tunnel
import churchpresenter.composeapp.generated.resources.qa_qr_code_shows
import churchpresenter.composeapp.generated.resources.qa_local
import churchpresenter.composeapp.generated.resources.qa_public
import churchpresenter.composeapp.generated.resources.qa_retry
import churchpresenter.composeapp.generated.resources.qa_pos_tl
import churchpresenter.composeapp.generated.resources.qa_pos_tc
import churchpresenter.composeapp.generated.resources.qa_pos_tr
import churchpresenter.composeapp.generated.resources.qa_pos_cl
import churchpresenter.composeapp.generated.resources.qa_pos_c
import churchpresenter.composeapp.generated.resources.qa_pos_cr
import churchpresenter.composeapp.generated.resources.qa_pos_bl
import churchpresenter.composeapp.generated.resources.qa_pos_bc
import churchpresenter.composeapp.generated.resources.qa_pos_br
import churchpresenter.composeapp.generated.resources.save
import churchpresenter.composeapp.generated.resources.qa_confirm_delete_prompt
import churchpresenter.composeapp.generated.resources.qa_submitter_device
import churchpresenter.composeapp.generated.resources.tooltip_clear_display
import churchpresenter.composeapp.generated.resources.tooltip_edit
import churchpresenter.composeapp.generated.resources.qa_add
import churchpresenter.composeapp.generated.resources.qa_clear
import churchpresenter.composeapp.generated.resources.qa_clear_all_confirm_message
import churchpresenter.composeapp.generated.resources.qa_copy_url
import churchpresenter.composeapp.generated.resources.qa_export_clear
import churchpresenter.composeapp.generated.resources.qa_opacity
import churchpresenter.composeapp.generated.resources.qa_filter_label
import churchpresenter.composeapp.generated.resources.qa_sort_label
import churchpresenter.composeapp.generated.resources.qa_sort_least_votes
import churchpresenter.composeapp.generated.resources.qa_sort_most_votes
import churchpresenter.composeapp.generated.resources.qa_sort_newest
import churchpresenter.composeapp.generated.resources.qa_sort_oldest
import churchpresenter.composeapp.generated.resources.qa_voting_disabled
import churchpresenter.composeapp.generated.resources.qa_voting_enabled
import churchpresenter.composeapp.generated.resources.qa_all
import churchpresenter.composeapp.generated.resources.qa_approved
import churchpresenter.composeapp.generated.resources.qa_denied
import churchpresenter.composeapp.generated.resources.qa_done
import churchpresenter.composeapp.generated.resources.qa_incoming_approved
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
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

@OptIn(ExperimentalFoundationApi::class)
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
    val screenLocks by presenterManager.screenLocks
    val isQALocked = screenLocks.values.any { it == Presenting.QA }

    // Reset QA display state when display is cleared (e.g. via Escape or Clear Display)
    LaunchedEffect(presentingMode) {
        if (presentingMode == Presenting.NONE && !isQALocked && (showQROnDisplay || displayedQuestion != null)) {
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
    var sortMode by remember { mutableStateOf(0) } // 0=newest, 1=oldest, 2=most votes, 3=least votes
    var showClearConfirm by remember { mutableStateOf(false) }
    var addQuestionText by remember { mutableStateOf("") }

    val pendingCount = questions.count { it.status == QuestionStatus.PENDING }
    val approvedCount = questions.count { it.status == QuestionStatus.APPROVED }
    val incomingApprovedCount = questions.count { it.status == QuestionStatus.PENDING || it.status == QuestionStatus.APPROVED }
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
            3 -> sortedBy { voteCount(it) }
            else -> this
        }
    }

    val filteredQuestions = remember(questions.toList(), selectedFilter, qaManager.history.toList(), sortMode) {
        val sorted = { list: List<Question> -> list.applySortMode(sortMode, { it.timestamp }, { it.voteCount }) }
        when (selectedFilter) {
            0 -> sorted(questions.toList())
            1 -> sorted(questions.filter { it.status == QuestionStatus.PENDING })
            2 -> sorted(questions.filter { it.status == QuestionStatus.APPROVED })
            3 -> sorted(questions.filter { it.status == QuestionStatus.PENDING || it.status == QuestionStatus.APPROVED })
            4 -> sorted(questions.filter { it.status == QuestionStatus.DONE })
            5 -> sorted(questions.filter { it.status == QuestionStatus.DENIED })
            6 -> qaManager.history
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
    val strQrMessageDefault = stringResource(Res.string.qa_qr_message_default)

    val coroutineScope = rememberCoroutineScope()

    val density = LocalDensity.current
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)
    val windowState = LocalMainWindowState.current
    val isMaximized = windowState?.placement != WindowPlacement.Floating
    val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

    var rightPanelPx by remember(currentLayout.qaRightPanelWidthDp, isMaximized) {
        mutableStateOf(with(density) { currentLayout.qaRightPanelWidthDp.dp.toPx() })
    }

    fun saveRightPanel() {
        val dp = with(density) { rightPanelPx.toDp().value.toInt() }
        onSettingsChangeState.value { s ->
            if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(qaRightPanelWidthDp = dp))
            else s.copy(windowedLayout = s.windowedLayout.copy(qaRightPanelWidthDp = dp))
        }
    }

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
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (sessionActive) {
                    Button(
                        onClick = {
                            qaManager.toggleSession()
                            presenterManager.setDisplayedQuestion(null)
                            presenterManager.setShowQRCodeOnDisplay(false)
                            presenting(Presenting.NONE)
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
                StatBadge(stringResource(Res.string.qa_finished), doneCount + deniedCount, MaterialTheme.colorScheme.secondary)

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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownSelector(
                    label = stringResource(Res.string.qa_filter_label),
                    items = filterItemsWithCount,
                    selected = if (selectedFilter < 6) filterItemsWithCount[selectedFilter] else filterItemsWithCount[0],
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
                    Text("${stringResource(Res.string.qa_history)} ($historyCount)", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Add question input
            if (sessionActive) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
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
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (addQuestionText.isEmpty()) {
                                        Text(stringResource(Res.string.qa_add_question_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), maxLines = 1)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                        if (addQuestionText.isNotEmpty()) {
                            FilledIconButton(onClick = { addQuestionText = "" }, modifier = Modifier.size(30.dp), shape = RoundedCornerShape(5.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = null, modifier = Modifier.size(14.dp))
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
                            0 -> if (sessionActive) stringResource(Res.string.qa_waiting) else stringResource(Res.string.qa_start_session_hint)
                            1 -> if (sessionActive) stringResource(Res.string.qa_waiting) else stringResource(Res.string.qa_start_session_hint)
                            2 -> stringResource(Res.string.qa_no_approved)
                            3 -> if (sessionActive) stringResource(Res.string.qa_waiting) else stringResource(Res.string.qa_start_session_hint)
                            4 -> stringResource(Res.string.qa_no_finished)
                            5 -> stringResource(Res.string.qa_no_denied)
                            6 -> stringResource(Res.string.qa_no_history)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // History tab actions bar
                if (selectedFilter == 6 && filteredQuestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = {
                            coroutineScope.launch {
                                val path = FileChooser.platformInstance.save(
                                    location = null,
                                    suggestedName = "questions.txt",
                                    filters = listOf(javax.swing.filechooser.FileNameExtensionFilter("Text files", "txt")),
                                    title = strExportTitle
                                )
                                if (path != null) {
                                    val export = filteredQuestions.joinToString("\n") { q ->
                                        val status = q.status.name.lowercase().replaceFirstChar { it.uppercase() }
                                        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(q.timestamp))
                                        "[$time] [$status] ${q.text}"
                                    }
                                    try {
                                        withContext(Dispatchers.IO) { path.toFile().writeText(export) }
                                    } catch (e: Exception) {
                                        CrashReporter.reportException(e, context = "QATab.exportQuestions")
                                    }
                                }
                            }
                        },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(Res.string.qa_export_to_file), color = MaterialTheme.colorScheme.onSurface)
                        }
                        OutlinedButton(onClick = {
                            coroutineScope.launch {
                                val path = FileChooser.platformInstance.chooseSingle(
                                    path = null,
                                    filters = listOf(javax.swing.filechooser.FileNameExtensionFilter("Text files", "txt")),
                                    title = strImportTitle,
                                    selectDirectory = false
                                )
                                if (path != null) {
                                    val lines = try {
                                        withContext(Dispatchers.IO) { path.toFile().readLines() }
                                    } catch (e: Exception) {
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
                            Text(stringResource(Res.string.qa_import_from_file), color = MaterialTheme.colorScheme.onSurface)
                        }
                        OutlinedButton(
                            onClick = { qaManager.clearHistory() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp)
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
                            isHistory = selectedFilter == 6,
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
                        presenterManager.setDisplayedQuestion(null)
                        presenterManager.setShowQRCodeOnDisplay(false)
                        presenting(Presenting.NONE)
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
                                val path = FileChooser.platformInstance.save(
                                    location = null,
                                    suggestedName = "questions.txt",
                                    filters = listOf(javax.swing.filechooser.FileNameExtensionFilter("Text files", "txt")),
                                    title = strExportTitle
                                )
                                // Cancelling the save dialog aborts the clear — never delete unexported questions
                                if (path != null) {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            path.toFile().writeText(
                                                toExport.joinToString("\n") { q ->
                                                    "[${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(q.timestamp))}] [${q.status}] ${q.text}"
                                                }
                                            )
                                        }
                                    } catch (e: Exception) {
                                        CrashReporter.reportException(e, context = "QATab.exportAndClear")
                                        return@launch
                                    }
                                    qaManager.clearAll()
                                    presenterManager.setDisplayedQuestion(null)
                                    presenterManager.setShowQRCodeOnDisplay(false)
                                    presenting(Presenting.NONE)
                                }
                            }
                        }) { Text(stringResource(Res.string.qa_export_clear)) }
                        TextButton(shape = RoundedCornerShape(6.dp), onClick = { showClearConfirm = false }) { Text(stringResource(Res.string.cancel)) }
                    }
                }
            )
        }

        // Draggable separator
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        rightPanelPx = (rightPanelPx - delta).coerceAtLeast(with(density) { 160.dp.toPx() })
                    },
                    onDragStopped = { saveRightPanel() }
                )
        )

        // ── Right Panel: QR Codes, Styling & Settings ────────────────
        Column(
            modifier = Modifier
                .width(with(density) { rightPanelPx.toDp() })
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
                SelectionContainer {
                    Text(submissionUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
                OutlinedButton(
                    onClick = { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(submissionUrl), null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(Res.string.qa_copy_url), fontSize = 11.sp)
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (qaManager.showQRCodeOnDisplay && isQALocked) return@OutlinedButton
                        qaManager.toggleQRCodeDisplay()
                        presenterManager.setShowQRCodeOnDisplay(qaManager.showQRCodeOnDisplay)
                        if (qaManager.showQRCodeOnDisplay) { presenterManager.setDisplayedQuestion(null); presenting(Presenting.QA) }
                        else if (qaManager.displayedQuestion == null) { presenting(Presenting.NONE) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
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
                    stringResource(Res.string.qa_public_access),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.qa_public_access_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                when (tunnelStatus) {
                    TunnelStatus.Idle -> {
                        Button(onClick = onStartTunnel, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.qa_enable_public_access), fontSize = 12.sp)
                        }
                    }
                    TunnelStatus.Downloading -> {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(Res.string.qa_downloading_tunnel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    TunnelStatus.Starting -> {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(Res.string.qa_starting_tunnel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    is TunnelStatus.Connected -> {
                        Button(
                            onClick = {
                                onStopTunnel()
                                onQaDisplayUrlChanged(serverUrl)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.qa_disable_public_access), fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.qa_qr_code_shows), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            val isLocal = qaDisplayUrl.isEmpty() || qaDisplayUrl == serverUrl
                            OutlinedButton(
                                onClick = { onQaDisplayUrlChanged(serverUrl) },
                                modifier = Modifier.weight(1f),
                                colors = if (isLocal) ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) else ButtonDefaults.outlinedButtonColors(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(Res.string.qa_local), fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onQaDisplayUrlChanged(tunnelUrl) },
                                modifier = Modifier.weight(1f),
                                colors = if (!isLocal) ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) else ButtonDefaults.outlinedButtonColors(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(Res.string.qa_public), fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                tunnelStatus.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    is TunnelStatus.Error -> {
                        Text(
                            tunnelStatus.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Button(onClick = onStartTunnel, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.qa_retry), fontSize = 12.sp)
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
                            ) else ButtonDefaults.outlinedButtonColors(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(Res.string.qa_local), fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { adminUseTunnel = true },
                            modifier = Modifier.weight(1f),
                            colors = if (adminUseTunnel) ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) else ButtonDefaults.outlinedButtonColors(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(Res.string.qa_public), fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val adminQR = remember(adminQrUrl) { generateQRCodeBitmap(adminQrUrl, 256) }
                if (adminQR != null) {
                    Image(bitmap = adminQR, contentDescription = stringResource(Res.string.qa_admin_panel), modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)))
                }
                SelectionContainer {
                    Text(adminDisplayUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
                OutlinedButton(
                    onClick = { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(adminQrUrl.ifEmpty { adminDisplayUrl }), null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(Res.string.qa_copy_url), fontSize = 11.sp)
                }
            }

            // ── Voting Toggle ─────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(votingEnabled = !s.qaSettings.votingEnabled)) } },
                modifier = Modifier.fillMaxWidth(),
                colors = if (qaSettings.votingEnabled) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                ) else ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (qaSettings.votingEnabled) stringResource(Res.string.qa_voting_enabled) else stringResource(Res.string.qa_voting_disabled))
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Text(stringResource(Res.string.qa_display_styling), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            Text(stringResource(Res.string.qa_qr_message_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    BasicTextField(
                        value = qaSettings.qrCodeMessage,
                        onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrCodeMessage = it)) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (qaSettings.qrCodeMessage.isEmpty()) {
                                Text(strQrMessageDefault, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), maxLines = 1)
                            }
                            innerTextField()
                        }
                    )
                }
                FilledIconButton(
                    onClick = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrCodeMessage = "")) } },
                    modifier = Modifier.size(30.dp),
                    shape = RoundedCornerShape(5.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.qa_qr_message_reset), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(label = stringResource(Res.string.qa_qr_fg_color), color = qaSettings.qrForegroundColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrForegroundColor = it)) } }, modifier = Modifier.weight(1f))
                ColorPickerField(label = stringResource(Res.string.qa_qr_bg_color), color = qaSettings.qrBackgroundColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrBackgroundColor = it)) } }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.qa_opacity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(4.dp))
                androidx.compose.material3.Slider(
                    value = qaSettings.qrBackgroundOpacity / 100f,
                    onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrBackgroundOpacity = (it * 100).toInt())) } },
                    modifier = Modifier.weight(1f)
                )
                Text("${qaSettings.qrBackgroundOpacity}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(label = stringResource(Res.string.qa_text_color), color = qaSettings.textColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(textColor = it)) } }, modifier = Modifier.weight(1f))
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FontSettingsDropdown(label = stringResource(Res.string.qa_font), value = qaSettings.fontType, fonts = availableFonts, onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(fontType = it)) } }, modifier = Modifier.weight(1f))
                NumberSettingsTextField(label = stringResource(Res.string.qa_size), initialText = qaSettings.fontSize, range = 8..200, onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(fontSize = it)) } })
            }

            Spacer(Modifier.height(8.dp))
            Column(horizontalAlignment = Alignment.Start) {
                ColorPickerField(label = stringResource(Res.string.qa_background_color), color = qaSettings.backgroundColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundColor = it)) } }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.qa_opacity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    androidx.compose.material3.Slider(
                        value = qaSettings.backgroundOpacity / 100f,
                        onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundOpacity = (it * 100).toInt())) } },
                        modifier = Modifier.weight(1f)
                    )
                    Text("${qaSettings.backgroundOpacity}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.qa_position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            val positions = listOf(
                Constants.TOP_LEFT to stringResource(Res.string.qa_pos_tl),
                Constants.TOP_CENTER to stringResource(Res.string.qa_pos_tc),
                Constants.TOP_RIGHT to stringResource(Res.string.qa_pos_tr),
                Constants.CENTER_LEFT to stringResource(Res.string.qa_pos_cl),
                Constants.CENTER to stringResource(Res.string.qa_pos_c),
                Constants.CENTER_RIGHT to stringResource(Res.string.qa_pos_cr),
                Constants.BOTTOM_LEFT to stringResource(Res.string.qa_pos_bl),
                Constants.BOTTOM_CENTER to stringResource(Res.string.qa_pos_bc),
                Constants.BOTTOM_RIGHT to stringResource(Res.string.qa_pos_br),
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

            NumberSettingsTextField(
                label = stringResource(Res.string.qa_cooldown_label),
                initialText = qaSettings.rateLimitCooldownSeconds,
                range = 0..600,
                onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(rateLimitCooldownSeconds = it)) } },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            var passwordVisible by remember { mutableStateOf(false) }
            Text(stringResource(Res.string.qa_admin_password), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    BasicTextField(
                        value = qaSettings.adminPassword,
                        onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(adminPassword = it)) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (qaSettings.adminPassword.isEmpty()) {
                                Text(stringResource(Res.string.qa_no_password), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), maxLines = 1)
                            }
                            innerTextField()
                        }
                    )
                }
                FilledIconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(30.dp), shape = RoundedCornerShape(5.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    text = "\u25B2 ${question.upvotes}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (question.downvotes > 0) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.errorContainer) {
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
                                        Text(
                                            text = stringResource(Res.string.qa_submitter_device, question.submitterDeviceId),
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        } else {
                            Box {}
                        }
                    },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomStart, offset = DpOffset(0.dp, 4.dp)),
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
                        tint = if (editing) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.tertiary
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
                                QAIconButton(tooltip = strGoLive, onClick = onDisplay) {
                                    Icon(Icons.Default.Tv, strGoLive, tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            QAIconButton(tooltip = if (isDisplayed) strDoneClear else strMarkDone, onClick = onMarkDone) {
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
                                Icon(Icons.Default.Refresh, strBackToIncoming, tint = MaterialTheme.colorScheme.tertiary)
                            }
                            if (confirmGoLive) {
                                Text(stringResource(Res.string.qa_confirm_go_live_prompt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = 4.dp))
                                QAIconButton(tooltip = strConfirmGoLive, onClick = { confirmGoLive = false; onApprove(); onDisplay() }) {
                                    Icon(Icons.Default.Tv, strConfirmGoLive, tint = MaterialTheme.colorScheme.secondary)
                                }
                                QAIconButton(tooltip = strCancel, onClick = { confirmGoLive = false }) {
                                    Icon(Icons.Default.Close, strCancel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                QAIconButton(tooltip = strGoLive, onClick = { confirmGoLive = true }) {
                                    Icon(Icons.Default.Tv, strGoLive, tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                        QuestionStatus.DENIED -> {
                            QAIconButton(tooltip = strApprove, onClick = onApprove) {
                                Icon(Icons.Default.Check, strApprove, tint = MaterialTheme.colorScheme.inverseSurface)
                            }
                            if (confirmGoLive) {
                                Text(stringResource(Res.string.qa_confirm_go_live_prompt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(end = 4.dp))
                                QAIconButton(tooltip = strConfirmGoLive, onClick = { confirmGoLive = false; onApprove(); onDisplay() }) {
                                    Icon(Icons.Default.Tv, strConfirmGoLive, tint = MaterialTheme.colorScheme.secondary)
                                }
                                QAIconButton(tooltip = strCancel, onClick = { confirmGoLive = false }) {
                                    Icon(Icons.Default.Close, strCancel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                QAIconButton(tooltip = strGoLive, onClick = { confirmGoLive = true }) {
                                    Icon(Icons.Default.Tv, strGoLive, tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    if (confirmDelete) {
                        Text(strConfirmDelete, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 4.dp))
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
                            Text(stringResource(Res.string.qa_edit_question_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
    modifier: Modifier = Modifier.size(30.dp),
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
        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
    ) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = RoundedCornerShape(5.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
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
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

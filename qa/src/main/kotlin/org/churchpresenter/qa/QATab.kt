package org.churchpresenter.qa

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.qa_export_dialog_title
import org.churchpresenter.strings.generated.resources.qa_import_dialog_title
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.sharedui.models.Presenting
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.bibleListCard
import kotlinx.coroutines.CoroutineScope

private const val SORT_BY_VOTES = 3
internal const val DISPLAY_PREVIEW_CHARS = 50
internal const val FILTER_QUEUED = 3
internal const val FILTER_FINISHED = 4
internal const val FILTER_DENIED = 5
internal const val FILTER_HISTORY = 6

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QATab(
    modifier: Modifier = Modifier,
    qaManager: QAManager,
    output: QAOutput,
    serverUrl: String,
    presenting: (Presenting) -> Unit,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    /**
     * The remote-access dialog: the join link, the display page and the tunnel. Drawn by the app,
     * which owns the server and the tunnel; [onDismiss] closes it.
     */
    remoteDialog: @Composable (onDismiss: () -> Unit) -> Unit = {},
) {
    val ui = remember { QATabUi() }
    val coroutineScope = rememberCoroutineScope()
    // Hoisted: used inside non-composable lambdas
    val strExportTitle = stringResource(Res.string.qa_export_dialog_title)
    val strImportTitle = stringResource(Res.string.qa_import_dialog_title)
    // Remembered, so the handlers the pieces build around it stay the same across recompositions.
    val tab = remember(
        qaManager, output, serverUrl, presenting, appSettings, onSettingsChange,
        coroutineScope, strExportTitle, strImportTitle,
    ) {
        QATabScope(
            qaManager = qaManager,
            output = output,
            serverUrl = serverUrl,
            presenting = presenting,
            files = QAFileDialogs(coroutineScope, strExportTitle, strImportTitle),
            ui = ui,
            appSettings = appSettings,
            onSettingsChange = onSettingsChange,
        )
    }
    val presentingMode by output.presentingMode
    val isQALocked = tab.isQALocked

    // Reset QA display state when display is cleared (e.g. via Escape or Clear Display)
    val hasQAContentUp = tab.showQROnDisplay || tab.displayedQuestion != null
    LaunchedEffect(presentingMode) {
        if (presentingMode == Presenting.NONE && !isQALocked && hasQAContentUp) {
            qaManager.clearDisplay()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Question List ────────────────────────────────
        // Every control sits in the same card as the questions they act on.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
                .bibleListCard()
        ) {
            tab.TopBar()
            tab.FilterBar()
            if (tab.sessionActive) tab.AddQuestionField()
            tab.displayedQuestion?.let { tab.DisplayingBar(it) }
            val filtered = tab.filteredQuestions
            if (filtered.isEmpty()) {
                tab.EmptyNotice()
            } else {
                if (ui.selectedFilter == FILTER_HISTORY) tab.HistoryActions(filtered)
                tab.QuestionList(filtered)
            }
        }

        // ── Clear All Confirmation Dialog ─────────────────────────────
        if (ui.showClearConfirm) tab.ClearAllDialog()
    }

    if (ui.showRemoteDialog) {
        remoteDialog { ui.showRemoteDialog = false }
    }
}

/** What the tab itself remembers: which questions are listed, and which of its prompts is open. */
internal class QATabUi {
    var selectedFilter by mutableStateOf(0)

    /** 0=newest, 1=oldest, 2=most votes, 3=least votes. */
    var sortMode by mutableStateOf(0)
    var showClearConfirm by mutableStateOf(false)
    var addQuestionText by mutableStateOf("")
    var showRemoteDialog by mutableStateOf(false)
}

/** Where the tab's export and import run, and the titles of their file dialogs. */
internal class QAFileDialogs(
    val coroutineScope: CoroutineScope,
    val exportTitle: String,
    val importTitle: String,
)

/**
 * Everything the pieces of the tab read and act on, under the names the tab used for them.
 *
 * Stable: what it exposes is snapshot state or an input it is rebuilt for, so a piece handed the
 * same scope can skip.
 */
@Stable
internal class QATabScope(
    val qaManager: QAManager,
    val output: QAOutput,
    val serverUrl: String,
    val presenting: (Presenting) -> Unit,
    private val files: QAFileDialogs,
    private val ui: QATabUi,
    val appSettings: AppSettings = AppSettings(),
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
) {
    val coroutineScope get() = files.coroutineScope
    val strExportTitle get() = files.exportTitle
    val strImportTitle get() = files.importTitle

    var selectedFilter by ui::selectedFilter
    var sortMode by ui::sortMode
    var showClearConfirm by ui::showClearConfirm
    var addQuestionText by ui::addQuestionText
    var showRemoteDialog by ui::showRemoteDialog

    val sessionActive get() = qaManager.sessionActive
    val questions get() = qaManager.questions
    val displayedQuestion get() = qaManager.displayedQuestion
    val showQROnDisplay get() = qaManager.showQRCodeOnDisplay
    val isServerRunning get() = serverUrl.isNotEmpty()
    val qaSettings get() = appSettings.qaSettings
    val isQALocked get() = output.screenLocks.value.values.any { it == Presenting.QA }

    /** The questions the current filter and sort show; filter 6 is the history, unsorted. */
    val filteredQuestions: List<Question>
        get() {
            val sorted = { list: List<Question> -> list.applySortMode(sortMode, { it.timestamp }, { it.voteCount }) }
            return when (selectedFilter) {
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
}

private fun <T> List<T>.applySortMode(sortMode: Int, timestamp: (T) -> Long, voteCount: (T) -> Int): List<T> {
    return when (sortMode) {
        0 -> sortedByDescending { timestamp(it) }
        1 -> sortedBy { timestamp(it) }
        2 -> sortedByDescending { voteCount(it) }
        SORT_BY_VOTES -> sortedBy { voteCount(it) }
        else -> this
    }
}

package org.churchpresenter.bibletab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.book
import org.churchpresenter.strings.generated.resources.chapter
import org.churchpresenter.strings.generated.resources.scope
import org.churchpresenter.strings.generated.resources.verse
import java.awt.Window as AwtWindow
import org.churchpresenter.sharedui.composables.focusRescuePressHook
import org.churchpresenter.sharedui.composables.rememberFocusLostRescue
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.stt.STTManager
import org.jetbrains.compose.resources.stringResource


internal val CROSS_REF_MIN_WIDTH = 200.dp

internal val CROSS_REF_MAX_WIDTH = 500.dp

internal fun withBibleColumnWidths(
    settings: AppSettings,
    isMaximized: Boolean,
    bookWidthDp: Int,
    chapterWidthDp: Int,
): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(
        bibleColWidthBook = bookWidthDp,
        bibleColWidthChapter = chapterWidthDp,
    ))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(
        bibleColWidthBook = bookWidthDp,
        bibleColWidthChapter = chapterWidthDp,
    ))

internal fun withBibleSplitPanelWidth(settings: AppSettings, isMaximized: Boolean, widthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(splitLivePanelWidth = widthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(splitLivePanelWidth = widthDp))

internal fun withBibleCrossRefPanelWidth(settings: AppSettings, isMaximized: Boolean, widthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(bibleColWidthCrossRef = widthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(bibleColWidthCrossRef = widthDp))

internal fun withBibleCrossReferencePanel(settings: AppSettings, docked: Boolean): AppSettings =
    settings.copy(bibleSettings = settings.bibleSettings.copy(crossReferencesPanel = docked))

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun BibleTab(
    modifier: Modifier = Modifier,

    hostWindow: AwtWindow? = null,
    viewModel: BibleViewModel,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((
        bookName: String,
        chapter: Int,
        verseNumber: Int,
        verseText: String,
        verseRange: String,
        bookId: Int,
    ) -> Unit)? = null,
    selectedVerseItem: ScheduleItem.BibleVerseItem? = null,
    /**
     * Bumped by the caller on every schedule click, so clicking the *same* item twice re-runs the
     * selection. Without it the effect below is keyed on an unchanged item and the second click
     * does nothing at all — which is what made a failed first click unrecoverable.
     */
    selectedVerseItemVersion: Int = 0,
    onVerseSelected: (List<SelectedVerse>) -> Unit = {},

    onInstanceLinkSendVerse: ((
        bookName: String,
        chapter: Int,
        verseNumber: Int,
        verseText: String,
        verseRange: String,
    ) -> Unit)? = null,

    onInstanceLinkSendBibleHold: ((hold: Boolean) -> Unit)? = null,
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    isPresenting: Boolean = false,
    bibleOutput: BibleOutput? = null,
    verseStatistics: BibleVerseStatistics? = null,
    /** A verse has just gone live from this tab; the app records its usage telemetry here. */
    onVerseWentLive: () -> Unit = {},

    verseSequenceLog: VerseSequenceLog? = null,

    /** The cross references the panel and chips read; the app's is `sharedCrossReferences`. */
    crossReferences: CrossReferenceRepository,
    sttManager: STTManager? = null,
    engineStatus: BibleEngineStatus? = null,
    dialogDismissSignal: Int = 0,
) {
    val focusRequester = remember { FocusRequester() }
    val crossRefs = rememberBibleTabCrossRefs(viewModel, appSettings, crossReferences, verseSequenceLog)
    val live = remember { BibleLiveNavState() }
    val ui = remember { BibleTabUiState() }
    val fallbackDisplayedVerses = remember { mutableStateOf<List<SelectedVerse>>(emptyList()) }
    val widths = rememberBibleColumnWidths(appSettings, onSettingsChange)
    val displayedVersesState = bibleOutput?.displayedVerses ?: fallbackDisplayedVerses
    val currentIsPresentingState = rememberUpdatedState(isPresenting)
    val scope = rememberCoroutineScope()
    val shortcuts = LocalShortcuts.current
    // Remembered, not rebuilt on every recomposition: the verse, book and history rows key their
    // click detectors on the lambdas built from it, and a new scope on each selection would give
    // them new lambdas between the two clicks of a double-click.
    val states = remember(viewModel) { viewModel.tabStates() }
    // Read through state rather than keyed on, for the same reason: the app's hook is a new lambda
    // on each recomposition.
    val onVerseWentLiveState = rememberUpdatedState(onVerseWentLive)
    val tab = remember(
        states, appSettings, onSettingsChange, onAddToSchedule, onVerseSelected, onInstanceLinkSendVerse,
        onInstanceLinkSendBibleHold, onPresenting, bibleOutput, verseStatistics, verseSequenceLog, sttManager,
        engineStatus, focusRequester, crossRefs, live, ui, widths, displayedVersesState, currentIsPresentingState,
        scope, shortcuts
    ) {
        BibleTabScope(
            states = states,
            appSettings = appSettings,
            onSettingsChange = onSettingsChange,
            onAddToSchedule = onAddToSchedule,
            onVerseSelected = onVerseSelected,
            onInstanceLinkSendVerse = onInstanceLinkSendVerse,
            onInstanceLinkSendBibleHold = onInstanceLinkSendBibleHold,
            onPresenting = onPresenting,
            bibleOutput = bibleOutput,
            verseStatistics = verseStatistics,
            onVerseWentLive = { onVerseWentLiveState.value() },
            verseSequenceLog = verseSequenceLog,
            sttManager = sttManager,
            engineStatus = engineStatus,
            focusRequester = focusRequester,
            crossRefs = crossRefs,
            live = live,
            ui = ui,
            widths = widths,
            displayedVersesState = displayedVersesState,
            currentIsPresentingState = currentIsPresentingState,
            scope = scope,
            shortcuts = shortcuts,
        )
    }
    tab.BibleTabEffects(viewModel, selectedVerseItem, selectedVerseItemVersion, dialogDismissSignal)
    tab.BibleSplitLiveEffects(viewModel)
    tab.BibleSelectionEffects(viewModel)

    val focusRescue = rememberFocusLostRescue(hostWindow, focusRequester)
    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onFocusChanged { focusRescue.onFocusChanged(it.hasFocus) }
            .focusRescuePressHook(focusRescue)
            .focusable()
            .onPreviewKeyEvent { tab.handleKeyEvent(viewModel, it) }
    ) {
        BibleTabContent(viewModel, tab, focusRescue)
    }
}

/** The cross-reference state for the verse the tab has selected. */
@Composable
private fun rememberBibleTabCrossRefs(
    viewModel: BibleViewModel,
    appSettings: AppSettings,
    crossReferences: CrossReferenceRepository,
    verseSequenceLog: VerseSequenceLog?,
): BibleCrossReferenceState {
    val selectedBookIndex by viewModel.selectedBookIndex
    val selectedChapter by viewModel.selectedChapter
    val selectedVerseIndex by viewModel.selectedVerseIndex
    val verses by viewModel.verses
    val verseSelectionToken by viewModel.verseSelectionToken
    val crossRefsAvailable = appSettings.bibleSettings.crossReferencesEnabled
    val crossRefsDocked = crossRefsAvailable && appSettings.bibleSettings.crossReferencesPanel
    val crossRefRepository = crossReferences

    val fallbackAbbreviationResources =
        BibleBookAbbreviations.abbreviationResourceIds.map { stringResource(it) }
    val fallbackAbbreviations = remember(fallbackAbbreviationResources) {
        fallbackAbbreviationResources.map { BibleBookAbbreviations.parseVariants(it).firstOrNull().orEmpty() }
    }

    val loadedModule = viewModel.primaryBible.value

    return rememberBibleCrossReferenceState(
        available = crossRefsAvailable,
        panelDocked = crossRefsDocked,
        repository = crossRefRepository,
        fallbackAbbreviations = fallbackAbbreviations,
        selectedBookIndex = selectedBookIndex,
        selectedChapter = selectedChapter,
        selectedVerseIndex = selectedVerseIndex,
        verses = verses,
        verseSelectionToken = verseSelectionToken,
        loadedModule = loadedModule,
        moduleRefFor = viewModel::moduleRefFor,
        canonicalRefForDisplay = viewModel::canonicalRefForDisplay,
        selectedVerseNumbers = viewModel::getSelectedVerseNumbers,
        successors = { book, chapter, verse ->
            verseSequenceLog?.successors(book, chapter, verse).orEmpty()
        },
    )
}

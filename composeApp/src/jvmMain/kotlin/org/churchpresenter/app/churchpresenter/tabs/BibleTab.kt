package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_cross_references_count
import churchpresenter.composeapp.generated.resources.bible_cross_references_popover_title
import churchpresenter.composeapp.generated.resources.bible_no_primary_hint
import churchpresenter.composeapp.generated.resources.bible_no_primary_step1
import churchpresenter.composeapp.generated.resources.bible_no_primary_step2
import churchpresenter.composeapp.generated.resources.bible_no_primary_title
import churchpresenter.composeapp.generated.resources.bible_smart_search_hint
import churchpresenter.composeapp.generated.resources.book
import churchpresenter.composeapp.generated.resources.chapter
import churchpresenter.composeapp.generated.resources.contains_phrase
import churchpresenter.composeapp.generated.resources.current_book
import churchpresenter.composeapp.generated.resources.entire_bible
import churchpresenter.composeapp.generated.resources.exact_match
import churchpresenter.composeapp.generated.resources.no_results_found
import churchpresenter.composeapp.generated.resources.scope
import churchpresenter.composeapp.generated.resources.tab_focus_lost
import churchpresenter.composeapp.generated.resources.verse
import java.awt.Window as AwtWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.composables.FocusLostBanner
import org.churchpresenter.app.churchpresenter.composables.focusRescuePressHook
import org.churchpresenter.app.churchpresenter.composables.rememberFocusLostRescue
import org.churchpresenter.app.churchpresenter.composables.rememberTokenGate
import org.churchpresenter.app.churchpresenter.data.BibleBookAbbreviations
import org.churchpresenter.app.churchpresenter.data.CrossReferenceRepository
import org.churchpresenter.app.churchpresenter.data.formatCrossRefLabel
import org.churchpresenter.app.churchpresenter.data.sharedCrossReferences
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseSequenceLog
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.moveBibleTranslation
import org.churchpresenter.app.churchpresenter.data.settings.swapBibleTranslations
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.utils.isMultiTranslationPresentation
import org.churchpresenter.app.churchpresenter.utils.isSplitScreenBible
import org.churchpresenter.app.churchpresenter.viewmodel.BibleEngineClient
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.viewmodel.bibleSttStatus
import org.churchpresenter.app.churchpresenter.viewmodel.filteredSelectionIndices
import org.churchpresenter.app.churchpresenter.viewmodel.formatVerseReference
import org.churchpresenter.app.churchpresenter.viewmodel.nextLiveVerseNumber
import org.churchpresenter.app.churchpresenter.viewmodel.verseNumberOf
import org.churchpresenter.app.churchpresenter.viewmodel.verseSpan
import org.churchpresenter.app.churchpresenter.viewmodel.verseTextOf
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.viewmodel.applyDetectedReference
import org.churchpresenter.app.churchpresenter.viewmodel.canonicalRefForBookName
import org.churchpresenter.app.churchpresenter.viewmodel.canonicalRefForDisplay
import org.churchpresenter.app.churchpresenter.viewmodel.clearDetectedReferences
import org.churchpresenter.app.churchpresenter.viewmodel.displayIndexForBookName
import org.churchpresenter.app.churchpresenter.viewmodel.getSelectedVerses
import org.churchpresenter.app.churchpresenter.viewmodel.logGoLiveCorrection
import org.churchpresenter.app.churchpresenter.viewmodel.logLiveReference
import org.churchpresenter.app.churchpresenter.viewmodel.logOperatorFlag
import org.churchpresenter.app.churchpresenter.viewmodel.onSmartQueryChanged
import org.churchpresenter.app.churchpresenter.viewmodel.selectSearchResult
import org.churchpresenter.app.churchpresenter.viewmodel.submitSmartQuery
import org.churchpresenter.app.churchpresenter.viewmodel.updateSettings

internal val CROSS_REF_MIN_WIDTH = 200.dp

internal val CROSS_REF_MAX_WIDTH = 500.dp

internal fun withBibleColumnWidths(settings: AppSettings,
    isMaximized: Boolean,
    bookWidthDp: Int,
    chapterWidthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(bibleColWidthBook = bookWidthDp, bibleColWidthChapter = chapterWidthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(bibleColWidthBook = bookWidthDp, bibleColWidthChapter = chapterWidthDp))

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
    onAddToSchedule: ((bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String, bookId: Int) -> Unit)? = null,
    selectedVerseItem: ScheduleItem.BibleVerseItem? = null,
    onVerseSelected: (List<SelectedVerse>) -> Unit = {},

    onInstanceLinkSendVerse: ((bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String) -> Unit)? = null,

    onInstanceLinkSendBibleHold: ((hold: Boolean) -> Unit)? = null,
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    isPresenting: Boolean = false,
    presenterManager: PresenterManager? = null,
    statisticsManager: StatisticsManager? = null,

    verseSequenceLog: VerseSequenceLog? = null,

    crossReferences: CrossReferenceRepository? = null,
    sttManager: STTManager? = null,
    bibleEngineClient: BibleEngineClient? = null,
    dialogDismissSignal: Int = 0,
) {

    val isFirstComposition = remember { mutableStateOf(true) }
    val translationSelectionKey = appSettings.bibleSettings.translationSelectionKey()
    LaunchedEffect(
        appSettings.bibleSettings.storageDirectory,
        translationSelectionKey,
    ) {
        if (isFirstComposition.value) {
            isFirstComposition.value = false
        } else {
            viewModel.updateSettings(appSettings)
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedVerseItem) {
        selectedVerseItem?.let { item ->
            if (!viewModel.isFullyLoadedFlow.value) {
                viewModel.isFullyLoadedFlow.first { it }
            }
            val found = viewModel.selectVerseByDetails(item.bookName,
                item.chapter,
                item.verseNumber,
                item.verseRange,
                bookId = item.bookId)
            if (found) {
                focusRequester.requestFocus()
            }
        }
    }

    val sttConnected = sttManager?.connected?.value == true
    val engineSettings = appSettings.bibleEngineSettings
    val detectedReferences by viewModel.detectedReferences
    val autoFollowEnabled by viewModel.autoFollowEnabled
    val textMatchLevel by viewModel.textMatchLevel
    val continuationSpeed by viewModel.continuationSpeed

    val books by viewModel.books
    val loadErrors by viewModel.loadErrors
    val selectedBookIndex by viewModel.selectedBookIndex
    val selectedChapter by viewModel.selectedChapter
    val selectedVerseIndex by viewModel.selectedVerseIndex
    val verses by viewModel.verses
    val searchQuery by viewModel.searchQuery
    val searchResults by viewModel.searchResults
    val isSearchMode by viewModel.isSearchMode
    val searchMode by viewModel.searchMode
    val filteredBooks by viewModel.filteredBooks
    val filteredChapters by viewModel.filteredChapters
    val filteredVerses by viewModel.filteredVerses

    val scopeOptions = listOf(
        stringResource(Res.string.entire_bible),
        stringResource(Res.string.current_book),
    )
    val selectedScopeIndex by viewModel.selectedScopeIndex
    val selectedScope = scopeOptions.getOrElse(selectedScopeIndex) { scopeOptions.first() }

    val modeOptions = listOf(
        stringResource(Res.string.contains_phrase),
        stringResource(Res.string.exact_match),
    )
    val selectedModeIndex by viewModel.selectedModeIndex
    val selectedMode = modeOptions.getOrElse(selectedModeIndex) { modeOptions.first() }

    LaunchedEffect(dialogDismissSignal) { focusRequester.requestFocus() }

    val verseSelectionToken by viewModel.verseSelectionToken

    val currentIsPresenting by rememberUpdatedState(isPresenting)

    val splitBrowseMode = appSettings.bibleSettings.splitBrowseMode

    val isSplitActive = splitBrowseMode

    val crossRefsAvailable = appSettings.bibleSettings.crossReferencesEnabled
    val crossRefsDocked = crossRefsAvailable && appSettings.bibleSettings.crossReferencesPanel
    val crossRefRepository = crossReferences ?: sharedCrossReferences

    val fallbackAbbreviationResources =
        BibleBookAbbreviations.abbreviationResourceIds.map { stringResource(it) }
    val fallbackAbbreviations = remember(fallbackAbbreviationResources) {
        fallbackAbbreviationResources.map { BibleBookAbbreviations.parseVariants(it).firstOrNull().orEmpty() }
    }

    val loadedModule = viewModel.primaryBible.value

    val crossRefs = rememberBibleCrossReferenceState(
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

    val crossRefCountStr = stringResource(Res.string.bible_cross_references_count)
    val crossRefPopoverTitleStr = stringResource(Res.string.bible_cross_references_popover_title)

    fun openCrossRef(row: CrossRefRow) {
        crossRefs.followed(row)
        viewModel.selectVerseByCanonicalRef(row.bookId, row.chapter, row.verse)
        focusRequester.requestFocus()
    }

    fun goLiveCrossRef(row: CrossRefRow) {
        crossRefs.followed(row)
        viewModel.selectVerseByCanonicalRef(row.bookId, row.chapter, row.verse, goLiveSource = "crossref")
        focusRequester.requestFocus()
    }

    fun scheduleCrossRef(row: CrossRefRow) {
        viewModel.addCanonicalRefToSchedule(row.bookId, row.chapter, row.verse) {
                bookName, chapter, verseNumber, verseText, verseRange, bookId ->
            onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText, verseRange, bookId)
        }
        focusRequester.requestFocus()
    }

    var liveChapterVerses by remember { mutableStateOf<List<String>>(emptyList()) }
    var liveBookName by remember { mutableStateOf("") }
    var liveChapterNum by remember { mutableStateOf(0) }
    var liveVerseNumbers by remember { mutableStateOf<Set<Int>>(emptySet()) }

    var liveNavTargetVerse by remember { mutableStateOf(0) }
    var liveNavToken       by remember { mutableStateOf(0) }

    val fallbackDisplayedVerses = remember { mutableStateOf<List<SelectedVerse>>(emptyList()) }
    val displayedVerses by (presenterManager?.displayedVerses ?: fallbackDisplayedVerses)

    val scope = rememberCoroutineScope()

    LaunchedEffect(displayedVerses, splitBrowseMode) {
        if (!splitBrowseMode || displayedVerses.isEmpty()) return@LaunchedEffect
        val first = displayedVerses.first()
        liveBookName = first.bookName
        liveChapterNum = first.chapter
        liveVerseNumbers = setOf(displayedVerses.first().verseNumber)
        liveNavTargetVerse = liveVerseNumbers.minOrNull() ?: 0
        liveChapterVerses = viewModel.getChapterVerses(first.bookName, first.chapter)
    }

    LaunchedEffect(splitBrowseMode, verses.size) {
        if (!splitBrowseMode) return@LaunchedEffect
        if (liveChapterVerses.isNotEmpty() || displayedVerses.isNotEmpty()) return@LaunchedEffect
        val first = viewModel.getSelectedVerses().firstOrNull() ?: return@LaunchedEffect
        liveBookName = first.bookName
        liveChapterNum = first.chapter
        liveVerseNumbers = setOf(first.verseNumber)
        liveNavTargetVerse = first.verseNumber
        liveChapterVerses = viewModel.getChapterVerses(first.bookName, first.chapter)
    }

    LaunchedEffect(liveNavToken) {
        if (liveNavToken == 0 || liveNavTargetVerse == 0) return@LaunchedEffect
        val verses = viewModel.getVersesForDisplay(liveBookName, liveChapterNum, liveNavTargetVerse)
        if (verses.isNotEmpty()) {
            val primary = verses.first()
            statisticsManager?.recordVerseDisplay(
                primary.bibleName, primary.bookName, primary.chapter, primary.verseNumber
            )
            onVerseSelected(verses)
            onInstanceLinkSendVerse?.invoke(primary.bookName,
                primary.chapter,
                primary.verseNumber,
                primary.verseText,
                primary.verseRange)
            presenterManager?.let { if (it.bibleHold.value) { it.setBibleHold(false); onInstanceLinkSendBibleHold?.invoke(false) } }
            onPresenting(Presenting.BIBLE)
            viewModel.logLiveReference(
                displayBookIndex = viewModel.selectedBookIndex.value,
                chapter    = primary.chapter,
                verseStart = primary.verseNumber,
                verseEnd   = null,
                source     = "manual",
                autoFollow = viewModel.autoFollowEnabled.value,
            )
        }
    }

    fun goLiveWithHistory(source: String = "manual", matchType: String? = null) {
        val selectedVerses = viewModel.getSelectedVerses()
        selectedVerses.firstOrNull()?.let { v ->
            if (viewModel.multiVerseEnabled.value) {
                val verseNumbers = viewModel.getSelectedVerseNumbers()
                val rangeStr = viewModel.formatVerseRange(verseNumbers)
                viewModel.addToHistory(v.bookName, v.chapter, v.verseNumber, v.verseText, rangeStr)
            } else {
                viewModel.addToHistory(v.bookName, v.chapter, v.verseNumber, v.verseText)
            }
        }

        val primaryVerse = selectedVerses.firstOrNull()

        if (primaryVerse != null) {
            val translationCount = appSettings.bibleSettings.translationList().size
            val outputs = appSettings.projectionSettings.screenAssignments
            if (isMultiTranslationPresentation(translationCount, outputs)) {
                UsageEvents.record(UsageEvent.BIBLE_MULTI_TRANSLATION)
            }
            if (isSplitScreenBible(translationCount, outputs)) {
                UsageEvents.record(UsageEvent.BIBLE_SPLIT_SCREEN)
            }
        }
        if (primaryVerse != null && statisticsManager != null) {
            if (viewModel.multiVerseEnabled.value) {
                for (vNum in viewModel.getSelectedVerseNumbers()) {
                    statisticsManager.recordVerseDisplay(primaryVerse.bibleName,
                        primaryVerse.bookName,
                        primaryVerse.chapter,
                        vNum)
                }
            } else {
                statisticsManager.recordVerseDisplay(primaryVerse.bibleName,
                    primaryVerse.bookName,
                    primaryVerse.chapter,
                    primaryVerse.verseNumber)
            }
        }

        if (selectedVerses.isNotEmpty()) {
            onVerseSelected(selectedVerses)
        }
        primaryVerse?.let { v ->
            onInstanceLinkSendVerse?.invoke(v.bookName, v.chapter, v.verseNumber, v.verseText, v.verseRange)
        }
        if (primaryVerse != null) {

            val (verseStart, verseEnd) = verseSpan(primaryVerse.verseRange, primaryVerse.verseNumber)
            viewModel.logLiveReference(
                displayBookIndex = viewModel.selectedBookIndex.value,
                chapter    = primaryVerse.chapter,
                verseStart = verseStart,
                verseEnd   = verseEnd,
                source     = source,
                autoFollow = viewModel.autoFollowEnabled.value,
                matchType  = matchType,
            )

            viewModel.logGoLiveCorrection(viewModel.selectedBookIndex.value, primaryVerse.chapter, verseStart)

            viewModel.canonicalRefForDisplay(
                viewModel.selectedBookIndex.value, primaryVerse.chapter, verseStart,
            )?.let { (book, chapter, verse) ->
                if (verse != null) {
                    verseSequenceLog?.recordGoLive(book, chapter, verse)

                    crossRefs.anchorLiveVerse(Triple(book, chapter, verse))
                }
            }
        }
        if (viewModel.multiVerseEnabled.value) {
            viewModel.clearMultiVerseSelection()
        }
        presenterManager?.let { if (it.bibleHold.value) { it.setBibleHold(false); onInstanceLinkSendBibleHold?.invoke(false) } }
        onPresenting(Presenting.BIBLE)
    }

    val autoFollowLiveToken by viewModel.autoFollowLiveToken

    val autoFollowTokenGate = rememberTokenGate(autoFollowLiveToken)
    LaunchedEffect(autoFollowLiveToken) {
        if (!autoFollowTokenGate.consume()) return@LaunchedEffect
        goLiveWithHistory(source = viewModel.autoFollowLiveSource.value,
            matchType = viewModel.autoFollowLiveMatchType.value)
    }

    LaunchedEffect(verseSelectionToken) {

        if (viewModel.multiVerseEnabled.value && currentIsPresenting) return@LaunchedEffect

        if (splitBrowseMode) return@LaunchedEffect
        if (verses.isNotEmpty() && selectedVerseIndex >= 0 && selectedVerseIndex < verses.size) {
            val selectedVerses = viewModel.getSelectedVerses()
            if (selectedVerses.isNotEmpty()) {
                onVerseSelected(selectedVerses)

                if (currentIsPresenting && autoFollowLiveToken == autoFollowTokenGate.lastHandled) {
                    val primary = selectedVerses.first()
                    viewModel.logLiveReference(
                        displayBookIndex = viewModel.selectedBookIndex.value,
                        chapter    = primary.chapter,
                        verseStart = primary.verseNumber,
                        verseEnd   = null,
                        source     = "manual",
                        autoFollow = viewModel.autoFollowEnabled.value,
                    )
                }
            }
        }
    }

    LaunchedEffect(verses.size) {
        if (!currentIsPresenting && !splitBrowseMode && verses.isNotEmpty()) {
            val selectedVerses = viewModel.getSelectedVerses()
            if (selectedVerses.isNotEmpty()) onVerseSelected(selectedVerses)
        }
    }

    val prevBookRef = remember { mutableStateOf(selectedBookIndex) }
    val prevChapterRef = remember { mutableStateOf(selectedChapter) }
    LaunchedEffect(selectedBookIndex, selectedChapter) {
        val bookChanged = selectedBookIndex != prevBookRef.value
        val chapterChanged = selectedChapter != prevChapterRef.value
        prevBookRef.value = selectedBookIndex
        prevChapterRef.value = selectedChapter
        val wasSequentialAdvance = viewModel.consumeSequentialChapterAdvance()
        val navigatedAway = bookChanged || chapterChanged
        val autoHoldApplies = !splitBrowseMode && currentIsPresenting && !wasSequentialAdvance
        if (navigatedAway && autoHoldApplies) {
            presenterManager?.setBibleHold(true)
        }
    }

    var historyExpanded by remember { mutableStateOf(true) }
    var selectedHistoryIdx by remember { mutableStateOf(-1) }
    var selectedDetectionIdx by remember { mutableStateOf(0) }
    LaunchedEffect(detectedReferences.size) { selectedDetectionIdx = 0 }

    LaunchedEffect(sttConnected) {
        if (sttConnected) {
            val url = appSettings.sttSettings.serverUrl
            if (appSettings.sttSettings.lastConnectedUrl != url) {
                onSettingsChange { it.copy(sttSettings = it.sttSettings.copy(lastConnectedUrl = url)) }
            }
        }
    }

    var searchFieldFocused by remember { mutableStateOf(false) }
    val shortcuts = LocalShortcuts.current

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        if (searchFieldFocused) return false

        val movingUp = shortcuts.matches(ShortcutAction.BIBLE_PREVIOUS_VERSE, event)
        val movingDown = shortcuts.matches(ShortcutAction.BIBLE_NEXT_VERSE, event)

        val movingThroughVerses = movingUp || movingDown
                if (splitBrowseMode && liveChapterVerses.isNotEmpty() && movingThroughVerses) {
            val refVerse = if (liveNavTargetVerse > 0) liveNavTargetVerse
                           else liveVerseNumbers.minOrNull() ?: 1
            val nextVerseNum = nextLiveVerseNumber(
                liveChapterVerses, refVerse, moveUp = movingUp,
            )
            if (nextVerseNum != null) {
                liveNavTargetVerse = nextVerseNum
                liveNavToken++
            }
            return true
        }

        return when {
            movingUp -> viewModel.navigatePreviousVerse()
            movingDown -> viewModel.navigateNextVerse()
            shortcuts.matches(ShortcutAction.BIBLE_PREVIOUS_CHAPTER, event) -> viewModel.navigatePreviousChapter()
            shortcuts.matches(ShortcutAction.BIBLE_NEXT_CHAPTER, event) -> viewModel.navigateNextChapter()
            else -> false
        }
    }

    val density = LocalDensity.current
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)

    val windowState = LocalMainWindowState.current
    val isMaximized = windowState?.placement != WindowPlacement.Floating
    val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

    var colWBook by remember(currentLayout.bibleColWidthBook, isMaximized) {
        mutableStateOf(with(density) { currentLayout.bibleColWidthBook.dp.toPx() })
    }
    var colWChapter by remember(currentLayout.bibleColWidthChapter, isMaximized) {
        mutableStateOf(with(density) { currentLayout.bibleColWidthChapter.dp.toPx() })
    }

    fun saveColWidths() {
        val bookDp = with(density) { colWBook.toDp().value.toInt() }
        val chapterDp = with(density) { colWChapter.toDp().value.toInt() }
        onSettingsChangeState.value { s -> withBibleColumnWidths(s, isMaximized, bookDp, chapterDp) }
    }

    var colWSplit by remember(currentLayout.splitLivePanelWidth, isMaximized) {
        mutableStateOf(with(density) { currentLayout.splitLivePanelWidth.dp.toPx() })
    }

    fun saveColWSplit() {
        val widthDp = with(density) { colWSplit.toDp().value.toInt() }
        onSettingsChangeState.value { s -> withBibleSplitPanelWidth(s, isMaximized, widthDp) }
    }

    var colWCrossRef by remember(currentLayout.bibleColWidthCrossRef, isMaximized) {
        mutableStateOf(with(density) { currentLayout.bibleColWidthCrossRef.dp.toPx() })
    }

    fun saveColWCrossRef() {
        val widthDp = with(density) { colWCrossRef.toDp().value.toInt() }
        onSettingsChangeState.value { s -> withBibleCrossRefPanelWidth(s, isMaximized, widthDp) }
    }



    val focusRescue = rememberFocusLostRescue(hostWindow, focusRequester)
    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onFocusChanged { focusRescue.onFocusChanged(it.hasFocus) }
            .focusRescuePressHook(focusRescue)
            .focusable()
            .onPreviewKeyEvent { handleKeyEvent(it) }
    ) {

        if (loadErrors.isNotEmpty()) {
            BibleLoadErrorBanner(
                errors = loadErrors,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
        }

        val searchPlaceholder = stringResource(Res.string.bible_smart_search_hint)
        BibleSearchRow(
            searchQuery = searchQuery,
            searchPlaceholder = searchPlaceholder,
            searchMode = searchMode,
            scopeOptions = scopeOptions,
            selectedScope = selectedScope,
            modeOptions = modeOptions,
            selectedMode = selectedMode,
            onQueryChange = { viewModel.onSmartQueryChanged(it) },
            onClear = { viewModel.clearSearch(); focusRequester.requestFocus() },
            onSubmit = { viewModel.submitSmartQuery(); focusRequester.requestFocus() },
            onFocusChanged = { searchFieldFocused = it },
            onCycleSearchMode = { viewModel.cycleSearchMode(); focusRequester.requestFocus() },
            onScopeSelected = viewModel::updateSelectedScopeIndex,
            onModeSelected = viewModel::updateSelectedModeIndex,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (engineSettings.enabled && sttConnected) {
            val engineStartFailed = bibleEngineClient?.startFailed?.value == true

            val engineSttDown = bibleEngineClient?.engineSttConnected?.value == false
            val sttConnectError = sttManager.connectError.value == true
            val noBibleSelected = appSettings.bibleSettings.primaryBible.isBlank() &&
                appSettings.bibleSettings.secondaryBible.isBlank() &&
                viewModel.primaryBible.value == null
            BibleDetectionPanel(
                status = bibleSttStatus(
                    engineStartFailed = engineStartFailed,
                    noBibleSelected = noBibleSelected,
                    sttConnected = sttConnected,
                    engineConnected = bibleEngineClient?.connected?.value == true,
                    engineSttDown = engineSttDown,
                    sttReceiving = sttManager.inProgressText.value.isNotBlank() || sttManager.segments.isNotEmpty(),
                    hasDetectedReferences = detectedReferences.isNotEmpty(),
                    sttReconnecting = sttManager.reconnecting.value == true,
                    sttConnectError = sttConnectError,
                    sttConnecting = sttManager.connecting.value == true,
                ),
                statusIsError = engineStartFailed || noBibleSelected || sttConnectError || engineSttDown,
                autoFollowEnabled = autoFollowEnabled,
                textMatchLevel = textMatchLevel,
                continuationSpeed = continuationSpeed,
                detections = detectedReferences,
                selectedIndex = selectedDetectionIdx,
                showFlagButtons = engineSettings.helpDevMode,
                canFlagLive = displayedVerses.isNotEmpty(),
                onAutoFollowChange = { next ->
                    viewModel.setAutoFollow(next)
                    onSettingsChange { it.copy(bibleEngineSettings = it.bibleEngineSettings.copy(autoFollow = next)) }
                },
                onTextMatchLevelChange = { next ->
                    viewModel.setTextMatchLevel(next)
                    onSettingsChange { it.copy(bibleEngineSettings = it.bibleEngineSettings.copy(textMatchLevel = next.name.lowercase())) }
                },
                onContinuationSpeedChange = { next ->
                    viewModel.setContinuationSpeed(next)
                    onSettingsChange { it.copy(bibleEngineSettings = it.bibleEngineSettings.copy(continuationSpeed = next.name.lowercase())) }
                },
                onFlag = { kind ->
                    val live = displayedVerses
                    if (kind == "missed_passage") viewModel.logOperatorFlag(kind = kind)
                    else if (live.isNotEmpty()) viewModel.logOperatorFlag(
                        kind = kind,
                        bookName = live.first().bookName,
                        chapter = live.first().chapter,
                        verseStart = live.minOf { it.verseNumber },
                        verseEnd = live.maxOf { it.verseNumber }.takeIf { live.size > 1 },
                        matchType = viewModel.autoFollowLiveMatchType.value,
                    )
                },
                onClearDetections = { viewModel.clearDetectedReferences() },
                onDetectionClick = { idx ->
                    selectedDetectionIdx = idx
                    detectedReferences.getOrNull(idx)?.let { viewModel.applyDetectedReference(it) }
                    focusRequester.requestFocus()
                },
                onDetectionDoubleClick = { idx ->
                    selectedDetectionIdx = idx
                    detectedReferences.getOrNull(idx)
                        ?.let { viewModel.applyDetectedReference(it, goLiveSource = "detection") }
                    focusRequester.requestFocus()
                },
            )
        }

        if (appSettings.bibleSettings.primaryBible.isBlank() && viewModel.primaryBible.value == null) {

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 360.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📖",
                            style = MaterialTheme.typography.displaySmall
                        )
                        Text(
                            text = stringResource(Res.string.bible_no_primary_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = stringResource(Res.string.bible_no_primary_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (appSettings.bibleSettings.storageDirectory.isBlank()) {
                            Text(
                                text = stringResource(Res.string.bible_no_primary_step1),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(
                            text = stringResource(Res.string.bible_no_primary_step2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else if (isSearchMode && searchResults.isNotEmpty()) {
            BibleSearchResults(
                results = searchResults,
                query = searchQuery,
                onResultChosen = { result ->
                    viewModel.selectSearchResult(result)
                    viewModel.clearSearch()
                    focusRequester.requestFocus()
                },
            )
        } else if (isSearchMode && searchQuery.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.no_results_found, searchQuery),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {

            FocusLostBanner(focusRescue, stringResource(Res.string.tab_focus_lost))

            BibleColumnHeaderRow(
                bookWidth = with(density) { colWBook.toDp() },
                chapterWidth = with(density) { colWChapter.toDp() },
                crossRefsVisible = crossRefsAvailable,
                crossRefsDocked = crossRefsDocked,
                holdAvailable = presenterManager != null && !splitBrowseMode,
                holdLive = presenterManager?.bibleHold?.value ?: false,
                sttToggleVisible = appSettings.sttSettings.lastConnectedUrl.isNotBlank() &&
                    appSettings.sttSettings.lastConnectedUrl == appSettings.sttSettings.serverUrl &&
                    sttManager != null,
                sttConnected = sttConnected,
                translations = appSettings.bibleSettings.translationList(),
                storageDirectory = appSettings.bibleSettings.storageDirectory,
                translationSelectionKey = translationSelectionKey,
                onCrossReferencesToggle = {
                    onSettingsChange { s -> withBibleCrossReferencePanel(s, !crossRefsDocked) }

                    crossRefs.popoverIndex = -1
                    crossRefs.popoverAnchor = null
                    focusRequester.requestFocus()
                },
                onHoldLiveToggle = {
                    val next = !(presenterManager?.bibleHold?.value ?: false)
                    presenterManager?.setBibleHold(next)
                    onInstanceLinkSendBibleHold?.invoke(next)
                    focusRequester.requestFocus()
                },
                onSttToggle = {
                    if (sttConnected) sttManager?.disconnect()
                    else sttManager?.connect(appSettings.sttSettings.serverUrl)
                    focusRequester.requestFocus()
                },
                onSwapTranslations = {
                    onSettingsChange { s -> s.swapBibleTranslations() }
                    focusRequester.requestFocus()
                },
                onMoveTranslation = { index, offset ->
                    onSettingsChange { app -> app.moveBibleTranslation(index, offset) }
                    focusRequester.requestFocus()
                },
                onAddToSchedule = {
                    viewModel.addCurrentVerseToSchedule { bookName,
                        chapter,
                        verseNumber,
                        verseText,
                        verseRange,
                        bookId ->
                        onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText, verseRange, bookId)
                    }
                    focusRequester.requestFocus()
                },
                onGoLive = { goLiveWithHistory(); focusRequester.requestFocus() },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            BibleBrowserPane(
                books = books,
                filteredBooks = filteredBooks,
                filteredChapters = filteredChapters,
                filteredVerses = filteredVerses,
                selectedBookIndex = selectedBookIndex,
                selectedChapter = selectedChapter,
                selectedVerseIndices = filteredSelectionIndices(
                    viewModel.selectedVerseIndices, verses, filteredVerses,
                ),
                selectedVerseInFiltered = if (filteredVerses.isEmpty()) -1 else
                    filteredVerses.indexOf(verses.getOrNull(selectedVerseIndex)).coerceAtLeast(0),
                bookWidthPx = colWBook,
                chapterWidthPx = colWChapter,
                crossRefWidthPx = colWCrossRef,
                splitWidthPx = colWSplit,
                onBookWidthChange = { colWBook = it },
                onChapterWidthChange = { colWChapter = it },
                onCrossRefWidthChange = { colWCrossRef = it },
                onSplitWidthChange = { colWSplit = it },
                onSaveColumnWidths = ::saveColWidths,
                onSaveCrossRefWidth = ::saveColWCrossRef,
                onSaveSplitWidth = ::saveColWSplit,
                crossRefs = crossRefs,
                crossRefsDocked = crossRefsDocked,
                crossRefCountLabel = { count -> crossRefCountStr.format(count) },
                crossRefPopoverTitle = { label, size -> crossRefPopoverTitleStr.format(label, size) },
                onOpenCrossRef = ::openCrossRef,
                onGoLiveCrossRef = ::goLiveCrossRef,
                onScheduleCrossRef = ::scheduleCrossRef,
                onDockCrossRefs = {
                    onSettingsChange { s -> withBibleCrossReferencePanel(s, true) }
                    crossRefs.closePopover()
                    focusRequester.requestFocus()
                },
                onUndockCrossRefs = {
                    onSettingsChange { s -> withBibleCrossReferencePanel(s, false) }
                    focusRequester.requestFocus()
                },
                onDismissPopover = { crossRefs.closePopover(); focusRequester.requestFocus() },
                onRefsChipClicked = { index ->
                    val verseText = filteredVerses.getOrNull(index)
                    val realIndex = verseText?.let { verses.indexOf(it) } ?: -1
                    if (realIndex >= 0) viewModel.selectVerse(realIndex)
                    crossRefs.restartFrom()
                    val canonical = verseText?.let(::verseNumberOf)
                        ?.let { viewModel.canonicalRefForDisplay(selectedBookIndex, selectedChapter, it) }
                        ?.let { (book, chapter, verse) -> verse?.let { Triple(book, chapter, it) } }
                    if (crossRefsDocked || canonical == null || crossRefs.popoverIndex == index) {
                        crossRefs.closePopover()
                    } else {
                        crossRefs.popoverIndex = index
                        crossRefs.popoverAnchor = canonical
                        crossRefs.popoverLabel = viewModel
                            .moduleRefFor(canonical.first, canonical.second, canonical.third)
                            ?.let { formatCrossRefLabel(it.abbreviation, it.chapter, it.verse, null) }
                            ?: ""
                    }
                    focusRequester.requestFocus()
                },
                onBookSelected = { index ->
                    filteredBooks.getOrNull(index)?.let {
                        val realIndex = books.indexOf(it)
                        if (realIndex >= 0) viewModel.selectBook(realIndex)
                    }
                },
                onChapterSelected = { index ->
                    filteredChapters.getOrNull(index)?.toIntOrNull()?.let(viewModel::selectChapter)
                },
                onVerseSelected = { index ->
                    filteredVerses.getOrNull(index)?.let {
                        val realIndex = verses.indexOf(it)
                        if (realIndex >= 0) viewModel.selectVerse(realIndex)
                    }
                    crossRefs.restartFrom()
                    crossRefs.closePopover()
                    focusRequester.requestFocus()
                },
                onVerseCtrlClicked = { index ->
                    filteredVerses.getOrNull(index)?.let {
                        val realIndex = verses.indexOf(it)
                        if (realIndex >= 0) viewModel.ctrlClickVerse(realIndex)
                    }
                },
                onVerseShiftClicked = { index ->
                    filteredVerses.getOrNull(index)?.let {
                        val realIndex = verses.indexOf(it)
                        if (realIndex >= 0) viewModel.shiftClickVerse(realIndex)
                    }
                },
                onVerseRightClicked = { index ->
                    filteredVerses.getOrNull(index)?.let {
                        val realIndex = verses.indexOf(it)
                        if (realIndex >= 0) viewModel.selectVerse(realIndex)
                    }
                },
                onVerseDoubleClicked = { goLiveWithHistory(); focusRequester.requestFocus() },
                onCopyVerse = {
                    val verseStr = verses.getOrNull(selectedVerseIndex) ?: ""
                    val bookName = books.getOrNull(selectedBookIndex) ?: ""
                    val reference = formatVerseReference(verseStr, bookName, selectedChapter)
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        java.awt.datatransfer.StringSelection("$reference\n${verseTextOf(verseStr)}"), null,
                    )
                },
                onAddToSchedule = {
                    viewModel.addCurrentVerseToSchedule { bookName,
                        chapter,
                        verseNumber,
                        verseText,
                        verseRange,
                        bookId ->
                        onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText, verseRange, bookId)
                    }
                    focusRequester.requestFocus()
                },
                isSplitActive = isSplitActive,
                liveChapterVerses = liveChapterVerses,
                liveVerseNumbers = liveVerseNumbers,
                onLiveVerseClicked = { verseNum ->
                    scope.launch {
                        val shown = viewModel.getVersesForDisplay(liveBookName, liveChapterNum, verseNum)
                        if (shown.isNotEmpty()) {
                            val primary = shown.first()
                            statisticsManager?.recordVerseDisplay(primary.bibleName,
                                primary.bookName,
                                primary.chapter,
                                primary.verseNumber)
                            onVerseSelected(shown)
                            onInstanceLinkSendVerse?.invoke(primary.bookName,
                                primary.chapter,
                                primary.verseNumber,
                                primary.verseText,
                                primary.verseRange)
                            presenterManager?.let { if (it.bibleHold.value) { it.setBibleHold(false); onInstanceLinkSendBibleHold?.invoke(false) } }
                            onPresenting(Presenting.BIBLE)
                            viewModel.logLiveReference(
                                displayBookIndex = viewModel.displayIndexForBookName(liveBookName)
                                    .takeIf { it >= 0 } ?: viewModel.selectedBookIndex.value,
                                chapter    = primary.chapter,
                                verseStart = primary.verseNumber,
                                verseEnd   = null,
                                source     = "manual",
                                autoFollow = viewModel.autoFollowEnabled.value,
                            )
                            viewModel.canonicalRefForBookName(
                                liveBookName, primary.chapter, primary.verseNumber,
                            )?.let(crossRefs::anchorLiveVerse)
                        }
                    }
                },
            ) {
                BibleHistoryPanel(
                    entries = viewModel.history,
                    expanded = historyExpanded,
                    selectedIndex = selectedHistoryIdx,
                    onToggleExpanded = { historyExpanded = !historyExpanded },
                    onClear = { viewModel.clearHistory() },
                    onEntryClick = { idx ->
                        selectedHistoryIdx = idx
                        viewModel.history.getOrNull(idx)?.let {
                            viewModel.selectVerseByDetails(it.bookName, it.chapter, it.verseNumber, it.verseRange)
                        }
                        focusRequester.requestFocus()
                    },
                    onEntryDoubleClick = { idx ->
                        selectedHistoryIdx = idx
                        viewModel.history.getOrNull(idx)?.let {
                            viewModel.selectVerseByDetails(
                                it.bookName, it.chapter, it.verseNumber, it.verseRange,
                                goLiveSource = "history",
                            )
                        }
                        focusRequester.requestFocus()
                    },
                )
            }
        }
    }
}

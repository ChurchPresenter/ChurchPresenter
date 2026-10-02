package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.DetectedReference
import org.churchpresenter.app.churchpresenter.viewmodel.ContinuationSpeed
import org.churchpresenter.app.churchpresenter.viewmodel.TextMatchLevel
import org.churchpresenter.app.churchpresenter.viewmodel.BibleSearchMode
import org.churchpresenter.bible.BibleSearch
import org.churchpresenter.bible.BibleLoadError
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.strings.generated.resources.book
import org.churchpresenter.strings.generated.resources.chapter
import org.churchpresenter.strings.generated.resources.verse
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseSequenceLog
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.lottieBandPath
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.utils.isLiveOutput
import org.churchpresenter.app.churchpresenter.utils.isMultiTranslationPresentation
import org.churchpresenter.settings.profileFor
import org.churchpresenter.app.churchpresenter.utils.isSplitScreenBible
import org.churchpresenter.app.churchpresenter.viewmodel.BibleEngineClient
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.viewmodel.nextLiveVerseNumber
import org.churchpresenter.app.churchpresenter.viewmodel.verseNumberOf
import org.churchpresenter.app.churchpresenter.viewmodel.verseSpan
import org.churchpresenter.app.churchpresenter.viewmodel.verseTextOf
import org.churchpresenter.app.churchpresenter.viewmodel.canonicalRefForDisplay
import org.churchpresenter.app.churchpresenter.viewmodel.getSelectedVerses
import org.churchpresenter.app.churchpresenter.viewmodel.logGoLiveCorrection
import org.churchpresenter.app.churchpresenter.viewmodel.logLiveReference
import org.churchpresenter.app.churchpresenter.viewmodel.stepVersePage
import org.churchpresenter.app.churchpresenter.viewmodel.publishLandingPage
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import org.churchpresenter.sharedui.utils.ShortcutMap

/**
 * Everything the Bible tab's pieces read, for one composition: its parameters, the view model's
 * state, and the go-live, cross-reference and key handling they share.
 */
@Suppress("LongParameterList")
internal class BibleTabScope(
    val states: BibleTabStates,
    val appSettings: AppSettings,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    val onAddToSchedule: ((
        bookName: String,
        chapter: Int,
        verseNumber: Int,
        verseText: String,
        verseRange: String,
        bookId: Int,
    ) -> Unit)?,
    val onVerseSelected: (List<SelectedVerse>) -> Unit,
    val onInstanceLinkSendVerse: ((
        bookName: String,
        chapter: Int,
        verseNumber: Int,
        verseText: String,
        verseRange: String,
    ) -> Unit)?,
    val onInstanceLinkSendBibleHold: ((hold: Boolean) -> Unit)?,
    val onPresenting: (Presenting) -> Unit,
    val presenterManager: PresenterManager?,
    val statisticsManager: StatisticsManager?,
    val verseSequenceLog: VerseSequenceLog?,
    val sttManager: STTManager?,
    val bibleEngineClient: BibleEngineClient?,
    val focusRequester: FocusRequester,
    val crossRefs: BibleCrossReferenceState,
    val live: BibleLiveNavState,
    val ui: BibleTabUiState,
    val widths: BibleColumnWidths,
    displayedVersesState: State<List<SelectedVerse>>,
    currentIsPresentingState: State<Boolean>,
    val scope: CoroutineScope,
    val shortcuts: ShortcutMap,
) {
    val translationSelectionKey get() = appSettings.bibleSettings.translationSelectionKey()
    val sttConnected get() = sttManager?.connected?.value == true
    val engineSettings get() = appSettings.bibleEngineSettings
    val detectedReferences by states.detectedReferences
    val autoFollowEnabled by states.autoFollowEnabled
    val textMatchLevel by states.textMatchLevel
    val continuationSpeed by states.continuationSpeed

    val books by states.books
    val loadErrors by states.loadErrors
    val selectedBookIndex by states.selectedBookIndex
    val selectedChapter by states.selectedChapter
    val selectedVerseIndex by states.selectedVerseIndex
    val verses by states.verses
    val searchQuery by states.searchQuery
    val searchResults by states.searchResults
    val isSearchMode by states.isSearchMode
    val searchMode by states.searchMode
    val filteredBooks by states.filteredBooks
    val filteredChapters by states.filteredChapters
    val filteredVerses by states.filteredVerses
    val selectedScopeIndex by states.selectedScopeIndex
    val selectedModeIndex by states.selectedModeIndex
    val verseSelectionToken by states.verseSelectionToken
    val currentIsPresenting by currentIsPresentingState
    val splitBrowseMode get() = appSettings.bibleSettings.splitBrowseMode
    val isSplitActive get() = splitBrowseMode
    val crossRefsAvailable get() = appSettings.bibleSettings.crossReferencesEnabled
    val crossRefsDocked get() = crossRefsAvailable && appSettings.bibleSettings.crossReferencesPanel
    val displayedVerses by displayedVersesState
    var liveChapterVerses by live::liveChapterVerses
    var liveBookName by live::liveBookName
    var liveChapterNum by live::liveChapterNum
    var liveVerseNumbers by live::liveVerseNumbers
    var liveNavTargetVerse by live::liveNavTargetVerse
    var liveNavToken by live::liveNavToken
    var liveNavPageStep by live::liveNavPageStep
    var historyExpanded by ui::historyExpanded
    var selectedHistoryIdx by ui::selectedHistoryIdx
    var selectedDetectionIdx by ui::selectedDetectionIdx
    var searchFieldFocused by ui::searchFieldFocused
    var colWBook by widths::colWBook
    var colWChapter by widths::colWChapter
    var colWSplit by widths::colWSplit
    var colWCrossRef by widths::colWCrossRef

    fun saveColWidths() = widths.saveColWidths()

    fun saveColWSplit() = widths.saveColWSplit()

    fun saveColWCrossRef() = widths.saveColWCrossRef()

    fun openCrossRef(viewModel: BibleViewModel, row: CrossRefRow) {
        crossRefs.followed(row)
        viewModel.selectVerseByCanonicalRef(row.bookId, row.chapter, row.verse)
        focusRequester.requestFocus()
    }

    fun goLiveCrossRef(viewModel: BibleViewModel, row: CrossRefRow) {
        crossRefs.followed(row)
        viewModel.selectVerseByCanonicalRef(row.bookId, row.chapter, row.verse, goLiveSource = "crossref")
        focusRequester.requestFocus()
    }

    fun scheduleCrossRef(viewModel: BibleViewModel, row: CrossRefRow) {
        viewModel.addCanonicalRefToSchedule(row.bookId, row.chapter, row.verse) {
                bookName, chapter, verseNumber, verseText, verseRange, bookId ->
            onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText, verseRange, bookId)
        }
        focusRequester.requestFocus()
    }

    fun goLiveWithHistory(viewModel: BibleViewModel, source: String = "manual", matchType: String? = null) {
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
            val proj = appSettings.projectionSettings
            val outputs = proj.screenAssignments.filter { it.isLiveOutput() }.mapNotNull { proj.profileFor(it) }
            if (isMultiTranslationPresentation(translationCount, outputs)) {
                UsageEvents.record(UsageEvent.BIBLE_MULTI_TRANSLATION)
            }
            if (isSplitScreenBible(translationCount, outputs)) {
                UsageEvents.record(UsageEvent.BIBLE_SPLIT_SCREEN)
            }
            if (lottieBandPath(appSettings, Presenting.BIBLE) != null) {
                UsageEvents.record(UsageEvent.BIBLE_LOTTIE_BAND)
            }
        }
        if (primaryVerse != null && statisticsManager != null) {
            if (viewModel.multiVerseEnabled.value) {
                for (vNum in viewModel.getSelectedVerseNumbers()) {
                    statisticsManager.recordVerseDisplay(
                        primaryVerse.bibleName,
                        primaryVerse.bookName,
                        primaryVerse.chapter,
                        vNum,
                    )
                }
            } else {
                statisticsManager.recordVerseDisplay(
                    primaryVerse.bibleName,
                    primaryVerse.bookName,
                    primaryVerse.chapter,
                    primaryVerse.verseNumber,
                )
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
        presenterManager?.let { if (it.bibleHold.value) {
            it.setBibleHold(false)
            onInstanceLinkSendBibleHold?.invoke(false)
        } }
        onPresenting(Presenting.BIBLE)
    }

    fun handleKeyEvent(viewModel: BibleViewModel, event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        if (searchFieldFocused) return false

        val movingUp = shortcuts.matches(ShortcutAction.BIBLE_PREVIOUS_VERSE, event)
        val movingDown = shortcuts.matches(ShortcutAction.BIBLE_NEXT_VERSE, event)

        val movingThroughVerses = movingUp || movingDown
                if (splitBrowseMode && liveChapterVerses.isNotEmpty() && movingThroughVerses) {
            val refVerse = if (liveNavTargetVerse > 0) liveNavTargetVerse
                           else liveVerseNumbers.minOrNull() ?: 1
            fun liveVerseText(verseNumber: Int) = liveChapterVerses
                .firstOrNull { verseNumberOf(it) == verseNumber }
                ?.let { verseTextOf(it) }
                .orEmpty()
            // A split verse's other half comes before the next verse does, in either direction.
            if (viewModel.stepVersePage(liveVerseText(refVerse), forward = movingDown)) {
                liveNavTargetVerse = refVerse
                liveNavPageStep = true
                liveNavToken++
                return true
            }
            val nextVerseNum = nextLiveVerseNumber(
                liveChapterVerses, refVerse, moveUp = movingUp,
            )
            if (nextVerseNum != null) {
                viewModel.publishLandingPage(liveVerseText(nextVerseNum), fromBehind = movingUp)
                liveNavTargetVerse = nextVerseNum
                liveNavPageStep = false
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
}

/** Where the split-browse live panel stands: the chapter it shows and the verse a key press steps to. */
@Stable
internal class BibleLiveNavState {
    var liveChapterVerses by mutableStateOf<List<String>>(emptyList())
    var liveBookName by mutableStateOf("")
    var liveChapterNum by mutableStateOf(0)
    var liveVerseNumbers by mutableStateOf<Set<Int>>(emptySet())

    var liveNavTargetVerse by mutableStateOf(0)
    var liveNavToken       by mutableStateOf(0)
    // Set when the step was to the other half of a split verse rather than to another verse, so the
    // same verse is not counted twice in the statistics it is already recorded in.
    var liveNavPageStep    by mutableStateOf(false)
}

/** The history panel, detection list and search field state the tab remembers. */
@Stable
internal class BibleTabUiState {
    var historyExpanded by mutableStateOf(true)
    var selectedHistoryIdx by mutableStateOf(-1)
    var selectedDetectionIdx by mutableStateOf(0)
    var searchFieldFocused by mutableStateOf(false)
}

/** The book, chapter, split and cross-reference column widths, and saving them. */
@Suppress("LongParameterList")
internal class BibleColumnWidths(
    colWBookState: MutableState<Float>,
    colWChapterState: MutableState<Float>,
    colWSplitState: MutableState<Float>,
    colWCrossRefState: MutableState<Float>,
    val density: Density,
    val isMaximized: Boolean,
    val onSettingsChangeState: State<((AppSettings) -> AppSettings) -> Unit>,
) {
    var colWBook by colWBookState
    var colWChapter by colWChapterState
    var colWSplit by colWSplitState
    var colWCrossRef by colWCrossRefState

    fun saveColWidths() {
        val bookDp = with(density) { colWBook.toDp().value.toInt() }
        val chapterDp = with(density) { colWChapter.toDp().value.toInt() }
        onSettingsChangeState.value { s -> withBibleColumnWidths(s, isMaximized, bookDp, chapterDp) }
    }

    fun saveColWSplit() {
        val widthDp = with(density) { colWSplit.toDp().value.toInt() }
        onSettingsChangeState.value { s -> withBibleSplitPanelWidth(s, isMaximized, widthDp) }
    }

    fun saveColWCrossRef() {
        val widthDp = with(density) { colWCrossRef.toDp().value.toInt() }
        onSettingsChangeState.value { s -> withBibleCrossRefPanelWidth(s, isMaximized, widthDp) }
    }
}

@Composable
internal fun rememberBibleColumnWidths(
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
): BibleColumnWidths {
    val density = LocalDensity.current
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)

    val windowState = LocalMainWindowState.current
    val isMaximized = windowState?.placement != WindowPlacement.Floating
    val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

    val colWBook = remember(currentLayout.bibleColWidthBook, isMaximized) {
        mutableStateOf(with(density) { currentLayout.bibleColWidthBook.dp.toPx() })
    }
    val colWChapter = remember(currentLayout.bibleColWidthChapter, isMaximized) {
        mutableStateOf(with(density) { currentLayout.bibleColWidthChapter.dp.toPx() })
    }

    val colWSplit = remember(currentLayout.splitLivePanelWidth, isMaximized) {
        mutableStateOf(with(density) { currentLayout.splitLivePanelWidth.dp.toPx() })
    }

    val colWCrossRef = remember(currentLayout.bibleColWidthCrossRef, isMaximized) {
        mutableStateOf(with(density) { currentLayout.bibleColWidthCrossRef.dp.toPx() })
    }
    return remember(colWBook, colWChapter, colWSplit, colWCrossRef, density, isMaximized, onSettingsChangeState) {
        BibleColumnWidths(
            colWBook,
            colWChapter,
            colWSplit,
            colWCrossRef,
            density,
            isMaximized,
            onSettingsChangeState,
        )
    }
}

/** The Bible ViewModel's state the tab's pieces read, as states, so the scope never holds the ViewModel. */
@Suppress("LongParameterList")
internal class BibleTabStates(
    val detectedReferences: State<List<DetectedReference>>,
    val autoFollowEnabled: State<Boolean>,
    val textMatchLevel: State<TextMatchLevel>,
    val continuationSpeed: State<ContinuationSpeed>,
    val books: State<List<String>>,
    val loadErrors: State<List<BibleLoadError>>,
    val selectedBookIndex: State<Int>,
    val selectedChapter: State<Int>,
    val selectedVerseIndex: State<Int>,
    val verses: State<List<String>>,
    val searchQuery: State<String>,
    val searchResults: State<List<BibleSearch>>,
    val isSearchMode: State<Boolean>,
    val searchMode: State<BibleSearchMode>,
    val filteredBooks: State<List<String>>,
    val filteredChapters: State<List<String>>,
    val filteredVerses: State<List<String>>,
    val selectedScopeIndex: State<Int>,
    val selectedModeIndex: State<Int>,
    val verseSelectionToken: State<Int>,
)

internal fun BibleViewModel.tabStates() = BibleTabStates(
    detectedReferences = detectedReferences,
    autoFollowEnabled = autoFollowEnabled,
    textMatchLevel = textMatchLevel,
    continuationSpeed = continuationSpeed,
    books = books,
    loadErrors = loadErrors,
    selectedBookIndex = selectedBookIndex,
    selectedChapter = selectedChapter,
    selectedVerseIndex = selectedVerseIndex,
    verses = verses,
    searchQuery = searchQuery,
    searchResults = searchResults,
    isSearchMode = isSearchMode,
    searchMode = searchMode,
    filteredBooks = filteredBooks,
    filteredChapters = filteredChapters,
    filteredVerses = filteredVerses,
    selectedScopeIndex = selectedScopeIndex,
    selectedModeIndex = selectedModeIndex,
    verseSelectionToken = verseSelectionToken,
)

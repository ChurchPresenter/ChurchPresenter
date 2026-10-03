package org.churchpresenter.bibletab





import org.churchpresenter.bible.BibleSearch
import org.churchpresenter.bible.BibleLoadError
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
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.stt.STTManager
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
    val bibleOutput: BibleOutput?,
    val verseStatistics: BibleVerseStatistics?,
    val onVerseWentLive: () -> Unit,
    val verseSequenceLog: VerseSequenceLog?,
    val sttManager: STTManager?,
    val engineStatus: BibleEngineStatus?,
    val focusRequester: FocusRequester,
    val crossRefs: BibleCrossReferenceState,
    val live: BibleLiveNavState,
    val ui: BibleTabUiState,
    val widths: BibleColumnWidths,
    private val displayedVersesState: State<List<SelectedVerse>>,
    private val currentIsPresentingState: State<Boolean>,
    val scope: CoroutineScope,
    val shortcuts: ShortcutMap,
) {
    val translationSelectionKey get() = appSettings.bibleSettings.translationSelectionKey()
    val sttConnected get() = sttManager?.connected?.value == true
    val engineSettings get() = appSettings.bibleEngineSettings
    val detectedReferences get() = states.detectedReferences.value
    val autoFollowEnabled get() = states.autoFollowEnabled.value
    val textMatchLevel get() = states.textMatchLevel.value
    val continuationSpeed get() = states.continuationSpeed.value

    val books get() = states.books.value
    val loadErrors get() = states.loadErrors.value
    val selectedBookIndex get() = states.selectedBookIndex.value
    val selectedChapter get() = states.selectedChapter.value
    val selectedVerseIndex get() = states.selectedVerseIndex.value
    val verses get() = states.verses.value
    val searchQuery get() = states.searchQuery.value
    val searchResults get() = states.searchResults.value
    val isSearchMode get() = states.isSearchMode.value
    val searchMode get() = states.searchMode.value
    val filteredBooks get() = states.filteredBooks.value
    val filteredChapters get() = states.filteredChapters.value
    val filteredVerses get() = states.filteredVerses.value
    val selectedScopeIndex get() = states.selectedScopeIndex.value
    val selectedModeIndex get() = states.selectedModeIndex.value
    val verseSelectionToken get() = states.verseSelectionToken.value
    val currentIsPresenting get() = currentIsPresentingState.value
    val splitBrowseMode get() = appSettings.bibleSettings.splitBrowseMode
    val isSplitActive get() = splitBrowseMode
    val crossRefsAvailable get() = appSettings.bibleSettings.crossReferencesEnabled
    val crossRefsDocked get() = crossRefsAvailable && appSettings.bibleSettings.crossReferencesPanel
    val displayedVerses get() = displayedVersesState.value
    var liveChapterVerses
        get() = live.liveChapterVerses
        set(value) { live.liveChapterVerses = value }
    var liveBookName
        get() = live.liveBookName
        set(value) { live.liveBookName = value }
    var liveChapterNum
        get() = live.liveChapterNum
        set(value) { live.liveChapterNum = value }
    var liveVerseNumbers
        get() = live.liveVerseNumbers
        set(value) { live.liveVerseNumbers = value }
    var liveNavTargetVerse
        get() = live.liveNavTargetVerse
        set(value) { live.liveNavTargetVerse = value }
    var liveNavToken
        get() = live.liveNavToken
        set(value) { live.liveNavToken = value }
    var liveNavPageStep
        get() = live.liveNavPageStep
        set(value) { live.liveNavPageStep = value }
    var historyExpanded
        get() = ui.historyExpanded
        set(value) { ui.historyExpanded = value }
    var selectedHistoryIdx
        get() = ui.selectedHistoryIdx
        set(value) { ui.selectedHistoryIdx = value }
    var selectedDetectionIdx
        get() = ui.selectedDetectionIdx
        set(value) { ui.selectedDetectionIdx = value }
    var searchFieldFocused
        get() = ui.searchFieldFocused
        set(value) { ui.searchFieldFocused = value }
    var colWBook
        get() = widths.colWBook
        set(value) { widths.colWBook = value }
    var colWChapter
        get() = widths.colWChapter
        set(value) { widths.colWChapter = value }
    var colWSplit
        get() = widths.colWSplit
        set(value) { widths.colWSplit = value }
    var colWCrossRef
        get() = widths.colWCrossRef
        set(value) { widths.colWCrossRef = value }

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

        // What the app records of a verse going live: bilingual and split-screen Bible, the lottie band.
        if (primaryVerse != null) onVerseWentLive()
        if (primaryVerse != null && verseStatistics != null) {
            if (viewModel.multiVerseEnabled.value) {
                for (vNum in viewModel.getSelectedVerseNumbers()) {
                    verseStatistics.recordVerseDisplay(
                        primaryVerse.bibleName,
                        primaryVerse.bookName,
                        primaryVerse.chapter,
                        vNum,
                    )
                }
            } else {
                verseStatistics.recordVerseDisplay(
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
            viewModel.logLiveReference(LiveReference(
                displayBookIndex = viewModel.selectedBookIndex.value,
                chapter    = primaryVerse.chapter,
                verseStart = verseStart,
                verseEnd   = verseEnd,
                source     = source,
                autoFollow = viewModel.autoFollowEnabled.value,
                matchType  = matchType,
            ))

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
        bibleOutput?.let { if (it.bibleHold.value) {
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

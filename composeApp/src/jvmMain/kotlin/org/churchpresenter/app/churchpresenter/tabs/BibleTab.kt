package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondary
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Cursor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import churchpresenter.composeapp.generated.resources.bible_no_primary_title
import churchpresenter.composeapp.generated.resources.bible_no_primary_hint
import churchpresenter.composeapp.generated.resources.bible_no_primary_step1
import churchpresenter.composeapp.generated.resources.bible_no_primary_step2
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.book
import churchpresenter.composeapp.generated.resources.chapter
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.contains_phrase
import churchpresenter.composeapp.generated.resources.current_book
import churchpresenter.composeapp.generated.resources.entire_bible
import churchpresenter.composeapp.generated.resources.exact_match
import churchpresenter.composeapp.generated.resources.found_results
import churchpresenter.composeapp.generated.resources.bible_history
import churchpresenter.composeapp.generated.resources.bible_history_clear
import churchpresenter.composeapp.generated.resources.bible_split_browse_mode
import churchpresenter.composeapp.generated.resources.copy_verse
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_copy
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.ArrowForward
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.viewmodel.BibleEngineClient
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionSource
import org.churchpresenter.app.churchpresenter.viewmodel.TextMatchLevel
import churchpresenter.composeapp.generated.resources.bible_stt_heard
import churchpresenter.composeapp.generated.resources.bible_stt_listening
import churchpresenter.composeapp.generated.resources.bible_stt_auto_follow
import churchpresenter.composeapp.generated.resources.bible_stt_auto_follow_hint
import churchpresenter.composeapp.generated.resources.bible_stt_clear
import churchpresenter.composeapp.generated.resources.bible_stt_src_explicit
import churchpresenter.composeapp.generated.resources.bible_stt_src_reverse
import churchpresenter.composeapp.generated.resources.bible_stt_src_continuation
import churchpresenter.composeapp.generated.resources.bible_stt_match_label
import churchpresenter.composeapp.generated.resources.bible_stt_level_off
import churchpresenter.composeapp.generated.resources.bible_stt_level_conservative
import churchpresenter.composeapp.generated.resources.bible_stt_level_balanced
import churchpresenter.composeapp.generated.resources.bible_stt_level_aggressive
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_search
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.ic_swap
import churchpresenter.composeapp.generated.resources.mode
import churchpresenter.composeapp.generated.resources.no_results_found
import churchpresenter.composeapp.generated.resources.primary_bible
import churchpresenter.composeapp.generated.resources.secondary_bible
import churchpresenter.composeapp.generated.resources.scope
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.bible_smart_search_hint
import churchpresenter.composeapp.generated.resources.bible_search_mode_auto
import churchpresenter.composeapp.generated.resources.bible_search_mode_reference
import churchpresenter.composeapp.generated.resources.bible_search_mode_text
import churchpresenter.composeapp.generated.resources.bible_search_mode_tooltip
import churchpresenter.composeapp.generated.resources.hold_live
import churchpresenter.composeapp.generated.resources.stt_connect
import churchpresenter.composeapp.generated.resources.stt_disconnect
import churchpresenter.composeapp.generated.resources.swap_bibles
import churchpresenter.composeapp.generated.resources.swap_bibles_hint
import churchpresenter.composeapp.generated.resources.verse
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.initialPassClickable
import org.churchpresenter.app.churchpresenter.composables.SelectionListWithIndex
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.BibleSearchMode
import org.churchpresenter.app.churchpresenter.utils.TrainingDataLogger
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun BibleTab(
    modifier: Modifier = Modifier,
    viewModel: BibleViewModel,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String) -> Unit)? = null,
    selectedVerseItem: ScheduleItem.BibleVerseItem? = null,
    onVerseSelected: (List<SelectedVerse>) -> Unit = {},
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    isPresenting: Boolean = false,
    presenterManager: PresenterManager? = null,
    statisticsManager: StatisticsManager? = null,
    sttManager: STTManager? = null,
    bibleEngineClient: BibleEngineClient? = null,
    dialogDismissSignal: Int = 0,
) {
    // Update settings when bible paths change
    val isFirstComposition = remember { mutableStateOf(true) }
    LaunchedEffect(
        appSettings.bibleSettings.storageDirectory,
        appSettings.bibleSettings.primaryBible,
        appSettings.bibleSettings.secondaryBible
    ) {
        if (isFirstComposition.value) {
            isFirstComposition.value = false
        } else {
            viewModel.updateSettings(appSettings)
        }
    }

    LaunchedEffect(selectedVerseItem) {
        selectedVerseItem?.let { item ->
            if (!viewModel.isFullyLoadedFlow.value) {
                viewModel.isFullyLoadedFlow.first { it }
            }
            viewModel.selectVerseByDetails(item.bookName, item.chapter, item.verseNumber, item.verseRange)
        }
    }

    // ── Scripture detection via the Bible Lookup Engine ────────────────────────
    // The engine is started when STT connects (pointed at the same STT server), and stopped on
    // disconnect. Detection results arrive over the engine's WebSocket and feed the rows below.
    val sttConnected = sttManager?.connected?.value == true
    val engineSettings = appSettings.bibleEngineSettings
    // The SET of bibles to index (sorted, blanks removed). Keying the engine restart on this means
    // swapping primary↔secondary (same set) does NOT trigger a re-index, while changing to a
    // different bible does. Supports primary-only (secondary blank → single-element set).
    val engineBibles = remember(appSettings.bibleSettings.primaryBible, appSettings.bibleSettings.secondaryBible) {
        listOf(appSettings.bibleSettings.primaryBible, appSettings.bibleSettings.secondaryBible)
            .filter { it.isNotBlank() }
            .sorted()
    }
    if (sttManager != null && bibleEngineClient != null) {
        LaunchedEffect(sttConnected, engineSettings.enabled, engineSettings.runLocal, engineSettings.host, engineSettings.port, engineBibles) {
            if (sttConnected && engineSettings.enabled && engineBibles.isNotEmpty()) {
                bibleEngineClient.start(
                    sttUrl = appSettings.sttSettings.serverUrl,
                    bibleRoot = appSettings.bibleSettings.storageDirectory,
                    bibleFiles = engineBibles,
                    runLocal = engineSettings.runLocal,
                    host = engineSettings.host,
                    port = engineSettings.port,
                    level = viewModel.textMatchLevel.value.name.lowercase(),
                )
            } else {
                bibleEngineClient.stop()
                viewModel.clearDetectedReferences()
            }
        }
    }
    val detectedReferences by viewModel.detectedReferences
    val autoFollowEnabled by viewModel.autoFollowEnabled
    val textMatchLevel by viewModel.textMatchLevel

    val books by viewModel.books
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

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(dialogDismissSignal) { focusRequester.requestFocus() }

    val verseSelectionToken by viewModel.verseSelectionToken

    val currentIsPresenting by rememberUpdatedState(isPresenting)

    val splitBrowseMode = appSettings.bibleSettings.splitBrowseMode
    // Split view is always visible when splitBrowseMode is ON (panel just has no content until live)
    val isSplitActive = splitBrowseMode

    // Live chapter state for split view (right panel)
    var liveChapterVerses by remember { mutableStateOf<List<String>>(emptyList()) }
    var liveBookName by remember { mutableStateOf("") }
    var liveChapterNum by remember { mutableStateOf(0) }
    var liveVerseNumbers by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // Keyboard navigation state for the live panel
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

    LaunchedEffect(liveNavToken) {
        if (liveNavToken == 0 || liveNavTargetVerse == 0) return@LaunchedEffect
        val verses = viewModel.getVersesForDisplay(liveBookName, liveChapterNum, liveNavTargetVerse)
        if (verses.isNotEmpty()) {
            val primary = verses.first()
            statisticsManager?.recordVerseDisplay(
                primary.bibleName, primary.bookName, primary.chapter, primary.verseNumber
            )
            onVerseSelected(verses)
            presenterManager?.let { if (it.bibleHold.value) it.setBibleHold(false) }
            onPresenting(Presenting.BIBLE)
        }
    }

    fun goLiveWithHistory() {
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
        // Record each individual verse for statistics (primary bible only)
        val primaryVerse = selectedVerses.firstOrNull()
        if (primaryVerse != null && statisticsManager != null) {
            if (viewModel.multiVerseEnabled.value) {
                for (vNum in viewModel.getSelectedVerseNumbers()) {
                    statisticsManager.recordVerseDisplay(primaryVerse.bibleName, primaryVerse.bookName, primaryVerse.chapter, vNum)
                }
            } else {
                statisticsManager.recordVerseDisplay(primaryVerse.bibleName, primaryVerse.bookName, primaryVerse.chapter, primaryVerse.verseNumber)
            }
        }
        // Always push verse content so the output updates immediately
        if (selectedVerses.isNotEmpty()) {
            onVerseSelected(selectedVerses)
        }
        if (primaryVerse != null) {
            val bookNum = viewModel.selectedBookIndex.value + 1
            val verseNums = if (viewModel.multiVerseEnabled.value) viewModel.getSelectedVerseNumbers()
                            else listOf(primaryVerse.verseNumber)
            TrainingDataLogger.logLiveReference(
                book       = bookNum,
                chapter    = primaryVerse.chapter,
                verseStart = verseNums.firstOrNull(),
                verseEnd   = verseNums.lastOrNull().takeIf { verseNums.size > 1 },
                source     = "manual"
            )
        }
        if (viewModel.multiVerseEnabled.value) {
            viewModel.clearMultiVerseSelection()
        }
        presenterManager?.let { if (it.bibleHold.value) it.setBibleHold(false) }
        onPresenting(Presenting.BIBLE)
    }

    // Only push to presenter when:
    //  - not currently presenting (free browsing always updates preview), OR
    //  - an explicit verse selection happened (token changed) while presenting
    LaunchedEffect(verseSelectionToken) {
        // In multi-verse mode while presenting, don't update until Go Live is pressed
        if (viewModel.multiVerseEnabled.value && currentIsPresenting) return@LaunchedEffect
        // In split browse mode while presenting: suppress auto-live (explicit Go Live button still works)
        if (splitBrowseMode && currentIsPresenting) return@LaunchedEffect
        if (verses.isNotEmpty() && selectedVerseIndex >= 0 && selectedVerseIndex < verses.size) {
            val selectedVerses = viewModel.getSelectedVerses()
            if (selectedVerses.isNotEmpty()) onVerseSelected(selectedVerses)
        }
    }

    // While not presenting, also update preview when chapter loads so the first verse shows
    LaunchedEffect(verses.size) {
        if (!currentIsPresenting && verses.isNotEmpty()) {
            val selectedVerses = viewModel.getSelectedVerses()
            if (selectedVerses.isNotEmpty()) onVerseSelected(selectedVerses)
        }
    }

    // Auto-pause when user navigates to a different chapter or book while presenting
    val prevBookRef = remember { mutableStateOf(selectedBookIndex) }
    val prevChapterRef = remember { mutableStateOf(selectedChapter) }
    LaunchedEffect(selectedBookIndex, selectedChapter) {
        val bookChanged = selectedBookIndex != prevBookRef.value
        val chapterChanged = selectedChapter != prevChapterRef.value
        prevBookRef.value = selectedBookIndex
        prevChapterRef.value = selectedChapter
        if ((bookChanged || chapterChanged) && !splitBrowseMode && currentIsPresenting) {
            presenterManager?.setBibleHold(true)
        }
    }

    var historyExpanded by remember { mutableStateOf(true) }

    var searchFieldFocused by remember { mutableStateOf(false) }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        // Don't intercept arrow keys when the search field has focus (cursor navigation)
        if (searchFieldFocused) return false

        // In split mode, Up/Down navigate the live (right) panel
        if (splitBrowseMode && liveChapterVerses.isNotEmpty() &&
            (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
        ) {
            val refVerse = if (liveNavTargetVerse > 0) liveNavTargetVerse
                           else liveVerseNumbers.minOrNull() ?: 1
            val currentIdx = liveChapterVerses.indexOfFirst { v ->
                v.substringBefore(". ").toIntOrNull() == refVerse
            }.takeIf { it >= 0 } ?: 0
            val nextIdx = if (event.key == Key.DirectionUp)
                (currentIdx - 1).coerceAtLeast(0)
            else
                (currentIdx + 1).coerceAtMost(liveChapterVerses.size - 1)
            val nextVerseNum = liveChapterVerses.getOrNull(nextIdx)
                ?.substringBefore(". ")?.toIntOrNull()
            if (nextVerseNum != null) {
                liveNavTargetVerse = nextVerseNum
                liveNavToken++
            }
            return true
        }

        return when (event.key) {
            Key.DirectionUp    -> viewModel.navigatePreviousVerse()
            Key.DirectionDown  -> viewModel.navigateNextVerse()
            Key.DirectionLeft  -> viewModel.navigatePreviousChapter()
            Key.DirectionRight -> viewModel.navigateNextChapter()
            else -> false
        }
    }

    // ── Resizable column widths ───────────────────────────────────────
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
        onSettingsChangeState.value { s ->
            if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(bibleColWidthBook = bookDp, bibleColWidthChapter = chapterDp))
            else s.copy(windowedLayout = s.windowedLayout.copy(bibleColWidthBook = bookDp, bibleColWidthChapter = chapterDp))
        }
    }

    var colWSplit by remember(currentLayout.splitLivePanelWidth, isMaximized) {
        mutableStateOf(with(density) { currentLayout.splitLivePanelWidth.dp.toPx() })
    }

    fun saveColWSplit() {
        val widthDp = with(density) { colWSplit.toDp().value.toInt() }
        onSettingsChangeState.value { s ->
            if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(splitLivePanelWidth = widthDp))
            else s.copy(windowedLayout = s.windowedLayout.copy(splitLivePanelWidth = widthDp))
        }
    }

    // Column header — keeps the Book/Chapter list tops aligned with the verse toolbar row
    // now that the per-column filter fields have been replaced by the unified search box.
    @Composable
    fun ColumnHeader(label: String) {
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // Compact Auto / Reference / Text mode chip, shown inside the search field (leading slot).
    @Composable
    fun SearchModeChip(modifier: Modifier = Modifier) {
        val (label, container, content) = when (searchMode) {
            BibleSearchMode.AUTO -> Triple(
                Res.string.bible_search_mode_auto,
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer
            )
            BibleSearchMode.REFERENCE -> Triple(
                Res.string.bible_search_mode_reference,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer
            )
            BibleSearchMode.TEXT -> Triple(
                Res.string.bible_search_mode_text,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        TooltipArea(
            tooltip = {
                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                    Text(
                        text = stringResource(Res.string.bible_search_mode_tooltip),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
        ) {
            Surface(
                onClick = { viewModel.cycleSearchMode(); focusRequester.requestFocus() },
                modifier = modifier,
                shape = MaterialTheme.shapes.small,
                color = container,
                contentColor = content
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }

    @Composable
    fun DragHandle(onDrag: (Float) -> Unit) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = ::saveColWidths) { _, amount ->
                        onDrag(amount)
                    }
                }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { handleKeyEvent(it) }
    ) {
        // ── Search row — wraps to two lines when window is narrow ──
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(all = 4.dp)) {
            val searchIsNarrow = maxWidth < 550.dp

            if (searchIsNarrow) {
                // Narrow: search field on its own line, controls below
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            .onFocusChanged { searchFieldFocused = it.isFocused }
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) {
                                    viewModel.submitSmartQuery(); focusRequester.requestFocus(); true
                                } else false
                            },
                        value = searchQuery,
                        onValueChange = { viewModel.onSmartQueryChanged(it) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        label = { Text(text = stringResource(Res.string.search), style = MaterialTheme.typography.bodyMedium) },
                        placeholder = { Text(text = stringResource(Res.string.bible_smart_search_hint), style = MaterialTheme.typography.bodyMedium) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch(); focusRequester.requestFocus() }) {
                                        Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.clear), modifier = Modifier.size(20.dp))
                                    }
                                }
                                SearchModeChip(modifier = Modifier.padding(end = 8.dp))
                            }
                        },
                        singleLine = true,
                        maxLines = 1,
                        colors = OutlinedTextFieldDefaults.colors().copy(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DropdownSelector(
                            modifier = Modifier.weight(1f, fill = false).widthIn(max = 160.dp).padding(end = 8.dp),
                            label = stringResource(Res.string.scope),
                            items = scopeOptions,
                            selected = selectedScope,
                            onSelectedChange = { newValue ->
                                val newIndex = scopeOptions.indexOf(newValue).coerceAtLeast(0)
                                viewModel.updateSelectedScopeIndex(newIndex)
                            }
                        )
                        DropdownSelector(
                            modifier = Modifier.weight(1f, fill = false).widthIn(max = 160.dp).padding(end = 8.dp),
                            label = stringResource(Res.string.mode),
                            items = modeOptions,
                            selected = selectedMode,
                            onSelectedChange = { newValue ->
                                val newIndex = modeOptions.indexOf(newValue).coerceAtLeast(0)
                                viewModel.updateSelectedModeIndex(newIndex)
                            }
                        )
                        IconButton(
                            onClick = { viewModel.submitSmartQuery(); focusRequester.requestFocus() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } else {
                // Wide: everything on one line
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                            .onFocusChanged { searchFieldFocused = it.isFocused }
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) {
                                    viewModel.submitSmartQuery(); focusRequester.requestFocus(); true
                                } else false
                            },
                        value = searchQuery,
                        onValueChange = { viewModel.onSmartQueryChanged(it) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        label = { Text(text = stringResource(Res.string.search), style = MaterialTheme.typography.bodyMedium) },
                        placeholder = { Text(text = stringResource(Res.string.bible_smart_search_hint), style = MaterialTheme.typography.bodyMedium) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch(); focusRequester.requestFocus() }) {
                                        Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.clear), modifier = Modifier.size(20.dp))
                                    }
                                }
                                SearchModeChip(modifier = Modifier.padding(end = 8.dp))
                            }
                        },
                        singleLine = true,
                        maxLines = 1,
                        colors = OutlinedTextFieldDefaults.colors().copy(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                        )
                    )
                    DropdownSelector(
                        modifier = Modifier.width(160.dp).padding(end = 8.dp),
                        label = stringResource(Res.string.scope),
                        items = scopeOptions,
                        selected = selectedScope,
                        onSelectedChange = { newValue ->
                            val newIndex = scopeOptions.indexOf(newValue).coerceAtLeast(0)
                            viewModel.updateSelectedScopeIndex(newIndex)
                        }
                    )
                    DropdownSelector(
                        modifier = Modifier.width(200.dp).padding(end = 8.dp),
                        label = stringResource(Res.string.mode),
                        items = modeOptions,
                        selected = selectedMode,
                        onSelectedChange = { newValue ->
                            val newIndex = modeOptions.indexOf(newValue).coerceAtLeast(0)
                            viewModel.updateSelectedModeIndex(newIndex)
                        }
                    )
                    IconButton(
                        onClick = { viewModel.submitSmartQuery(); focusRequester.requestFocus() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // ── Detected references strip — visible whenever STT is connected ──
        if (sttConnected) {
            val levelName = when (textMatchLevel) {
                TextMatchLevel.OFF -> stringResource(Res.string.bible_stt_level_off)
                TextMatchLevel.CONSERVATIVE -> stringResource(Res.string.bible_stt_level_conservative)
                TextMatchLevel.BALANCED -> stringResource(Res.string.bible_stt_level_balanced)
                TextMatchLevel.AGGRESSIVE -> stringResource(Res.string.bible_stt_level_aggressive)
            }
            // ── Controls row: status + auto-follow + reverse-lookup level + clear ──
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(32.dp).padding(end = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (detectedReferences.isEmpty()) stringResource(Res.string.bible_stt_listening)
                               else stringResource(Res.string.bible_stt_heard),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = if (detectedReferences.isEmpty()) 0.6f else 1f)
                    )
                }
                TooltipArea(tooltip = {
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = stringResource(Res.string.bible_stt_auto_follow_hint),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }) {
                    FilterChip(
                        selected = autoFollowEnabled,
                        onClick = {
                            val next = !autoFollowEnabled
                            viewModel.setAutoFollow(next)
                            onSettingsChange { it.copy(bibleEngineSettings = it.bibleEngineSettings.copy(autoFollow = next)) }
                        },
                        label = { Text(stringResource(Res.string.bible_stt_auto_follow)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
                // Reverse-lookup level: tap to cycle Off → Conservative → Balanced → Aggressive.
                FilterChip(
                    selected = textMatchLevel != TextMatchLevel.OFF,
                    onClick = {
                        val all = TextMatchLevel.values()
                        val next = all[(textMatchLevel.ordinal + 1) % all.size]
                        viewModel.setTextMatchLevel(next)
                        onSettingsChange { it.copy(bibleEngineSettings = it.bibleEngineSettings.copy(textMatchLevel = next.name.lowercase())) }
                    },
                    label = { Text("${stringResource(Res.string.bible_stt_match_label)}: $levelName") },
                    leadingIcon = {
                        Icon(Icons.Filled.FormatQuote, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                if (detectedReferences.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearDetectedReferences() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.bible_stt_clear),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── Detected references — History-style rows (max ~5 visible; scrolls beyond) ──
            Box(modifier = Modifier.fillMaxWidth()) {
                val detScroll = rememberScrollState()
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = 132.dp)
                        .verticalScroll(detScroll)
                        .padding(end = 10.dp)
                ) {
                detectedReferences.forEach { ref ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .clickable { viewModel.applyDetectedReference(ref); focusRequester.requestFocus() }
                        .padding(vertical = 2.dp)
                ) {
                    // Fixed-width icon column so every reference + verse text lines up vertically,
                    // regardless of how many source markers a row has.
                    Row(
                        modifier = Modifier.width(68.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ref.sources.forEach { src ->
                            val (icon, descRes, tint) = when (src) {
                                DetectionSource.EXPLICIT -> Triple(
                                    Icons.Filled.Mic, Res.string.bible_stt_src_explicit,
                                    MaterialTheme.colorScheme.primary
                                )
                                DetectionSource.REVERSE -> Triple(
                                    Icons.Filled.FormatQuote, Res.string.bible_stt_src_reverse,
                                    MaterialTheme.colorScheme.tertiary
                                )
                                DetectionSource.CONTINUATION -> Triple(
                                    Icons.Filled.ArrowForward, Res.string.bible_stt_src_continuation,
                                    MaterialTheme.colorScheme.secondary
                                )
                            }
                            TooltipArea(tooltip = {
                                Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(
                                        text = stringResource(descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stringResource(descRes),
                                    tint = tint,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(Modifier.width(3.dp))
                        }
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) {
                                append(ref.label)
                            }
                            ref.verseText?.let { append("  $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(detScroll)
                )
            }
        }

        // ── Main content ─────────────────────────────────────────────
        if (appSettings.bibleSettings.primaryBible.isBlank()) {
            // ── Empty state: primary bible not configured ─────────────
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
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
                        // Show step 1 only when the directory is also missing
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
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text(
                    text = stringResource(Res.string.found_results, searchResults.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
                        itemsIndexed(searchResults) { _, result ->
                            val resultText = "${result.book} ${result.chapter}:${result.verse} - ${result.verseText}"
                            val highlightedText = buildAnnotatedString {
                                var lastIndex = 0
                                val lowerText = resultText.lowercase()
                                // Match against the same trimmed query that produced the results;
                                // an empty query would make indexOf() loop forever below.
                                val lowerQuery = searchQuery.trim().lowercase()
                                var startIndex = if (lowerQuery.isEmpty()) -1 else lowerText.indexOf(lowerQuery, lastIndex)
                                while (startIndex != -1) {
                                    // lowercase() can change string length in some locales, so clamp
                                    // indices derived from lowerText before slicing resultText
                                    val safeStart = startIndex.coerceAtMost(resultText.length)
                                    val safeEnd = (startIndex + lowerQuery.length).coerceAtMost(resultText.length)
                                    append(resultText.substring(lastIndex.coerceAtMost(safeStart), safeStart))
                                    withStyle(style = SpanStyle(
                                        background = MaterialTheme.colorScheme.primaryContainer,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )) {
                                        append(resultText.substring(safeStart, safeEnd))
                                    }
                                    lastIndex = startIndex + lowerQuery.length
                                    startIndex = lowerText.indexOf(lowerQuery, lastIndex)
                                }
                                if (lastIndex < resultText.length) append(resultText.substring(lastIndex))
                            }
                            Text(
                                text = highlightedText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .initialPassClickable {
                                        viewModel.selectSearchResult(result)
                                        viewModel.clearSearch()
                                        focusRequester.requestFocus()
                                    }
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState = listState)
                    )
                }
            }
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
            // ── Toolbar: swap, bible labels, go live, add to schedule ─
            // Multi-verse selection is keyboard-driven: Ctrl/Cmd+Click to toggle, Shift+Click for range
            val holdLiveStr = stringResource(Res.string.hold_live)
            val swapBiblesStr = stringResource(Res.string.swap_bibles)
            val goLiveStr = stringResource(Res.string.go_live)
            val addScheduleStr = stringResource(Res.string.add_to_schedule)

            val toolbarContent: @Composable () -> Unit = {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                        if (presenterManager != null && !splitBrowseMode) {
                            val holdLive by presenterManager.bibleHold
                            TooltipArea(
                                    tooltip = {
                                        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                            Text(holdLiveStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                                ) {
                                    IconButton(
                                        onClick = { presenterManager.setBibleHold(!holdLive); focusRequester.requestFocus() },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (holdLive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (holdLive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Icon(painter = painterResource(Res.drawable.ic_pause), contentDescription = holdLiveStr, modifier = Modifier.size(20.dp))
                                    }
                                }
                        }
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(
                                        "Ctrl+Click to toggle verses, Shift+Click for range",
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                        ) {
                            Text(
                                text = "⌘/Ctrl · Shift",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        // STT connect/disconnect — hidden when STT is not configured
                        if (sttManager != null) {
                            val sttButtonStr = if (sttConnected) stringResource(Res.string.stt_disconnect)
                                               else stringResource(Res.string.stt_connect)
                            TooltipArea(
                                tooltip = {
                                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(sttButtonStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                            ) {
                                IconButton(
                                    onClick = {
                                        if (sttConnected) sttManager.disconnect()
                                        else sttManager.connect(appSettings.sttSettings.serverUrl)
                                        focusRequester.requestFocus()
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (sttConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (sttConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Filled.Mic, contentDescription = sttButtonStr, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        // Swap Bibles — always wrapped in tooltip showing bible names
                        TooltipArea(
                            tooltip = {
                                Surface(
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    tonalElevation = 4.dp
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(
                                            text = stringResource(Res.string.swap_bibles_hint),
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${stringResource(Res.string.primary_bible)} ${appSettings.bibleSettings.primaryBible.substringBeforeLast('.').ifEmpty { "-" }}",
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "${stringResource(Res.string.secondary_bible)} ${appSettings.bibleSettings.secondaryBible.substringBeforeLast('.').ifEmpty { "-" }}",
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            },
                            tooltipPlacement = TooltipPlacement.ComponentRect(
                                anchor = Alignment.BottomCenter,
                                offset = DpOffset(0.dp, 4.dp)
                            )
                        ) {
                                IconButton(
                                    onClick = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.swapped()) }; focusRequester.requestFocus() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                ) {
                                    Icon(painter = painterResource(Res.drawable.ic_swap), contentDescription = swapBiblesStr, modifier = Modifier.size(20.dp))
                                }
                        }
                        // Add to Schedule
                        TooltipArea(
                                tooltip = {
                                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(addScheduleStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.addCurrentVerseToSchedule { bookName, chapter, verseNumber, verseText, verseRange ->
                                            onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText, verseRange)
                                        }
                                        focusRequester.requestFocus()
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) {
                                    Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = addScheduleStr, modifier = Modifier.size(20.dp))
                                }
                            }
                        // Go Live
                        TooltipArea(
                                tooltip = {
                                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(goLiveStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                            ) {
                                IconButton(
                                    onClick = { goLiveWithHistory(); focusRequester.requestFocus() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.Tv, contentDescription = goLiveStr, modifier = Modifier.size(20.dp))
                                }
                            }
                    }
                }

            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(start = 4.dp)) {

                // Book column (resizable)
                Column(modifier = Modifier.width(with(density) { colWBook.toDp() }).fillMaxHeight()) {
                    ColumnHeader(stringResource(Res.string.book))
                    SelectionListWithIndex(
                        list = filteredBooks,
                        selectedIndex = filteredBooks.indexOf(books.getOrNull(selectedBookIndex) ?: "").coerceAtLeast(0),
                        singleLine = true,
                        onItemSelected = { index, _ ->
                            val bookName = filteredBooks.getOrNull(index)
                            bookName?.let {
                                val realIndex = books.indexOf(it)
                                if (realIndex >= 0) viewModel.selectBook(realIndex)
                            }
                        }
                    )
                }

                DragHandle { amount ->
                    colWBook = (colWBook + amount).coerceIn(
                        with(density) { 80.dp.toPx() },
                        with(density) { 400.dp.toPx() }
                    )
                }

                // Chapter column (resizable)
                Column(modifier = Modifier.width(with(density) { colWChapter.toDp() }).fillMaxHeight()) {
                    ColumnHeader(stringResource(Res.string.chapter))
                    SelectionListWithIndex(
                        list = filteredChapters,
                        selectedIndex = filteredChapters.indexOf(selectedChapter.toString()).coerceAtLeast(0),
                        onItemSelected = { index, _ ->
                            val chapterStr = filteredChapters.getOrNull(index)
                            chapterStr?.toIntOrNull()?.let { chapter -> viewModel.selectChapter(chapter) }
                        }
                    )
                }

                DragHandle { amount ->
                    colWChapter = (colWChapter + amount).coerceIn(
                        with(density) { 60.dp.toPx() },
                        with(density) { 300.dp.toPx() }
                    )
                }

                // Right area: toolbar + (verse list + live panel) + history
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                    // Shared toolbar row (verse search + buttons)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.verse),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        toolbarContent()
                    }

                    // Verse list + live panel (drag handle starts here, below toolbar)
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val effectiveSplitWidth = if (isSplitActive)
                        colWSplit.coerceAtMost(
                            (constraints.maxWidth - with(density) { (100.dp + 6.dp).toPx() }).coerceAtLeast(0f)
                        )
                    else 0f
                    Row(modifier = Modifier.fillMaxSize()) {

                        // Verse list column
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            var showVerseContextMenu by remember { mutableStateOf(false) }
                            var verseContextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

                            Box(modifier = Modifier.fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            if (event.type == PointerEventType.Press &&
                                                event.button?.isSecondary == true
                                            ) {
                                                val pos = event.changes.first().position
                                                verseContextMenuOffset = with(density) {
                                                    DpOffset(pos.x.toDp(), pos.y.toDp())
                                                }
                                            }
                                        }
                                    }
                                }
                            ) {
                                // Always map multi-verse indices into the filtered verse list
                                val multiIndicesInFiltered = viewModel.selectedVerseIndices
                                    .mapNotNull { realIdx ->
                                        val verseStr = verses.getOrNull(realIdx)
                                        verseStr?.let { filteredVerses.indexOf(it).takeIf { i -> i >= 0 } }
                                    }
                                    .toSet()
                                    .takeIf { it.isNotEmpty() }

                                SelectionListWithIndex(
                                    list = filteredVerses,
                                    selectedIndex = if (filteredVerses.isEmpty()) -1 else {
                                        val currentVerse = verses.getOrNull(selectedVerseIndex)
                                        filteredVerses.indexOf(currentVerse).coerceAtLeast(0)
                                    },
                                    selectedIndices = multiIndicesInFiltered,
                                    onItemSelected = { index, _ ->
                                        val verseText = filteredVerses.getOrNull(index)
                                        verseText?.let {
                                            val realIndex = verses.indexOf(it)
                                            if (realIndex >= 0) viewModel.selectVerse(realIndex)
                                        }
                                        focusRequester.requestFocus()
                                    },
                                    onItemDoubleClicked = { _, _ -> goLiveWithHistory() },
                                    onItemCtrlClicked = { index, _ ->
                                        val verseText = filteredVerses.getOrNull(index)
                                        verseText?.let {
                                            val realIndex = verses.indexOf(it)
                                            if (realIndex >= 0) viewModel.ctrlClickVerse(realIndex)
                                        }
                                    },
                                    onItemShiftClicked = { index, _ ->
                                        val verseText = filteredVerses.getOrNull(index)
                                        verseText?.let {
                                            val realIndex = verses.indexOf(it)
                                            if (realIndex >= 0) viewModel.shiftClickVerse(realIndex)
                                        }
                                    },
                                    onRightClicked = { index ->
                                        val verseText = filteredVerses.getOrNull(index)
                                        verseText?.let {
                                            val realIndex = verses.indexOf(it)
                                            if (realIndex >= 0) viewModel.selectVerse(realIndex)
                                        }
                                        showVerseContextMenu = true
                                    }
                                )

                                DropdownMenu(
                                    expanded = showVerseContextMenu,
                                    onDismissRequest = { showVerseContextMenu = false },
                                    offset = verseContextMenuOffset
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.copy_verse)) },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_copy),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            val verseStr = verses.getOrNull(selectedVerseIndex) ?: ""
                                            val verseNum = verseStr.substringBefore(". ").toIntOrNull()
                                            val verseText = verseStr.substringAfter(". ", verseStr)
                                            val bookName = books.getOrNull(selectedBookIndex) ?: ""
                                            val reference = if (verseNum != null) "$bookName $selectedChapter:$verseNum" else "$bookName $selectedChapter"
                                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                            clipboard.setContents(java.awt.datatransfer.StringSelection("$reference\n$verseText"), null)
                                            showVerseContextMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.add_to_schedule)) },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_playlist_add),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        },
                                        onClick = {
                                            viewModel.addCurrentVerseToSchedule { bookName, chapter, verseNumber, verseText, verseRange ->
                                                onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText, verseRange)
                                            }
                                            focusRequester.requestFocus()
                                            showVerseContextMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.go_live)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            goLiveWithHistory()
                                            focusRequester.requestFocus()
                                            showVerseContextMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Live panel (split mode) — drag handle starts below toolbar
                        if (isSplitActive) {
                            DragHandle { amount ->
                                colWSplit = (colWSplit - amount).coerceIn(
                                    with(density) { 150.dp.toPx() },
                                    with(density) { 600.dp.toPx() }
                                )
                                saveColWSplit()
                            }
                            Column(modifier = Modifier.width(with(density) { effectiveSplitWidth.toDp() }).fillMaxHeight()) {
                                LiveChapterPanel(
                                    verses = liveChapterVerses,
                                    liveVerseNumbers = liveVerseNumbers,
                                    onVerseClicked = { verseNum ->
                                        scope.launch {
                                            val verses = viewModel.getVersesForDisplay(liveBookName, liveChapterNum, verseNum)
                                            if (verses.isNotEmpty()) {
                                                val primary = verses.first()
                                                statisticsManager?.recordVerseDisplay(primary.bibleName, primary.bookName, primary.chapter, primary.verseNumber)
                                                onVerseSelected(verses)
                                                presenterManager?.let { if (it.bibleHold.value) it.setBibleHold(false) }
                                                onPresenting(Presenting.BIBLE)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                    } // end verse + live Row
                    } // end BoxWithConstraints

                    // ── History panel — spans verse + live columns ──────────
                    if (viewModel.history.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { historyExpanded = !historyExpanded }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (historyExpanded) Res.drawable.ic_arrow_down else Res.drawable.ic_arrow_up
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(Res.string.bible_history),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TooltipArea(
                                tooltip = {
                                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(stringResource(Res.string.bible_history_clear), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                            ) {
                                IconButton(onClick = { viewModel.clearHistory() }) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_delete),
                                        contentDescription = stringResource(Res.string.bible_history_clear),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(visible = historyExpanded) {
                            val historyListState = rememberLazyListState()
                            LaunchedEffect(viewModel.history.size) {
                                historyListState.scrollToItem(0)
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                LazyColumn(state = historyListState, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
                                    itemsIndexed(viewModel.history) { idx, entry ->
                                        Text(
                                            text = buildAnnotatedString {
                                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) {
                                                    append(entry.displayText)
                                                }
                                                append("  ${entry.verseText}")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            modifier = Modifier.fillMaxWidth()
                                                .background(
                                                    if (idx % 2 == 0) MaterialTheme.colorScheme.surface
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                )
                                                .initialPassClickable {
                                                    viewModel.selectVerseByDetails(entry.bookName, entry.chapter, entry.verseNumber)
                                                    focusRequester.requestFocus()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                VerticalScrollbar(
                                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                    adapter = rememberScrollbarAdapter(scrollState = historyListState)
                                )
                            }
                        }
                    }

                } // end right area Column

            } // end outer Row
        }
    }
}

@Composable
private fun LiveChapterPanel(
    verses: List<String>,
    liveVerseNumbers: Set<Int>,
    onVerseClicked: ((verseNumber: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(liveVerseNumbers) {
        val firstLiveIndex = verses.indexOfFirst { verse ->
            verse.substringBefore(". ").toIntOrNull()?.let { it in liveVerseNumbers } == true
        }
        if (firstLiveIndex >= 0) listState.animateScrollToItem(firstLiveIndex)
    }

    Box(modifier = modifier.fillMaxWidth().padding(top = 8.dp).fillMaxHeight()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 12.dp)
        ) {
            itemsIndexed(verses) { _, verseStr ->
                val verseNum = verseStr.substringBefore(". ").toIntOrNull()
                val isLive = verseNum != null && verseNum in liveVerseNumbers
                Text(
                    text = verseStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isLive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .then(
                            if (onVerseClicked != null && verseNum != null)
                                Modifier.clickable { onVerseClicked(verseNum) }
                            else Modifier
                        )
                        .padding(6.dp)
                )
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState)
        )
    }
}
package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
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
import churchpresenter.composeapp.generated.resources.copy_verse
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_copy
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionTrack
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.viewmodel.BibleEngineClient
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionSource
import org.churchpresenter.app.churchpresenter.viewmodel.TextMatchLevel
import churchpresenter.composeapp.generated.resources.bible_stt_listening
import churchpresenter.composeapp.generated.resources.bible_stt_engine_connecting
import churchpresenter.composeapp.generated.resources.bible_stt_engine_unavailable
import churchpresenter.composeapp.generated.resources.bible_stt_no_bible
import churchpresenter.composeapp.generated.resources.bible_stt_waiting_for_stt
import churchpresenter.composeapp.generated.resources.stt_status_reconnecting
import churchpresenter.composeapp.generated.resources.stt_status_unreachable
import churchpresenter.composeapp.generated.resources.stt_status_connecting
import churchpresenter.composeapp.generated.resources.stt_status_not_connected
import churchpresenter.composeapp.generated.resources.stt_connect
import churchpresenter.composeapp.generated.resources.stt_disconnect
import churchpresenter.composeapp.generated.resources.bible_stt_auto_follow
import churchpresenter.composeapp.generated.resources.bible_stt_auto_follow_hint
import churchpresenter.composeapp.generated.resources.bible_stt_clear
import churchpresenter.composeapp.generated.resources.bible_stt_src_explicit
import churchpresenter.composeapp.generated.resources.bible_stt_src_reverse
import churchpresenter.composeapp.generated.resources.bible_stt_src_continuation
import churchpresenter.composeapp.generated.resources.bible_stt_src_chapter_scan
import churchpresenter.composeapp.generated.resources.bible_stt_src_chapter_history
import churchpresenter.composeapp.generated.resources.bible_stt_text_match_hint
import churchpresenter.composeapp.generated.resources.bible_stt_track_transcription
import churchpresenter.composeapp.generated.resources.bible_stt_track_translation
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
import churchpresenter.composeapp.generated.resources.swap_bibles
import churchpresenter.composeapp.generated.resources.swap_bibles_hint
import churchpresenter.composeapp.generated.resources.verse
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import org.churchpresenter.app.churchpresenter.composables.initialPassClickable
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import org.churchpresenter.app.churchpresenter.composables.rememberTokenGate
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isMetaPressed

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

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedVerseItem) {
        selectedVerseItem?.let { item ->
            if (!viewModel.isFullyLoadedFlow.value) {
                viewModel.isFullyLoadedFlow.first { it }
            }
            val found = viewModel.selectVerseByDetails(item.bookName, item.chapter, item.verseNumber, item.verseRange)
            if (found) {
                focusRequester.requestFocus()
            }
        }
    }

    // ── Scripture detection via the Bible Lookup Engine ────────────────────────
    // The engine link itself (start/stop on STT connect/disconnect) is owned by MainDesktop so it
    // survives tab switches; here we only read its connection state and the detected rows below.
    val sttConnected = sttManager?.connected?.value == true
    val engineSettings = appSettings.bibleEngineSettings
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

    // On startup (split mode), seed the live panel with the current left selection
    // (Genesis 1:1 by default) so the right side isn't blank before the first Go Live.
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
            presenterManager?.let { if (it.bibleHold.value) it.setBibleHold(false) }
            onPresenting(Presenting.BIBLE)
            TrainingDataLogger.logLiveReference(
                book       = viewModel.canonicalBookIdForDisplayIndex(viewModel.selectedBookIndex.value),
                chapter    = primary.chapter,
                verseStart = primary.verseNumber,
                verseEnd   = null,
                source     = "manual",
                segmentId  = viewModel.lastDetectionSegmentId,
                autoFollow = viewModel.autoFollowEnabled.value,
            )
        }
    }

    // [source] is logged to the training data: "manual" for an operator action (button / double-click
    // / Enter) or "auto" when auto-follow drove the go-live from an engine detection. [matchType] is
    // the triggering detection's engine match type, when this go-live traces back to one.
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
            // Canonical book id (not the raw display position) so the ground-truth log is comparable
            // to the engine's canonical detection log.
            val bookNum = viewModel.canonicalBookIdForDisplayIndex(viewModel.selectedBookIndex.value)
            // Derive the displayed span from the primary verse itself: its range string ("1-3",
            // "2,4,5") when a multi-verse passage is on screen, else the single verse number. This
            // captures the full range even when shown without the multi-verse toggle (the previous
            // toggle-gated logic logged only the first verse).
            val displayedNums = primaryVerse.verseRange
                .takeIf { it.isNotBlank() }
                ?.split(",", "-")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(primaryVerse.verseNumber)
            val verseStart = displayedNums.min()
            val verseEnd = displayedNums.max().takeIf { it > verseStart }
            TrainingDataLogger.logLiveReference(
                book       = bookNum,
                chapter    = primaryVerse.chapter,
                verseStart = verseStart,
                verseEnd   = verseEnd,
                source     = source,
                segmentId  = viewModel.lastDetectionSegmentId,
                autoFollow = viewModel.autoFollowEnabled.value,
                matchType  = matchType,
            )
            // If this go-live overrode the engine's top suggestion, log it as a correction (engine
            // said X, operator showed Y) — labeled training data for false positives.
            viewModel.logGoLiveCorrection(viewModel.selectedBookIndex.value, primaryVerse.chapter, verseStart)
        }
        if (viewModel.multiVerseEnabled.value) {
            viewModel.clearMultiVerseSelection()
        }
        presenterManager?.let { if (it.bibleHold.value) it.setBibleHold(false) }
        onPresenting(Presenting.BIBLE)
    }

    // Auto-follow: when a detection navigates with go-live requested, present it for real (content +
    // switch the presenter to BIBLE), not just select it. Reuses the manual go-live path so history,
    // stats and training logging happen too.
    val autoFollowLiveToken by viewModel.autoFollowLiveToken
    // Seeded (via rememberTokenGate) with the token value at composition time so detections that
    // happened while the tab was inactive (AnimatedContent destroys BibleTab on switch) don't re-fire
    // go-live on re-entry.
    val autoFollowTokenGate = rememberTokenGate(autoFollowLiveToken)
    LaunchedEffect(autoFollowLiveToken) {
        if (!autoFollowTokenGate.consume()) return@LaunchedEffect
        goLiveWithHistory(source = viewModel.autoFollowLiveSource.value, matchType = viewModel.autoFollowLiveMatchType.value)
    }

    // Only push to presenter when:
    //  - not currently presenting (free browsing always updates preview), OR
    //  - an explicit verse selection happened (token changed) while presenting
    LaunchedEffect(verseSelectionToken) {
        // In multi-verse mode while presenting, don't update until Go Live is pressed
        if (viewModel.multiVerseEnabled.value && currentIsPresenting) return@LaunchedEffect
        // In split browse mode, never auto-live on browse — only explicit Go Live updates the live panel
        if (splitBrowseMode) return@LaunchedEffect
        if (verses.isNotEmpty() && selectedVerseIndex >= 0 && selectedVerseIndex < verses.size) {
            val selectedVerses = viewModel.getSelectedVerses()
            if (selectedVerses.isNotEmpty()) {
                onVerseSelected(selectedVerses)
                // Log manual navigation while live. Skip when auto-follow also incremented the
                // token this frame — goLiveWithHistory already logs that case with source="auto".
                if (currentIsPresenting && autoFollowLiveToken == autoFollowTokenGate.lastHandled) {
                    val primary = selectedVerses.first()
                    val bookNum = viewModel.canonicalBookIdForDisplayIndex(viewModel.selectedBookIndex.value)
                    TrainingDataLogger.logLiveReference(
                        book       = bookNum,
                        chapter    = primary.chapter,
                        verseStart = primary.verseNumber,
                        verseEnd   = null,
                        source     = "manual",
                        segmentId  = viewModel.lastDetectionSegmentId,
                        autoFollow = viewModel.autoFollowEnabled.value,
                    )
                }
            }
        }
    }

    // While not presenting, also update preview when chapter loads so the first verse shows
    LaunchedEffect(verses.size) {
        if (!currentIsPresenting && !splitBrowseMode && verses.isNotEmpty()) {
            val selectedVerses = viewModel.getSelectedVerses()
            if (selectedVerses.isNotEmpty()) onVerseSelected(selectedVerses)
        }
    }

    // Auto-pause when user navigates to a different chapter or book while presenting — except
    // when it's just a sequential chapter advance (Left/Right arrow-key continuation, including
    // rolling past a chapter's last verse), which is a deliberate continuation of what's live,
    // not browsing away from it.
    val prevBookRef = remember { mutableStateOf(selectedBookIndex) }
    val prevChapterRef = remember { mutableStateOf(selectedChapter) }
    LaunchedEffect(selectedBookIndex, selectedChapter) {
        val bookChanged = selectedBookIndex != prevBookRef.value
        val chapterChanged = selectedChapter != prevChapterRef.value
        prevBookRef.value = selectedBookIndex
        prevChapterRef.value = selectedChapter
        val wasSequentialAdvance = viewModel.consumeSequentialChapterAdvance()
        if ((bookChanged || chapterChanged) && !splitBrowseMode && currentIsPresenting && !wasSequentialAdvance) {
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



    // Compact Auto / Reference / Text mode chip, shown inside the search field (leading slot).
    @Composable
    fun SearchModeChip(modifier: Modifier = Modifier) {
        val (label, container, content) = when (searchMode) {
            BibleSearchMode.AUTO -> Triple(
                Res.string.bible_search_mode_auto,
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.onPrimary
            )
            BibleSearchMode.REFERENCE -> Triple(
                Res.string.bible_search_mode_reference,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.onSecondary
            )
            BibleSearchMode.TEXT -> Triple(
                Res.string.bible_search_mode_text,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.onTertiary
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
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.05.sp
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }

    @Composable
    fun DragHandle(onDragEnd: () -> Unit = ::saveColWidths, onDrag: (Float) -> Unit) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> onDrag(delta) },
                    onDragStopped = { onDragEnd() }
                )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { handleKeyEvent(it) }
    ) {
        // ── Search row ────────────────────────────────────────────────
        val searchPlaceholder = stringResource(Res.string.bible_smart_search_hint)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)) {
            val searchIsNarrow = maxWidth < 440.dp

            if (searchIsNarrow) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BibleSearchField(
                        value = searchQuery,
                        placeholder = searchPlaceholder,
                        onValueChange = { viewModel.onSmartQueryChanged(it) },
                        onClear = { viewModel.clearSearch(); focusRequester.requestFocus() },
                        onSubmit = { viewModel.submitSmartQuery(); focusRequester.requestFocus() },
                        onFocusChanged = { searchFieldFocused = it },
                        modeChip = { SearchModeChip() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DropdownSelector(
                            label = stringResource(Res.string.scope),
                            items = scopeOptions,
                            selected = selectedScope,
                            onSelectedChange = { newValue ->
                                viewModel.updateSelectedScopeIndex(scopeOptions.indexOf(newValue).coerceAtLeast(0))
                            }
                        )
                        DropdownSelector(
                            label = stringResource(Res.string.mode),
                            items = modeOptions,
                            selected = selectedMode,
                            onSelectedChange = { newValue ->
                                viewModel.updateSelectedModeIndex(modeOptions.indexOf(newValue).coerceAtLeast(0))
                            }
                        )
                        Box(
                            modifier = Modifier.size(42.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    viewModel.submitSmartQuery(); focusRequester.requestFocus()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    BibleSearchField(
                        value = searchQuery,
                        placeholder = searchPlaceholder,
                        onValueChange = { viewModel.onSmartQueryChanged(it) },
                        onClear = { viewModel.clearSearch(); focusRequester.requestFocus() },
                        onSubmit = { viewModel.submitSmartQuery(); focusRequester.requestFocus() },
                        onFocusChanged = { searchFieldFocused = it },
                        modeChip = { SearchModeChip() },
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelector(
                        label = stringResource(Res.string.scope),
                        items = scopeOptions,
                        selected = selectedScope,
                        onSelectedChange = { newValue ->
                            viewModel.updateSelectedScopeIndex(scopeOptions.indexOf(newValue).coerceAtLeast(0))
                        }
                    )
                    DropdownSelector(
                        label = stringResource(Res.string.mode),
                        items = modeOptions,
                        selected = selectedMode,
                        onSelectedChange = { newValue ->
                            viewModel.updateSelectedModeIndex(modeOptions.indexOf(newValue).coerceAtLeast(0))
                        }
                    )
                    Box(
                        modifier = Modifier.size(42.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                viewModel.submitSmartQuery(); focusRequester.requestFocus()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Detection status + controls & detected references ──
        // Only shown when STT is actually connected — at first launch the Bible tab stays clean
        // with just navigation and verse display.
        if (engineSettings.enabled && sttConnected) {
            val levelName = when (textMatchLevel) {
                TextMatchLevel.OFF -> stringResource(Res.string.bible_stt_level_off)
                TextMatchLevel.CONSERVATIVE -> stringResource(Res.string.bible_stt_level_conservative)
                TextMatchLevel.BALANCED -> stringResource(Res.string.bible_stt_level_balanced)
                TextMatchLevel.AGGRESSIVE -> stringResource(Res.string.bible_stt_level_aggressive)
            }
            // ── Controls row: status + auto-follow + reverse-lookup level + clear ──
            val engineStartFailed = bibleEngineClient?.startFailed?.value == true
            val engineConnected = bibleEngineClient?.connected?.value == true
            val sttConnecting = sttManager.connecting.value == true
            val sttConnectError = sttManager.connectError.value == true
            val sttReconnecting = sttManager.reconnecting.value == true
            val noBibleSelected = appSettings.bibleSettings.primaryBible.isBlank() &&
                appSettings.bibleSettings.secondaryBible.isBlank()
            val sttReceiving = sttManager.inProgressText.value.isNotBlank() || sttManager.segments.isNotEmpty()
            val statusIsError = engineStartFailed || noBibleSelected || sttConnectError
            val statusText = when {
                engineStartFailed -> stringResource(Res.string.bible_stt_engine_unavailable)
                noBibleSelected -> stringResource(Res.string.bible_stt_no_bible)
                sttConnected && !engineConnected -> stringResource(Res.string.bible_stt_engine_connecting)
                sttConnected && !sttReceiving && detectedReferences.isEmpty() ->
                    stringResource(Res.string.bible_stt_waiting_for_stt)
                sttConnected -> stringResource(Res.string.bible_stt_listening)
                sttReconnecting -> stringResource(Res.string.stt_status_reconnecting)
                sttConnectError -> stringResource(Res.string.stt_status_unreachable)
                sttConnecting -> stringResource(Res.string.stt_status_connecting)
                else -> stringResource(Res.string.stt_status_not_connected)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (statusIsError) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = if (statusIsError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
                // Auto-follow flat button
                TooltipArea(tooltip = {
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = stringResource(Res.string.bible_stt_auto_follow_hint),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }) {
                    Box(
                        modifier = Modifier
                            .height(27.dp)
                            .background(
                                if (autoFollowEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (autoFollowEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                val next = !autoFollowEnabled
                                viewModel.setAutoFollow(next)
                                onSettingsChange { it.copy(bibleEngineSettings = it.bibleEngineSettings.copy(autoFollow = next)) }
                            }
                            .padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = if (autoFollowEnabled) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = stringResource(Res.string.bible_stt_auto_follow),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (autoFollowEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                // Text match flat button (cycles Off → Conservative → Balanced → Aggressive)
                TooltipArea(tooltip = {
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = stringResource(Res.string.bible_stt_text_match_hint),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }) {
                Box(
                    modifier = Modifier
                        .height(27.dp)
                        .background(
                            if (textMatchLevel != TextMatchLevel.OFF) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (textMatchLevel != TextMatchLevel.OFF) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            val all = TextMatchLevel.values()
                            val next = all[(textMatchLevel.ordinal + 1) % all.size]
                            viewModel.setTextMatchLevel(next)
                            onSettingsChange { it.copy(bibleEngineSettings = it.bibleEngineSettings.copy(textMatchLevel = next.name.lowercase())) }
                        }
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = if (textMatchLevel != TextMatchLevel.OFF) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${stringResource(Res.string.bible_stt_match_label)}: $levelName",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (textMatchLevel != TextMatchLevel.OFF) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                }
                if (detectedReferences.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearDetectedReferences() },
                        modifier = Modifier.size(27.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.bible_stt_clear),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // ── Detected references — at most 5 rows tall, scrolls beyond ──
            val detRowHeight = 24.dp
            val detMaxVisibleRows = 5
            if (detectedReferences.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val detScroll = rememberScrollState()
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = detRowHeight * detMaxVisibleRows)) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .verticalScroll(detScroll)
                        .padding(end = 10.dp)
                ) {
                detectedReferences.forEachIndexed { idx, ref ->
                val isSelected = idx == selectedDetectionIdx
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                        .drawBehind {
                            if (isSelected) drawRect(color = Color(0xFFC4972A), size = Size(4f, size.height))
                        }
                        .initialPassCombinedClickable(
                            onClick = { selectedDetectionIdx = idx; viewModel.applyDetectedReference(ref); focusRequester.requestFocus() },
                            onDoubleClick = { selectedDetectionIdx = idx; viewModel.applyDetectedReference(ref, goLiveSource = "detection"); focusRequester.requestFocus() }
                        )
                        .padding(start = 12.dp, top = 4.dp, end = 6.dp, bottom = 4.dp)
                ) {
                    // Fixed-width icon column (source markers + transcription/translation markers) so
                    // every reference + verse text lines up vertically, regardless of marker count.
                    Row(
                        modifier = Modifier.width(96.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ref.sources.forEach { src ->
                            val (icon, descRes, tint) = when (src) {
                                DetectionSource.EXPLICIT -> Triple(
                                    Icons.Filled.RecordVoiceOver, Res.string.bible_stt_src_explicit,
                                    MaterialTheme.colorScheme.primary
                                )
                                DetectionSource.REVERSE -> Triple(
                                    Icons.Filled.FormatQuote, Res.string.bible_stt_src_reverse,
                                    MaterialTheme.colorScheme.tertiary
                                )
                                DetectionSource.CONTINUATION -> Triple(
                                    Icons.AutoMirrored.Filled.ArrowForward, Res.string.bible_stt_src_continuation,
                                    MaterialTheme.colorScheme.secondary
                                )
                                DetectionSource.CHAPTER_SCAN -> Triple(
                                    Icons.AutoMirrored.Filled.ManageSearch, Res.string.bible_stt_src_chapter_scan,
                                    MaterialTheme.colorScheme.tertiary
                                )
                                DetectionSource.CHAPTER_HISTORY -> Triple(
                                    Icons.Filled.History, Res.string.bible_stt_src_chapter_history,
                                    MaterialTheme.colorScheme.tertiary
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
                        // Track markers (mic = transcription, globe = translation) grouped with the
                        // source markers, before the reference; shown only when that track corroborated.
                        listOf(
                            Triple(DetectionTrack.TRANSCRIPTION, Icons.Filled.Mic, Res.string.bible_stt_track_transcription),
                            Triple(DetectionTrack.TRANSLATION, Icons.Filled.Public, Res.string.bible_stt_track_translation),
                        ).forEach { (track, icon, descRes) ->
                            if (track in ref.tracks) {
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
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(Modifier.width(3.dp))
                            }
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
                if (detectedReferences.size > detMaxVisibleRows) {
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(detScroll)
                    )
                }
            }
            }
        }

        // ── Main content ─────────────────────────────────────────────
        if (appSettings.bibleSettings.primaryBible.isBlank()) {
            // ── Empty state: primary bible not configured ─────────────
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
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(31.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.found_results, searchResults.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            val holdLiveStr = stringResource(Res.string.hold_live)
            val swapBiblesStr = stringResource(Res.string.swap_bibles)
            val goLiveStr = stringResource(Res.string.go_live)
            val addScheduleStr = stringResource(Res.string.add_to_schedule)

            // ── Unified column headers row ───────────────────────────────
            val accentColor = MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 31.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(with(density) { colWBook.toDp() }).padding(start = 12.dp)) {
                    Text(
                        text = stringResource(Res.string.book).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                VerticalDivider(modifier = Modifier.height(16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Box(modifier = Modifier.width(with(density) { colWChapter.toDp() }).padding(start = 12.dp)) {
                    Text(
                        text = stringResource(Res.string.chapter).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                VerticalDivider(modifier = Modifier.height(16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.verse).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.weight(1f))
                    // Combined hold + kbd hint pill
                    val holdPillActive = presenterManager != null && !splitBrowseMode
                    val holdLiveState = presenterManager?.bibleHold?.value ?: false
                    TooltipArea(
                        tooltip = {
                            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall) {
                                Text(
                                    if (holdPillActive) holdLiveStr else "Ctrl+Click to toggle, Shift+Click for range",
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .height(27.dp)
                                .background(
                                    when {
                                        holdLiveState -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    when {
                                        holdLiveState -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .then(
                                    if (holdPillActive)
                                        Modifier.clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            presenterManager.setBibleHold(!holdLiveState)
                                            focusRequester.requestFocus()
                                        }
                                    else Modifier
                                )
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_pause),
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = when {
                                        holdLiveState -> MaterialTheme.colorScheme.onError
                                        holdPillActive -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    }
                                )
                                Text(
                                    "⌘/Ctrl Shift",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = when {
                                        holdLiveState -> MaterialTheme.colorScheme.onError
                                        holdPillActive -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                    }
                                )
                            }
                        }
                    }
                    // STT mic button — visible once a successful connection has been made to the current URL
                    val sttEverConnectedToCurrentUrl = appSettings.sttSettings.lastConnectedUrl.isNotBlank() &&
                        appSettings.sttSettings.lastConnectedUrl == appSettings.sttSettings.serverUrl
                    if (sttEverConnectedToCurrentUrl && sttManager != null) {
                        val sttActionStr = if (sttConnected) stringResource(Res.string.stt_disconnect) else stringResource(Res.string.stt_connect)
                        ActionIconButton(
                            onClick = {
                                if (sttConnected) sttManager.disconnect()
                                else sttManager.connect(appSettings.sttSettings.serverUrl)
                                focusRequester.requestFocus()
                            },
                            tooltipText = sttActionStr,
                            icon = Icons.Filled.Mic,
                            containerColor = if (sttConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (sttConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Swap Bibles (blue)
                    ActionIconButton(
                        onClick = {
                            onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.swapped()) }
                            focusRequester.requestFocus()
                        },
                        tooltipText = swapBiblesStr,
                        painter = painterResource(Res.drawable.ic_swap),
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        tooltipContent = {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(stringResource(Res.string.swap_bibles_hint), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("${stringResource(Res.string.primary_bible)} ${appSettings.bibleSettings.primaryBible.substringBeforeLast('.').ifEmpty { "-" }}", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall)
                                Text("${stringResource(Res.string.secondary_bible)} ${appSettings.bibleSettings.secondaryBible.substringBeforeLast('.').ifEmpty { "-" }}", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    )
                    // Add to Schedule (teal)
                    AddToScheduleButton(
                        onClick = {
                            viewModel.addCurrentVerseToSchedule { bookName, chapter, verseNumber, verseText, verseRange ->
                                onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText, verseRange)
                            }
                            focusRequester.requestFocus()
                        },
                        tooltipText = addScheduleStr
                    )
                    // Go Live (amber)
                    GoLiveButton(
                        onClick = { goLiveWithHistory(); focusRequester.requestFocus() },
                        tooltipText = goLiveStr
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Three-column browser ─────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 4.dp)) {

                // Book column (resizable)
                Column(modifier = Modifier.width(with(density) { colWBook.toDp() }).fillMaxHeight()) {
                    BibleBrowserColumn(
                        items = filteredBooks,
                        selectedIndex = filteredBooks.indexOf(books.getOrNull(selectedBookIndex) ?: "").coerceAtLeast(0),
                        singleLine = true,
                        onItemSelected = { index ->
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
                    BibleBrowserColumn(
                        items = filteredChapters,
                        selectedIndex = filteredChapters.indexOf(selectedChapter.toString()).coerceAtLeast(0),
                        centerText = true,
                        rowHeight = 31.dp,
                        onItemSelected = { index ->
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

                // Right area: verse list + live panel + history
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

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
                                            if (event.type == PointerEventType.Press && event.button?.isSecondary == true) {
                                                val pos = event.changes.first().position
                                                verseContextMenuOffset = with(density) { DpOffset(pos.x.toDp(), pos.y.toDp()) }
                                            }
                                        }
                                    }
                                }
                            ) {
                                val multiIndicesInFiltered = viewModel.selectedVerseIndices
                                    .mapNotNull { realIdx ->
                                        val verseStr = verses.getOrNull(realIdx)
                                        verseStr?.let { filteredVerses.indexOf(it).takeIf { i -> i >= 0 } }
                                    }
                                    .toSet()
                                    .takeIf { it.isNotEmpty() }

                                BibleVerseColumn(
                                    verses = filteredVerses,
                                    selectedIndex = if (filteredVerses.isEmpty()) -1 else {
                                        val currentVerse = verses.getOrNull(selectedVerseIndex)
                                        filteredVerses.indexOf(currentVerse).coerceAtLeast(0)
                                    },
                                    selectedIndices = multiIndicesInFiltered,
                                    accentColor = accentColor,
                                    onItemSelected = { index ->
                                        val verseText = filteredVerses.getOrNull(index)
                                        verseText?.let {
                                            val realIndex = verses.indexOf(it)
                                            if (realIndex >= 0) viewModel.selectVerse(realIndex)
                                        }
                                        focusRequester.requestFocus()
                                    },
                                    onItemDoubleClicked = { _ -> goLiveWithHistory() },
                                    onItemCtrlClicked = { index ->
                                        val verseText = filteredVerses.getOrNull(index)
                                        verseText?.let {
                                            val realIndex = verses.indexOf(it)
                                            if (realIndex >= 0) viewModel.ctrlClickVerse(realIndex)
                                        }
                                    },
                                    onItemShiftClicked = { index ->
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
                                        leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
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
                                        leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary) },
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
                                        leadingIcon = { Icon(imageVector = Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { goLiveWithHistory(); focusRequester.requestFocus(); showVerseContextMenu = false }
                                    )
                                }
                            }
                        }

                        // Live panel (split mode)
                        if (isSplitActive) {
                            DragHandle(onDragEnd = ::saveColWSplit) { amount ->
                                colWSplit = (colWSplit - amount).coerceIn(with(density) { 150.dp.toPx() }, with(density) { 600.dp.toPx() })
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
                                                TrainingDataLogger.logLiveReference(
                                                    book       = viewModel.canonicalBookIdForDisplayIndex(viewModel.selectedBookIndex.value),
                                                    chapter    = primary.chapter,
                                                    verseStart = primary.verseNumber,
                                                    verseEnd   = null,
                                                    source     = "manual",
                                                    segmentId  = viewModel.lastDetectionSegmentId,
                                                    autoFollow = viewModel.autoFollowEnabled.value,
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                    } // end verse + live Row
                    } // end BoxWithConstraints

                    // ── History panel ──────────────────────────────────────
                    if (viewModel.history.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { historyExpanded = !historyExpanded }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painter = painterResource(if (historyExpanded) Res.drawable.ic_arrow_down else Res.drawable.ic_arrow_up), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = stringResource(Res.string.bible_history), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
                            Spacer(modifier = Modifier.weight(1f))
                            TooltipArea(
                                tooltip = {
                                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall) {
                                        Text(stringResource(Res.string.bible_history_clear), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                            ) {
                                IconButton(onClick = { viewModel.clearHistory() }) {
                                    Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = stringResource(Res.string.bible_history_clear), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        AnimatedVisibility(visible = historyExpanded) {
                            val historyListState = rememberLazyListState()
                            LaunchedEffect(viewModel.history.size) { historyListState.scrollToItem(0) }
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                LazyColumn(state = historyListState, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
                                    itemsIndexed(viewModel.history) { idx, entry ->
                                        Text(
                                            text = buildAnnotatedString {
                                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) { append(entry.displayText) }
                                                append("  ${entry.verseText}")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            modifier = Modifier.fillMaxWidth()
                                                .background(
                                                    if (idx == selectedHistoryIdx) MaterialTheme.colorScheme.surfaceVariant
                                                    else if (idx % 2 == 0) MaterialTheme.colorScheme.surface
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                )
                                                .drawBehind {
                                                    if (idx == selectedHistoryIdx) drawRect(color = Color(0xFFC4972A), size = Size(4f, size.height))
                                                }
                                                .initialPassCombinedClickable(
                                                    onClick = {
                                                        selectedHistoryIdx = idx
                                                        viewModel.selectVerseByDetails(entry.bookName, entry.chapter, entry.verseNumber, entry.verseRange)
                                                        focusRequester.requestFocus()
                                                    },
                                                    onDoubleClick = {
                                                        selectedHistoryIdx = idx
                                                        viewModel.selectVerseByDetails(entry.bookName, entry.chapter, entry.verseNumber, entry.verseRange, goLiveSource = "history")
                                                        focusRequester.requestFocus()
                                                    }
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                VerticalScrollbar(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), adapter = rememberScrollbarAdapter(scrollState = historyListState))
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

    LaunchedEffect(verses) {
        val firstLiveIndex = verses.indexOfFirst { verse ->
            verse.substringBefore(". ").toIntOrNull()?.let { it in liveVerseNumbers } == true
        }
        if (firstLiveIndex >= 0) listState.scrollToItem(firstLiveIndex)
    }

    LaunchedEffect(liveVerseNumbers) {
        val firstLiveIndex = verses.indexOfFirst { verse ->
            verse.substringBefore(". ").toIntOrNull()?.let { it in liveVerseNumbers } == true
        }
        if (firstLiveIndex < 0 || firstLiveIndex + 1 >= verses.size) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val lastVisible = visibleItems.lastOrNull() ?: return@LaunchedEffect
        if (firstLiveIndex < lastVisible.index - 1) return@LaunchedEffect
        val viewportEnd = layoutInfo.viewportEndOffset
        val itemHeight = lastVisible.size.toFloat()
        val target2 = visibleItems.firstOrNull { it.index == firstLiveIndex + 2 }
        val target1 = visibleItems.firstOrNull { it.index == firstLiveIndex + 1 }
        val scrollAmount = when {
            target2 != null -> ((target2.offset + target2.size) - viewportEnd).toFloat().coerceAtLeast(0f)
            target1 != null -> ((target1.offset + target1.size) - viewportEnd + itemHeight).coerceAtLeast(0f)
            else -> itemHeight * 2
        }
        if (scrollAmount > 0f) listState.scroll { scrollBy(scrollAmount) }
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
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 13.5.sp * 1.6f
                    ),
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

@Composable
private fun BibleSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modeChip: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.padding(start = 11.dp).size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) {
                            onSubmit(); true
                        } else false
                    },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(modifier = Modifier.padding(end = 6.dp)) {
            modeChip()
        }
    }
}

@Composable
private fun BibleBrowserColumn(
    items: List<String>,
    selectedIndex: Int,
    singleLine: Boolean = false,
    centerText: Boolean = false,
    rowHeight: Dp = 28.dp,
    onItemSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < items.size) {
            listState.animateScrollToItem(selectedIndex.coerceAtMost(items.size - 1))
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { onItemSelected(index) }
                        .padding(start = 12.dp, end = 4.dp),
                    contentAlignment = if (centerText) Alignment.Center else Alignment.CenterStart
                ) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (centerText) TextAlign.Center else TextAlign.Start
                    )
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BibleVerseColumn(
    verses: List<String>,
    selectedIndex: Int,
    accentColor: Color,
    selectedIndices: Set<Int>? = null,
    onItemSelected: (Int) -> Unit,
    onItemDoubleClicked: (Int) -> Unit = {},
    onItemCtrlClicked: (Int) -> Unit = {},
    onItemShiftClicked: (Int) -> Unit = {},
    onRightClicked: (Int) -> Unit = {}
) {
    val listState = rememberLazyListState()
    LaunchedEffect(verses) {
        if (selectedIndex >= 0 && selectedIndex < verses.size) {
            listState.scrollToItem(selectedIndex.coerceAtMost(verses.size - 1))
        }
    }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0 || selectedIndex + 1 >= verses.size) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val lastVisible = visibleItems.lastOrNull() ?: return@LaunchedEffect
        if (selectedIndex < lastVisible.index - 1) return@LaunchedEffect
        val viewportEnd = layoutInfo.viewportEndOffset
        val itemHeight = lastVisible.size.toFloat()
        val target2 = visibleItems.firstOrNull { it.index == selectedIndex + 2 }
        val target1 = visibleItems.firstOrNull { it.index == selectedIndex + 1 }
        val scrollAmount = when {
            target2 != null -> ((target2.offset + target2.size) - viewportEnd).toFloat().coerceAtLeast(0f)
            target1 != null -> ((target1.offset + target1.size) - viewportEnd + itemHeight).coerceAtLeast(0f)
            else -> itemHeight * 2
        }
        if (scrollAmount > 0f) listState.scroll { scrollBy(scrollAmount) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 12.dp)
        ) {
            itemsIndexed(verses) { index, verseStr ->
                val isSelected = index == selectedIndex || (selectedIndices != null && index in selectedIndices)
                Text(
                    text = verseStr,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 13.5.sp * 1.6f,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                        .pointerInput(index) {
                            var lastClickTime = 0L
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    if (event.type == PointerEventType.Press) {
                                        val isRight = event.button?.isSecondary == true
                                        val mods = event.keyboardModifiers
                                        val isCtrl = mods.isCtrlPressed || mods.isMetaPressed
                                        val isShift = mods.isShiftPressed
                                        when {
                                            isRight -> onRightClicked(index)
                                            isCtrl -> onItemCtrlClicked(index)
                                            isShift -> onItemShiftClicked(index)
                                            else -> {
                                                val now = System.currentTimeMillis()
                                                val isDouble = now - lastClickTime < 300L
                                                lastClickTime = now
                                                if (isDouble) onItemDoubleClicked(index) else onItemSelected(index)
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
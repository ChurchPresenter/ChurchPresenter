package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.zIndex
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.bible_history
import churchpresenter.composeapp.generated.resources.bible_cross_references_none
import churchpresenter.composeapp.generated.resources.bible_cross_references_often_next
import churchpresenter.composeapp.generated.resources.bible_cross_references_passage
import churchpresenter.composeapp.generated.resources.bible_cross_references_source_count
import churchpresenter.composeapp.generated.resources.bible_cross_references_title
import churchpresenter.composeapp.generated.resources.bible_history_clear
import churchpresenter.composeapp.generated.resources.bible_next_verse_speed_balanced
import churchpresenter.composeapp.generated.resources.bible_next_verse_speed_fast
import churchpresenter.composeapp.generated.resources.bible_next_verse_speed_label
import churchpresenter.composeapp.generated.resources.bible_next_verse_speed_tooltip_balanced
import churchpresenter.composeapp.generated.resources.bible_next_verse_speed_tooltip_fast
import churchpresenter.composeapp.generated.resources.bible_no_primary_hint
import churchpresenter.composeapp.generated.resources.bible_no_primary_step1
import churchpresenter.composeapp.generated.resources.bible_no_primary_step2
import churchpresenter.composeapp.generated.resources.bible_no_primary_title
import churchpresenter.composeapp.generated.resources.bible_search_mode_auto
import churchpresenter.composeapp.generated.resources.bible_search_mode_reference
import churchpresenter.composeapp.generated.resources.bible_search_mode_text
import churchpresenter.composeapp.generated.resources.bible_search_mode_tooltip
import churchpresenter.composeapp.generated.resources.bible_smart_search_hint
import churchpresenter.composeapp.generated.resources.bible_stt_auto_follow
import churchpresenter.composeapp.generated.resources.bible_stt_auto_follow_hint
import churchpresenter.composeapp.generated.resources.bible_stt_clear
import churchpresenter.composeapp.generated.resources.bible_stt_detected_version_tooltip
import churchpresenter.composeapp.generated.resources.bible_stt_engine_connecting
import churchpresenter.composeapp.generated.resources.bible_stt_engine_stt_down
import churchpresenter.composeapp.generated.resources.bible_stt_engine_unavailable
import churchpresenter.composeapp.generated.resources.bible_stt_flag_missed
import churchpresenter.composeapp.generated.resources.bible_stt_flag_missed_hint
import churchpresenter.composeapp.generated.resources.bible_stt_flag_needs_live
import churchpresenter.composeapp.generated.resources.bible_stt_flag_premature
import churchpresenter.composeapp.generated.resources.bible_stt_flag_premature_hint
import churchpresenter.composeapp.generated.resources.bible_stt_flag_wrong
import churchpresenter.composeapp.generated.resources.bible_stt_flag_wrong_hint
import churchpresenter.composeapp.generated.resources.bible_stt_level_aggressive
import churchpresenter.composeapp.generated.resources.bible_stt_level_balanced
import churchpresenter.composeapp.generated.resources.bible_stt_level_conservative
import churchpresenter.composeapp.generated.resources.bible_stt_level_off
import churchpresenter.composeapp.generated.resources.bible_stt_listening
import churchpresenter.composeapp.generated.resources.bible_stt_match_label
import churchpresenter.composeapp.generated.resources.bible_stt_no_bible
import churchpresenter.composeapp.generated.resources.bible_stt_src_chapter_history
import churchpresenter.composeapp.generated.resources.bible_stt_src_chapter_scan
import churchpresenter.composeapp.generated.resources.bible_stt_src_continuation
import churchpresenter.composeapp.generated.resources.bible_stt_src_explicit
import churchpresenter.composeapp.generated.resources.bible_stt_src_reverse
import churchpresenter.composeapp.generated.resources.bible_stt_text_match_hint
import churchpresenter.composeapp.generated.resources.bible_stt_track_transcription
import churchpresenter.composeapp.generated.resources.bible_stt_track_translation
import churchpresenter.composeapp.generated.resources.bible_stt_waiting_for_stt
import churchpresenter.composeapp.generated.resources.bible_translation_order
import churchpresenter.composeapp.generated.resources.bible_translation_order_hint
import churchpresenter.composeapp.generated.resources.bible_translation_order_more
import churchpresenter.composeapp.generated.resources.bible_translation_order_panel_subtitle
import churchpresenter.composeapp.generated.resources.bible_translation_order_panel_title
import churchpresenter.composeapp.generated.resources.bible_verse_selection_hint
import churchpresenter.composeapp.generated.resources.book
import churchpresenter.composeapp.generated.resources.chapter
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.contains_phrase
import churchpresenter.composeapp.generated.resources.copy_verse
import churchpresenter.composeapp.generated.resources.current_book
import churchpresenter.composeapp.generated.resources.drag_to_reorder_translation
import churchpresenter.composeapp.generated.resources.entire_bible
import churchpresenter.composeapp.generated.resources.exact_match
import churchpresenter.composeapp.generated.resources.found_results
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.hold_live
import churchpresenter.composeapp.generated.resources.hold_live_modifier_hint
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_copy
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ic_drag_dots
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.ic_search
import churchpresenter.composeapp.generated.resources.ic_swap
import churchpresenter.composeapp.generated.resources.mode
import churchpresenter.composeapp.generated.resources.move_translation_down
import churchpresenter.composeapp.generated.resources.move_translation_up
import churchpresenter.composeapp.generated.resources.no_results_found
import churchpresenter.composeapp.generated.resources.primary_bible
import churchpresenter.composeapp.generated.resources.scope
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.secondary_bible
import churchpresenter.composeapp.generated.resources.stt_connect
import churchpresenter.composeapp.generated.resources.stt_disconnect
import churchpresenter.composeapp.generated.resources.stt_status_connecting
import churchpresenter.composeapp.generated.resources.stt_status_not_connected
import churchpresenter.composeapp.generated.resources.stt_status_reconnecting
import churchpresenter.composeapp.generated.resources.stt_status_unreachable
import churchpresenter.composeapp.generated.resources.swap_bibles
import churchpresenter.composeapp.generated.resources.swap_bibles_hint
import churchpresenter.composeapp.generated.resources.tab_focus_lost
import churchpresenter.composeapp.generated.resources.verse
import java.awt.Cursor
import java.awt.Window as AwtWindow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.FocusLostBanner
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import org.churchpresenter.app.churchpresenter.composables.focusRescuePressHook
import org.churchpresenter.app.churchpresenter.composables.initialPassClickable
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import org.churchpresenter.app.churchpresenter.composables.rememberFocusLostRescue
import org.churchpresenter.app.churchpresenter.composables.rememberTokenGate
import org.churchpresenter.app.churchpresenter.data.BibleBookAbbreviations
import org.churchpresenter.app.churchpresenter.data.CrossReferenceRepository
import org.churchpresenter.app.churchpresenter.data.formatCrossRefLabel
import org.churchpresenter.app.churchpresenter.data.aggregateCrossRefs
import org.churchpresenter.app.churchpresenter.data.mergeCrossRefs
import org.churchpresenter.app.churchpresenter.data.sharedCrossReferences
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseSequenceLog
import org.churchpresenter.app.churchpresenter.data.bibleDisplayNames
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.data.settings.moveBibleTranslation
import org.churchpresenter.app.churchpresenter.data.settings.swapBibleTranslations
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.utils.highlightRanges
import org.churchpresenter.app.churchpresenter.utils.isMultiTranslationPresentation
import org.churchpresenter.app.churchpresenter.utils.isSplitScreenBible
import org.churchpresenter.app.churchpresenter.viewmodel.BibleEngineClient
import org.churchpresenter.app.churchpresenter.viewmodel.BibleSearchMode
import org.churchpresenter.app.churchpresenter.viewmodel.BibleSttStatus
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.ContinuationSpeed
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionSource
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionTrack
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.viewmodel.TextMatchLevel
import org.churchpresenter.app.churchpresenter.viewmodel.bibleSttStatus
import org.churchpresenter.app.churchpresenter.viewmodel.filteredSelectionIndices
import org.churchpresenter.app.churchpresenter.viewmodel.formatVerseReference
import org.churchpresenter.app.churchpresenter.viewmodel.indexOfFirstLiveVerse
import org.churchpresenter.app.churchpresenter.viewmodel.nextLiveVerseNumber
import org.churchpresenter.app.churchpresenter.viewmodel.verseNumberOf
import org.churchpresenter.app.churchpresenter.viewmodel.verseSpan
import org.churchpresenter.app.churchpresenter.viewmodel.verseTextOf
import org.churchpresenter.app.churchpresenter.ui.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Narrowest useful cross-reference column: a reference and the first word or two of its verse. */
private val CROSS_REF_MIN_WIDTH = 120.dp

/** Widest: past this the column is taking space from the verse text it exists to support. */
private val CROSS_REF_MAX_WIDTH = 500.dp

/** How many verses of a multi-verse selection contribute cross-references. */
private const val CROSS_REF_RANGE_ANCHORS = 3

/** How many bundled references the column shows below the learned ones. */
private const val CROSS_REF_STATIC_LIMIT = 8

internal fun withBibleColumnWidths(settings: AppSettings, isMaximized: Boolean, bookWidthDp: Int, chapterWidthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(bibleColWidthBook = bookWidthDp, bibleColWidthChapter = chapterWidthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(bibleColWidthBook = bookWidthDp, bibleColWidthChapter = chapterWidthDp))

internal fun withBibleSplitPanelWidth(settings: AppSettings, isMaximized: Boolean, widthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(splitLivePanelWidth = widthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(splitLivePanelWidth = widthDp))

internal fun withBibleCrossRefPanelWidth(settings: AppSettings, isMaximized: Boolean, widthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(bibleColWidthCrossRef = widthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(bibleColWidthCrossRef = widthDp))

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun BibleTab(
    modifier: Modifier = Modifier,
    /** The hosting AWT window — used by the focus-lost rescue to heal AWT focus (see
     *  composables/FocusLostRescue.kt). */
    hostWindow: AwtWindow? = null,
    viewModel: BibleViewModel,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String, bookId: Int) -> Unit)? = null,
    selectedVerseItem: ScheduleItem.BibleVerseItem? = null,
    onVerseSelected: (List<SelectedVerse>) -> Unit = {},
    /** Instance Link Controller mode — non-null only when connected and controlling. Sends every
     *  verse go-live to the primary (always instant on the primary's side, no approval gate). */
    onInstanceLinkSendVerse: ((bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String) -> Unit)? = null,
    /** Instance Link Controller mode — non-null only when connected and controlling. Toggles Bible
     *  Hold on the primary (always instant, no approval gate). */
    onInstanceLinkSendBibleHold: ((hold: Boolean) -> Unit)? = null,
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    isPresenting: Boolean = false,
    presenterManager: PresenterManager? = null,
    statisticsManager: StatisticsManager? = null,
    /** Learns what tends to follow what, to suggest it in the cross-reference panel. */
    verseSequenceLog: VerseSequenceLog? = null,
    /** The bundled cross-references. Defaults to the shared instance; tests pass a fixture. */
    crossReferences: CrossReferenceRepository? = null,
    sttManager: STTManager? = null,
    bibleEngineClient: BibleEngineClient? = null,
    dialogDismissSignal: Int = 0,
) {
    // Hand the Bible modules any change to the active mode or its ordered file list. Multi mode
    // deliberately leaves legacy primary/secondary fields untouched, so those fields cannot be used
    // as the only effect keys. Whether a given change needs a re-read off disk or just a rearrange
    // of what is already loaded is BibleViewModel.updateSettings's call, not this key's.
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
            val found = viewModel.selectVerseByDetails(item.bookName, item.chapter, item.verseNumber, item.verseRange, bookId = item.bookId)
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
    val continuationSpeed by viewModel.continuationSpeed

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

    // Cross-reference column state
    val crossRefsEnabled = appSettings.bibleSettings.crossReferencesPanel
    val crossRefRepository = crossReferences ?: sharedCrossReferences
    var crossRefRows by remember { mutableStateOf<List<CrossRefRow>>(emptyList()) }
    var selectedCrossRefIdx by remember { mutableStateOf(-1) }
    /**
     * Where a click in this column has just sent the selection.
     *
     * While the selection is there the column keeps showing the passage it was describing, rather
     * than re-resolving around the verse it just sent you to. Two reasons, and the second is not
     * optional: exploring several of a verse's references in turn is the point of the column, and
     * a list that rebuilt on the first click would destroy the row under the pointer — making the
     * second click of a double-click land on whatever row replaced it, so "double-click to go
     * live" could never work here at all.
     */
    var crossRefNavigatedTo by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    /**
     * Bumped when the operator picks a starting point themselves, to re-resolve the column even
     * though nothing in the selection changed — clicking the very verse the column just sent you
     * to has to bring back that verse's own references rather than leave the previous list up.
     */
    var crossRefAnchorEpoch by remember { mutableStateOf(0) }
    /**
     * Fallback labels for references the loaded module does not contain, in the app's language.
     *
     * Every other label comes from the module itself, but a module with no Habakkuk cannot name
     * Habakkuk — and a row with no label at all would be worse than one in the wrong language.
     * Read here rather than in the panel because `stringResource` cannot be called from the effect
     * that resolves the rows.
     */
    val fallbackAbbreviations = BibleBookAbbreviations.abbreviationResourceIds.map { resource ->
        BibleBookAbbreviations.parseVariants(stringResource(resource)).firstOrNull().orEmpty()
    }
    // Re-resolve when the module changes, so labels and previews follow a translation switch.
    // LaunchedEffect does not observe Compose State reads, so this has to be a key of its own.
    val moduleBooks = viewModel.books.value

    /** The canonical verses the column is describing. */
    var crossRefAnchors by remember { mutableStateOf<List<Triple<Int, Int, Int>>>(emptyList()) }
    /** Whether those came from going live, as opposed to from browsing. */
    var crossRefAnchorIsLive by remember { mutableStateOf(false) }
    /**
     * The consecutive verses taken live in one chapter — the passage currently being read.
     *
     * A preacher reads down a passage and then moves to another book, and will not continue from
     * the verse they stopped on. Once two verses have been read in sequence the column pools their
     * references instead of describing the last one alone.
     */
    var crossRefRun by remember { mutableStateOf<List<Triple<Int, Int, Int>>>(emptyList()) }

    /**
     * Points the column at a verse that has just gone live, extending the passage being read.
     *
     * The run continues while the reading moves forward through one chapter, and starts over on
     * any jump — another book, another chapter, or back up this one — which is the moment the
     * passage has been left behind.
     */
    fun anchorLiveVerse(ref: Triple<Int, Int, Int>) {
        val previous = crossRefRun.lastOrNull()
        val continues = previous != null &&
            previous.first == ref.first && previous.second == ref.second && ref.third > previous.third
        crossRefRun = if (continues) crossRefRun + ref else listOf(ref)
        crossRefAnchors = listOf(ref)
        crossRefAnchorIsLive = true
        crossRefNavigatedTo = null
    }

    // Follow the browse selection, for every path that moves it — the verse list, the schedule,
    // the Companion API, auto-follow. This does NOT clear the run: looking ahead in the verse list
    // while a passage is being read should not throw away what has been read.
    LaunchedEffect(selectedBookIndex, selectedChapter, selectedVerseIndex, verseSelectionToken, crossRefAnchorEpoch) {
        val selectedNumbers = viewModel.getSelectedVerseNumbers().ifEmpty {
            listOfNotNull(verses.getOrNull(selectedVerseIndex)?.let(::verseNumberOf))
        }
        // TSK is per verse, so a long passage would produce a scroll of near-duplicates. Three
        // verses is enough for the head of the list to stay useful without the panel churning on
        // every shift-click.
        crossRefAnchors = selectedNumbers.take(CROSS_REF_RANGE_ANCHORS).mapNotNull { number ->
            viewModel.canonicalRefForDisplay(selectedBookIndex, selectedChapter, number)
                ?.let { (book, chapter, verse) -> verse?.let { Triple(book, chapter, it) } }
        }
        crossRefAnchorIsLive = false
    }

    // Whether the column is describing a passage being read rather than a single verse. Both
    // conditions matter: a run only means something while the anchor is still the live reading, so
    // browsing away shows that verse's own references without discarding the run.
    val crossRefPassageMode = crossRefAnchorIsLive && crossRefRun.size > 1

    // Resolve the column's contents. Keyed on the anchor, so a fast arrow-key scroll cancels the
    // in-flight resolution rather than queueing one per verse; and gated on the setting, so the
    // 3 MB dataset is never read while the panel is off.
    LaunchedEffect(
        crossRefsEnabled, crossRefAnchors, crossRefPassageMode, crossRefRun,
        // Picking the very verse this column sent you to changes no anchor, so without the epoch
        // the pin below would hold the previous list up for ever.
        crossRefAnchorEpoch, moduleBooks, fallbackAbbreviations,
    ) {
        if (!crossRefsEnabled || crossRefAnchors.isEmpty()) {
            crossRefRows = emptyList()
            crossRefNavigatedTo = null
            return@LaunchedEffect
        }
        // Sitting on the verse this column just sent us to: leave the list, and the highlight, be.
        if (crossRefAnchors.size == 1 && crossRefAnchors.first() == crossRefNavigatedTo) return@LaunchedEffect
        crossRefNavigatedTo = null

        // Anchored on the verse most recently reached, matching what goLiveWithHistory records, so
        // what is asked for and what was written use the same key.
        val learned = crossRefAnchors.first().let { (book, chapter, verse) ->
            verseSequenceLog?.successors(book, chapter, verse).orEmpty()
        }.map { crossRefRow(viewModel, fallbackAbbreviations, it.bookId, it.chapter, it.verse, null, learned = true) }

        crossRefRepository.ensureLoaded()
        val sources = if (crossRefPassageMode) crossRefRun else crossRefAnchors
        val perVerse = sources.map { (book, chapter, verse) -> crossRefRepository.forVerse(book, chapter, verse) }
        val references = if (crossRefPassageMode) {
            aggregateCrossRefs(perVerse, limit = CROSS_REF_STATIC_LIMIT).map {
                crossRefRow(
                    viewModel, fallbackAbbreviations, it.bookId, it.chapter, it.startVerse,
                    it.endVerse, learned = false, count = it.sourceCount,
                )
            }
        } else {
            mergeCrossRefs(perVerse, limit = CROSS_REF_STATIC_LIMIT).map {
                crossRefRow(viewModel, fallbackAbbreviations, it.bookId, it.chapter, it.verse, it.endVerse, learned = false)
            }
        }

        // A reference already offered as a habit is not repeated as a bare cross-reference.
        val learnedKeys = learned.map { Triple(it.bookId, it.chapter, it.verse) }.toSet()
        crossRefRows = learned + references.filter { Triple(it.bookId, it.chapter, it.verse) !in learnedKeys }
        selectedCrossRefIdx = -1
    }

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
            onInstanceLinkSendVerse?.invoke(primary.bookName, primary.chapter, primary.verseNumber, primary.verseText, primary.verseRange)
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
        // Parallel translations genuinely on screen, as opposed to merely configured.
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
        primaryVerse?.let { v ->
            onInstanceLinkSendVerse?.invoke(v.bookName, v.chapter, v.verseNumber, v.verseText, v.verseRange)
        }
        if (primaryVerse != null) {
            // Derive the displayed span from the primary verse itself: its range string ("1-3",
            // "2,4,5") when a multi-verse passage is on screen, else the single verse number. This
            // captures the full range even when shown without the multi-verse toggle (the previous
            // toggle-gated logic logged only the first verse).
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
            // If this go-live overrode the engine's top suggestion, log it as a correction (engine
            // said X, operator showed Y) — labeled training data for false positives.
            viewModel.logGoLiveCorrection(viewModel.selectedBookIndex.value, primaryVerse.chapter, verseStart)
            // Learn what follows what, for the cross-reference panel's "often next" suggestions.
            // Anchored on the span's start verse and on canonical numbering, so a range and a
            // single verse key the same way and a translation switch does not split the history.
            viewModel.canonicalRefForDisplay(
                viewModel.selectedBookIndex.value, primaryVerse.chapter, verseStart,
            )?.let { (book, chapter, verse) ->
                if (verse != null) {
                    verseSequenceLog?.recordGoLive(book, chapter, verse)
                    // The cross-reference column follows what went live, and this extends the
                    // passage being read.
                    anchorLiveVerse(Triple(book, chapter, verse))
                }
            }
        }
        if (viewModel.multiVerseEnabled.value) {
            viewModel.clearMultiVerseSelection()
        }
        presenterManager?.let { if (it.bibleHold.value) { it.setBibleHold(false); onInstanceLinkSendBibleHold?.invoke(false) } }
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
    val shortcuts = LocalShortcuts.current

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        // Don't intercept arrow keys when the search field has focus (cursor navigation)
        if (searchFieldFocused) return false

        val movingUp = shortcuts.matches(ShortcutAction.BIBLE_PREVIOUS_VERSE, event)
        val movingDown = shortcuts.matches(ShortcutAction.BIBLE_NEXT_VERSE, event)

        // In split mode, the prev/next-verse bindings navigate the live (right) panel
        if (splitBrowseMode && liveChapterVerses.isNotEmpty() && (movingUp || movingDown)) {
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

    // Focus-lost rescue: arrow-key verse/chapter navigation only works while the tab holds
    // keyboard focus AND the window is focused — full machinery in
    // composables/FocusLostRescue.kt (shared with Presentation/Songs).
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
            // The engine's OWN upstream STT link (engine_status broadcasts). Null = older engine /
            // not yet received — the proxy inference below stays authoritative in that case.
            val engineSttDown = bibleEngineClient?.engineSttConnected?.value == false
            val sttConnecting = sttManager.connecting.value == true
            val sttConnectError = sttManager.connectError.value == true
            val sttReconnecting = sttManager.reconnecting.value == true
            val noBibleSelected = appSettings.bibleSettings.primaryBible.isBlank() &&
                appSettings.bibleSettings.secondaryBible.isBlank() &&
                viewModel.primaryBible.value == null
            val sttReceiving = sttManager.inProgressText.value.isNotBlank() || sttManager.segments.isNotEmpty()
            val statusIsError = engineStartFailed || noBibleSelected || sttConnectError || engineSttDown
            // Engine reachable but ITS STT socket down is surfaced here (bible_stt_engine_stt_down):
            // previously invisible, because the app's own separate STT connection made the UI say
            // "Listening" while no transcript reached the engine at all.
            val statusText = when (bibleSttStatus(
                engineStartFailed = engineStartFailed,
                noBibleSelected = noBibleSelected,
                sttConnected = sttConnected,
                engineConnected = engineConnected,
                engineSttDown = engineSttDown,
                sttReceiving = sttReceiving,
                hasDetectedReferences = detectedReferences.isNotEmpty(),
                sttReconnecting = sttReconnecting,
                sttConnectError = sttConnectError,
                sttConnecting = sttConnecting,
            )) {
                BibleSttStatus.ENGINE_UNAVAILABLE -> stringResource(Res.string.bible_stt_engine_unavailable)
                BibleSttStatus.NO_BIBLE -> stringResource(Res.string.bible_stt_no_bible)
                BibleSttStatus.ENGINE_CONNECTING -> stringResource(Res.string.bible_stt_engine_connecting)
                BibleSttStatus.ENGINE_STT_DOWN -> stringResource(Res.string.bible_stt_engine_stt_down)
                BibleSttStatus.WAITING_FOR_STT -> stringResource(Res.string.bible_stt_waiting_for_stt)
                BibleSttStatus.LISTENING -> stringResource(Res.string.bible_stt_listening)
                BibleSttStatus.RECONNECTING -> stringResource(Res.string.stt_status_reconnecting)
                BibleSttStatus.UNREACHABLE -> stringResource(Res.string.stt_status_unreachable)
                BibleSttStatus.CONNECTING -> stringResource(Res.string.stt_status_connecting)
                BibleSttStatus.NOT_CONNECTED -> stringResource(Res.string.stt_status_not_connected)
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
                // Verse speed flat button (cycles Balanced → Fast) — only affects how fast the
                // engine confirms a verse while reading straight through several in a row.
                val verseSpeedName = when (continuationSpeed) {
                    ContinuationSpeed.BALANCED -> stringResource(Res.string.bible_next_verse_speed_balanced)
                    ContinuationSpeed.FAST -> stringResource(Res.string.bible_next_verse_speed_fast)
                }
                val verseSpeedHint = when (continuationSpeed) {
                    ContinuationSpeed.BALANCED -> stringResource(Res.string.bible_next_verse_speed_tooltip_balanced)
                    ContinuationSpeed.FAST -> stringResource(Res.string.bible_next_verse_speed_tooltip_fast)
                }
                TooltipArea(tooltip = {
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = verseSpeedHint,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }) {
                Box(
                    modifier = Modifier
                        .height(27.dp)
                        .background(
                            if (continuationSpeed != ContinuationSpeed.BALANCED) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (continuationSpeed != ContinuationSpeed.BALANCED) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            val all = ContinuationSpeed.values()
                            val next = all[(continuationSpeed.ordinal + 1) % all.size]
                            viewModel.setContinuationSpeed(next)
                            onSettingsChange { it.copy(bibleEngineSettings = it.bibleEngineSettings.copy(continuationSpeed = next.name.lowercase())) }
                        }
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = if (continuationSpeed != ContinuationSpeed.BALANCED) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${stringResource(Res.string.bible_next_verse_speed_label)}: $verseSpeedName",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (continuationSpeed != ContinuationSpeed.BALANCED) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                }
                if (engineSettings.helpDevMode) {
                    FlagPillButton(
                        icon = Icons.Filled.Flag,
                        label = stringResource(Res.string.bible_stt_flag_wrong),
                        tooltip = stringResource(Res.string.bible_stt_flag_wrong_hint),
                        tint = MaterialTheme.colorScheme.error,
                        // Both of these describe what went LIVE, so they mean nothing with an empty
                        // output — and they used to swallow the click silently in that state.
                        enabled = displayedVerses.isNotEmpty(),
                        disabledTooltip = stringResource(Res.string.bible_stt_flag_needs_live),
                        onClick = {
                            val live = displayedVerses
                            if (live.isNotEmpty()) {
                                viewModel.logOperatorFlag(
                                    kind = "wrong_passage",
                                    bookName = live.first().bookName,
                                    chapter = live.first().chapter,
                                    verseStart = live.minOf { it.verseNumber },
                                    verseEnd = live.maxOf { it.verseNumber }.takeIf { live.size > 1 },
                                    matchType = viewModel.autoFollowLiveMatchType.value,
                                )
                            }
                        }
                    )
                    FlagPillButton(
                        icon = Icons.Filled.FastForward,
                        label = stringResource(Res.string.bible_stt_flag_premature),
                        tooltip = stringResource(Res.string.bible_stt_flag_premature_hint),
                        tint = MaterialTheme.colorScheme.tertiary,
                        enabled = displayedVerses.isNotEmpty(),
                        disabledTooltip = stringResource(Res.string.bible_stt_flag_needs_live),
                        onClick = {
                            val live = displayedVerses
                            if (live.isNotEmpty()) {
                                viewModel.logOperatorFlag(
                                    kind = "premature",
                                    bookName = live.first().bookName,
                                    chapter = live.first().chapter,
                                    verseStart = live.minOf { it.verseNumber },
                                    verseEnd = live.maxOf { it.verseNumber }.takeIf { live.size > 1 },
                                    matchType = viewModel.autoFollowLiveMatchType.value,
                                )
                            }
                        }
                    )
                    FlagPillButton(
                        icon = Icons.Filled.SearchOff,
                        label = stringResource(Res.string.bible_stt_flag_missed),
                        tooltip = stringResource(Res.string.bible_stt_flag_missed_hint),
                        // Reports that the engine found nothing, so it needs nothing on screen.
                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = {
                            viewModel.logOperatorFlag(kind = "missed_passage")
                        }
                    )
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

            // ── Detected references — at most 4 rows tall, scrolls beyond ──
            val detRowHeight = 24.dp
            val detMaxVisibleRows = 4
            // Hoisted: drawBehind is not composable, so the theme colour is read here and captured.
            val markerColor = MaterialTheme.semantic.marker
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
                            if (isSelected) drawRect(color = markerColor, size = Size(4f, size.height))
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
                    // The translation the speaker appears to be READING, when the engine could tell.
                    // Kept to the right, past the verse text, for two reasons: it arrives later than
                    // the row (the engine needs a verse or two of reading to decide, then backfills),
                    // and it is usually NOT one of the loaded Bibles — so it must not sit next to the
                    // reference where it would read as the source of the text shown. The verse text
                    // simply truncates a little earlier to make room.
                    ref.detectedVersion?.let { version ->
                        Spacer(Modifier.width(6.dp))
                        TooltipArea(tooltip = {
                            Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    text = stringResource(Res.string.bible_stt_detected_version_tooltip),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }) {
                            Text(
                                text = version,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
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
        if (appSettings.bibleSettings.primaryBible.isBlank() && viewModel.primaryBible.value == null) {
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
                            // `verseText` already begins with "Book Chapter:Verse " (Bible.addSearchResult
                            // builds it that way so a result line reads on its own) — prefixing the
                            // reference again here printed it twice on every row.
                            val resultText = result.verseText
                            val highlightedText = buildAnnotatedString {
                                var lastIndex = 0
                                // Match against the same trimmed query that produced the results.
                                for ((safeStart, safeEnd) in highlightRanges(resultText, searchQuery)) {
                                    append(resultText.substring(lastIndex.coerceAtMost(safeStart), safeStart))
                                    withStyle(style = SpanStyle(
                                        background = MaterialTheme.colorScheme.primaryContainer,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )) {
                                        append(resultText.substring(safeStart, safeEnd))
                                    }
                                    lastIndex = safeEnd
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
            val verseSelectionHint = stringResource(Res.string.bible_verse_selection_hint)
            val swapBiblesStr = stringResource(Res.string.swap_bibles)
            val translationOrderStr = stringResource(Res.string.bible_translation_order)
            val goLiveStr = stringResource(Res.string.go_live)
            val addScheduleStr = stringResource(Res.string.add_to_schedule)

            FocusLostBanner(focusRescue, stringResource(Res.string.tab_focus_lost))

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
                                    if (holdPillActive) holdLiveStr else verseSelectionHint,
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
                                            onInstanceLinkSendBibleHold?.invoke(!holdLiveState)
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
                                    stringResource(Res.string.hold_live_modifier_hint),
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
                    // Two bibles is the bilingual case and keeps its one-tap swap. Three or more
                    // needs to express an order rather than a flip, so it gets the reorder menu.
                    if (appSettings.bibleSettings.translationList().size == 2) {
                        ActionIconButton(
                            onClick = {
                                onSettingsChange { s -> s.swapBibleTranslations() }
                                focusRequester.requestFocus()
                            },
                            tooltipText = swapBiblesStr,
                            painter = painterResource(Res.drawable.ic_swap),
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                            tooltipContent = {
                                val pair = appSettings.bibleSettings.translationList()
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(stringResource(Res.string.swap_bibles_hint), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    pair.forEachIndexed { position, item ->
                                        Text(
                                            "${position + 1}. ${item.fileName.substringBeforeLast('.').ifEmpty { "-" }}",
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        )
                    } else if (appSettings.bibleSettings.translationList().size > 2) {
                        val translations = appSettings.bibleSettings.translationList()
                        // One header read per translation, so it does not belong in a `remember`,
                        // which would run it during composition. Until it lands, the options below
                        // fall back to file stems on their own.
                        val storageDirectory = appSettings.bibleSettings.storageDirectory
                        val translationDisplayNames by produceState(
                            initialValue = emptyMap<String, String>(),
                            storageDirectory,
                            translationSelectionKey,
                        ) {
                            value = withContext(Dispatchers.IO) {
                                bibleDisplayNames(storageDirectory, translations.map { it.fileName })
                            }
                        }
                        TranslationOrderSelector(
                            label = translationOrderStr,
                            translations = translations,
                            displayNames = translationDisplayNames,
                            onMove = { index, offset ->
                                onSettingsChange { app -> app.moveBibleTranslation(index, offset) }
                                focusRequester.requestFocus()
                            },
                            modifier = Modifier
                                .widthIn(min = 127.dp, max = 174.dp),
                        )
                    }
                    // Add to Schedule (teal)
                    AddToScheduleButton(
                        onClick = {
                            viewModel.addCurrentVerseToSchedule { bookName, chapter, verseNumber, verseText, verseRange, bookId ->
                                onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText, verseRange, bookId)
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
                    // The verse list keeps a 100dp floor. The cross-reference column is a new
                    // sibling in this Row, so its width has to come out of what the live panel may
                    // claim — otherwise both panels on in a narrow window squeeze the verses out.
                    val crossRefReserve =
                        if (crossRefsEnabled) colWCrossRef + with(density) { 5.dp.toPx() } else 0f
                    val effectiveSplitWidth = if (isSplitActive)
                        colWSplit.coerceAtMost(
                            (constraints.maxWidth - crossRefReserve - with(density) { (100.dp + 6.dp).toPx() }).coerceAtLeast(0f)
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
                                val multiIndicesInFiltered = filteredSelectionIndices(
                                    viewModel.selectedVerseIndices, verses, filteredVerses,
                                )

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
                                        // Picking a verse here is a new starting point, so the
                                        // cross-reference column follows again even if this is the
                                        // very verse it just sent us to.
                                        crossRefNavigatedTo = null
                                        crossRefAnchorEpoch++
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
                                            val verseText = verseTextOf(verseStr)
                                            val bookName = books.getOrNull(selectedBookIndex) ?: ""
                                            val reference = formatVerseReference(verseStr, bookName, selectedChapter)
                                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                            clipboard.setContents(java.awt.datatransfer.StringSelection("$reference\n$verseText"), null)
                                            showVerseContextMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.add_to_schedule)) },
                                        leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary) },
                                        onClick = {
                                            viewModel.addCurrentVerseToSchedule { bookName, chapter, verseNumber, verseText, verseRange, bookId ->
                                                onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText, verseRange, bookId)
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

                        // Cross-reference column — between the verse list and the live panel, so
                        // the same slot serves both layouts.
                        if (crossRefsEnabled) {
                            DragHandle(onDragEnd = ::saveColWCrossRef) { amount ->
                                colWCrossRef = (colWCrossRef - amount).coerceIn(
                                    with(density) { CROSS_REF_MIN_WIDTH.toPx() },
                                    with(density) { CROSS_REF_MAX_WIDTH.toPx() },
                                )
                            }
                            CrossReferencePanel(
                                rows = crossRefRows,
                                selectedIndex = selectedCrossRefIdx,
                                onClick = { idx ->
                                    selectedCrossRefIdx = idx
                                    crossRefRows.getOrNull(idx)?.let { row ->
                                        crossRefNavigatedTo = Triple(row.bookId, row.chapter, row.verse)
                                        viewModel.selectVerseByCanonicalRef(row.bookId, row.chapter, row.verse)
                                    }
                                    focusRequester.requestFocus()
                                },
                                onDoubleClick = { idx ->
                                    selectedCrossRefIdx = idx
                                    crossRefRows.getOrNull(idx)?.let { row ->
                                        crossRefNavigatedTo = Triple(row.bookId, row.chapter, row.verse)
                                        viewModel.selectVerseByCanonicalRef(
                                            row.bookId, row.chapter, row.verse, goLiveSource = "crossref",
                                        )
                                    }
                                    focusRequester.requestFocus()
                                },
                                passageSpan = if (crossRefPassageMode) {
                                    val chapter = crossRefRun.first().second
                                    "$chapter:${crossRefRun.first().third}-${crossRefRun.last().third}"
                                } else null,
                                modifier = Modifier.width(with(density) { colWCrossRef.toDp() }).fillMaxHeight(),
                            )
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
                                                onInstanceLinkSendVerse?.invoke(primary.bookName, primary.chapter, primary.verseNumber, primary.verseText, primary.verseRange)
                                                presenterManager?.let { if (it.bibleHold.value) { it.setBibleHold(false); onInstanceLinkSendBibleHold?.invoke(false) } }
                                                onPresenting(Presenting.BIBLE)
                                                // The live panel's book, not the browse side's: the
                                                // two diverge as soon as the operator looks ahead,
                                                // and this used to log whichever book was being
                                                // browsed rather than the one going on screen.
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
                                                )?.let(::anchorLiveVerse)
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
                            val markerColor = MaterialTheme.semantic.marker
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
                                                    if (idx == selectedHistoryIdx) drawRect(color = markerColor, size = Size(4f, size.height))
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

/** One row of the cross-reference column, resolved against the loaded module. */
private data class CrossRefRow(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val endVerse: Int?,
    /** True for "often next" — drawn from the operator's own go-lives rather than from TSK. */
    val learned: Boolean,
    /** The reference as this module writes it, e.g. "Rom 5:8". */
    val label: String,
    /** The start of the verse, or empty when the module does not have it. */
    val preview: String,
    /** False when the module has no such verse: the row is shown, but greyed and inert. */
    val available: Boolean,
    /** In passage mode, how many of the read verses point here. 0 for a single-verse row. */
    val count: Int = 0,
)

/**
 * Builds one row, translating the canonical reference into the loaded module's own words.
 *
 * A module that lacks the book still gets a row — labelled from the app's own abbreviations and
 * marked unavailable — because silently dropping it would leave the operator wondering why a
 * passage they know is cross-referenced shows nothing.
 */
private fun crossRefRow(
    viewModel: BibleViewModel,
    fallbackAbbreviations: List<String>,
    bookId: Int,
    chapter: Int,
    verse: Int,
    endVerse: Int?,
    learned: Boolean,
    count: Int = 0,
): CrossRefRow {
    val moduleRef = viewModel.moduleRefFor(bookId, chapter, verse)
    val abbreviation = moduleRef?.abbreviation
        ?: fallbackAbbreviations.getOrNull(bookId - 1).orEmpty()
    return CrossRefRow(
        bookId = bookId,
        chapter = chapter,
        verse = verse,
        endVerse = endVerse,
        learned = learned,
        // The module's own numbering where it has an opinion: a Synodal psalm is labelled with the
        // number its operator will find in it, not the KJV number the dataset stores.
        label = formatCrossRefLabel(
            abbreviation,
            moduleRef?.chapter ?: chapter,
            moduleRef?.verse ?: verse,
            endVerse,
        ),
        preview = moduleRef?.text.orEmpty(),
        available = moduleRef != null,
        count = count,
    )
}

/**
 * The narrow column of references beside the verse list.
 *
 * Two kinds of suggestion share one scrolling list rather than two panels: what the passage points
 * at (TSK) and what this operator usually shows next (their own go-lives). They are separated by a
 * label and a divider and distinguished by a leading dot, so it still reads as one list — during a
 * service the eye should find the reference, not navigate a layout.
 *
 * Each row carries an abbreviated reference and the start of the verse, both already resolved
 * against the loaded module — so they read in the module's language and its own numbering, and
 * this composable renders rather than resolves.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CrossReferencePanel(
    rows: List<CrossRefRow>,
    selectedIndex: Int,
    onClick: (Int) -> Unit,
    onDoubleClick: (Int) -> Unit,
    /** The span of the passage being read, e.g. "1:1-10", or null when describing one verse. */
    passageSpan: String?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val markerColor = MaterialTheme.semantic.marker
    val firstLearned = rows.indexOfFirst { it.learned }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        // Naming the span makes it unambiguous which verses produced the list, which matters
        // precisely because the list changes shape as a reading goes on.
        Text(
            text = passageSpan?.let { stringResource(Res.string.bible_cross_references_passage, it) }
                ?: stringResource(Res.string.bible_cross_references_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.bible_cross_references_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            return@Column
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
                itemsIndexed(rows) { idx, row ->
                    if (idx == firstLearned) {
                        Text(
                            text = stringResource(Res.string.bible_cross_references_often_next),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        )
                    }
                    // The one boundary between the two kinds, drawn only when both are present.
                    if (!row.learned && idx > 0 && rows[idx - 1].learned) {
                        HorizontalDivider(
                            thickness = Dp.Hairline,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .background(
                                if (idx == selectedIndex) MaterialTheme.colorScheme.surfaceVariant
                                else if (idx % 2 == 0) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .drawBehind {
                                if (idx == selectedIndex) drawRect(color = markerColor, size = Size(4f, size.height))
                            }
                            // An unavailable row is inert: clicking it could only fail, and a row
                            // that responds to nothing reads as a broken app rather than as a
                            // reference this translation happens not to carry.
                            .then(
                                if (row.available) Modifier.initialPassCombinedClickable(
                                    onClick = { onClick(idx) },
                                    onDoubleClick = { onDoubleClick(idx) },
                                ) else Modifier
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        if (row.learned) {
                            Box(
                                modifier = Modifier.padding(end = 4.dp).size(4.dp)
                                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
                            )
                        }
                        val referenceColor =
                            if (row.available) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = referenceColor)) {
                                    append(row.label)
                                }
                                if (row.preview.isNotEmpty()) append("  ${row.preview}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Takes the row less whatever the count needs, so the preview uses
                            // every remaining pixel rather than splitting the row down the middle.
                            modifier = Modifier.weight(1f),
                        )
                        if (row.count > 0) {
                            // Its own Text rather than part of the label, so it holds its place at
                            // the end of the row while the reference and preview truncate.
                            Text(
                                text = stringResource(Res.string.bible_cross_references_source_count, row.count),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState),
            )
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
        val firstLiveIndex = indexOfFirstLiveVerse(verses, liveVerseNumbers)
        if (firstLiveIndex >= 0) listState.scrollToItem(firstLiveIndex)
    }

    LaunchedEffect(liveVerseNumbers) {
        val firstLiveIndex = indexOfFirstLiveVerse(verses, liveVerseNumbers)
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
                val verseNum = verseNumberOf(verseStr)
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

/**
 * Small flat pill button matching the auto-follow/text-match pills in the status row above.
 *
 * Coloured rather than muted, and it flashes when pressed, because pressing one has no other visible
 * effect at all — it appends a line to a training log. Drawn in the muted palette with no press
 * feedback, a working button was indistinguishable from a dead one, and an operator logged the same
 * flag seven times in under two seconds trying to make it respond.
 *
 * [enabled] false keeps that muted look, drops the click, and lets the tooltip explain why — so grey
 * means "not available right now" instead of being how every one of these buttons looks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlagPillButton(
    icon: ImageVector,
    label: String,
    tooltip: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledTooltip: String? = null,
) {
    // Cleared by the LaunchedEffect below; a plain flag rather than a timestamp so nothing here
    // depends on the wall clock.
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(flashing) {
        if (flashing) {
            delay(FLAG_FLASH_MS)
            flashing = false
        }
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val contentColor = when {
        !enabled -> muted
        flashing -> MaterialTheme.colorScheme.surface
        else -> tint
    }
    val background = when {
        flashing -> tint
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (enabled) tint.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant

    TooltipArea(tooltip = {
        Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = if (enabled) tooltip else (disabledTooltip ?: tooltip),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        }
    }) {
        Box(
            modifier = Modifier
                .height(27.dp)
                .background(background, RoundedCornerShape(6.dp))
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                .then(
                    if (!enabled) Modifier
                    else Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        flashing = true
                        onClick()
                    }
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = contentColor
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** How long a flag pill stays filled after a click — long enough to notice, short enough not to nag. */
private const val FLAG_FLASH_MS = 600L

/** Row height for [TranslationOrderPanel], including its vertical padding — drag distance is measured in row-heights. */
private val TRANSLATION_ORDER_ROW_HEIGHT = 46.dp

/**
 * [bibleDisplayNames] appends "  (file or folder)" to a title when another translation shares it,
 * so a flat picker list can still tell the two apart. The translation-order trigger and rows show
 * the file name on their own line already, so that suffix would just repeat it — stripped here.
 */
private fun translationTitle(displayNames: Map<String, String>, translation: BibleTranslationSettings): String =
    (displayNames[translation.fileName] ?: translation.fileName.substringBeforeLast('.'))
        .substringBefore("  (")

/**
 * Trigger button + dropdown panel for reordering the multi-translation stack (3+ translations).
 * The trigger shows the current first/primary translation; the panel lists every translation with
 * a drag handle and up/down buttons to reorder. [onMove] mirrors [BibleSettings.moveTranslation]:
 * moving [index] by [offset] positions (not just ±1 — a drag can jump straight to a target row).
 */
@Composable
private fun TranslationOrderSelector(
    label: String,
    translations: List<BibleTranslationSettings>,
    displayNames: Map<String, String>,
    onMove: (index: Int, offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val primary = translations.first()
    val primaryName = translationTitle(displayNames, primary)
    val extraCount = translations.size - 1

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(10.dp),
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = label.uppercase(),
                    fontSize = 8.5.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = primaryName,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (extraCount > 0) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.bible_translation_order_more, extraCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_down),
                contentDescription = null,
                modifier = Modifier.size(12.dp).rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            offset = DpOffset(0.dp, 8.dp),
        ) {
            TranslationOrderPanel(translations = translations, displayNames = displayNames, onMove = onMove)
        }
    }
}

@Composable
private fun TranslationOrderPanel(
    translations: List<BibleTranslationSettings>,
    displayNames: Map<String, String>,
    onMove: (index: Int, offset: Int) -> Unit,
) {
    Column(modifier = Modifier.width(320.dp)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(Res.string.bible_translation_order_panel_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.bible_translation_order_panel_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // The reorder commits once, on drop — not per row-height crossed during the drag. Calling
        // onMove mid-gesture would reshuffle `translations`, changing which file sits in the
        // dragged row's slot; since that file name is this row's pointerInput key, the live-swap
        // version cancelled its own gesture the moment it moved anything, freezing the row until
        // the whole panel recomposed from scratch (closing and reopening it).
        val density = LocalDensity.current
        val rowHeightPx = with(density) { TRANSLATION_ORDER_ROW_HEIGHT.toPx() }
        val translationsState = rememberUpdatedState(translations)
        var draggingFileName by remember { mutableStateOf<String?>(null) }
        var dragOffsetY by remember { mutableStateOf(0f) }

        Column(modifier = Modifier.padding(6.dp)) {
            translations.forEachIndexed { index, translation ->
                val isPrimary = index == 0
                val isDragged = translation.fileName == draggingFileName
                val name = translationTitle(displayNames, translation)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TRANSLATION_ORDER_ROW_HEIGHT)
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffsetY else 0f }
                        .background(
                            if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f) else Color.Transparent,
                            RoundedCornerShape(9.dp),
                        )
                        .border(
                            1.dp,
                            if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(9.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_drag_dots),
                        contentDescription = stringResource(Res.string.drag_to_reorder_translation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(width = 4.dp, height = 16.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(translation.fileName) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingFileName = translation.fileName
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        val current = translationsState.value
                                        val from = current.indexOfFirst { it.fileName == draggingFileName }
                                        if (from >= 0) {
                                            val steps = (dragOffsetY / rowHeightPx).roundToInt()
                                            val to = (from + steps).coerceIn(0, current.lastIndex)
                                            if (to != from) onMove(from, to - from)
                                        }
                                        draggingFileName = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingFileName = null
                                        dragOffsetY = 0f
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                }
                            },
                    )

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(
                                if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(7.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        Text(
                            text = translation.fileName,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ReorderArrowButton(
                            icon = painterResource(Res.drawable.ic_arrow_up),
                            contentDescription = stringResource(Res.string.move_translation_up),
                            enabled = index > 0,
                            onClick = { onMove(index, -1) },
                        )
                        ReorderArrowButton(
                            icon = painterResource(Res.drawable.ic_arrow_down),
                            contentDescription = stringResource(Res.string.move_translation_down),
                            enabled = index < translations.lastIndex,
                            onClick = { onMove(index, 1) },
                        )
                    }
                }
                if (index != translations.lastIndex) Spacer(Modifier.height(4.dp))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                text = stringResource(Res.string.bible_translation_order_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ReorderArrowButton(
    icon: Painter,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 22.dp, height = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(5.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.5f),
                RoundedCornerShape(5.dp),
            )
            .then(
                if (enabled) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(10.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
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
            IconButton(
                onClick = onClear,
                // Tagged for tests: the icon is decorative, so there is no label to address it by.
                modifier = Modifier.size(30.dp).testTag("bible_searchClear")
            ) {
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

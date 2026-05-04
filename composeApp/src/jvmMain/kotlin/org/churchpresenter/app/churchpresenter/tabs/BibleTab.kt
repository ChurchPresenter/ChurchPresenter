package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.input.pointer.PointerIcon
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
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
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_cast
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
import churchpresenter.composeapp.generated.resources.hold_live
import churchpresenter.composeapp.generated.resources.swap_bibles
import churchpresenter.composeapp.generated.resources.verse
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.SearchTextField
import org.churchpresenter.app.churchpresenter.composables.SelectionListWithIndex
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
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

    val books by viewModel.books
    val selectedBookIndex by viewModel.selectedBookIndex
    val selectedChapter by viewModel.selectedChapter
    val selectedVerseIndex by viewModel.selectedVerseIndex
    val verses by viewModel.verses
    val searchQuery by viewModel.searchQuery
    val searchResults by viewModel.searchResults
    val isSearchMode by viewModel.isSearchMode
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
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val verseSelectionToken by viewModel.verseSelectionToken

    val currentIsPresenting by rememberUpdatedState(isPresenting)

    // Only push to presenter when:
    //  - not currently presenting (free browsing always updates preview), OR
    //  - an explicit verse selection happened (token changed) while presenting
    LaunchedEffect(verseSelectionToken) {
        // In multi-verse mode while presenting, don't update until Go Live is pressed
        if (viewModel.multiVerseEnabled.value && currentIsPresenting) return@LaunchedEffect
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

    var historyExpanded by remember { mutableStateOf(true) }

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
        // In multi-verse mode, explicitly push verses and clear selection for next pick
        if (viewModel.multiVerseEnabled.value && selectedVerses.isNotEmpty()) {
            onVerseSelected(selectedVerses)
            viewModel.clearMultiVerseSelection()
        }
        onPresenting(Presenting.BIBLE)
    }

    var searchFieldFocused by remember { mutableStateOf(false) }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        // Don't intercept arrow keys when the search field has focus (cursor navigation)
        if (searchFieldFocused) return false
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

    var colWBook by remember(appSettings.bibleSettings.bibleColWidthBook) {
        mutableStateOf(with(density) { appSettings.bibleSettings.bibleColWidthBook.dp.toPx() })
    }
    var colWChapter by remember(appSettings.bibleSettings.bibleColWidthChapter) {
        mutableStateOf(with(density) { appSettings.bibleSettings.bibleColWidthChapter.dp.toPx() })
    }

    fun saveColWidths() {
        onSettingsChangeState.value { s ->
            s.copy(bibleSettings = s.bibleSettings.copy(
                bibleColWidthBook    = with(density) { colWBook.toDp().value.toInt() },
                bibleColWidthChapter = with(density) { colWChapter.toDp().value.toInt() }
            ))
        }
    }

    @Composable
    fun DragHandle(onDrag: (Float) -> Unit) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                .pointerHoverIcon(PointerIcon.Hand)
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
                            .onFocusChanged { searchFieldFocused = it.isFocused },
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        label = { Text(text = stringResource(Res.string.search), style = MaterialTheme.typography.bodyMedium) },
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
                            onClick = { viewModel.performSearch() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(20.dp))
                        }
                        if (isSearchMode) {
                            IconButton(
                                modifier = Modifier.padding(start = 4.dp),
                                onClick = { viewModel.clearSearch() },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.clear), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            } else {
                // Wide: everything on one line
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                            .onFocusChanged { searchFieldFocused = it.isFocused },
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        label = { Text(text = stringResource(Res.string.search), style = MaterialTheme.typography.bodyMedium) },
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
                        onClick = { viewModel.performSearch() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(20.dp))
                    }
                    if (isSearchMode) {
                        IconButton(
                            modifier = Modifier.padding(start = 4.dp),
                            onClick = { viewModel.clearSearch() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.clear), modifier = Modifier.size(20.dp))
                        }
                    }
                }
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
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(searchResults) { _, result ->
                            val resultText = "${result.book} ${result.chapter}:${result.verse} - ${result.verseText}"
                            val highlightedText = buildAnnotatedString {
                                var lastIndex = 0
                                val lowerText = resultText.lowercase()
                                val lowerQuery = searchQuery.lowercase()
                                var startIndex = lowerText.indexOf(lowerQuery, lastIndex)
                                while (startIndex != -1) {
                                    append(resultText.substring(lastIndex, startIndex))
                                    withStyle(style = SpanStyle(
                                        background = MaterialTheme.colorScheme.primaryContainer,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )) {
                                        append(resultText.substring(startIndex, startIndex + searchQuery.length))
                                    }
                                    lastIndex = startIndex + searchQuery.length
                                    startIndex = lowerText.indexOf(lowerQuery, lastIndex)
                                }
                                if (lastIndex < resultText.length) append(resultText.substring(lastIndex))
                            }
                            Text(
                                text = highlightedText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
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
                        if (presenterManager != null) {
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
                                    Icon(painter = painterResource(Res.drawable.ic_cast), contentDescription = goLiveStr, modifier = Modifier.size(20.dp))
                                }
                            }
                    }
                }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ── Book / Chapter / Verse columns ───────────────────────
                    Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 4.dp)) {

                        // Book column (resizable)
                        Column(modifier = Modifier.width(with(density) { colWBook.toDp() }).fillMaxHeight()) {
                            SearchTextField(label = stringResource(Res.string.book)) { query ->
                                viewModel.updateBookSearchQuery(query)
                            }
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
                            SearchTextField(label = stringResource(Res.string.chapter)) { query ->
                                viewModel.updateChapterSearchQuery(query)
                            }
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

                        // Verse column (fills remaining space)
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                SearchTextField(
                                    modifier = Modifier.width(with(density) { colWChapter.toDp() }),
                                    label = stringResource(Res.string.verse),
                                ) { query ->
                                    viewModel.updateVerseSearchQuery(query)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                toolbarContent()
                            }
                    Box(modifier = Modifier.weight(1f)) {
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
                            }
                        )
                    }

                    // ── History panel ─────────────────────────────────────
                    if (viewModel.history.isNotEmpty()) {
                        HorizontalDivider()
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
                                LazyColumn(state = historyListState, modifier = Modifier.fillMaxSize()) {
                                    items(viewModel.history) { entry ->
                                        Text(
                                            text = "${entry.displayText}  ${entry.verseText}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable {
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
                }
            }
                }
                }
        }
    }
}
package org.churchpresenter.bibletab

import org.churchpresenter.strings.generated.resources.bible_no_match_inside_words
import org.churchpresenter.strings.generated.resources.bible_no_match_body_exact
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import org.churchpresenter.sharedui.composables.EmptyStateSecondaryButton
import org.churchpresenter.strings.generated.resources.bible_no_match_clear
import org.churchpresenter.strings.generated.resources.bible_no_match_body_book
import org.churchpresenter.strings.generated.resources.bible_no_match_body
import org.churchpresenter.strings.generated.resources.bible_no_match_title_book
import org.churchpresenter.strings.generated.resources.bible_no_match_title
import org.churchpresenter.strings.generated.resources.bible_empty_open_settings
import org.churchpresenter.strings.generated.resources.bible_empty_body
import org.churchpresenter.strings.generated.resources.bible_empty_title
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_search
import org.churchpresenter.icons.generated.resources.ic_settings
import org.churchpresenter.icons.generated.resources.ic_storage_bible
import org.churchpresenter.sharedui.composables.EmptyStateContent
import org.churchpresenter.sharedui.composables.EmptyStateButton
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_cross_references_count
import org.churchpresenter.strings.generated.resources.bible_cross_references_popover_title
import org.churchpresenter.strings.generated.resources.bible_smart_search_hint
import org.churchpresenter.strings.generated.resources.chapter
import org.churchpresenter.strings.generated.resources.contains_phrase
import org.churchpresenter.strings.generated.resources.current_book
import org.churchpresenter.strings.generated.resources.entire_bible
import org.churchpresenter.strings.generated.resources.exact_match
import org.churchpresenter.strings.generated.resources.tab_focus_lost
import org.churchpresenter.sharedui.composables.FocusLostBanner
import org.churchpresenter.sharedui.composables.SearchKeyboardBanner
import org.churchpresenter.settings.moveBibleTranslation
import org.churchpresenter.settings.swapBibleTranslations
import org.churchpresenter.stt.STTManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.ColumnScope
import org.churchpresenter.sharedui.composables.FocusLostRescueState
import org.churchpresenter.sharedui.composables.bibleListCard

/** The tab's body under its key handler: the search row, detections, and the browser or a notice. */
@Composable
internal fun ColumnScope.BibleTabContent(
    viewModel: BibleViewModel,
    tab: BibleTabScope,
    focusRescue: FocusLostRescueState,
) {
    with(tab) {
        if (loadErrors.isNotEmpty()) {
            BibleLoadErrorBanner(
                errors = loadErrors,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
            )
        }

        // Hide lasts until the app restarts; "never remind me" is saved per file name, per install.
        var hiddenThisSession by remember { mutableStateOf(false) }
        val hiddenFiles = appSettings.bibleSettings.hiddenRowWarnings
        val rowWarnings = viewModel.loadWarnings.value.filter { it.fileName !in hiddenFiles }
        if (rowWarnings.isNotEmpty() && !hiddenThisSession) {
            BibleRowWarningBanner(
                warnings = rowWarnings,
                onHide = { hiddenThisSession = true },
                onNeverRemind = {
                    val names = rowWarnings.map { it.fileName }
                    onSettingsChange { s ->
                        s.copy(bibleSettings = s.bibleSettings.copy(
                            hiddenRowWarnings = (s.bibleSettings.hiddenRowWarnings + names).distinct(),
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
            )
        }

        val scopeOptions = listOf(
            stringResource(Res.string.entire_bible),
            stringResource(Res.string.current_book),
        )
        val selectedScope = scopeOptions.getOrElse(selectedScopeIndex) { scopeOptions.first() }

        val modeOptions = listOf(
            stringResource(Res.string.contains_phrase),
            stringResource(Res.string.exact_match),
        )
        val selectedMode = modeOptions.getOrElse(selectedModeIndex) { modeOptions.first() }

        val scopeBook = if (selectedScopeIndex == SCOPE_CURRENT_BOOK) books.getOrNull(selectedBookIndex) else null

        val searchPlaceholder = stringResource(Res.string.bible_smart_search_hint)
        BibleSearchRow(
            searchQuery = searchQuery,
            searchPlaceholder = searchPlaceholder,
            searchMode = searchMode,
            scopeOptions = scopeOptions,
            selectedScope = selectedScope,
            modeOptions = modeOptions,
            selectedMode = selectedMode,
            onQueryChange = { searchQueryChanged(viewModel, it) },
            onClear = { viewModel.clearSearch(); focusRequester.requestFocus() },
            onSubmit = { viewModel.submitSmartQuery(); focusRequester.requestFocus() },
            onFocusChanged = { searchFieldFocused = it },
            onCycleSearchMode = { viewModel.cycleSearchMode(); focusRequester.requestFocus() },
            onScopeSelected = viewModel::updateSelectedScopeIndex,
            onModeSelected = viewModel::updateSelectedModeIndex,
            searchFocus = searchFocus,
        )

        if (engineSettings.enabled && sttConnected) {
            sttManager?.let { BibleTabDetection(viewModel, it) }
        }

        if (appSettings.bibleSettings.primaryBible.isBlank() && viewModel.primaryBible.value == null) {
            BibleNoPrimaryHint(onOpenBibleSettings)
        } else if (isSearchMode && searchResults.isNotEmpty()) {
            LaunchedEffect(searchResults) { ui.highlightedResult = -1 }
            BibleSearchResults(
                results = searchResults,
                query = searchQuery,
                scopeBook = scopeBook,
                highlightedIndex = ui.highlightedResult,
                onResultChosen = { result ->
                    viewModel.selectSearchResult(result)
                    viewModel.clearSearch()
                    focusRequester.requestFocus()
                },
            )
        } else if (isSearchMode && searchQuery.isNotEmpty()) {
            BibleNoMatch(
                query = searchQuery,
                scopeBook = scopeBook,
                wholeWords = selectedModeIndex == MODE_EXACT_MATCH,
                onMatchInsideWords = { viewModel.updateSelectedModeIndex(0); viewModel.performSearch() },
                onClear = { viewModel.clearSearch(); focusRequester.requestFocus() },
            )
        } else {
            BibleBrowser(viewModel, tab, focusRescue)
        }
    }
}

/** Nothing to show until a primary Bible is chosen: say so, with Settings one click away. */
@Composable
private fun ColumnScope.BibleNoPrimaryHint(onOpenSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        EmptyStateContent(
            icon = IconRes.drawable.ic_storage_bible,
            neutral = false,
            title = stringResource(Res.string.bible_empty_title),
            body = stringResource(Res.string.bible_empty_body),
        ) {
            EmptyStateButton(
                stringResource(Res.string.bible_empty_open_settings),
                IconRes.drawable.ic_settings,
                onOpenSettings,
            )
        }
    }
}

/** A search with no verses: what was searched for, where, and the way back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BibleNoMatch(
    query: String,
    scopeBook: String?,
    wholeWords: Boolean,
    onMatchInsideWords: () -> Unit,
    onClear: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        EmptyStateContent(
            icon = IconRes.drawable.ic_search,
            neutral = true,
            compact = true,
            title = if (scopeBook == null) {
                stringResource(Res.string.bible_no_match_title, query.trim())
            } else {
                stringResource(Res.string.bible_no_match_title_book, query.trim(), scopeBook)
            },
            body = stringResource(
                when {
                    wholeWords -> Res.string.bible_no_match_body_exact
                    scopeBook == null -> Res.string.bible_no_match_body
                    else -> Res.string.bible_no_match_body_book
                },
            ),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (wholeWords) {
                    EmptyStateButton(
                        stringResource(Res.string.bible_no_match_inside_words),
                        IconRes.drawable.ic_search,
                        onMatchInsideWords,
                    )
                    EmptyStateSecondaryButton(
                        stringResource(Res.string.bible_no_match_clear),
                        IconRes.drawable.ic_close,
                        onClear,
                    )
                } else {
                    EmptyStateButton(
                        stringResource(Res.string.bible_no_match_clear),
                        IconRes.drawable.ic_close,
                        onClear,
                    )
                }
            }
        }
    }
}

@Composable
private fun BibleTabScope.BibleTabDetection(viewModel: BibleViewModel, sttManager: STTManager) {
    val engineStartFailed = engineStatus?.startFailed?.value == true

    val engineSttDown = engineStatus?.engineSttConnected?.value == false
    val sttConnectError = sttManager.connectError.value == true
    val noBibleSelected = appSettings.bibleSettings.primaryBible.isBlank() &&
        appSettings.bibleSettings.secondaryBible.isBlank() &&
        viewModel.primaryBible.value == null
    BibleDetectionPanel(
        status = bibleSttStatus(BibleSttSignals(
            engineStartFailed = engineStartFailed,
            noBibleSelected = noBibleSelected,
            sttConnected = sttConnected,
            engineConnected = engineStatus?.connected?.value == true,
            engineSttDown = engineSttDown,
            sttReceiving = sttManager.inProgressText.value.isNotBlank() || sttManager.segments.isNotEmpty(),
            hasDetectedReferences = detectedReferences.isNotEmpty(),
            sttReconnecting = sttManager.reconnecting.value == true,
            sttConnectError = sttConnectError,
            sttConnecting = sttManager.connecting.value == true,
        )),
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
            onSettingsChange { it.copy(
                bibleEngineSettings = it.bibleEngineSettings.copy(textMatchLevel = next.name.lowercase()),
            ) }
        },
        onContinuationSpeedChange = { next ->
            viewModel.setContinuationSpeed(next)
            onSettingsChange { it.copy(
                bibleEngineSettings = it.bibleEngineSettings.copy(continuationSpeed = next.name.lowercase()),
            ) }
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
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp).bibleListCard(),
    )
}

/** The book, chapter and verse browser, with the history panel under it. */
@Composable
private fun ColumnScope.BibleBrowser(viewModel: BibleViewModel, tab: BibleTabScope, focusRescue: FocusLostRescueState) {
    with(tab) {
        val crossRefCountStr = stringResource(Res.string.bible_cross_references_count)
        val crossRefPopoverTitleStr = stringResource(Res.string.bible_cross_references_popover_title)

        // No top gap of its own: the search card above already ends in an 8dp margin, which
        // matches the banner's 8dp below.
        FocusLostBanner(focusRescue, stringResource(Res.string.tab_focus_lost), topPadding = 0.dp)
        // The other way the step keys stand down: the caret is in the search box, browsing.
        if (searchFieldFocused) {
            SearchKeyboardBanner(somethingLive = currentIsPresenting, onClick = { switchSearchLive(viewModel) })
        }

        // Provided rather than passed: the verse row that draws it is five levels down.
        CompositionLocalProvider(
            LocalVerseSplitMark provides viewModel.liveVerseSplitMark(displayedVerses.firstOrNull())
        ) {
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
                verseScrollAhead = appSettings.bibleSettings.verseAutoScroll,
                bookWidthPx = colWBook,
                chapterWidthPx = colWChapter,
                crossRefWidthPx = colWCrossRef,
                splitWidthPx = colWSplit,
                onBookWidthChange = { update -> colWBook = update(colWBook) },
                onChapterWidthChange = { update -> colWChapter = update(colWChapter) },
                onCrossRefWidthChange = { update -> colWCrossRef = update(colWCrossRef) },
                onSplitWidthChange = { update -> colWSplit = update(colWSplit) },
                onSaveColumnWidths = ::saveColWidths,
                onSaveCrossRefWidth = ::saveColWCrossRef,
                onSaveSplitWidth = ::saveColWSplit,
                crossRefs = crossRefs,
                crossRefsDocked = crossRefsDocked,
                crossRefCountLabel = { count -> crossRefCountStr.format(count) },
                crossRefPopoverTitle = { label, size -> crossRefPopoverTitleStr.format(label, size) },
                onOpenCrossRef = { row -> openCrossRef(viewModel, row) },
                onGoLiveCrossRef = { row -> goLiveCrossRef(viewModel, row) },
                onScheduleCrossRef = { row -> scheduleCrossRef(viewModel, row) },
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
                onRefsChipClicked = { index -> refsChipClicked(viewModel, index) },
                onBookSelected = { index -> selectFilteredBook(viewModel, index) },
                onChapterSelected = { index ->
                    filteredChapters.getOrNull(index)?.toIntOrNull()?.let(viewModel::selectChapter)
                },
                onVerseSelected = { index -> clickFilteredVerse(viewModel, index) },
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
                onVerseDoubleClicked = { goLiveWithHistory(viewModel); focusRequester.requestFocus() },
                onCopyVerse = { copySelectedVerse() },
                onAddToSchedule = { scheduleCurrentVerse(viewModel) },
                translationTitle = viewModel.primaryBible.value?.getBibleTitle().orEmpty(),
                isSplitActive = isSplitActive,
                liveChapterVerses = liveChapterVerses,
                liveVerseNumbers = liveVerseNumbers,
                onLiveVerseClicked = { verseNum -> liveVerseClicked(viewModel, verseNum) },
                verseHeader = { showLabel -> BibleTabVerseHeader(viewModel, showLabel) },
            ) {
                BibleTabHistory(viewModel)
            }
        }
    }
}

@Composable
private fun BibleTabScope.BibleTabVerseHeader(viewModel: BibleViewModel, showLabel: Boolean) {
    BibleVerseHeader(
        showLabel = showLabel,
        crossRefsVisible = crossRefsAvailable,
        crossRefsDocked = crossRefsDocked,
        holdAvailable = bibleOutput != null && !splitBrowseMode,
        holdLive = bibleOutput?.bibleHold?.value ?: false,
        sttToggleVisible = appSettings.sttSettings.lastConnectedUrl.isNotBlank() &&
            appSettings.sttSettings.lastConnectedUrl == appSettings.sttSettings.serverUrl &&
            sttManager != null,
        sttConnected = sttConnected,
        translations = appSettings.bibleSettings.translationList(),
        storageDirectory = appSettings.bibleSettings.storageDirectory,
        translationSelectionKey = translationSelectionKey,
        onCrossReferencesToggle = { toggleCrossReferences() },
        onHoldLiveToggle = { toggleHoldLive() },
        onSttToggle = { toggleStt() },
        onSwapTranslations = {
            onSettingsChange { s -> s.swapBibleTranslations() }
            focusRequester.requestFocus()
        },
        onMoveTranslation = { index, offset ->
            onSettingsChange { app -> app.moveBibleTranslation(index, offset) }
            focusRequester.requestFocus()
        },
        onAddToSchedule = { scheduleCurrentVerse(viewModel) },
        onGoLive = { goLiveWithHistory(viewModel); focusRequester.requestFocus() },
    )
}

@Composable
private fun BibleTabScope.BibleTabHistory(viewModel: BibleViewModel) {
    Box(Modifier.guideTarget(GuideTargets.BIBLE_HISTORY)) {
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

/** The search mode that matches whole words only; 0 is Contains phrase. */
private const val MODE_EXACT_MATCH = 1

package org.churchpresenter.bibletab


import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_cross_references_count
import org.churchpresenter.strings.generated.resources.bible_cross_references_popover_title
import org.churchpresenter.strings.generated.resources.bible_no_primary_hint
import org.churchpresenter.strings.generated.resources.bible_no_primary_step1
import org.churchpresenter.strings.generated.resources.bible_no_primary_step2
import org.churchpresenter.strings.generated.resources.bible_no_primary_title
import org.churchpresenter.strings.generated.resources.bible_smart_search_hint
import org.churchpresenter.strings.generated.resources.chapter
import org.churchpresenter.strings.generated.resources.contains_phrase
import org.churchpresenter.strings.generated.resources.current_book
import org.churchpresenter.strings.generated.resources.entire_bible
import org.churchpresenter.strings.generated.resources.exact_match
import org.churchpresenter.strings.generated.resources.no_results_found
import org.churchpresenter.strings.generated.resources.tab_focus_lost
import org.churchpresenter.sharedui.composables.FocusLostBanner
import org.churchpresenter.sharedui.composables.SearchKeyboardBanner
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.moveBibleTranslation
import org.churchpresenter.settings.swapBibleTranslations
import org.churchpresenter.stt.STTManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.ColumnScope
import org.churchpresenter.sharedui.composables.FocusLostRescueState
import org.churchpresenter.sharedui.composables.bibleListCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon

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
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
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
            BibleNoPrimaryHint(appSettings)
        } else if (isSearchMode && searchResults.isNotEmpty()) {
            LaunchedEffect(searchResults) { ui.highlightedResult = -1 }
            BibleSearchResults(
                results = searchResults,
                query = searchQuery,
                highlightedIndex = ui.highlightedResult,
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
            BibleBrowser(viewModel, tab, focusRescue)
        }
    }
}

/** Nothing to show until a primary Bible is chosen: say so, and how. */
@Composable
private fun ColumnScope.BibleNoPrimaryHint(appSettings: AppSettings) {
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
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
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

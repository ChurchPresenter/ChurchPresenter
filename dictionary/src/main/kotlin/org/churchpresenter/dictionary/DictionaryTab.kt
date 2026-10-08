package org.churchpresenter.dictionary

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.sharedui.composables.DragHandle
import org.churchpresenter.sharedui.composables.SearchFieldFocus
import org.churchpresenter.sharedui.composables.goLiveKeyTarget
import org.churchpresenter.sharedui.composables.bibleListCard

/** The divider between the entry list and the detail pane, dragged to resize the list. */
internal const val DICTIONARY_LIST_DIVIDER_TAG = "dictionary-list-divider"

@Composable
fun DictionaryTab(
    modifier: Modifier = Modifier,
    viewModel: DictionaryViewModel,
    appSettings: AppSettings? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((number: String, word: String, transliteration: String, definition: String) -> Unit)? = null,
    onGoLive: ((StrongsEntry) -> Unit)? = null,
    /** The number of the entry on air, or null when the dictionary is not; Go Live skips it. */
    liveEntryNumber: String? = null,
    getVerseText: ((bookId: Int, chapter: Int, verse: Int) -> String?)? = null,
    getBookName: ((bookId: Int) -> String?)? = null,
    onWordClick: ((strongsNumber: String) -> Unit)? = null,
    onVerseClick: ((bookId: Int, chapter: Int, verse: Int) -> Unit)? = null,
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val entryIndex = remember(viewModel.entries) { viewModel.entries.associateBy { it.number } }
    val density = LocalDensity.current
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)
    val initialWidth = appSettings?.windowedLayout?.dictionaryListWidthDp ?: 320
    var listWidthPx by remember(initialWidth) {
        mutableStateOf(with(density) { initialWidth.dp.toPx() })
    }

    // How the dictionary looks on screen is one setting per install rather than something an output
    // can differ on, so it is reached from the tab that shows the thing being styled -- the gear
    // sits in the detail pane's action row beside Go Live, the way STTTab's does. Offered only
    // where there is a document to edit: the tab is also composed with no settings at all in
    // previews and tests.

    // Opening the tab puts the caret in the search box (#798); otherwise the tab root takes the
    // keyboard, where the Go Live key sends the entry shown.
    val searchFocus = remember { SearchFieldFocus() }
    val searchFirst = appSettings?.keyboardShortcutSettings?.focusSearchOnTabOpen == true
    LaunchedEffect(searchFocus) { if (searchFirst) searchFocus.focusAndSelectAll() }
    val entry = viewModel.selectedEntry
    Row(
        modifier = modifier.goLiveKeyTarget(
            enabled = entry != null && onGoLive != null && entry.number != liveEntryNumber,
            focusOnOpen = !searchFirst,
        ) { entry?.let { onGoLive?.invoke(it) } }
    ) {
        DictionaryListPane(
            modifier = Modifier.width(with(density) { listWidthPx.toDp() }).fillMaxHeight()
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                .bibleListCard(),
            viewModel = viewModel,
            getBookName = getBookName,
            searchFocus = searchFocus,
        )
        DragHandle(
            modifier = Modifier.testTag(DICTIONARY_LIST_DIVIDER_TAG),
            onDragEnd = {
                val newWidthDp = with(density) { listWidthPx.toDp().value.toInt() }
                onSettingsChangeState.value { s ->
                    s.copy(windowedLayout = s.windowedLayout.copy(dictionaryListWidthDp = newWidthDp))
                }
            },
        ) { delta ->
            listWidthPx = (listWidthPx + delta)
                .coerceIn(
                    with(density) { 180.dp.toPx() },
                    with(density) { 600.dp.toPx() }
                )
        }
        DictionaryDetailPane(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .padding(top = 4.dp, end = 4.dp, bottom = 4.dp)
                .bibleListCard(),
            entry = viewModel.selectedEntry,
            canGoBack = viewModel.canGoBack,
            canGoForward = viewModel.canGoForward,
            onGoBack = viewModel::goBack,
            onGoForward = viewModel::goForward,
            dictLanguage = viewModel.dictLanguage,
            onToggleDictLanguage = viewModel::toggleDictLanguage,
            interlinearVerses = viewModel.filteredSortedInterlinearVerses,
            totalInterlinearCount = viewModel.interlinearVerses.size,
            isInterlinearLoading = viewModel.isInterlinearLoading,
            interlinearDisplayLimit = viewModel.interlinearDisplayLimit,
            onShowMore = viewModel::showMoreInterlinear,
            cardBookFilter = viewModel.cardBookFilter,
            cardChapterFilter = viewModel.cardChapterFilter,
            cardAvailableBooks = viewModel.cardAvailableBooks,
            cardAvailableChapters = viewModel.cardAvailableChapters,
            onFilterCardsByBook = viewModel::filterCardsByBook,
            onFilterCardsByChapter = viewModel::filterCardsByChapter,
            getVerseText = getVerseText,
            getBookName = getBookName,
            onWordClick = onWordClick,
            onVerseClick = onVerseClick,
            getEntry = { number -> entryIndex[number] },
            onAddToSchedule = onAddToSchedule?.let { cb ->
                { e -> cb(e.number, e.word, e.transliteration, e.definition) }
            },
            onGoLive = onGoLive,
        )
    }
}

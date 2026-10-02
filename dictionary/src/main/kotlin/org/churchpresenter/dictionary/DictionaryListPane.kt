package org.churchpresenter.dictionary

import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.CircularProgressIndicator
import org.churchpresenter.theme.components.RaisedFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.dictionary_entry_count
import org.churchpresenter.strings.generated.resources.dictionary_filter_all
import org.churchpresenter.strings.generated.resources.dictionary_filter_greek
import org.churchpresenter.strings.generated.resources.dictionary_filter_hebrew
import org.churchpresenter.strings.generated.resources.dictionary_bible_primary
import org.churchpresenter.strings.generated.resources.dictionary_bible_select
import org.churchpresenter.strings.generated.resources.dictionary_loading
import org.churchpresenter.strings.generated.resources.dictionary_no_results
import org.churchpresenter.strings.generated.resources.dictionary_search_hint
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.BibleListRowShape
import org.churchpresenter.sharedui.composables.bibleRowColors
import org.churchpresenter.sharedui.composables.rememberRowHover
import org.churchpresenter.sharedui.composables.rowPad

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DictionaryListPane(
    modifier: Modifier,
    viewModel: DictionaryViewModel,
    getBookName: ((bookId: Int) -> String?)?,
) {
    val results = viewModel.searchResults
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.scrollRequestToken) {
        val entry = viewModel.selectedEntry ?: return@LaunchedEffect
        val idx = results.indexOfFirst { it.number == entry.number }
        if (idx < 0) return@LaunchedEffect
        // Only scroll when the entry is off-screen — never yank a visible row to the top.
        val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == idx }
        if (!isVisible) listState.animateScrollToItem(idx)
    }

    Column(modifier = modifier) {
        LanguageChips(viewModel)

        // Search field — capped so it doesn't stretch edge-to-edge on a wide panel.
        DictionarySearchField(
            value = viewModel.searchQuery,
            placeholder = stringResource(Res.string.dictionary_search_hint),
            onValueChange = { viewModel.searchQuery = it },
            onClear = { viewModel.searchQuery = "" },
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
        )

        // Book / chapter filter for the entry list (visible once interlinear data is loaded)
        if (viewModel.isInterlinearDataLoaded) {
            BibleSelector(viewModel)
            PassageFilterRow(viewModel, getBookName)
        }

        ListStatus(viewModel, results.size)
        EntryList(viewModel, results, listState)
    }
}

/**
 * Language filter chips — FlowRow so a chip wraps onto a new line instead of overflowing/getting
 * clipped off the edge when the panel is narrow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChips(viewModel: DictionaryViewModel) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DictionaryLanguageFilter.entries.forEach { filter ->
            RaisedFilterChip(
                selected = viewModel.filterLanguage == filter,
                onClick = { viewModel.setLanguageFilter(filter) },
                label = {
                    Text(
                        text = when (filter) {
                            DictionaryLanguageFilter.ALL -> stringResource(Res.string.dictionary_filter_all)
                            DictionaryLanguageFilter.HEBREW -> stringResource(Res.string.dictionary_filter_hebrew)
                            DictionaryLanguageFilter.GREEK -> stringResource(Res.string.dictionary_filter_greek)
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Which installed translation the verse rows quote; offered only when there is more than the primary. */
@Composable
private fun BibleSelector(viewModel: DictionaryViewModel) {
    if (viewModel.availableDictBibles.isEmpty()) return
    val primaryBibleStr = stringResource(Res.string.dictionary_bible_primary)
    val bibleOptions = listOf("" to primaryBibleStr) + viewModel.availableDictBibles
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DropdownSelector(
            label = stringResource(Res.string.dictionary_bible_select),
            value = viewModel.dictBibleFile,
            options = bibleOptions,
            onValueChange = { viewModel.setDictBible(it) }
        )
        if (viewModel.isDictBibleLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

/** Book, then chapter, then verse: each offered once the one before it narrows to more than one choice. */
@Composable
private fun PassageFilterRow(viewModel: DictionaryViewModel, getBookName: ((bookId: Int) -> String?)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InScriptureBookDropdown(
            allBooksLabel = stringResource(Res.string.dictionary_filter_all),
            selectedBookId = viewModel.entryBookFilter,
            availableBooks = viewModel.entryAvailableBooks,
            getBookName = getBookName,
            onSelect = viewModel::filterEntryListByBook,
            modifier = Modifier.weight(BOOK_FILTER_WEIGHT),
        )
        if (viewModel.entryBookFilter != null && viewModel.entryAvailableChapters.size > 1) {
            InScriptureChapterDropdown(
                allChaptersLabel = stringResource(Res.string.dictionary_filter_all),
                selectedChapter = viewModel.entryChapterFilter,
                availableChapters = viewModel.entryAvailableChapters,
                onSelect = viewModel::filterEntryListByChapter,
                modifier = Modifier.weight(1f),
            )
        }
        if (viewModel.entryChapterFilter != null && viewModel.entryAvailableVerses.size > 1) {
            InScriptureVerseDropdown(
                allVersesLabel = stringResource(Res.string.dictionary_filter_all),
                selectedVerse = viewModel.entryVerseFilter,
                availableVerses = viewModel.entryAvailableVerses,
                onSelect = viewModel::filterEntryListByVerse,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "Loading…" while the entries load, then how many the list shows. */
@Composable
private fun ListStatus(viewModel: DictionaryViewModel, count: Int) {
    if (viewModel.isLoading) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(Res.string.dictionary_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else if (viewModel.entries.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.dictionary_entry_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
        )
    }
}

/** The entries themselves, or a notice when nothing matches. */
@Composable
private fun EntryList(viewModel: DictionaryViewModel, results: List<StrongsEntry>, listState: LazyListState) {
    val listCard = Modifier.fillMaxSize()
    if (!viewModel.isLoading && results.isEmpty()) {
        Box(
            modifier = listCard,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.dictionary_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Box(modifier = listCard) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 6.dp, top = 6.dp, end = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(rowPad(2.dp)),
        ) {
            items(results, key = { it.number }) { entry ->
                DictionaryEntryRow(
                    entry = entry,
                    isSelected = viewModel.selectedEntry?.number == entry.number,
                    onClick = {
                        UsageEvents.record(UsageEvent.STRONGS_LOOKUP)
                        viewModel.onEntrySelected(entry)
                    },
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun DictionaryEntryRow(
    entry: StrongsEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val numberColor = if (entry.isHebrew) MaterialTheme.semantic.hebrew else MaterialTheme.semantic.greek
    val (hover, hovered) = rememberRowHover()
    val colors = bibleRowColors(isSelected, hovered)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BibleListRowShape)
            .background(colors.background)
            .hoverable(hover)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .padding(horizontal = rowPad(10.dp), vertical = rowPad(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Number badge
        Surface(
            shape = AppShape(4.dp),
            color = numberColor.copy(alpha = 0.12f),
            modifier = Modifier.widthIn(min = 44.dp),
        ) {
            Text(
                text = entry.number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = numberColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                maxLines = 1,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.word,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) colors.ink else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.transliteration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
            )
        }
    }
}

/** The book filter's share of the passage row against the chapter's and verse's 1 each: book names run long. */
private const val BOOK_FILTER_WEIGHT = 1.5f

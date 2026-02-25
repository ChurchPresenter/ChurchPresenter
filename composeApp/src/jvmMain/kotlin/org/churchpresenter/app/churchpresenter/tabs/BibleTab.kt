package org.churchpresenter.app.churchpresenter.tabs


import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.book
import churchpresenter.composeapp.generated.resources.chapter
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.contains_phrase
import churchpresenter.composeapp.generated.resources.current_book
import churchpresenter.composeapp.generated.resources.entire_bible
import churchpresenter.composeapp.generated.resources.exact_match
import churchpresenter.composeapp.generated.resources.found_results
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.mode
import churchpresenter.composeapp.generated.resources.no_results_found
import churchpresenter.composeapp.generated.resources.scope
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.verse
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.SearchTextField
import org.churchpresenter.app.churchpresenter.composables.SelectionListWithIndex
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun BibleTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onAddToSchedule: ((bookName: String, chapter: Int, verseNumber: Int, verseText: String) -> Unit)? = null,
    selectedVerseItem: ScheduleItem.BibleVerseItem? = null,
    onVerseSelected: (List<SelectedVerse>) -> Unit = {},
    onPresenting: (Presenting) -> Unit = { Presenting.NONE }
) {
    val viewModel = remember { BibleViewModel(appSettings) }

    DisposableEffect(Unit) {
        onDispose { viewModel.dispose() }
    }

    // React to schedule item selection.
    // If data is still loading when the item arrives, wait for loading to finish
    // then retry — no fixed delay, no polling, no race condition.
    LaunchedEffect(selectedVerseItem) {
        selectedVerseItem?.let { item ->
            // Wait until data is ready if currently loading
            if (viewModel.isLoading.value) {
                snapshotFlow { viewModel.isLoading.value }
                    .first { !it }
            }
            viewModel.selectVerseByDetails(item.bookName, item.chapter, item.verseNumber)
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

    // Filtered lists are managed entirely by ViewModel
    val filteredBooks by viewModel.filteredBooks
    val filteredChapters by viewModel.filteredChapters
    val filteredVerses by viewModel.filteredVerses

    // String resources for scope and mode options
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

    // Focus management for keyboard navigation
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Notify parent when selected verse changes
    LaunchedEffect(selectedVerseIndex, verses.size) {
        if (verses.isNotEmpty() && selectedVerseIndex >= 0 && selectedVerseIndex < verses.size) {
            val selectedVerses = viewModel.getSelectedVerses()
            if (selectedVerses.isNotEmpty()) {
                onVerseSelected(selectedVerses)
            }
        }
    }

    // Handle keyboard navigation — thin UI delegation to ViewModel
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionUp -> viewModel.navigatePreviousVerse()
            Key.DirectionDown -> viewModel.navigateNextVerse()
            Key.DirectionLeft -> viewModel.navigatePreviousChapter()
            Key.DirectionRight -> viewModel.navigateNextChapter()
            else -> false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { handleKeyEvent(it) }
    ) {

        // Search row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.width(400.dp).padding(end = 8.dp),
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text(text = stringResource(Res.string.search)) },
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

            Button(onClick = { viewModel.performSearch() }) {
                Text(text = stringResource(Res.string.search))
            }

            if (isSearchMode) {
                Button(
                    modifier = Modifier.padding(start = 8.dp),
                    onClick = { viewModel.clearSearch() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(stringResource(Res.string.clear))
                }
            }
        }

        // Show search results or normal view
        if (isSearchMode && searchResults.isNotEmpty()) {
            // Display search results
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
                                    withStyle(
                                        style = SpanStyle(
                                            background = MaterialTheme.colorScheme.primaryContainer,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    ) {
                                        append(resultText.substring(startIndex, startIndex + searchQuery.length))
                                    }
                                    lastIndex = startIndex + searchQuery.length
                                    startIndex = lowerText.indexOf(lowerQuery, lastIndex)
                                }
                                if (lastIndex < resultText.length) {
                                    append(resultText.substring(lastIndex))
                                }
                            }

                            Text(
                                text = highlightedText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectSearchResult(result)
                                        viewModel.clearSearch()
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
            // Show "no results" message
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
            // Normal book/chapter/verse view
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                Column(modifier = Modifier.width(200.dp).padding(end = 8.dp)) {
                    SearchTextField(label = stringResource(Res.string.book)) { query ->
                        viewModel.updateBookSearchQuery(query)
                    }
                    SelectionListWithIndex(
                        list = filteredBooks,
                        selectedIndex = filteredBooks.indexOf(books.getOrNull(selectedBookIndex) ?: "").coerceAtLeast(0),
                        onItemSelected = { index, _ ->
                            val bookName = filteredBooks.getOrNull(index)
                            bookName?.let {
                                val realIndex = books.indexOf(it)
                                if (realIndex >= 0) viewModel.selectBook(realIndex)
                            }
                        }
                    )
                }

                Column(modifier = Modifier.width(120.dp).padding(end = 8.dp)) {
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

                // Verses column
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SearchTextField(
                            modifier = Modifier.width(120.dp),
                            label = stringResource(Res.string.verse),
                        ) { query ->
                            viewModel.updateVerseSearchQuery(query)
                        }
                        Button(
                            modifier = Modifier.wrapContentSize().padding(start = 8.dp),
                            onClick = { onPresenting(Presenting.BIBLE) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.go_live),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1
                            )
                        }
                        Button(
                            modifier = Modifier.wrapContentSize().padding(start = 8.dp),
                            onClick = {
                                viewModel.addCurrentVerseToSchedule { bookName, chapter, verseNumber, verseText ->
                                    onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.add_to_schedule),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondary,
                                maxLines = 1
                            )
                        }
                    }
                    Box {
                        SelectionListWithIndex(
                            list = filteredVerses,
                            selectedIndex = if (filteredVerses.isEmpty()) -1 else {
                                val currentVerse = verses.getOrNull(selectedVerseIndex)
                                filteredVerses.indexOf(currentVerse).coerceAtLeast(0)
                            },
                            onItemSelected = { index, _ ->
                                val verseText = filteredVerses.getOrNull(index)
                                verseText?.let {
                                    val realIndex = verses.indexOf(it)
                                    if (realIndex >= 0) viewModel.selectVerse(realIndex)
                                }
                            },
                            onItemDoubleClicked = { _, _ ->
                                onPresenting(Presenting.BIBLE)
                            }
                        )
                    }
                }
            }
        }
    }
}
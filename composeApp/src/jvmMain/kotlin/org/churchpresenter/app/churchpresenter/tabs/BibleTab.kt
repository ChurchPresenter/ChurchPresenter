package org.churchpresenter.app.churchpresenter.tabs


import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun BibleTab(
    modifier: Modifier = Modifier,
    viewModel: BibleViewModel,
    onVerseSelected: (List<SelectedVerse>) -> Unit = {},
    onPresenting: (Presenting) -> Unit = { Presenting.NONE }
) {
    val books by viewModel.books
    val selectedBookIndex by viewModel.selectedBookIndex
    val selectedChapter by viewModel.selectedChapter
    val selectedVerseIndex by viewModel.selectedVerseIndex
    val verses by viewModel.verses
    val searchQuery by viewModel.searchQuery
    val searchResults by viewModel.searchResults
    val isSearchMode by viewModel.isSearchMode

    // Get filtered lists from ViewModel
    // Use bookSearchQuery from ViewModel for filtering books (independent of verse search)
    val bookSearchQuery by viewModel.bookSearchQuery
    val chapterSearchQuery by viewModel.chapterSearchQuery
    val verseSearchQuery by viewModel.verseSearchQuery

    val filteredBooks = remember(books, bookSearchQuery) {
        println("DEBUG BibleTab: Recalculating filteredBooks, books.size=${books.size}, bookSearchQuery='$bookSearchQuery'")
        val result = viewModel.getFilteredBooks()
        println("DEBUG BibleTab: filteredBooks result size=${result.size}")
        result
    }
    val filteredChapters = remember(selectedBookIndex, chapterSearchQuery) {
        println("DEBUG BibleTab: Recalculating filteredChapters, selectedBookIndex=$selectedBookIndex, chapterSearchQuery='$chapterSearchQuery'")
        val result = viewModel.getFilteredChapters()
        println("DEBUG BibleTab: filteredChapters result size=${result.size}")
        result
    }
    val filteredVerses = remember(verses, verseSearchQuery) {
        println("DEBUG BibleTab: Recalculating filteredVerses, verses.size=${verses.size}, verseSearchQuery='$verseSearchQuery'")
        val result = viewModel.getFilteredVerses()
        println("DEBUG BibleTab: filteredVerses result size=${result.size}")
        result
    }

    // String resources for scope and mode options
    val scopeOptions = listOf(
        stringResource(Res.string.entire_bible),
        stringResource(Res.string.current_book),
    )
    var selectedScope by rememberSaveable { mutableStateOf(scopeOptions.firstOrNull() ?: "") }

    val modeOptions = listOf(
        stringResource(Res.string.contains_phrase),
        stringResource(Res.string.exact_match),
    )
    var selectedMode by rememberSaveable { mutableStateOf(modeOptions.firstOrNull() ?: "") }

    // Focus management for keyboard navigation
    val focusRequester = remember { FocusRequester() }

    // Request focus when component is first composed
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Send verse selection when it changes
    LaunchedEffect(selectedVerseIndex, verses.size) {
        // Only get verses if we have a valid index
        if (verses.isNotEmpty() && selectedVerseIndex >= 0 && selectedVerseIndex < verses.size) {
            val selectedVerses = viewModel.getSelectedVerses()
            if (selectedVerses.isNotEmpty()) {
                onVerseSelected(selectedVerses)
            }
        }
    }

    // Handle keyboard navigation
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        when (event.key) {
            Key.DirectionUp -> {
                return viewModel.navigatePreviousVerse()
            }

            Key.DirectionDown -> {
                return viewModel.navigateNextVerse()
            }

            Key.DirectionLeft -> {
                return viewModel.navigatePreviousChapter()
            }

            Key.DirectionRight -> {
                return viewModel.navigateNextChapter()
            }
        }
        return false
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
                label = {
                    Text(text = stringResource(Res.string.search))
                },
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
                onSelectedChange = {
                    selectedScope = it
                    viewModel.updateSelectedScope(it)
                }
            )

            DropdownSelector(
                modifier = Modifier.width(200.dp).padding(end = 8.dp),
                label = stringResource(Res.string.mode),
                items = modeOptions,
                selected = selectedMode,
                onSelectedChange = {
                    selectedMode = it
                    viewModel.updateSelectedMode(it)
                }
            )

            Button(onClick = { viewModel.performSearch() }) {
                Text(text = stringResource(Res.string.search))
            }

            if (isSearchMode) {
                Button(
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

                SelectionListWithIndex(
                    list = searchResults.map { "${it.book} ${it.chapter}:${it.verse} - ${it.verseText}" },
                    selectedIndex = -1
                ) { index, _ ->
                    searchResults.getOrNull(index)?.let { result ->
                        viewModel.selectSearchResult(result)
                        viewModel.clearSearch()
                    }
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
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(200.dp).padding(end = 8.dp)) {
                    SearchTextField(label = stringResource(Res.string.book)) { query ->
                        viewModel.updateBookSearchQuery(query)
                    }
                    SelectionListWithIndex(
                        list = filteredBooks,
                        selectedIndex = filteredBooks.indexOf(books.getOrNull(selectedBookIndex) ?: "").coerceAtLeast(0)
                    ) { index, _ ->
                        println("DEBUG BibleTab: Book selected at filteredIndex=$index")
                        // Find the real index in the original books list
                        val bookName = filteredBooks.getOrNull(index)
                        println("DEBUG BibleTab: Book name='$bookName'")
                        bookName?.let {
                            val realIndex = books.indexOf(it)
                            println("DEBUG BibleTab: Real book index=$realIndex")
                            if (realIndex >= 0) {
                                viewModel.selectBook(realIndex)
                            }
                        }
                    }
                }

                Column(modifier = Modifier.width(120.dp).padding(end = 8.dp)) {
                    SearchTextField(label = stringResource(Res.string.chapter)) { query ->
                        viewModel.updateChapterSearchQuery(query)
                    }
                    SelectionListWithIndex(
                        list = filteredChapters,
                        selectedIndex = filteredChapters.indexOf(selectedChapter.toString()).coerceAtLeast(0)
                    ) { index, _ ->
                        println("DEBUG BibleTab: Chapter selected at filteredIndex=$index")
                        // Find the real chapter number from the filtered list
                        val chapterStr = filteredChapters.getOrNull(index)
                        println("DEBUG BibleTab: Chapter string='$chapterStr'")
                        chapterStr?.toIntOrNull()?.let { chapter ->
                            println("DEBUG BibleTab: Calling selectChapter with chapter=$chapter")
                            viewModel.selectChapter(chapter)
                        }
                    }
                }

                // Verses view
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
                                // TOOD
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.add_to_schedule),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
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
                            }
                        ) { index, _ ->
                            // Find the real index in the original verses list
                            val verseText = filteredVerses.getOrNull(index)
                            verseText?.let {
                                val realIndex = verses.indexOf(it)
                                if (realIndex >= 0) {
                                    viewModel.selectVerse(realIndex)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import churchpresenter.composeapp.generated.resources.book
import churchpresenter.composeapp.generated.resources.chapter
import churchpresenter.composeapp.generated.resources.contains_phrase
import churchpresenter.composeapp.generated.resources.current_book
import churchpresenter.composeapp.generated.resources.entire_bible
import churchpresenter.composeapp.generated.resources.exact_match
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.mode
import churchpresenter.composeapp.generated.resources.scope
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.verse
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.SearchTextField
import org.churchpresenter.app.churchpresenter.composables.SelectionList
import org.churchpresenter.app.churchpresenter.composables.SelectionListWithIndex
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun BibleTab(
    modifier: Modifier = Modifier,
    viewModel: BibleViewModel,
    onVerseSelected: (List<org.churchpresenter.app.churchpresenter.models.SelectedVerse>) -> Unit = {},
    onPresenting: (Presenting) -> Unit = { Presenting.NONE }
) {
    val books by viewModel.books
    val selectedBookIndex by viewModel.selectedBookIndex
    val selectedChapter by viewModel.selectedChapter
    val selectedVerseIndex by viewModel.selectedVerseIndex
    val verses by viewModel.verses
    val searchQuery by viewModel.searchQuery

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
        }

        // Book / Chapter / Verse columns
        Row(modifier = Modifier.fillMaxWidth()) {
            val chapterList = viewModel.getChaptersForCurrentBook()

            Column(modifier = Modifier.width(200.dp).padding(end = 8.dp)) {
                SearchTextField(label = stringResource(Res.string.book)) { _ ->
                    // simple local filtering
                }
                SelectionList(
                    list = books,
                    selectedIndex = selectedBookIndex
                ) { bookName ->
                    viewModel.selectBook(books.indexOf(bookName))
                }
            }

            Column(modifier = Modifier.width(120.dp).padding(end = 8.dp)) {
                SearchTextField(label = stringResource(Res.string.chapter)) { _ ->
                    // chapter filter input
                }
                SelectionList(
                    list = chapterList,
                    selectedIndex = selectedChapter - 1  // Convert to 0-based index
                ) { chapter ->
                    viewModel.selectChapter(chapter.toIntOrNull() ?: 1)
                }
            }

            // Verses view
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SearchTextField(
                        modifier = Modifier.width(120.dp),
                        label = stringResource(Res.string.verse),
                    ) { _ ->
                        // verse search
                    }
                    Button(
                        modifier = Modifier.wrapContentSize(),
                        onClick = { onPresenting(Presenting.BIBLE) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.go_live),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 2
                        )
                    }
                }
                Box {
                    SelectionListWithIndex(
                        list = verses,
                        selectedIndex = selectedVerseIndex
                    ) { index, _ ->
                        // Use the index directly from the list
                        viewModel.selectVerse(index)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column {
                Box {
                    val versesListState = rememberLazyListState()
                    LazyColumn(
                        state = versesListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp)
                    ) {
                        items(verses) { v ->
                            Text(
                                text = v,
                                modifier = Modifier.padding(4.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState = versesListState)
                    )
                }
            }
        }
    }
}
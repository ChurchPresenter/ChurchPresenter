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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
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
import churchpresenter.composeapp.generated.resources.mode
import churchpresenter.composeapp.generated.resources.scope
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.verse
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.SearchTextField
import org.churchpresenter.app.churchpresenter.composables.SelectionList
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.jetbrains.compose.resources.stringResource

@Composable
fun BibleTab(
    modifier: Modifier = Modifier,
    bible: Bible,
    onVerseSelected: (SelectedVerse) -> Unit = {}
) {
    val books = bible.getBooks()
    val bookCount = bible.getBookCount()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val scopeOptions = listOf(
        stringResource(Res.string.entire_bible),
        stringResource(Res.string.current_book),
    )

    var selectedScope by rememberSaveable { mutableStateOf(scopeOptions.first()) }
    val modeOptions = listOf(
        stringResource(Res.string.contains_phrase),
        stringResource(Res.string.exact_match),
    )

    var selectedMode by rememberSaveable { mutableStateOf(modeOptions.first()) }

    var selectedBookIndex by rememberSaveable { mutableStateOf(0) }
    var selectedChapter by rememberSaveable { mutableStateOf(1) }
    var selectedVerseIndex by rememberSaveable { mutableStateOf(0) }
    var verses by remember { mutableStateOf(listOf<String>()) }

    // Focus management for keyboard navigation
    val focusRequester = remember { FocusRequester() }

    // When book data arrives, set initial selection to first book
    LaunchedEffect(bookCount) {
        if (bookCount > 0) {
            selectedBookIndex = 0
            selectedChapter = 1
        }
    }

    // Request focus when component is first composed
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(selectedBookIndex, selectedChapter, bookCount) {
        try {
            if (bookCount == 0) {
                verses = emptyList()
            } else {
                val clampedIndex = selectedBookIndex.coerceIn(0, bookCount - 1)
                if (clampedIndex != selectedBookIndex) {
                    selectedBookIndex = clampedIndex
                }
                val bookId = clampedIndex + 1
                verses = bible.getChapter(bookId, selectedChapter)
                selectedVerseIndex = 0

                // Send initial verse selection
                if (verses.isNotEmpty()) {
                    val verseNumber = verses[0].substringBefore(". ").toIntOrNull() ?: 1
                    bible.getVerseDetails(bookId, selectedChapter, verseNumber)?.let { (bookName, verseText, _) ->
                        onVerseSelected(SelectedVerse(
                            bookName = bookName,
                            chapter = selectedChapter,
                            verseNumber = verseNumber,
                            verseText = verseText
                        ))
                    }
                }
            }
        } catch (_: Exception) {
            verses = emptyList()
        }
    }

    // Handle keyboard navigation
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        when (event.key) {
            Key.DirectionUp -> {
                // Previous verse
                if (selectedVerseIndex > 0) {
                    selectedVerseIndex--
                    val verse = verses[selectedVerseIndex]
                    val verseNumber = verse.substringBefore(". ").toIntOrNull() ?: 1
                    val bookId = selectedBookIndex + 1
                    bible.getVerseDetails(bookId, selectedChapter, verseNumber)?.let { (bookName, verseText, _) ->
                        onVerseSelected(SelectedVerse(
                            bookName = bookName,
                            chapter = selectedChapter,
                            verseNumber = verseNumber,
                            verseText = verseText
                        ))
                    }
                    return true
                }
            }
            Key.DirectionDown -> {
                // Next verse
                if (selectedVerseIndex < verses.size - 1) {
                    selectedVerseIndex++
                    val verse = verses[selectedVerseIndex]
                    val verseNumber = verse.substringBefore(". ").toIntOrNull() ?: 1
                    val bookId = selectedBookIndex + 1
                    bible.getVerseDetails(bookId, selectedChapter, verseNumber)?.let { (bookName, verseText, _) ->
                        onVerseSelected(SelectedVerse(
                            bookName = bookName,
                            chapter = selectedChapter,
                            verseNumber = verseNumber,
                            verseText = verseText
                        ))
                    }
                    return true
                }
            }
            Key.DirectionLeft -> {
                // Previous chapter
                if (selectedChapter > 1) {
                    selectedChapter--
                    // LaunchedEffect will handle verse loading and selection
                    return true
                }
            }
            Key.DirectionRight -> {
                // Next chapter
                val maxChapter = bible.getChapterCount(selectedBookIndex)
                if (selectedChapter < maxChapter) {
                    selectedChapter++
                    // LaunchedEffect will handle verse loading and selection
                    return true
                }
            }
        }
        return false
    }

    fun chaptersFor(bookIndex: Int): List<String> {
        val chapterCount = bible.getChapterCount(bookIndex)
        val count = if (chapterCount > 0) chapterCount else 1
        return (1..count).map { it.toString() }
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
                onValueChange = { searchQuery = it },
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
                onSelectedChange = { selectedScope = it }
            )

            DropdownSelector(
                modifier = Modifier.width(200.dp).padding(end = 8.dp),
                label = stringResource(Res.string.mode),
                items = modeOptions,
                selected = selectedMode,
                onSelectedChange = { selectedMode = it }
            )

            Button(onClick = {}) {
                Text(text = stringResource(Res.string.search))
            }
        }

        // Book / Chapter / Verse columns
        Row(modifier = Modifier.fillMaxWidth()) {
            val chapterList = chaptersFor(selectedBookIndex)

            Column(modifier = Modifier.width(200.dp).padding(end = 8.dp)) {
                SearchTextField(label = stringResource(Res.string.book)) { _ ->
                    // simple local filtering
                }
                SelectionList(
                    list = books,
                    selectedIndex = selectedBookIndex
                ) { bookName ->
                    selectedBookIndex = books.indexOf(bookName)
                    selectedChapter = 1
                    selectedVerseIndex = 1
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
                    selectedChapter = chapter.toIntOrNull() ?: 1
                    selectedVerseIndex = 1
                }
            }

            // Verses view
            Column(modifier = Modifier.fillMaxWidth()) {
                SearchTextField(
                    modifier = Modifier.width(120.dp),
                    label = stringResource(Res.string.verse),
                ) { _ ->
                    // verse search
                }
                SelectionList(
                    list = verses,
                    selectedIndex = selectedVerseIndex
                ) { verse ->
                    selectedVerseIndex = verses.indexOf(verse)

                    // Extract verse number from the formatted text (e.g., "1. In the beginning..." -> 1)
                    val verseNumber = verse.substringBefore(". ").toIntOrNull() ?: 1

                    // Get verse details and send to presenter
                    val bookId = selectedBookIndex + 1
                    bible.getVerseDetails(bookId, selectedChapter, verseNumber)?.let { (bookName, verseText, _) ->
                        onVerseSelected(SelectedVerse(
                            bookName = bookName,
                            chapter = selectedChapter,
                            verseNumber = verseNumber,
                            verseText = verseText
                        ))
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
                            Text(text = v,
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
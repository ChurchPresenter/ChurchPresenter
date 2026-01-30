package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
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
import org.jetbrains.compose.resources.stringResource

@Composable
fun BibleTab(bible: Bible) {
    val books = bible.getBooks()
    val bookCount = bible.getBookCount()
    val verseCount = bible.getVerseCount()

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

    // When book data arrives, set initial selection to first book
    LaunchedEffect(bookCount) {
        if (bookCount > 0) {
            selectedBookIndex = 0
            selectedChapter = 1
        }
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
            }
        } catch (e: Exception) {
            verses = emptyList()
        }
    }

    fun chaptersFor(bookIndex: Int): List<String> {
        val chapterCount = bible.getChapterCount(bookIndex)
        val count = if (chapterCount > 0) chapterCount else 1
        return (1..count).map { it.toString() }
    }

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
            }
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
            SearchTextField(label = stringResource(Res.string.book)) { newValue ->
                // simple local filtering
            }
            SelectionList(
                list = books,
                selectedIndex = selectedBookIndex
            ) { bookName ->
                selectedBookIndex = books.indexOf(bookName)
                selectedChapter = 1
                selectedVerseIndex = 1
                // LaunchedEffect will handle verse loading
            }
        }

        Column(modifier = Modifier.width(120.dp).padding(end = 8.dp)) {
            SearchTextField(label = stringResource(Res.string.chapter)) { newValue ->
                // chapter filter input
            }
            SelectionList(
                list = chapterList
            ) { chapter ->
                selectedChapter = chapter.toIntOrNull() ?: 1
                selectedVerseIndex = 1
                // LaunchedEffect will handle verse loading
            }
        }

        // Verses view
        Column(modifier = Modifier.fillMaxWidth()) {
            SearchTextField(
                modifier = Modifier.width(120.dp),
                label = stringResource(Res.string.verse),
            ) { newValue ->
                // verse search
            }
            SelectionList(
                list = verses,
                selectedIndex = selectedVerseIndex
            ) { verse ->
                selectedVerseIndex = verses.indexOf(verse)
                logger.info("Selected verse: $verse")
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(4.dp)
            ) {
                items(verses) { v ->
                    Text(text = v, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}
package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
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
import churchpresenter.composeapp.generated.resources.service_schedule
import churchpresenter.composeapp.generated.resources.verse
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.jetbrains.compose.resources.stringResource

@Composable
fun DropdownSelector(
    label: String,
    items: List<String>,
    selected: String,
    onSelectedChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            modifier = modifier.clickable { expanded = true },
            value = selected,
            onValueChange = { /* read-only */ },
            readOnly = true,
            label = { Text(text = label) },
            trailingIcon = { Text("▾") }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = {
                    onSelectedChange(item)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun MainDesktop(
    modifier: Modifier = Modifier,
) {
    // Sample data: short list of books + chapter counts (demo)
    val bookChapterCounts = remember {
        linkedMapOf(
            "Genesis" to 50,
            "Exodus" to 40,
            "Leviticus" to 27,
            "Numbers" to 36,
            "Deuteronomy" to 34
        )
    }
    val books = bookChapterCounts.keys.toList()

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

    var selectedBook by rememberSaveable { mutableStateOf(books.first()) }
    var selectedChapter by rememberSaveable { mutableStateOf("1") }
    var selectedVerseIndex by rememberSaveable { mutableStateOf(0) }

    // helper to get chapter/verse lists
    fun chaptersFor(book: String): List<String> {
        val count = bookChapterCounts[book] ?: 1
        return (1..count).map { it.toString() }
    }

    fun versesFor(book: String, chapter: Int): List<String> {
        // Demo: use fixed 30 verses per chapter; replace with real data if available
        return (1..30).map { verseNum -> "$verseNum. Example verse text for $book $chapter:$verseNum" }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Row {
            // Left: Service schedule column
            Column(modifier = Modifier.fillMaxWidth(0.20f)) {
                Text(
                    text = stringResource(Res.string.service_schedule),
                    modifier = Modifier.padding(8.dp)
                )
                Box(modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp)) {
                    // Placeholder for schedule list
                    Text("Service schedule (empty)")
                }
            }

            // Center: main content
            Column(modifier = Modifier.fillMaxWidth(0.8f).padding(8.dp)) {
                // TabSection (we don't track the selected tab here)
                TabSection { }

                // Search row
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = {
                            Text(text = stringResource(Res.string.search))
                        },
                    )

                    DropdownSelector(
                        modifier = Modifier.width(160.dp).padding(end = 8.dp),
                        label = stringResource(Res.string.scope),
                        items = scopeOptions,
                        selected = selectedScope,
                        onSelectedChange = { selectedScope = it }
                    )

                    DropdownSelector(
                        modifier = Modifier.width(160.dp).padding(end = 8.dp),
                        label = stringResource(Res.string.mode),
                        items = modeOptions,
                        selected = selectedMode,
                        onSelectedChange = { selectedMode = it }
                    )

                    Button(onClick = {
                        // TODO run search
                    }) {
                        Text(text = stringResource(Res.string.search))
                    }
                }

                // Book / Chapter / Verse columns
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Books list
                    Column(modifier = Modifier.width(200.dp).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(Res.string.book),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        LazyColumn(modifier = Modifier.fillMaxWidth().background(Color.White).padding(4.dp)) {
                            items(books) { book ->
                                val isSelected = book == selectedBook
                                Text(
                                    text = book,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedBook = book
                                            // reset chapter and verse when book changes
                                            selectedChapter = "1"
                                            selectedVerseIndex = 0
                                        }
                                        .padding(6.dp),
                                    color = if (isSelected) Color.Blue else Color.Black
                                )
                            }
                        }
                    }

                    // Chapters list
                    Column(modifier = Modifier.width(100.dp).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(Res.string.chapter),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        val chapterList = chaptersFor(selectedBook)
                        LazyColumn(modifier = Modifier.fillMaxWidth().background(Color.White).padding(4.dp)) {
                            items(chapterList) { ch ->
                                val isSelected = ch == selectedChapter
                                Text(
                                    text = ch,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedChapter = ch
                                            selectedVerseIndex = 0
                                        }
                                        .padding(6.dp),
                                    color = if (isSelected) Color.Blue else Color.Black
                                )
                            }
                        }
                    }

                    // Verses view
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(Res.string.verse),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        val verses = versesFor(selectedBook, selectedChapter.toIntOrNull() ?: 1)
                        LazyColumn(modifier = Modifier.fillMaxWidth().background(Color.White).padding(4.dp)) {
                            items(verses.withIndex().toList()) { (index, verseText) ->
                                val isSelected = index == selectedVerseIndex
                                Text(
                                    text = verseText,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedVerseIndex = index }
                                        .padding(6.dp),
                                    color = if (isSelected) Color.Blue else Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun MainDesktopPreview() {
    MainDesktop()
}
package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import churchpresenter.composeapp.generated.resources.bible_book_55
import churchpresenter.composeapp.generated.resources.bible_book_1
import churchpresenter.composeapp.generated.resources.bible_book_10
import churchpresenter.composeapp.generated.resources.bible_book_11
import churchpresenter.composeapp.generated.resources.bible_book_12
import churchpresenter.composeapp.generated.resources.bible_book_13
import churchpresenter.composeapp.generated.resources.bible_book_14
import churchpresenter.composeapp.generated.resources.bible_book_15
import churchpresenter.composeapp.generated.resources.bible_book_16
import churchpresenter.composeapp.generated.resources.bible_book_17
import churchpresenter.composeapp.generated.resources.bible_book_18
import churchpresenter.composeapp.generated.resources.bible_book_19
import churchpresenter.composeapp.generated.resources.bible_book_2
import churchpresenter.composeapp.generated.resources.bible_book_20
import churchpresenter.composeapp.generated.resources.bible_book_21
import churchpresenter.composeapp.generated.resources.bible_book_22
import churchpresenter.composeapp.generated.resources.bible_book_23
import churchpresenter.composeapp.generated.resources.bible_book_24
import churchpresenter.composeapp.generated.resources.bible_book_25
import churchpresenter.composeapp.generated.resources.bible_book_26
import churchpresenter.composeapp.generated.resources.bible_book_27
import churchpresenter.composeapp.generated.resources.bible_book_28
import churchpresenter.composeapp.generated.resources.bible_book_29
import churchpresenter.composeapp.generated.resources.bible_book_3
import churchpresenter.composeapp.generated.resources.bible_book_30
import churchpresenter.composeapp.generated.resources.bible_book_31
import churchpresenter.composeapp.generated.resources.bible_book_32
import churchpresenter.composeapp.generated.resources.bible_book_33
import churchpresenter.composeapp.generated.resources.bible_book_34
import churchpresenter.composeapp.generated.resources.bible_book_35
import churchpresenter.composeapp.generated.resources.bible_book_36
import churchpresenter.composeapp.generated.resources.bible_book_37
import churchpresenter.composeapp.generated.resources.bible_book_38
import churchpresenter.composeapp.generated.resources.bible_book_39
import churchpresenter.composeapp.generated.resources.bible_book_4
import churchpresenter.composeapp.generated.resources.bible_book_40
import churchpresenter.composeapp.generated.resources.bible_book_41
import churchpresenter.composeapp.generated.resources.bible_book_42
import churchpresenter.composeapp.generated.resources.bible_book_43
import churchpresenter.composeapp.generated.resources.bible_book_44
import churchpresenter.composeapp.generated.resources.bible_book_45
import churchpresenter.composeapp.generated.resources.bible_book_46
import churchpresenter.composeapp.generated.resources.bible_book_47
import churchpresenter.composeapp.generated.resources.bible_book_48
import churchpresenter.composeapp.generated.resources.bible_book_49
import churchpresenter.composeapp.generated.resources.bible_book_5
import churchpresenter.composeapp.generated.resources.bible_book_50
import churchpresenter.composeapp.generated.resources.bible_book_51
import churchpresenter.composeapp.generated.resources.bible_book_52
import churchpresenter.composeapp.generated.resources.bible_book_53
import churchpresenter.composeapp.generated.resources.bible_book_54
import churchpresenter.composeapp.generated.resources.bible_book_56
import churchpresenter.composeapp.generated.resources.bible_book_57
import churchpresenter.composeapp.generated.resources.bible_book_58
import churchpresenter.composeapp.generated.resources.bible_book_59
import churchpresenter.composeapp.generated.resources.bible_book_6
import churchpresenter.composeapp.generated.resources.bible_book_60
import churchpresenter.composeapp.generated.resources.bible_book_61
import churchpresenter.composeapp.generated.resources.bible_book_62
import churchpresenter.composeapp.generated.resources.bible_book_63
import churchpresenter.composeapp.generated.resources.bible_book_64
import churchpresenter.composeapp.generated.resources.bible_book_65
import churchpresenter.composeapp.generated.resources.bible_book_66
import churchpresenter.composeapp.generated.resources.bible_book_7
import churchpresenter.composeapp.generated.resources.bible_book_8
import churchpresenter.composeapp.generated.resources.bible_book_9
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
    // Build the book -> chapter count map using localized string resources (bible_book_1..bible_book_66)
    val bookResIds = listOf(
        Res.string.bible_book_1, Res.string.bible_book_2, Res.string.bible_book_3, Res.string.bible_book_4,
        Res.string.bible_book_5, Res.string.bible_book_6, Res.string.bible_book_7, Res.string.bible_book_8,
        Res.string.bible_book_9, Res.string.bible_book_10, Res.string.bible_book_11, Res.string.bible_book_12,
        Res.string.bible_book_13, Res.string.bible_book_14, Res.string.bible_book_15, Res.string.bible_book_16,
        Res.string.bible_book_17, Res.string.bible_book_18, Res.string.bible_book_19, Res.string.bible_book_20,
        Res.string.bible_book_21, Res.string.bible_book_22, Res.string.bible_book_23, Res.string.bible_book_24,
        Res.string.bible_book_25, Res.string.bible_book_26, Res.string.bible_book_27, Res.string.bible_book_28,
        Res.string.bible_book_29, Res.string.bible_book_30, Res.string.bible_book_31, Res.string.bible_book_32,
        Res.string.bible_book_33, Res.string.bible_book_34, Res.string.bible_book_35, Res.string.bible_book_36,
        Res.string.bible_book_37, Res.string.bible_book_38, Res.string.bible_book_39, Res.string.bible_book_40,
        Res.string.bible_book_41, Res.string.bible_book_42, Res.string.bible_book_43, Res.string.bible_book_44,
        Res.string.bible_book_45, Res.string.bible_book_46, Res.string.bible_book_47, Res.string.bible_book_48,
        Res.string.bible_book_49, Res.string.bible_book_50, Res.string.bible_book_51, Res.string.bible_book_52,
        Res.string.bible_book_53, Res.string.bible_book_54, Res.string.bible_book_55, Res.string.bible_book_56,
        Res.string.bible_book_57, Res.string.bible_book_58, Res.string.bible_book_59, Res.string.bible_book_60,
        Res.string.bible_book_61, Res.string.bible_book_62, Res.string.bible_book_63, Res.string.bible_book_64,
        Res.string.bible_book_65, Res.string.bible_book_66
    )

    val chapterCounts = listOf(
        50,
        40,
        27,
        36,
        34,
        24,
        21,
        4,
        31,
        24,
        22,
        25,
        29,
        36,
        10,
        13,
        10,
        42,
        150,
        31,
        12,
        8,
        66,
        52,
        5,
        48,
        12,
        14,
        3,
        9,
        1,
        4,
        7,
        3,
        3,
        3,
        2,
        14,
        4,
        28,
        16,
        24,
        21,
        28,
        16,
        16,
        13,
        6,
        6,
        4,
        4,
        5,
        3,
        6,
        4,
        3,
        1,
        13,
        5,
        5,
        3,
        5,
        1,
        1,
        1,
        22
    )

    val bookChapterCounts = linkedMapOf(*bookResIds.mapIndexed { idx, resId ->
        stringResource(resId) to chapterCounts[idx]
    }.toTypedArray())

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
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(500.dp)
                                .background(Color.White)
                                .padding(4.dp)
                        ) {
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
                        LazyColumn(modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp)
                            .background(Color.White)
                            .padding(4.dp)) {
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
                        LazyColumn(modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp)
                            .background(Color.White)
                            .padding(4.dp)) {
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
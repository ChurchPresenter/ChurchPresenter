package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.dictionary_definition
import churchpresenter.composeapp.generated.resources.dictionary_entry_count
import churchpresenter.composeapp.generated.resources.dictionary_filter_all
import churchpresenter.composeapp.generated.resources.dictionary_filter_greek
import churchpresenter.composeapp.generated.resources.dictionary_filter_hebrew
import churchpresenter.composeapp.generated.resources.dictionary_in_scripture_all_books
import churchpresenter.composeapp.generated.resources.dictionary_in_scripture_all_chapters
import churchpresenter.composeapp.generated.resources.dictionary_in_scripture_all_verses
import churchpresenter.composeapp.generated.resources.dictionary_in_scripture_count
import churchpresenter.composeapp.generated.resources.dictionary_in_scripture_header
import churchpresenter.composeapp.generated.resources.dictionary_in_scripture_loading
import churchpresenter.composeapp.generated.resources.dictionary_in_scripture_none
import churchpresenter.composeapp.generated.resources.dictionary_in_scripture_show_more
import churchpresenter.composeapp.generated.resources.dictionary_kjv_usage
import churchpresenter.composeapp.generated.resources.dictionary_loading
import churchpresenter.composeapp.generated.resources.dictionary_no_results
import churchpresenter.composeapp.generated.resources.dictionary_pronunciation
import churchpresenter.composeapp.generated.resources.dictionary_search_hint
import churchpresenter.composeapp.generated.resources.dictionary_select_entry
import churchpresenter.composeapp.generated.resources.dictionary_transliteration
import churchpresenter.composeapp.generated.resources.go_live
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Tv
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import org.churchpresenter.app.churchpresenter.data.InterlinearVerse
import org.churchpresenter.app.churchpresenter.data.InterlinearWord
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.viewmodel.DictionaryLanguageFilter
import org.churchpresenter.app.churchpresenter.viewmodel.DictionaryViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val hebrewNumberColor = Color(0xFFB45309)
private val greekNumberColor = Color(0xFF1D4ED8)

@Composable
fun DictionaryTab(
    modifier: Modifier = Modifier,
    viewModel: DictionaryViewModel,
    onAddToSchedule: ((number: String, word: String, transliteration: String, definition: String) -> Unit)? = null,
    onGoLive: ((StrongsEntry) -> Unit)? = null,
    getVerseText: ((bookId: Int, chapter: Int, verse: Int) -> String?)? = null,
    getBookName: ((bookId: Int) -> String?)? = null,
    onWordClick: ((strongsNumber: String) -> Unit)? = null,
) {
    LaunchedEffect(Unit) { viewModel.load() }

    Row(modifier = modifier) {
        DictionaryListPane(
            modifier = Modifier.width(320.dp).fillMaxHeight(),
            viewModel = viewModel,
            getBookName = getBookName,
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            thickness = 1.dp,
        )
        DictionaryDetailPane(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            entry = viewModel.selectedEntry,
            interlinearVerses = viewModel.sortedInterlinearVerses,
            totalInterlinearCount = viewModel.interlinearVerses.size,
            isInterlinearLoading = viewModel.isInterlinearLoading,
            interlinearDisplayLimit = viewModel.interlinearDisplayLimit,
            onShowMore = viewModel::showMoreInterlinear,
            getVerseText = getVerseText,
            getBookName = getBookName,
            onWordClick = onWordClick,
            onAddToSchedule = onAddToSchedule?.let { cb -> { e -> cb(e.number, e.word, e.transliteration, e.definition) } },
            onGoLive = onGoLive,
        )
    }
}

@Composable
private fun DictionaryListPane(
    modifier: Modifier = Modifier,
    viewModel: DictionaryViewModel,
    getBookName: ((bookId: Int) -> String?)? = null,
) {
    val results = viewModel.searchResults
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.selectedEntry) {
        val entry = viewModel.selectedEntry ?: return@LaunchedEffect
        val idx = results.indexOfFirst { it.number == entry.number }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    Column(modifier = modifier) {
        // Language filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DictionaryLanguageFilter.entries.forEach { filter ->
                FilterChip(
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
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        // Search field
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.searchQuery = it },
            placeholder = {
                Text(
                    text = stringResource(Res.string.dictionary_search_hint),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            textStyle = MaterialTheme.typography.bodySmall,
        )

        // Book / chapter filter for the entry list (visible once interlinear data is loaded)
        if (viewModel.isInterlinearDataLoaded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InScriptureBookDropdown(
                    allBooksLabel = stringResource(Res.string.dictionary_in_scripture_all_books),
                    selectedBookId = viewModel.entryBookFilter,
                    availableBooks = viewModel.entryAvailableBooks,
                    getBookName = getBookName,
                    onSelect = viewModel::filterEntryListByBook,
                )
                if (viewModel.entryBookFilter != null && viewModel.entryAvailableChapters.size > 1) {
                    InScriptureChapterDropdown(
                        allChaptersLabel = stringResource(Res.string.dictionary_in_scripture_all_chapters),
                        selectedChapter = viewModel.entryChapterFilter,
                        availableChapters = viewModel.entryAvailableChapters,
                        onSelect = viewModel::filterEntryListByChapter,
                    )
                }
                if (viewModel.entryChapterFilter != null && viewModel.entryAvailableVerses.size > 1) {
                    InScriptureVerseDropdown(
                        allVersesLabel = stringResource(Res.string.dictionary_in_scripture_all_verses),
                        selectedVerse = viewModel.entryVerseFilter,
                        availableVerses = viewModel.entryAvailableVerses,
                        onSelect = viewModel::filterEntryListByVerse,
                    )
                }
            }
        }

        // Entry count / loading state
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
                text = stringResource(Res.string.dictionary_entry_count, results.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
            )
        }

        HorizontalDivider()

        // Results list
        if (!viewModel.isLoading && results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.dictionary_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.number }) { entry ->
                        DictionaryEntryRow(
                            entry = entry,
                            isSelected = viewModel.selectedEntry?.number == entry.number,
                            onClick = { viewModel.onEntrySelected(entry) },
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun DictionaryEntryRow(
    entry: StrongsEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val numberColor = if (entry.isHebrew) hebrewNumberColor else greekNumberColor
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Number badge
        Surface(
            shape = RoundedCornerShape(4.dp),
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
                color = MaterialTheme.colorScheme.onSurface,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DictionaryDetailPane(
    modifier: Modifier = Modifier,
    entry: StrongsEntry?,
    interlinearVerses: List<InterlinearVerse>,
    totalInterlinearCount: Int,
    isInterlinearLoading: Boolean,
    interlinearDisplayLimit: Int,
    onShowMore: () -> Unit,
    getVerseText: ((bookId: Int, chapter: Int, verse: Int) -> String?)? = null,
    getBookName: ((bookId: Int) -> String?)? = null,
    onWordClick: ((String) -> Unit)? = null,
    onAddToSchedule: ((StrongsEntry) -> Unit)? = null,
    onGoLive: ((StrongsEntry) -> Unit)? = null,
) {
    if (entry == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.dictionary_select_entry),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    val numberColor = if (entry.isHebrew) hebrewNumberColor else greekNumberColor
    val languageLabel = if (entry.isHebrew)
        stringResource(Res.string.dictionary_filter_hebrew).uppercase()
    else
        stringResource(Res.string.dictionary_filter_greek).uppercase()

    val addScheduleStr = stringResource(Res.string.add_to_schedule)
    val goLiveStr = stringResource(Res.string.go_live)

    Column(modifier = modifier) {
        // Action toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onAddToSchedule != null) {
                TooltipArea(
                    tooltip = {
                        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                            Text(addScheduleStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp)),
                ) {
                    IconButton(
                        onClick = { onAddToSchedule(entry) },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = addScheduleStr, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (onGoLive != null) {
                TooltipArea(
                    tooltip = {
                        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                            Text(goLiveStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp)),
                ) {
                    IconButton(
                        onClick = { onGoLive(entry) },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = goLiveStr, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        HorizontalDivider()

        val scrollState = rememberScrollState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header: number + language badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = entry.number,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = numberColor,
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = numberColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = languageLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = numberColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                HorizontalDivider()

                // Original word — large display
                Text(
                    text = entry.word,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 60.sp,
                )

                // Transliteration + pronunciation
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetailRow(
                        label = stringResource(Res.string.dictionary_transliteration),
                        value = entry.transliteration,
                    )
                    DetailRow(
                        label = stringResource(Res.string.dictionary_pronunciation),
                        value = entry.pronunciation,
                    )
                }

                HorizontalDivider()

                // Definition
                DetailSection(
                    label = stringResource(Res.string.dictionary_definition),
                    body = entry.definition,
                    onStrongsClick = onWordClick,
                )

                // KJV Usage (only if present)
                if (entry.kjvUsage.isNotBlank()) {
                    DetailSection(
                        label = stringResource(Res.string.dictionary_kjv_usage),
                        body = entry.kjvUsage,
                        onStrongsClick = onWordClick,
                    )
                }

                // In Scripture section
                HorizontalDivider()

                Text(
                    text = stringResource(Res.string.dictionary_in_scripture_header),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isInterlinearLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(Res.string.dictionary_in_scripture_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (interlinearVerses.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.dictionary_in_scripture_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.dictionary_in_scripture_count, totalInterlinearCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        interlinearVerses.take(interlinearDisplayLimit).forEach { verse ->
                            InterlinearVerseRow(
                                interlinearVerse = verse,
                                highlightedNumber = entry.number,
                                getVerseText = getVerseText,
                                getBookName = getBookName,
                                onWordClick = onWordClick,
                            )
                        }
                    }
                    if (interlinearVerses.size > interlinearDisplayLimit) {
                        val remaining = interlinearVerses.size - interlinearDisplayLimit
                        TextButton(onClick = onShowMore) {
                            Text(
                                text = stringResource(Res.string.dictionary_in_scripture_show_more, remaining),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterlinearVerseRow(
    interlinearVerse: InterlinearVerse,
    highlightedNumber: String,
    getVerseText: ((bookId: Int, chapter: Int, verse: Int) -> String?)? = null,
    getBookName: ((bookId: Int) -> String?)? = null,
    onWordClick: ((String) -> Unit)? = null,
) {
    val verseText = remember(interlinearVerse.ref) {
        getVerseText?.invoke(interlinearVerse.bookId, interlinearVerse.chapter, interlinearVerse.verseNumber)
    }
    val bookName = remember(interlinearVerse.bookId) {
        getBookName?.invoke(interlinearVerse.bookId) ?: "Book ${interlinearVerse.bookId}"
    }
    val refLabel = "$bookName ${interlinearVerse.chapter}:${interlinearVerse.verseNumber}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = refLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (verseText != null) {
            Text(
                text = verseText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            interlinearVerse.words.forEach { word ->
                InterlinearWordChip(
                    word = word,
                    isHighlighted = word.strongsNumber == highlightedNumber,
                    onClick = if (word.strongsNumber != highlightedNumber && onWordClick != null) {
                        { onWordClick(word.strongsNumber) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun InterlinearWordChip(
    word: InterlinearWord,
    isHighlighted: Boolean,
    onClick: (() -> Unit)?,
) {
    val containerColor = when {
        isHighlighted -> MaterialTheme.colorScheme.primaryContainer
        onClick != null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = when {
        isHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
        onClick != null -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    SuggestionChip(
        onClick = onClick ?: {},
        label = {
            Text(
                text = word.text,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
        ),
        enabled = isHighlighted || onClick != null,
    )
}

@Composable
private fun InScriptureBookDropdown(
    allBooksLabel: String,
    selectedBookId: Int?,
    availableBooks: List<Int>,
    getBookName: ((bookId: Int) -> String?)?,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (selectedBookId == null) allBooksLabel
    else getBookName?.invoke(selectedBookId) ?: "Book $selectedBookId"

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allBooksLabel, style = MaterialTheme.typography.bodySmall) },
                onClick = { onSelect(null); expanded = false },
            )
            availableBooks.forEach { bookId ->
                val bookName = getBookName?.invoke(bookId) ?: "Book $bookId"
                DropdownMenuItem(
                    text = { Text(bookName, style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelect(bookId); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun InScriptureChapterDropdown(
    allChaptersLabel: String,
    selectedChapter: Int?,
    availableChapters: List<Int>,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (selectedChapter == null) allChaptersLabel else selectedChapter.toString()

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allChaptersLabel, style = MaterialTheme.typography.bodySmall) },
                onClick = { onSelect(null); expanded = false },
            )
            availableChapters.forEach { chapter ->
                DropdownMenuItem(
                    text = { Text(chapter.toString(), style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelect(chapter); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun InScriptureVerseDropdown(
    allVersesLabel: String,
    selectedVerse: Int?,
    availableVerses: List<Int>,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (selectedVerse == null) allVersesLabel else selectedVerse.toString()

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allVersesLabel, style = MaterialTheme.typography.bodySmall) },
                onClick = { onSelect(null); expanded = false },
            )
            availableVerses.forEach { verse ->
                DropdownMenuItem(
                    text = { Text(verse.toString(), style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelect(verse); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val strongsPattern = Regex("[HGhg]\\d+")

private fun buildStrongsAnnotatedString(text: String, onClick: (String) -> Unit) = buildAnnotatedString {
    var lastEnd = 0
    for (match in strongsPattern.findAll(text)) {
        append(text.substring(lastEnd, match.range.first))
        val upper = match.value.uppercase()
        val linkColor = if (upper.startsWith("H")) hebrewNumberColor else greekNumberColor
        withLink(
            LinkAnnotation.Clickable(
                tag = upper,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = { onClick(upper) },
            ),
        ) { append(match.value) }
        lastEnd = match.range.last + 1
    }
    append(text.substring(lastEnd))
}

@Composable
private fun DetailSection(
    label: String,
    body: String,
    onStrongsClick: ((String) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onStrongsClick != null) {
            val cb = onStrongsClick
            val annotated = remember(body) { buildStrongsAnnotatedString(body, cb) }
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )
        } else {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )
        }
    }
}

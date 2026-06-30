package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import churchpresenter.composeapp.generated.resources.dictionary_back
import churchpresenter.composeapp.generated.resources.dictionary_forward
import churchpresenter.composeapp.generated.resources.dictionary_switch_language
import churchpresenter.composeapp.generated.resources.dictionary_bible_primary
import churchpresenter.composeapp.generated.resources.dictionary_bible_select
import churchpresenter.composeapp.generated.resources.ic_redo
import churchpresenter.composeapp.generated.resources.ic_undo
import churchpresenter.composeapp.generated.resources.dictionary_go_to_verse
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
import androidx.compose.material.icons.filled.Tv
import churchpresenter.composeapp.generated.resources.book
import churchpresenter.composeapp.generated.resources.chapter
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.ic_search
import churchpresenter.composeapp.generated.resources.verse
import java.awt.Cursor
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.data.InterlinearVerse
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
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
    appSettings: AppSettings? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((number: String, word: String, transliteration: String, definition: String) -> Unit)? = null,
    onGoLive: ((StrongsEntry) -> Unit)? = null,
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

    Row(modifier = modifier) {
        DictionaryListPane(
            modifier = Modifier.width(with(density) { listWidthPx.toDp() }).fillMaxHeight(),
            viewModel = viewModel,
            getBookName = getBookName,
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val newWidthDp = with(density) { listWidthPx.toDp().value.toInt() }
                            onSettingsChangeState.value { s ->
                                s.copy(windowedLayout = s.windowedLayout.copy(dictionaryListWidthDp = newWidthDp))
                            }
                        }
                    ) { _, dragAmount ->
                        listWidthPx = (listWidthPx + dragAmount)
                            .coerceIn(
                                with(density) { 180.dp.toPx() },
                                with(density) { 600.dp.toPx() }
                            )
                    }
                }
        )
        DictionaryDetailPane(
            modifier = Modifier.weight(1f).fillMaxHeight(),
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

    LaunchedEffect(viewModel.scrollRequestToken) {
        val entry = viewModel.selectedEntry ?: return@LaunchedEffect
        val idx = results.indexOfFirst { it.number == entry.number }
        if (idx < 0) return@LaunchedEffect
        // Only scroll when the entry is off-screen — never yank a visible row to the top.
        val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == idx }
        if (!isVisible) listState.animateScrollToItem(idx)
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
        DictionarySearchField(
            value = viewModel.searchQuery,
            placeholder = stringResource(Res.string.dictionary_search_hint),
            onValueChange = { viewModel.searchQuery = it },
            onClear = { viewModel.searchQuery = "" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
        )

        // Book / chapter filter for the entry list (visible once interlinear data is loaded)
        if (viewModel.isInterlinearDataLoaded) {
            // Bible translation selector
            if (viewModel.availableDictBibles.isNotEmpty()) {
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
                        onValueChange = { viewModel.setDictBible(it) },
                        modifier = Modifier.weight(1f)
                    )
                    if (viewModel.isDictBibleLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }

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
                )
                if (viewModel.entryBookFilter != null && viewModel.entryAvailableChapters.size > 1) {
                    InScriptureChapterDropdown(
                        allChaptersLabel = stringResource(Res.string.dictionary_filter_all),
                        selectedChapter = viewModel.entryChapterFilter,
                        availableChapters = viewModel.entryAvailableChapters,
                        onSelect = viewModel::filterEntryListByChapter,
                    )
                }
                if (viewModel.entryChapterFilter != null && viewModel.entryAvailableVerses.size > 1) {
                    InScriptureVerseDropdown(
                        allVersesLabel = stringResource(Res.string.dictionary_filter_all),
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
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    onGoBack: () -> Unit = {},
    onGoForward: () -> Unit = {},
    dictLanguage: String = "en",
    onToggleDictLanguage: () -> Unit = {},
    interlinearVerses: List<InterlinearVerse>,
    totalInterlinearCount: Int,
    isInterlinearLoading: Boolean,
    interlinearDisplayLimit: Int,
    onShowMore: () -> Unit,
    cardBookFilter: Int? = null,
    cardChapterFilter: Int? = null,
    cardAvailableBooks: List<Int> = emptyList(),
    cardAvailableChapters: List<Int> = emptyList(),
    onFilterCardsByBook: (Int?) -> Unit = {},
    onFilterCardsByChapter: (Int?) -> Unit = {},
    getVerseText: ((bookId: Int, chapter: Int, verse: Int) -> String?)? = null,
    getBookName: ((bookId: Int) -> String?)? = null,
    onWordClick: ((String) -> Unit)? = null,
    onVerseClick: ((bookId: Int, chapter: Int, verse: Int) -> Unit)? = null,
    getEntry: ((strongsNumber: String) -> StrongsEntry?)? = null,
    onAddToSchedule: ((StrongsEntry) -> Unit)? = null,
    onGoLive: ((StrongsEntry) -> Unit)? = null,
) {
    val addScheduleStr = stringResource(Res.string.add_to_schedule)
    val goLiveStr = stringResource(Res.string.go_live)
    val backStr = stringResource(Res.string.dictionary_back)
    val forwardStr = stringResource(Res.string.dictionary_forward)
    val switchLangStr = stringResource(Res.string.dictionary_switch_language)

    Column(modifier = modifier) {
        // Action toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Back / Forward history buttons
            TooltipArea(
                tooltip = {
                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                        Text(backStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp)),
            ) {
                IconButton(onClick = onGoBack, enabled = canGoBack) {
                    Icon(
                        painterResource(Res.drawable.ic_undo),
                        contentDescription = backStr,
                        modifier = Modifier.size(20.dp),
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            TooltipArea(
                tooltip = {
                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                        Text(forwardStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp)),
            ) {
                IconButton(onClick = onGoForward, enabled = canGoForward) {
                    Icon(
                        painterResource(Res.drawable.ic_redo),
                        contentDescription = forwardStr,
                        modifier = Modifier.size(20.dp),
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            TooltipArea(
                tooltip = {
                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                        Text(switchLangStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleDictLanguage,
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (dictLanguage == "en") "EN" else "RU",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                        onClick = { entry?.let { onAddToSchedule(it) } },
                        enabled = entry != null,
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
                        onClick = { entry?.let { onGoLive(it) } },
                        enabled = entry != null,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = goLiveStr, modifier = Modifier.size(20.dp))
                    }
                }
            }
            } // end right-side Row
        }

        HorizontalDivider()

        if (entry == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.dictionary_select_entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
        val numberColor = if (entry.isHebrew) hebrewNumberColor else greekNumberColor
        val languageLabel = if (entry.isHebrew)
            stringResource(Res.string.dictionary_filter_hebrew).uppercase()
        else
            stringResource(Res.string.dictionary_filter_greek).uppercase()

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.dictionary_in_scripture_header),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isInterlinearLoading && cardAvailableBooks.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InScriptureBookDropdown(
                                allBooksLabel = stringResource(Res.string.dictionary_filter_all),
                                selectedBookId = cardBookFilter,
                                availableBooks = cardAvailableBooks,
                                getBookName = getBookName,
                                onSelect = onFilterCardsByBook,
                            )
                            if (cardBookFilter != null && cardAvailableChapters.size > 1) {
                                InScriptureChapterDropdown(
                                    allChaptersLabel = stringResource(Res.string.dictionary_filter_all),
                                    selectedChapter = cardChapterFilter,
                                    availableChapters = cardAvailableChapters,
                                    onSelect = onFilterCardsByChapter,
                                )
                            }
                        }
                    }
                }

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
                                onVerseClick = onVerseClick,
                                getEntry = getEntry,
                            )
                        }
                    }
                    if (interlinearVerses.size > interlinearDisplayLimit) {
                        val remaining = interlinearVerses.size - interlinearDisplayLimit
                        TextButton(shape = RoundedCornerShape(6.dp), onClick = onShowMore) {
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
        } // end else (entry != null)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun InterlinearVerseRow(
    interlinearVerse: InterlinearVerse,
    highlightedNumber: String,
    getVerseText: ((bookId: Int, chapter: Int, verse: Int) -> String?)? = null,
    getBookName: ((bookId: Int) -> String?)? = null,
    onWordClick: ((String) -> Unit)? = null,
    onVerseClick: ((bookId: Int, chapter: Int, verse: Int) -> Unit)? = null,
    getEntry: ((strongsNumber: String) -> StrongsEntry?)? = null,
) {
    val verseText = getVerseText?.invoke(interlinearVerse.bookId, interlinearVerse.chapter, interlinearVerse.verseNumber)
    val bookName = getBookName?.invoke(interlinearVerse.bookId) ?: "Book ${interlinearVerse.bookId}"
    val refLabel = "$bookName ${interlinearVerse.chapter}:${interlinearVerse.verseNumber}"
    val goToVerseStr = stringResource(Res.string.dictionary_go_to_verse)

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
        if (onVerseClick != null) {
            TooltipArea(
                tooltip = {
                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                        Text(goToVerseStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomEnd, offset = DpOffset(0.dp, 4.dp)),
            ) {
                Text(
                    text = refLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        onVerseClick(interlinearVerse.bookId, interlinearVerse.chapter, interlinearVerse.verseNumber)
                    },
                )
            }
        } else {
            Text(
                text = refLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                    entry = getEntry?.invoke(word.strongsNumber),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InterlinearWordChip(
    word: InterlinearWord,
    isHighlighted: Boolean,
    onClick: (() -> Unit)?,
    entry: StrongsEntry? = null,
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
    val chip = @Composable {
        SuggestionChip(
            onClick = onClick ?: {},
            label = { Text(text = word.text, style = MaterialTheme.typography.labelSmall) },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = containerColor,
                labelColor = labelColor,
            ),
            enabled = isHighlighted || onClick != null,
        )
    }
    if (entry != null) {
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = entry.number,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                            )
                            Text(
                                text = entry.word,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                            )
                            Text(
                                text = entry.transliteration,
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
                            )
                        }
                        Text(
                            text = entry.definition.take(200).let {
                                if (entry.definition.length > 200) "$it…" else it
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            },
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp)),
        ) { chip() }
    } else {
        chip()
    }
}

@Composable
private fun InScriptureBookDropdown(
    allBooksLabel: String,
    selectedBookId: Int?,
    availableBooks: List<Int>,
    getBookName: ((bookId: Int) -> String?)?,
    onSelect: (Int?) -> Unit,
) {
    val options = listOf("" to allBooksLabel) +
        availableBooks.map { it.toString() to (getBookName?.invoke(it) ?: "Book $it") }
    DropdownSelector(
        label = stringResource(Res.string.book),
        value = selectedBookId?.toString() ?: "",
        options = options,
        onValueChange = { onSelect(it.toIntOrNull()) },
    )
}

@Composable
private fun InScriptureChapterDropdown(
    allChaptersLabel: String,
    selectedChapter: Int?,
    availableChapters: List<Int>,
    onSelect: (Int?) -> Unit,
) {
    val options = listOf("" to allChaptersLabel) +
        availableChapters.map { it.toString() to it.toString() }
    DropdownSelector(
        label = stringResource(Res.string.chapter),
        value = selectedChapter?.toString() ?: "",
        options = options,
        onValueChange = { onSelect(it.toIntOrNull()) },
    )
}

@Composable
private fun InScriptureVerseDropdown(
    allVersesLabel: String,
    selectedVerse: Int?,
    availableVerses: List<Int>,
    onSelect: (Int?) -> Unit,
) {
    val options = listOf("" to allVersesLabel) +
        availableVerses.map { it.toString() to it.toString() }
    DropdownSelector(
        label = stringResource(Res.string.verse),
        value = selectedVerse?.toString() ?: "",
        options = options,
        onValueChange = { onSelect(it.toIntOrNull()) },
    )
}

@Composable
private fun DictionarySearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.padding(start = 11.dp).size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

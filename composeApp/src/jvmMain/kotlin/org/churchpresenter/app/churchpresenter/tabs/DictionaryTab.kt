package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.dictionary_definition
import churchpresenter.composeapp.generated.resources.dictionary_entry_count
import churchpresenter.composeapp.generated.resources.dictionary_filter_all
import churchpresenter.composeapp.generated.resources.dictionary_filter_greek
import churchpresenter.composeapp.generated.resources.dictionary_filter_hebrew
import churchpresenter.composeapp.generated.resources.dictionary_kjv_usage
import churchpresenter.composeapp.generated.resources.dictionary_loading
import churchpresenter.composeapp.generated.resources.dictionary_no_results
import churchpresenter.composeapp.generated.resources.dictionary_pronunciation
import churchpresenter.composeapp.generated.resources.dictionary_search_hint
import churchpresenter.composeapp.generated.resources.dictionary_select_entry
import churchpresenter.composeapp.generated.resources.dictionary_transliteration
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.viewmodel.DictionaryLanguageFilter
import org.churchpresenter.app.churchpresenter.viewmodel.DictionaryViewModel
import org.jetbrains.compose.resources.stringResource

private val hebrewNumberColor = Color(0xFFB45309)
private val greekNumberColor = Color(0xFF1D4ED8)

@Composable
fun DictionaryTab(
    modifier: Modifier = Modifier,
    viewModel: DictionaryViewModel,
) {
    LaunchedEffect(Unit) { viewModel.load() }

    Row(modifier = modifier) {
        DictionaryListPane(
            modifier = Modifier.width(320.dp).fillMaxHeight(),
            viewModel = viewModel,
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            thickness = 1.dp,
        )
        DictionaryDetailPane(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            entry = viewModel.selectedEntry,
        )
    }
}

@Composable
private fun DictionaryListPane(
    modifier: Modifier = Modifier,
    viewModel: DictionaryViewModel,
) {
    val results = viewModel.searchResults
    val listState = rememberLazyListState()

    Column(modifier = modifier) {
        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DictionaryLanguageFilter.entries.forEach { filter ->
                FilterChip(
                    selected = viewModel.filterLanguage == filter,
                    onClick = {
                        viewModel.filterLanguage = filter
                        viewModel.selectedEntry = null
                    },
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
                            onClick = { viewModel.selectedEntry = entry },
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

@Composable
private fun DictionaryDetailPane(
    modifier: Modifier = Modifier,
    entry: StrongsEntry?,
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

    val scrollState = rememberScrollState()
    Box(modifier = modifier) {
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
            )

            // KJV Usage (only if present)
            if (entry.kjvUsage.isNotBlank()) {
                DetailSection(
                    label = stringResource(Res.string.dictionary_kjv_usage),
                    body = entry.kjvUsage,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
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

@Composable
private fun DetailSection(label: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
        )
    }
}

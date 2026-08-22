package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.found_results
import org.churchpresenter.app.churchpresenter.composables.initialPassClickable
import org.churchpresenter.app.churchpresenter.utils.highlightRanges
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.ColumnScope
import org.churchpresenter.bible.BibleSearch

/**
 * The list a text search produced, with the matched words picked out of each verse.
 */
@Composable
internal fun ColumnScope.BibleSearchResults(
    results: List<BibleSearch>,
    query: String,
    onResultChosen: (BibleSearch) -> Unit,
) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(31.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.found_results, results.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
                    itemsIndexed(results) { _, result ->

                        val resultText = result.verseText
                        val highlightedText = buildAnnotatedString {
                            var lastIndex = 0

                            for ((safeStart, safeEnd) in highlightRanges(resultText, query)) {
                                append(resultText.substring(lastIndex.coerceAtMost(safeStart), safeStart))
                                withStyle(style = SpanStyle(
                                    background = MaterialTheme.colorScheme.primaryContainer,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )) {
                                    append(resultText.substring(safeStart, safeEnd))
                                }
                                lastIndex = safeEnd
                            }
                            if (lastIndex < resultText.length) append(resultText.substring(lastIndex))
                        }
                        Text(
                            text = highlightedText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .initialPassClickable { onResultChosen(result) }
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState)
                )
            }
        }
}

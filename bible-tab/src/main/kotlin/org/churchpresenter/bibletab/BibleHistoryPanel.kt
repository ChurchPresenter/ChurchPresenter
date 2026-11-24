package org.churchpresenter.bibletab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_history
import org.churchpresenter.strings.generated.resources.bible_history_clear
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.sharedui.composables.initialPassCombinedClickable
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent

private const val SELECTION_BAR_WIDTH = 4f

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BibleHistoryPanel(
    entries: List<BibleViewModel.HistoryEntry>,
    expanded: Boolean,
    selectedIndex: Int,
    onToggleExpanded: () -> Unit,
    onClear: () -> Unit,
    onEntryClick: (Int) -> Unit,
    onEntryDoubleClick: (Int) -> Unit,
) {
        if (entries.isEmpty()) return
    // Its own card under the verse card; the gap lives here so an empty history leaves none.
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).bibleListCard()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(if (expanded) IconRes.drawable.ic_arrow_down else IconRes.drawable.ic_arrow_up),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Res.string.bible_history),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Tooltip(text = stringResource(Res.string.bible_history_clear)) {
                    IconButton(
                        icon = painterResource(IconRes.drawable.ic_delete),
                        contentDescription = stringResource(Res.string.bible_history_clear),
                        onClick = onClear,
                        hierarchy = ButtonHierarchy.Tertiary,
                        intent = ButtonIntent.Neutral,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                val historyListState = rememberLazyListState()
                LaunchedEffect(entries.size) { historyListState.scrollToItem(0) }
                val markerColor = MaterialTheme.semantic.marker
                Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    LazyColumn(state = historyListState, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
                        itemsIndexed(entries) { idx, entry ->
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    ) { append(entry.displayText) }
                                    append("  ${entry.verseText}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                                    .background(
                                        if (idx == selectedIndex) MaterialTheme.colorScheme.surfaceVariant
                                        else if (idx % 2 == 0) MaterialTheme.colorScheme.surface
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .drawBehind {
                                        if (idx == selectedIndex) {
                                            drawRect(color = markerColor, size = Size(SELECTION_BAR_WIDTH, size.height))
                                        }
                                    }
                                    .initialPassCombinedClickable(
                                        onClick = { onEntryClick(idx) },
                                        onDoubleClick = { onEntryDoubleClick(idx) }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState = historyListState)
                    )
                }
            }
        }
}

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.bible_history
import org.churchpresenter.resources.generated.resources.bible_history_clear
import org.churchpresenter.resources.generated.resources.ic_arrow_down
import org.churchpresenter.resources.generated.resources.ic_arrow_up
import org.churchpresenter.resources.generated.resources.ic_delete
import org.churchpresenter.ui.initialPassCombinedClickable
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painter = painterResource(if (expanded) Res.drawable.ic_arrow_down else Res.drawable.ic_arrow_up), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = stringResource(Res.string.bible_history), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
                Spacer(modifier = Modifier.weight(1f))
                TooltipArea(
                    tooltip = {
                        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall) {
                            Text(stringResource(Res.string.bible_history_clear), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = onClear) {
                        Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = stringResource(Res.string.bible_history_clear), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) { append(entry.displayText) }
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
                                        if (idx == selectedIndex) drawRect(color = markerColor, size = Size(SELECTION_BAR_WIDTH, size.height))
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
                    VerticalScrollbar(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), adapter = rememberScrollbarAdapter(scrollState = historyListState))
                }
            }
        }
}

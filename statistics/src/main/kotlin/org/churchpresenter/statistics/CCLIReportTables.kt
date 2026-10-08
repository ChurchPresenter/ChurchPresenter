package org.churchpresenter.statistics

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import org.churchpresenter.theme.AppShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.ccli_bible_books_chart
import org.churchpresenter.strings.generated.resources.ccli_bible_summary
import org.churchpresenter.strings.generated.resources.ccli_col_author
import org.churchpresenter.strings.generated.resources.obs_mode_bible
import org.churchpresenter.strings.generated.resources.ccli_col_ccli
import org.churchpresenter.strings.generated.resources.ccli_col_first
import org.churchpresenter.strings.generated.resources.ccli_col_last
import org.churchpresenter.strings.generated.resources.ccli_col_rank
import org.churchpresenter.strings.generated.resources.ccli_col_songbook
import org.churchpresenter.strings.generated.resources.ccli_col_title
import org.churchpresenter.strings.generated.resources.ccli_col_used
import org.churchpresenter.strings.generated.resources.ccli_col_verse
import org.churchpresenter.strings.generated.resources.ccli_no_data
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.strings.generated.resources.stats_clear_item
import org.churchpresenter.strings.generated.resources.ccli_songs_chart
import org.churchpresenter.strings.generated.resources.ccli_songs_summary
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SongsReportContent(songs: List<SongSummary>, onClear: (SongSummary) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val totalPlays = songs.sumOf { it.count }

    if (songs.isEmpty()) {
        EmptyState(stringResource(Res.string.ccli_no_data))
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left: ranked bar list (top 12)
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)
        ) {
            ChartPanelHeader(
                title = stringResource(Res.string.ccli_songs_chart),
                subtitle = stringResource(Res.string.ccli_songs_summary, songs.size, totalPlays)
            )
            TopItemsChart(
                data = songs.take(TOP_CHART_ENTRIES).map { it.title to it.count },
                accent = primary,
                modifier = Modifier.fillMaxSize()
            )
        }

        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

        // Right: full table
        SongTable(songs = songs, onClear = onClear, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

// ── Bible tab ─────────────────────────────────────────────────────────────────

@Composable
internal fun BibleReportContent(verses: List<VerseSummary>, onClear: (VerseSummary) -> Unit) {
    val secondary = MaterialTheme.colorScheme.tertiary
    val totalPlays = verses.sumOf { it.count }

    if (verses.isEmpty()) {
        EmptyState(stringResource(Res.string.ccli_no_data))
        return
    }

    // Aggregate by book for the chart
    val byBook = verses
        .groupBy { it.bookName }
        .map { (book, vs) -> book to vs.sumOf { it.count } }
        .sortedByDescending { it.second }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)
        ) {
            ChartPanelHeader(
                title = stringResource(Res.string.ccli_bible_books_chart),
                subtitle = stringResource(Res.string.ccli_bible_summary, verses.size, totalPlays)
            )
            TopItemsChart(
                data = byBook.take(TOP_CHART_ENTRIES),
                accent = secondary,
                modifier = Modifier.fillMaxSize()
            )
        }

        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

        VerseTable(verses = verses, onClear = onClear, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

// ── Activity tab ──────────────────────────────────────────────────────────────

@Composable
private fun SongTable(songs: List<SongSummary>, onClear: (SongSummary) -> Unit, modifier: Modifier = Modifier) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val maxCount = remember(songs) { songs.maxOfOrNull { it.count } ?: 1 }
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TableHeader(stringResource(Res.string.ccli_col_rank), 28.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_title), null, weight = 2f)
            TableHeader(stringResource(Res.string.ccli_col_author), null, weight = 1.5f)
            TableHeader(stringResource(Res.string.ccli_col_songbook), null, weight = 1f)
            TableHeader(stringResource(Res.string.ccli_col_ccli), 66.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_used), 52.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_first), 90.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_last), 90.dp.value)
            Spacer(Modifier.width(CLEAR_COLUMN_WIDTH))
        }
        HorizontalDivider()
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(songs) { index, song ->
                    val interactionSource = remember(song) { MutableInteractionSource() }
                    val hovered by interactionSource.collectIsHoveredAsState()
                    val clearAlpha by animateFloatAsState(if (hovered) 1f else 0f, label = "songRowClearAlpha")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hoverable(interactionSource)
                            .background(
                                if (index % 2 == 0) {
                                    Color.Transparent
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                            .padding(start = 12.dp, end = 20.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TableCell("${index + 1}", fixedWidth = 28.dp.value, align = TextAlign.End)
                        TableCell(song.title, weight = 2f)
                        TableCell(song.author.ifBlank { "—" }, weight = 1.5f, muted = song.author.isBlank())
                        TableCell(song.songbook.ifBlank { "—" }, weight = 1f, muted = song.songbook.isBlank())
                        TableCell(
                            song.ccliNumber.ifBlank { "—" },
                            fixedWidth = 66.dp.value,
                            muted = song.ccliNumber.isBlank()
                        )
                        UsageBadgeCell(song.count, maxCount, accent, fixedWidth = 52.dp.value)
                        TableCell(dateFmt.format(Date(song.firstUsed)), fixedWidth = 90.dp.value)
                        TableCell(dateFmt.format(Date(song.lastUsed)), fixedWidth = 90.dp.value)
                        RowClearButton(label = song.title, alpha = clearAlpha) { onClear(song) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(listState)
            )
        }
    }
}

@Composable
private fun VerseTable(verses: List<VerseSummary>, onClear: (VerseSummary) -> Unit, modifier: Modifier = Modifier) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val maxCount = remember(verses) { verses.maxOfOrNull { it.count } ?: 1 }
    val accent = MaterialTheme.colorScheme.tertiary
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TableHeader(stringResource(Res.string.ccli_col_rank), 28.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_verse), null, weight = 2f)
            TableHeader(stringResource(Res.string.obs_mode_bible), null, weight = 1f)
            TableHeader(stringResource(Res.string.ccli_col_used), 52.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_first), 90.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_last), 90.dp.value)
            Spacer(Modifier.width(CLEAR_COLUMN_WIDTH))
        }
        HorizontalDivider()
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(verses) { index, verse ->
                    val interactionSource = remember(verse) { MutableInteractionSource() }
                    val hovered by interactionSource.collectIsHoveredAsState()
                    val clearAlpha by animateFloatAsState(if (hovered) 1f else 0f, label = "verseRowClearAlpha")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hoverable(interactionSource)
                            .background(
                                if (index % 2 == 0) {
                                    Color.Transparent
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                            .padding(start = 12.dp, end = 20.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TableCell("${index + 1}", fixedWidth = 28.dp.value, align = TextAlign.End)
                        TableCell("${verse.bookName} ${verse.chapter}:${verse.verseNumber}", weight = 2f)
                        TableCell(verse.bibleName.ifBlank { "—" }, weight = 1f, muted = verse.bibleName.isBlank())
                        UsageBadgeCell(verse.count, maxCount, accent, fixedWidth = 52.dp.value)
                        TableCell(dateFmt.format(Date(verse.firstUsed)), fixedWidth = 90.dp.value)
                        TableCell(dateFmt.format(Date(verse.lastUsed)), fixedWidth = 90.dp.value)
                        RowClearButton(
                            label = "${verse.bookName} ${verse.chapter}:${verse.verseNumber}",
                            alpha = clearAlpha,
                        ) { onClear(verse) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(listState)
            )
        }
    }
}

/**
 * The delete at the end of a table row, faded in on hover. Kept out of the tab order of a scanning
 * eye when idle, but always in the semantics tree so a test can reach it.
 */
@Composable
private fun RowClearButton(label: String, alpha: Float, onClear: () -> Unit) {
    TooltipIconButton(
        painter = painterResource(IconRes.drawable.ic_delete),
        text = stringResource(Res.string.stats_clear_item),
        onClick = onClear,
        iconSize = 13.dp,
        buttonSize = CLEAR_COLUMN_WIDTH,
        iconTint = MaterialTheme.colorScheme.error,
        modifier = Modifier.alpha(alpha).testTag(REPORT_CLEAR_ROW_TAG + label)
    )
}

@Composable
internal fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.TableHeader(
    text: String,
    fixedWidth: Float? = null,
    weight: Float = 1f
) {
    val mod = if (fixedWidth != null) Modifier.width(fixedWidth.dp) else Modifier.weight(weight)
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = mod, maxLines = 1
    )
}

/**
 * A "times used" count rendered as a rounded badge whose fill intensity scales with how
 * high the count is relative to the busiest item in the list.
 */
@Composable
private fun RowScope.UsageBadgeCell(
    count: Int,
    maxCount: Int,
    accent: Color,
    fixedWidth: Float
) {
    val ratio = if (maxCount > 0) count.toFloat() / maxCount else 0f
    val bg = accent.copy(alpha = (0.18f + 0.72f * ratio).coerceIn(0.18f, 0.9f))
    val fg = if (ratio > 0.5f) Color.White else accent
    Box(modifier = Modifier.width(fixedWidth.dp)) {
        Box(
            modifier = Modifier
                .widthIn(min = 24.dp)
                .clip(AppShape(5.dp))
                .background(bg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = fg
            )
        }
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    fixedWidth: Float? = null,
    weight: Float = 1f,
    align: TextAlign = TextAlign.Start,
    bold: Boolean = false,
    color: Color? = null,
    muted: Boolean = false
) {
    val textColor = when {
        color != null -> color
        muted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val style = if (bold) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
    else MaterialTheme.typography.bodySmall
    val mod = if (fixedWidth != null) Modifier.width(fixedWidth.dp) else Modifier.weight(weight)
    Text(
        text,
        style = style,
        color = textColor,
        modifier = mod,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align
    )
}

private const val TOP_CHART_ENTRIES = 12

private val CLEAR_COLUMN_WIDTH = 26.dp

internal const val REPORT_CLEAR_ROW_TAG = "reportClearRow:"

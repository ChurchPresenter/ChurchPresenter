package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ccli_report
import churchpresenter.composeapp.generated.resources.ccli_report_description
import churchpresenter.composeapp.generated.resources.clear_statistics
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.em_dash
import churchpresenter.composeapp.generated.resources.export_to_xls
import churchpresenter.composeapp.generated.resources.file_chooser_save_statistics
import churchpresenter.composeapp.generated.resources.file_filter_xls
import churchpresenter.composeapp.generated.resources.statistics
import churchpresenter.composeapp.generated.resources.statistics_exported_error
import churchpresenter.composeapp.generated.resources.statistics_exported_success
import churchpresenter.composeapp.generated.resources.statistics_song_count
import churchpresenter.composeapp.generated.resources.statistics_verse_count
import churchpresenter.composeapp.generated.resources.top_songs
import churchpresenter.composeapp.generated.resources.top_verses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.data.SongDisplayEntry
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseDisplayEntry
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
import javax.swing.filechooser.FileNameExtensionFilter
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser

@Composable
fun StatisticsDialog(
    isVisible: Boolean,
    theme: ThemeMode,
    statisticsManager: StatisticsManager,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    val coroutineScope = rememberCoroutineScope()

    var topSongsBySongbook by remember { mutableStateOf(statisticsManager.getTopSongsBySongbook()) }
    var topVersesByBible by remember { mutableStateOf(statisticsManager.getTopVersesByBible()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showCCLIReport by remember { mutableStateOf(false) }

    val successMsg = stringResource(Res.string.statistics_exported_success)
    val errorMsg = stringResource(Res.string.statistics_exported_error)
    val saveTitle = stringResource(Res.string.file_chooser_save_statistics)
    val filterDesc = stringResource(Res.string.file_filter_xls)

    if (showCCLIReport) {
        CCLIReportDialog(
            isVisible = true,
            theme = theme,
            statisticsManager = statisticsManager,
            onDismiss = { showCCLIReport = false }
        )
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 700.dp, 620.dp),
            width = 700.dp,
            height = 620.dp
        ),
        title = stringResource(Res.string.statistics),
        resizable = true
    ) {
        AppThemeWrapper(theme = theme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            CcliBanner(onOpen = { showCCLIReport = true })
                            TopSongsSection(topSongsBySongbook)
                            TopVersesSection(topVersesByBible)
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(scrollState)
                        )
                    }

                    if (statusMessage != null) {
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (statusMessage == successMsg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = {
                                coroutineScope.launch {
                                    val path = FileChooser.platformInstance.save(
                                        location = null,
                                        suggestedName = "statistics.xls",
                                        filters = listOf(FileNameExtensionFilter(filterDesc, "xls")),
                                        title = saveTitle
                                    )
                                    if (path != null) {
                                        val ok = withContext(Dispatchers.IO) { statisticsManager.exportStatisticsToXls(path.toFile()) }
                                        statusMessage = if (ok) successMsg else errorMsg
                                    }
                                }
                            }
                        ) { Text(stringResource(Res.string.export_to_xls)) }

                        Button(
                            shape = RoundedCornerShape(6.dp),
                            onClick = {
                                statisticsManager.clearStatistics()
                                topSongsBySongbook = statisticsManager.getTopSongsBySongbook()
                                topVersesByBible = statisticsManager.getTopVersesByBible()
                                statusMessage = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) { Text(stringResource(Res.string.clear_statistics)) }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(shape = RoundedCornerShape(6.dp), onClick = onDismiss) { Text(stringResource(Res.string.close)) }
                    }
                }
            }
        }
    }
}

// ── CCLI report callout ─────────────────────────────────────────────────────────

@Composable
private fun CcliBanner(onOpen: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Assessment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.ccli_report),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            Text(
                stringResource(Res.string.ccli_report_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(shape = RoundedCornerShape(8.dp), onClick = onOpen) {
            Text(stringResource(Res.string.ccli_report), style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ── Top Songs / Top Verses sections ─────────────────────────────────────────────

@Composable
private fun TopSongsSection(data: Map<String, List<SongDisplayEntry>>) {
    val accent = MaterialTheme.colorScheme.primary
    if (data.isEmpty()) {
        EmptyStatSection(stringResource(Res.string.top_songs))
        return
    }
    val dash = stringResource(Res.string.em_dash)
    val songbooks = remember(data) { data.keys.toList() }
    var selected by remember(data) { mutableStateOf(songbooks.first()) }
    val songs = data[selected] ?: emptyList()
    val maxCount = songs.maxOfOrNull { it.count } ?: 1

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(
            title = stringResource(Res.string.top_songs),
            selectedLabel = selected.ifBlank { dash },
            options = songbooks,
            optionLabel = { it.ifBlank { dash } },
            countLabel = stringResource(Res.string.statistics_song_count, songs.size),
            onSelect = { selected = it }
        )
        songs.forEachIndexed { index, entry ->
            StatBarRow(
                rank = index + 1,
                label = "#${entry.songNumber} ${entry.title}",
                count = entry.count,
                maxCount = maxCount,
                accent = accent,
                emphasize = index == 0
            )
        }
    }
}

@Composable
private fun TopVersesSection(data: Map<String, List<VerseDisplayEntry>>) {
    val accent = MaterialTheme.colorScheme.tertiary
    if (data.isEmpty()) {
        EmptyStatSection(stringResource(Res.string.top_verses))
        return
    }
    val dash = stringResource(Res.string.em_dash)
    val bibles = remember(data) { data.keys.toList() }
    var selected by remember(data) { mutableStateOf(bibles.first()) }
    val verses = data[selected] ?: emptyList()
    val maxCount = verses.maxOfOrNull { it.count } ?: 1

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(
            title = stringResource(Res.string.top_verses),
            selectedLabel = selected.ifBlank { dash },
            options = bibles,
            optionLabel = { it.ifBlank { dash } },
            countLabel = stringResource(Res.string.statistics_verse_count, verses.size),
            onSelect = { selected = it }
        )
        verses.forEachIndexed { index, entry ->
            StatBarRow(
                rank = index + 1,
                label = "${entry.bookName} ${entry.chapter}:${entry.verseNumber}",
                count = entry.count,
                maxCount = maxCount,
                accent = accent,
                emphasize = index == 0
            )
        }
    }
}

@Composable
private fun EmptyStatSection(title: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(Res.string.em_dash), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Row + header pieces ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    selectedLabel: String,
    options: List<String>,
    optionLabel: (String) -> String,
    countLabel: String,
    onSelect: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(10.dp))
        StatDropdown(selectedLabel, options, optionLabel, onSelect)
        Spacer(Modifier.weight(1f))
        Text(
            countLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatDropdown(
    label: String,
    options: List<String>,
    optionLabel: (String) -> String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            shape = RoundedCornerShape(8.dp),
            onClick = { expanded = true },
            contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 0.dp, bottom = 0.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionLabel(opt), style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelect(opt); expanded = false },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** A ranked row: rank, title, a gradient bar sized to the top count, and the play count. */
@Composable
private fun StatBarRow(
    rank: Int,
    label: String,
    count: Int,
    maxCount: Int,
    accent: Color,
    emphasize: Boolean
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val barBrush = Brush.horizontalGradient(listOf(accent, lerp(accent, Color.White, 0.35f)))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (count.toFloat() / maxCount).coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(barBrush)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = accent,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp)
        )
    }
}

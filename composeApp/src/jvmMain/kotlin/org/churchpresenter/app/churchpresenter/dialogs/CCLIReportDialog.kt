package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ccli_activity_title
import churchpresenter.composeapp.generated.resources.ccli_bible_books_chart
import churchpresenter.composeapp.generated.resources.ccli_bible_summary
import churchpresenter.composeapp.generated.resources.ccli_col_author
import churchpresenter.composeapp.generated.resources.ccli_col_bible
import churchpresenter.composeapp.generated.resources.ccli_col_ccli
import churchpresenter.composeapp.generated.resources.ccli_col_first
import churchpresenter.composeapp.generated.resources.ccli_col_last
import churchpresenter.composeapp.generated.resources.ccli_col_rank
import churchpresenter.composeapp.generated.resources.ccli_col_songbook
import churchpresenter.composeapp.generated.resources.ccli_col_title
import churchpresenter.composeapp.generated.resources.ccli_col_used
import churchpresenter.composeapp.generated.resources.ccli_col_verse
import churchpresenter.composeapp.generated.resources.ccli_export_csv
import churchpresenter.composeapp.generated.resources.ccli_export_xls
import churchpresenter.composeapp.generated.resources.ccli_exported_error
import churchpresenter.composeapp.generated.resources.ccli_exported_success
import churchpresenter.composeapp.generated.resources.ccli_file_chooser_csv
import churchpresenter.composeapp.generated.resources.ccli_file_chooser_xls
import churchpresenter.composeapp.generated.resources.ccli_file_filter_csv
import churchpresenter.composeapp.generated.resources.ccli_file_filter_xls
import churchpresenter.composeapp.generated.resources.ccli_from
import churchpresenter.composeapp.generated.resources.ccli_legend_bible
import churchpresenter.composeapp.generated.resources.ccli_legend_songs
import churchpresenter.composeapp.generated.resources.ccli_no_data
import churchpresenter.composeapp.generated.resources.ccli_no_events
import churchpresenter.composeapp.generated.resources.ccli_preset_30d
import churchpresenter.composeapp.generated.resources.ccli_preset_90d
import churchpresenter.composeapp.generated.resources.ccli_preset_all_time
import churchpresenter.composeapp.generated.resources.ccli_preset_last_year
import churchpresenter.composeapp.generated.resources.ccli_preset_this_year
import churchpresenter.composeapp.generated.resources.ccli_report_title
import churchpresenter.composeapp.generated.resources.ccli_songs_chart
import churchpresenter.composeapp.generated.resources.ccli_songs_summary
import churchpresenter.composeapp.generated.resources.ccli_stat_bible_verses
import churchpresenter.composeapp.generated.resources.ccli_stat_busiest
import churchpresenter.composeapp.generated.resources.ccli_stat_songs_presented
import churchpresenter.composeapp.generated.resources.ccli_tab_activity
import churchpresenter.composeapp.generated.resources.ccli_tab_bible
import churchpresenter.composeapp.generated.resources.ccli_tab_songs
import churchpresenter.composeapp.generated.resources.ccli_to
import churchpresenter.composeapp.generated.resources.ccli_month_january
import churchpresenter.composeapp.generated.resources.ccli_month_february
import churchpresenter.composeapp.generated.resources.ccli_month_march
import churchpresenter.composeapp.generated.resources.ccli_month_april
import churchpresenter.composeapp.generated.resources.ccli_month_may
import churchpresenter.composeapp.generated.resources.ccli_month_june
import churchpresenter.composeapp.generated.resources.ccli_month_july
import churchpresenter.composeapp.generated.resources.ccli_month_august
import churchpresenter.composeapp.generated.resources.ccli_month_september
import churchpresenter.composeapp.generated.resources.ccli_month_october
import churchpresenter.composeapp.generated.resources.ccli_month_november
import churchpresenter.composeapp.generated.resources.ccli_month_december
import churchpresenter.composeapp.generated.resources.ccli_month_jan
import churchpresenter.composeapp.generated.resources.ccli_month_feb
import churchpresenter.composeapp.generated.resources.ccli_month_mar
import churchpresenter.composeapp.generated.resources.ccli_month_apr
import churchpresenter.composeapp.generated.resources.ccli_month_may_short
import churchpresenter.composeapp.generated.resources.ccli_month_jun
import churchpresenter.composeapp.generated.resources.ccli_month_jul
import churchpresenter.composeapp.generated.resources.ccli_month_aug
import churchpresenter.composeapp.generated.resources.ccli_month_sep
import churchpresenter.composeapp.generated.resources.ccli_month_oct
import churchpresenter.composeapp.generated.resources.ccli_month_nov
import churchpresenter.composeapp.generated.resources.ccli_month_dec
import churchpresenter.composeapp.generated.resources.close
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.data.ActivityPoint
import org.churchpresenter.app.churchpresenter.data.SongSummary
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseSummary
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.swing.filechooser.FileNameExtensionFilter
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser

private val VERSE_BAR_COLOR = Color(0xFF43A047)
private val BUSIEST_CARD_COLOR = Color(0xFFFF9800)

@Composable
fun CCLIReportDialog(
    isVisible: Boolean,
    theme: ThemeMode,
    statisticsManager: StatisticsManager,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    val coroutineScope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }

    val yearRange = remember {
        val earliest = statisticsManager.getEarliestEventTime()
        val startYear = if (earliest != null)
            java.time.Instant.ofEpochMilli(earliest).atZone(zone).year
        else today.year
        startYear..today.year
    }

    var fromYear by remember { mutableStateOf(today.year) }
    var fromMonth by remember { mutableStateOf(1) }
    var fromDay by remember { mutableStateOf(1) }
    var toYear by remember { mutableStateOf(today.year) }
    var toMonth by remember { mutableStateOf(today.monthValue) }
    var toDay by remember { mutableStateOf(today.lengthOfMonth()) }

    fun fromMs(): Long = LocalDate.of(fromYear, fromMonth, fromDay.coerceAtMost(LocalDate.of(fromYear, fromMonth, 1).lengthOfMonth()))
        .atStartOfDay(zone).toInstant().toEpochMilli()
    fun toMs(): Long = LocalDate.of(toYear, toMonth, toDay.coerceAtMost(LocalDate.of(toYear, toMonth, 1).lengthOfMonth()))
        .atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

    var songs by remember { mutableStateOf(emptyList<SongSummary>()) }
    var verses by remember { mutableStateOf(emptyList<VerseSummary>()) }
    var activity by remember { mutableStateOf(emptyList<ActivityPoint>()) }
    var selectedTab by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsSuccess by remember { mutableStateOf(true) }
    // Which quick-range preset is currently highlighted (null once a date is edited by hand).
    var activePreset by remember { mutableStateOf<Int?>(null) }

    val hasLog = remember { statisticsManager.hasEventLog() }

    fun reload() {
        coroutineScope.launch {
            val f = fromMs(); val t = toMs()
            songs = withContext(Dispatchers.IO) { statisticsManager.getAllSongsInRange(f, t) }
            verses = withContext(Dispatchers.IO) { statisticsManager.getAllVersesInRange(f, t) }
            activity = withContext(Dispatchers.IO) { statisticsManager.getActivityByPeriod(f, t) }
        }
    }

    LaunchedEffect(fromYear, fromMonth, fromDay, toYear, toMonth, toDay) { reload() }

    fun applyPreset(daysBack: Int?) {
        if (daysBack == null) {
            fromYear = yearRange.first; fromMonth = 1; fromDay = 1
            toYear = today.year; toMonth = today.monthValue; toDay = today.dayOfMonth
        } else {
            val from = today.minusDays(daysBack.toLong())
            fromYear = from.year; fromMonth = from.monthValue; fromDay = from.dayOfMonth
            toYear = today.year; toMonth = today.monthValue; toDay = today.dayOfMonth
        }
    }

    val successMsg = stringResource(Res.string.ccli_exported_success)
    val errorMsg = stringResource(Res.string.ccli_exported_error)
    val csvChooserTitle = stringResource(Res.string.ccli_file_chooser_csv)
    val csvFilterDesc = stringResource(Res.string.ccli_file_filter_csv)
    val xlsChooserTitle = stringResource(Res.string.ccli_file_chooser_xls)
    val xlsFilterDesc = stringResource(Res.string.ccli_file_filter_xls)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 940.dp, 700.dp),
            width = 940.dp, height = 700.dp
        ),
        title = stringResource(Res.string.ccli_report_title),
        resizable = true
    ) {
        AppThemeWrapper(theme = theme) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ── Date range header ────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Preset buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PresetButton(stringResource(Res.string.ccli_preset_30d), active = activePreset == 0) {
                                activePreset = 0; applyPreset(30)
                            }
                            PresetButton(stringResource(Res.string.ccli_preset_90d), active = activePreset == 1) {
                                activePreset = 1; applyPreset(90)
                            }
                            PresetButton(stringResource(Res.string.ccli_preset_this_year), active = activePreset == 2) {
                                activePreset = 2
                                fromYear = today.year; fromMonth = 1; fromDay = 1
                                toYear = today.year; toMonth = today.monthValue; toDay = today.dayOfMonth
                            }
                            PresetButton(stringResource(Res.string.ccli_preset_last_year), active = activePreset == 3) {
                                activePreset = 3
                                fromYear = today.year - 1; fromMonth = 1; fromDay = 1
                                toYear = today.year - 1; toMonth = 12; toDay = 31
                            }
                            PresetButton(stringResource(Res.string.ccli_preset_all_time), active = activePreset == 4) {
                                activePreset = 4; applyPreset(null)
                            }
                        }
                        // Date pickers
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(Res.string.ccli_from), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DatePicker(
                                year = fromYear, month = fromMonth, day = fromDay,
                                yearRange = yearRange,
                                onChanged = { y, m, d -> activePreset = null; fromYear = y; fromMonth = m; fromDay = d }
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(Res.string.ccli_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DatePicker(
                                year = toYear, month = toMonth, day = toDay,
                                yearRange = yearRange,
                                onChanged = { y, m, d -> activePreset = null; toYear = y; toMonth = m; toDay = d }
                            )
                        }
                    }

                    HorizontalDivider()

                    if (!hasLog) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.ccli_no_events),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    } else {
                        // ── Tabs ─────────────────────────────────────────────
                        PrimaryTabRow(selectedTabIndex = selectedTab) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                                text = { Text(stringResource(Res.string.ccli_tab_songs) + " (${songs.size})") })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                                text = { Text(stringResource(Res.string.ccli_tab_bible) + " (${verses.size})") })
                            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                                text = { Text(stringResource(Res.string.ccli_tab_activity)) })
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (selectedTab) {
                                0 -> SongsReportContent(songs)
                                1 -> BibleReportContent(verses)
                                2 -> ActivityContent(activity)
                            }
                        }
                    }

                    // ── Status message ───────────────────────────────────────
                    if (statusMessage != null) {
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (statusIsSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }

                    // ── Bottom buttons ───────────────────────────────────────
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = {
                                coroutineScope.launch {
                                    val f = fromMs(); val t = toMs()
                                    val path = FileChooser.platformInstance.save(
                                        location = null,
                                        suggestedName = "ccli_report.csv",
                                        filters = listOf(FileNameExtensionFilter(csvFilterDesc, "csv")),
                                        title = csvChooserTitle
                                    )
                                    if (path != null) {
                                        val ok = withContext(Dispatchers.IO) { statisticsManager.exportCcliCsv(path.toFile(), f, t) }
                                        statusIsSuccess = ok; statusMessage = if (ok) successMsg else errorMsg
                                    }
                                }
                            }
                        ) { Text(stringResource(Res.string.ccli_export_csv)) }

                        OutlinedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = {
                                coroutineScope.launch {
                                    val f = fromMs(); val t = toMs()
                                    val path = FileChooser.platformInstance.save(
                                        location = null,
                                        suggestedName = "ccli_report.xls",
                                        filters = listOf(FileNameExtensionFilter(xlsFilterDesc, "xls")),
                                        title = xlsChooserTitle
                                    )
                                    if (path != null) {
                                        val ok = withContext(Dispatchers.IO) { statisticsManager.exportFilteredXls(path.toFile(), f, t) }
                                        statusIsSuccess = ok; statusMessage = if (ok) successMsg else errorMsg
                                    }
                                }
                            }
                        ) { Text(stringResource(Res.string.ccli_export_xls)) }

                        Spacer(Modifier.weight(1f))

                        Button(shape = RoundedCornerShape(6.dp), onClick = onDismiss) { Text(stringResource(Res.string.close)) }
                    }
                }
            }
        }
    }
}

// ── Songs tab ─────────────────────────────────────────────────────────────────

@Composable
private fun SongsReportContent(songs: List<SongSummary>) {
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
                data = songs.take(12).map { it.title to it.count },
                accent = primary,
                modifier = Modifier.fillMaxSize()
            )
        }

        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

        // Right: full table
        SongTable(songs = songs, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

// ── Bible tab ─────────────────────────────────────────────────────────────────

@Composable
private fun BibleReportContent(verses: List<VerseSummary>) {
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
                data = byBook.take(12),
                accent = secondary,
                modifier = Modifier.fillMaxSize()
            )
        }

        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

        VerseTable(verses = verses, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

// ── Activity tab ──────────────────────────────────────────────────────────────

@Composable
private fun ActivityContent(activity: List<ActivityPoint>) {
    val primary = MaterialTheme.colorScheme.primary
    val verseColor = VERSE_BAR_COLOR

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (activity.isEmpty() || activity.all { it.songCount + it.verseCount == 0 }) {
            EmptyState(stringResource(Res.string.ccli_no_data))
            return
        }

        // Summary stat cards
        val totalSongs = activity.sumOf { it.songCount }
        val totalVerses = activity.sumOf { it.verseCount }
        val busiest = activity.maxByOrNull { it.songCount + it.verseCount }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(stringResource(Res.string.ccli_stat_songs_presented), "$totalSongs", primary)
            StatCard(stringResource(Res.string.ccli_stat_bible_verses), "$totalVerses", verseColor)
            StatCard(
                stringResource(Res.string.ccli_stat_busiest),
                if (busiest != null) "${busiest.label} (${busiest.songCount + busiest.verseCount})" else "—",
                BUSIEST_CARD_COLOR
            )
        }

        Spacer(Modifier.height(16.dp))

        // Chart header + date range
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                stringResource(Res.string.ccli_activity_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${activity.first().label} – ${activity.last().label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Chart
        ActivityBarChart(
            data = activity,
            songColor = primary,
            verseColor = verseColor,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        // Legend
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            LegendDot(primary)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.ccli_legend_songs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            LegendDot(verseColor)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.ccli_legend_bible), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Tables ────────────────────────────────────────────────────────────────────

@Composable
private fun SongTable(songs: List<SongSummary>, modifier: Modifier = Modifier) {
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
        }
        HorizontalDivider()
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(songs) { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(start = 12.dp, end = 20.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TableCell("${index + 1}", fixedWidth = 28.dp.value, align = TextAlign.End)
                        TableCell(song.title, weight = 2f)
                        TableCell(song.author.ifBlank { "—" }, weight = 1.5f, muted = song.author.isBlank())
                        TableCell(song.songbook.ifBlank { "—" }, weight = 1f, muted = song.songbook.isBlank())
                        TableCell(song.ccliNumber.ifBlank { "—" }, fixedWidth = 66.dp.value, muted = song.ccliNumber.isBlank())
                        UsageBadgeCell(song.count, maxCount, accent, fixedWidth = 52.dp.value)
                        TableCell(dateFmt.format(Date(song.firstUsed)), fixedWidth = 90.dp.value)
                        TableCell(dateFmt.format(Date(song.lastUsed)), fixedWidth = 90.dp.value)
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
private fun VerseTable(verses: List<VerseSummary>, modifier: Modifier = Modifier) {
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
            TableHeader(stringResource(Res.string.ccli_col_bible), null, weight = 1f)
            TableHeader(stringResource(Res.string.ccli_col_used), 52.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_first), 90.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_last), 90.dp.value)
        }
        HorizontalDivider()
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(verses) { index, verse ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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

// ── Charts ────────────────────────────────────────────────────────────────────

@Composable
private fun ChartPanelHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Ranked list where each row shows the label and its value on one line with a thin
 * gradient bar below it, sized relative to the largest value. The bar is a quick
 * visual ranking indicator, not an axis-based chart.
 */
@Composable
private fun TopItemsChart(
    data: List<Pair<String, Int>>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val maxValue = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val brightEnd = lerp(accent, Color.White, 0.35f)
    val barBrush = Brush.horizontalGradient(listOf(accent, brightEnd))
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            data.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$value",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = accent
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(trackColor)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = value.toFloat() / maxValue)
                                .clip(RoundedCornerShape(3.dp))
                                .background(barBrush)
                        )
                    }
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityBarChart(
    data: List<ActivityPoint>,
    songColor: Color,
    verseColor: Color,
    modifier: Modifier = Modifier
) {
    val maxTotal = data.maxOf { it.songCount + it.verseCount }.coerceAtLeast(1)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val songsLabel = stringResource(Res.string.ccli_legend_songs)
    val versesLabel = stringResource(Res.string.ccli_legend_bible)
    val barCorner = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    // Lighter at the top, fading down to the series color.
    val songBrush = Brush.verticalGradient(listOf(lerp(songColor, Color.White, 0.3f), songColor))
    val verseBrush = Brush.verticalGradient(listOf(lerp(verseColor, Color.White, 0.3f), verseColor))
    val barWidth = when {
        data.size <= 14 -> 18.dp
        data.size <= 26 -> 9.dp
        else -> 5.dp
    }

    // How many labels to show on x-axis to avoid crowding
    val labelStep = ((data.size / 10) + 1).coerceAtLeast(1)

    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Y-axis labels
            Column(
                modifier = Modifier.width(30.dp).fillMaxHeight().padding(bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 4 downTo 0) {
                    Text(
                        "${(maxTotal * i / 4)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = labelColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Plot area: grid lines behind grouped bars
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(bottom = 4.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (i in 0..4) {
                        val y = size.height * (1f - i / 4f)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8f)
                    }
                }
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    data.forEach { pt ->
                        val songFrac = pt.songCount.toFloat() / maxTotal
                        val verseFrac = pt.verseCount.toFloat() / maxTotal
                        TooltipArea(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            tooltip = {
                                Surface(
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    tonalElevation = 4.dp
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                        Text(
                                            pt.label,
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "$songsLabel: ${pt.songCount}",
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "$versesLabel: ${pt.verseCount}",
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            },
                            tooltipPlacement = TooltipPlacement.ComponentRect(
                                anchor = Alignment.TopCenter,
                                offset = DpOffset(0.dp, (-6).dp)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
                            ) {
                                if (pt.songCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .width(barWidth)
                                            .fillMaxHeight(songFrac.coerceIn(0.004f, 1f))
                                            .clip(barCorner)
                                            .background(songBrush)
                                    )
                                }
                                if (pt.verseCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .width(barWidth)
                                            .fillMaxHeight(verseFrac.coerceIn(0.004f, 1f))
                                            .clip(barCorner)
                                            .background(verseBrush)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // X-axis labels (aligned under the plot area, past the 30dp y-axis gutter)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            data.forEachIndexed { idx, pt ->
                Box(modifier = Modifier.weight(1f)) {
                    if (idx % labelStep == 0 || idx == data.size - 1) {
                        Text(
                            pt.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = labelColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

// ── Date picker ───────────────────────────────────────────────────────────────

@Composable
private fun DatePicker(
    year: Int,
    month: Int,
    day: Int,
    yearRange: IntRange,
    onChanged: (Int, Int, Int) -> Unit
) {
    val daysInMonth = remember(year, month) { LocalDate.of(year, month, 1).lengthOfMonth() }
    val safeDay = day.coerceAtMost(daysInMonth)
    val years = remember(yearRange) { yearRange.map { it.toString() } }
    val days = remember(daysInMonth) { (1..daysInMonth).map { it.toString() } }
    val months = listOf(
        stringResource(Res.string.ccli_month_january),
        stringResource(Res.string.ccli_month_february),
        stringResource(Res.string.ccli_month_march),
        stringResource(Res.string.ccli_month_april),
        stringResource(Res.string.ccli_month_may),
        stringResource(Res.string.ccli_month_june),
        stringResource(Res.string.ccli_month_july),
        stringResource(Res.string.ccli_month_august),
        stringResource(Res.string.ccli_month_september),
        stringResource(Res.string.ccli_month_october),
        stringResource(Res.string.ccli_month_november),
        stringResource(Res.string.ccli_month_december)
    )
    val monthsShort = listOf(
        stringResource(Res.string.ccli_month_jan),
        stringResource(Res.string.ccli_month_feb),
        stringResource(Res.string.ccli_month_mar),
        stringResource(Res.string.ccli_month_apr),
        stringResource(Res.string.ccli_month_may_short),
        stringResource(Res.string.ccli_month_jun),
        stringResource(Res.string.ccli_month_jul),
        stringResource(Res.string.ccli_month_aug),
        stringResource(Res.string.ccli_month_sep),
        stringResource(Res.string.ccli_month_oct),
        stringResource(Res.string.ccli_month_nov),
        stringResource(Res.string.ccli_month_dec)
    )

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DropdownPicker(
            buttonLabel = year.toString(),
            options = years,
            modifier = Modifier.width(78.dp),
            onSelected = { idx -> onChanged(yearRange.first + idx, month, safeDay) }
        )
        DropdownPicker(
            buttonLabel = monthsShort[month - 1],
            options = months,
            modifier = Modifier.width(82.dp),
            onSelected = { idx -> onChanged(year, idx + 1, safeDay) }
        )
        DropdownPicker(
            buttonLabel = safeDay.toString(),
            options = days,
            modifier = Modifier.width(64.dp),
            onSelected = { idx -> onChanged(year, month, idx + 1) }
        )
    }
}

@Composable
private fun DropdownPicker(
    buttonLabel: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            shape = RoundedCornerShape(6.dp),
            onClick = { expanded = true },
            modifier = modifier.height(36.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 4.dp, top = 0.dp, bottom = 0.dp)
        ) {
            Text(
                buttonLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { idx, opt ->
                DropdownMenuItem(
                    text = { Text(opt, style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelected(idx); expanded = false },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────

@Composable
private fun PresetButton(label: String, active: Boolean, onClick: () -> Unit) {
    val contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
    if (active) {
        Button(
            shape = RoundedCornerShape(50),
            onClick = onClick,
            contentPadding = contentPadding,
            modifier = Modifier.height(32.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(
            shape = RoundedCornerShape(50),
            onClick = onClick,
            contentPadding = contentPadding,
            modifier = Modifier.height(32.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
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
                .clip(RoundedCornerShape(5.dp))
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
    Text(text, style = style, color = textColor, modifier = mod, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align)
}

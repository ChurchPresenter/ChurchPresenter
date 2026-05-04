package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val VERSE_BAR_COLOR = Color(0xFF43A047)

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
                            PresetButton(stringResource(Res.string.ccli_preset_30d)) { applyPreset(30) }
                            PresetButton(stringResource(Res.string.ccli_preset_90d)) { applyPreset(90) }
                            PresetButton(stringResource(Res.string.ccli_preset_this_year)) {
                                fromYear = today.year; fromMonth = 1; fromDay = 1
                                toYear = today.year; toMonth = today.monthValue; toDay = today.dayOfMonth
                            }
                            PresetButton(stringResource(Res.string.ccli_preset_last_year)) {
                                fromYear = today.year - 1; fromMonth = 1; fromDay = 1
                                toYear = today.year - 1; toMonth = 12; toDay = 31
                            }
                            PresetButton(stringResource(Res.string.ccli_preset_all_time)) { applyPreset(null) }
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
                                onChanged = { y, m, d -> fromYear = y; fromMonth = m; fromDay = d }
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(Res.string.ccli_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DatePicker(
                                year = toYear, month = toMonth, day = toDay,
                                yearRange = yearRange,
                                onChanged = { y, m, d -> toYear = y; toMonth = m; toDay = d }
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
                            onClick = {
                                coroutineScope.launch {
                                    val f = fromMs(); val t = toMs()
                                    val file = withContext(Dispatchers.IO) {
                                        val chooser = JFileChooser().apply {
                                            dialogTitle = csvChooserTitle
                                            fileFilter = FileNameExtensionFilter(csvFilterDesc, "csv")
                                            selectedFile = File("ccli_report.csv")
                                        }
                                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                            var fl = chooser.selectedFile
                                            if (!fl.name.endsWith(".csv", ignoreCase = true)) fl = File("${fl.absolutePath}.csv")
                                            fl
                                        } else null
                                    }
                                    if (file != null) {
                                        val ok = withContext(Dispatchers.IO) { statisticsManager.exportCcliCsv(file, f, t) }
                                        statusIsSuccess = ok; statusMessage = if (ok) successMsg else errorMsg
                                    }
                                }
                            }
                        ) { Text(stringResource(Res.string.ccli_export_csv)) }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val f = fromMs(); val t = toMs()
                                    val file = withContext(Dispatchers.IO) {
                                        val chooser = JFileChooser().apply {
                                            dialogTitle = xlsChooserTitle
                                            fileFilter = FileNameExtensionFilter(xlsFilterDesc, "xls")
                                            selectedFile = File("ccli_report.xls")
                                        }
                                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                            var fl = chooser.selectedFile
                                            if (!fl.name.endsWith(".xls", ignoreCase = true)) fl = File("${fl.absolutePath}.xls")
                                            fl
                                        } else null
                                    }
                                    if (file != null) {
                                        val ok = withContext(Dispatchers.IO) { statisticsManager.exportFilteredXls(file, f, t) }
                                        statusIsSuccess = ok; statusMessage = if (ok) successMsg else errorMsg
                                    }
                                }
                            }
                        ) { Text(stringResource(Res.string.ccli_export_xls)) }

                        Spacer(Modifier.weight(1f))

                        Button(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
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

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(Res.string.ccli_songs_summary, songs.size, totalPlays),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Left: bar chart (top 12)
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
            ) {
                Text(
                    stringResource(Res.string.ccli_songs_chart),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HorizontalBarChart(
                    data = songs.take(12).map { it.title to it.count },
                    barColor = primary,
                    modifier = Modifier.fillMaxSize()
                )
            }

            HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

            // Right: full table
            SongTable(songs = songs, modifier = Modifier.weight(1f).fillMaxHeight())
        }
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

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(Res.string.ccli_bible_summary, verses.size, totalPlays),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
            ) {
                Text(
                    stringResource(Res.string.ccli_bible_books_chart),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HorizontalBarChart(
                    data = byBook.take(12),
                    barColor = secondary,
                    modifier = Modifier.fillMaxSize()
                )
            }

            HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

            VerseTable(verses = verses, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// ── Activity tab ──────────────────────────────────────────────────────────────

@Composable
private fun ActivityContent(activity: List<ActivityPoint>) {
    val primary = MaterialTheme.colorScheme.primary
    val verseColor = VERSE_BAR_COLOR

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(Res.string.ccli_activity_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (activity.isEmpty() || activity.all { it.songCount + it.verseCount == 0 }) {
            EmptyState(stringResource(Res.string.ccli_no_data))
            return
        }

        // Summary stats
        val totalSongs = activity.sumOf { it.songCount }
        val totalVerses = activity.sumOf { it.verseCount }
        val busiest = activity.maxByOrNull { it.songCount + it.verseCount }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            StatChip(stringResource(Res.string.ccli_stat_songs_presented), "$totalSongs", primary)
            StatChip(stringResource(Res.string.ccli_stat_bible_verses), "$totalVerses", verseColor)
            if (busiest != null) StatChip(stringResource(Res.string.ccli_stat_busiest), "${busiest.label} (${busiest.songCount + busiest.verseCount})", MaterialTheme.colorScheme.secondary)
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
            TableHeader(stringResource(Res.string.ccli_col_used), 52.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_first), 90.dp.value)
            TableHeader(stringResource(Res.string.ccli_col_last), 90.dp.value)
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(songs) { index, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TableCell("${index + 1}", fixedWidth = 28.dp.value, align = TextAlign.End)
                    TableCell(song.title, weight = 2f)
                    TableCell(song.author.ifBlank { "—" }, weight = 1.5f, muted = song.author.isBlank())
                    TableCell(song.songbook.ifBlank { "—" }, weight = 1f, muted = song.songbook.isBlank())
                    TableCell("${song.count}", fixedWidth = 52.dp.value, align = TextAlign.End, bold = true, color = MaterialTheme.colorScheme.primary)
                    TableCell(dateFmt.format(Date(song.firstUsed)), fixedWidth = 90.dp.value)
                    TableCell(dateFmt.format(Date(song.lastUsed)), fixedWidth = 90.dp.value)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun VerseTable(verses: List<VerseSummary>, modifier: Modifier = Modifier) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(verses) { index, verse ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TableCell("${index + 1}", fixedWidth = 28.dp.value, align = TextAlign.End)
                    TableCell("${verse.bookName} ${verse.chapter}:${verse.verseNumber}", weight = 2f)
                    TableCell(verse.bibleName.ifBlank { "—" }, weight = 1f, muted = verse.bibleName.isBlank())
                    TableCell("${verse.count}", fixedWidth = 52.dp.value, align = TextAlign.End, bold = true, color = MaterialTheme.colorScheme.tertiary)
                    TableCell(dateFmt.format(Date(verse.firstUsed)), fixedWidth = 90.dp.value)
                    TableCell(dateFmt.format(Date(verse.lastUsed)), fixedWidth = 90.dp.value)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

// ── Charts ────────────────────────────────────────────────────────────────────

@Composable
private fun HorizontalBarChart(
    data: List<Pair<String, Int>>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val maxValue = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val bgColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        data.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(24.dp)) {
                Text(
                    label,
                    modifier = Modifier.width(120.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.weight(1f).height(18.dp)
                        .background(bgColor, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = value.toFloat() / maxValue)
                            .background(barColor, RoundedCornerShape(3.dp))
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "$value",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = barColor,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

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

    // How many labels to show on x-axis to avoid crowding
    val labelStep = ((data.size / 10) + 1).coerceAtLeast(1)

    Column(modifier = modifier) {
        // Chart area + y-axis
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(start = 32.dp, end = 8.dp, bottom = 4.dp)) {
                val chartH = size.height
                val chartW = size.width
                val barCount = data.size
                if (barCount == 0) return@Canvas
                val slotW = chartW / barCount
                val barW = (slotW * 0.75f).coerceAtLeast(4f)
                val barOffset = (slotW - barW) / 2f

                // Horizontal grid lines
                for (i in 0..4) {
                    val y = chartH * (1f - i / 4f)
                    drawLine(gridColor, Offset(0f, y), Offset(chartW, y), strokeWidth = 0.8f)
                }

                // Bars (stacked: verses bottom, songs top)
                data.forEachIndexed { idx, pt ->
                    val x = idx * slotW + barOffset
                    val total = pt.songCount + pt.verseCount
                    if (total == 0) return@forEachIndexed
                    val totalH = (total.toFloat() / maxTotal) * chartH
                    val songH = (pt.songCount.toFloat() / maxTotal) * chartH
                    val verseH = totalH - songH

                    // Verse bar (bottom)
                    if (verseH > 0f) {
                        drawRoundRect(
                            color = verseColor,
                            topLeft = Offset(x, chartH - verseH),
                            size = Size(barW, verseH),
                            cornerRadius = CornerRadius(2f)
                        )
                    }
                    // Song bar (top of verse bar)
                    if (songH > 0f) {
                        drawRoundRect(
                            color = songColor,
                            topLeft = Offset(x, chartH - totalH),
                            size = Size(barW, songH),
                            cornerRadius = CornerRadius(2f)
                        )
                    }
                }
            }

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
        }

        // X-axis labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
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
private fun PresetButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableHeader(
    text: String,
    fixedWidth: Float? = null,
    weight: Float = 1f
) {
    val mod = if (fixedWidth != null) Modifier.width(fixedWidth.dp) else Modifier.weight(weight)
    Text(
        text, style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = mod, maxLines = 1
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(
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

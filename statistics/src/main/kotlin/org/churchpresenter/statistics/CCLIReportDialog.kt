package org.churchpresenter.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.AlertDialog
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.obs_mode_bible
import org.churchpresenter.strings.generated.resources.ccli_export_csv
import org.churchpresenter.strings.generated.resources.ccli_export_xls
import org.churchpresenter.strings.generated.resources.ccli_exported_error
import org.churchpresenter.strings.generated.resources.ccli_exported_success
import org.churchpresenter.strings.generated.resources.ccli_file_chooser_csv
import org.churchpresenter.strings.generated.resources.ccli_file_chooser_xls
import org.churchpresenter.strings.generated.resources.ccli_file_filter_csv
import org.churchpresenter.strings.generated.resources.ccli_file_filter_xls
import org.churchpresenter.strings.generated.resources.obs_mode_songs
import org.churchpresenter.strings.generated.resources.ccli_no_events
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.clear_statistics
import org.churchpresenter.strings.generated.resources.confirm_delete
import org.churchpresenter.strings.generated.resources.delete_saved_string
import org.churchpresenter.strings.generated.resources.ccli_report_title
import org.churchpresenter.strings.generated.resources.stats_clear_all_confirm
import org.churchpresenter.strings.generated.resources.stats_clear_item_confirm
import org.churchpresenter.strings.generated.resources.stats_period_all_time
import org.churchpresenter.strings.generated.resources.stats_period_last_12_months
import org.churchpresenter.strings.generated.resources.stats_period_last_3_months
import org.churchpresenter.strings.generated.resources.stats_period_last_6_months
import org.churchpresenter.strings.generated.resources.stats_period_custom
import org.churchpresenter.strings.generated.resources.ccli_tab_activity
import org.churchpresenter.strings.generated.resources.close
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import java.time.ZoneId

/** The tab indices, so the filter row can tell which library it is narrowing. */
private const val SONGS_TAB = 0
private const val BIBLE_TAB = 1
private const val ACTIVITY_TAB = 2

@Composable
fun CCLIReportDialog(
    isVisible: Boolean,
    theme: ThemeMode,
    statisticsManager: StatisticsManager,
    onDismiss: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 940.dp, 700.dp),
            width = 940.dp, height = 700.dp
        ),
        title = stringResource(Res.string.ccli_report_title),
        resizable = true
    ) {
        CCLIReportContent(
            theme = theme,
            statisticsManager = statisticsManager,
            onDismiss = onDismiss,
            today = today,
        )
    }
}

/**
 * Everything the report window contains: the quick-range presets and date pickers, the tab row
 * over the three report bodies, the export buttons and the status line they write to.
 *
 * Held apart from [CCLIReportDialog] because that function's only other statement is the
 * `DialogWindow` it opens, which cannot be composed on a headless machine. Keeping the window down
 * to that one call leaves the report's own behaviour — which range each preset selects, what the
 * tab counts say, and what is shown when there is no event log at all — reachable from a test.
 * Public for the app's preview screenshot suite, which draws it inside the app's own frame.
 */
@Composable
fun CCLIReportContent(
    theme: ThemeMode,
    statisticsManager: StatisticsManager,
    onDismiss: () -> Unit,
    /**
     * The day the report treats as "now", for the default range and the quick spans.
     *
     * A parameter so a screenshot can pin it. Left on the real clock the committed image carries
     * whatever date it was recorded on and goes stale by the next day — which is exactly what
     * happened to `previewApp/ccli_report_*`.
     */
    today: LocalDate = LocalDate.now(),
) {
    val coroutineScope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

    val yearRange = remember { reportYearRange(statisticsManager.getEarliestEventTime(), zone, today) }

    val range = remember { ReportRange(today, zone) }
    val data = remember { ReportData(statisticsManager) }
    var selectedTab by remember { mutableStateOf(SONGS_TAB) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsSuccess by remember { mutableStateOf(true) }
    // Which songbook / Bible the two report tabs are narrowed to; null is all of them.
    var songbookFilter by remember { mutableStateOf<String?>(null) }
    var bibleFilter by remember { mutableStateOf<String?>(null) }
    var pendingClear by remember { mutableStateOf<PendingClear?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    fun reload() = coroutineScope.launch { data.load(range.fromMs(), range.toMs()) }

    LaunchedEffect(range.fromYear, range.fromMonth, range.fromDay, range.toYear, range.toMonth, range.toDay) {
        reload()
    }

    fun clearThenReload(clear: () -> Unit) = coroutineScope.launch {
        withContext(Dispatchers.IO) { clear() }
        reload()
    }

    val shownSongs = remember(data.songs, songbookFilter) {
        songbookFilter?.let { book -> data.songs.filter { it.songbook == book } } ?: data.songs
    }
    val shownVerses = remember(data.verses, bibleFilter) {
        bibleFilter?.let { bible -> data.verses.filter { it.bibleName == bible } } ?: data.verses
    }

    val successMsg = stringResource(Res.string.ccli_exported_success)
    val errorMsg = stringResource(Res.string.ccli_exported_error)
    val onExported: (Boolean) -> Unit = { ok ->
        statusIsSuccess = ok; statusMessage = if (ok) successMsg else errorMsg
    }

    AppWindowRoot(theme = theme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Date range header ────────────────────────────────────
                ReportRangeHeader(
                    range = range,
                    yearRange = yearRange,
                    years = remember(data.earliestEvent) { availableYears(today, data.earliestEvent) },
                    onPeriod = { range.apply(it, today, data.earliestEvent) },
                ) {
                    // The tab being looked at decides which library the filter narrows; the
                    // activity chart spans everything, so it offers none.
                    if (data.hasLog && selectedTab != ACTIVITY_TAB) {
                        ReportLibraryFilter(
                            songbooks = selectedTab == SONGS_TAB,
                            data = data,
                            selected = if (selectedTab == SONGS_TAB) songbookFilter else bibleFilter,
                        ) { chosen ->
                            if (selectedTab == SONGS_TAB) songbookFilter = chosen else bibleFilter = chosen
                        }
                    }
                }

                HorizontalDivider()

                if (!data.hasLog) {
                    NoEventsMessage()
                } else {
                    ReportTabs(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        songs = shownSongs,
                        verses = shownVerses,
                        activity = data.activity,
                        onClearSong = { pendingClear = songClear(statisticsManager, it) },
                        onClearVerse = { pendingClear = verseClear(statisticsManager, it) },
                    )
                }

                ReportFooter(statusMessage, statusIsSuccess, onClearAll = { confirmClearAll = true }, onDismiss) {
                    ReportExports(statisticsManager, range, onExported)
                }
            }

            val pending = pendingClear
            if (pending != null) {
                ConfirmClearDialog(
                    message = stringResource(
                        Res.string.stats_clear_item_confirm, pending.label, periodLabel(range.activePeriod),
                    ),
                    onConfirm = {
                        pendingClear = null
                        clearThenReload { pending.clear(range.fromMs(), range.toMs()) }
                    },
                    onDismiss = { pendingClear = null }
                )
            }

            if (confirmClearAll) {
                ConfirmClearDialog(
                    message = stringResource(Res.string.stats_clear_all_confirm),
                    onConfirm = {
                        confirmClearAll = false
                        statusMessage = null
                        clearThenReload { statisticsManager.clearStatistics() }
                    },
                    onDismiss = { confirmClearAll = false }
                )
            }
        }
    }
}


/** The three report tabs, each counted, over the body of the one chosen. */
@Composable
private fun ColumnScope.ReportTabs(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    songs: List<SongSummary>,
    verses: List<VerseSummary>,
    activity: List<ActivityPoint>,
    onClearSong: (SongSummary) -> Unit,
    onClearVerse: (VerseSummary) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = selectedTab) {
        Tab(selected = selectedTab == SONGS_TAB, onClick = { onSelectTab(SONGS_TAB) },
            text = { Text(stringResource(Res.string.obs_mode_songs) + " (${songs.size})") })
        Tab(selected = selectedTab == BIBLE_TAB, onClick = { onSelectTab(BIBLE_TAB) },
            text = { Text(stringResource(Res.string.obs_mode_bible) + " (${verses.size})") })
        Tab(selected = selectedTab == ACTIVITY_TAB, onClick = { onSelectTab(ACTIVITY_TAB) },
            text = { Text(stringResource(Res.string.ccli_tab_activity)) })
    }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (selectedTab) {
            SONGS_TAB -> SongsReportContent(songs, onClearSong)
            BIBLE_TAB -> BibleReportContent(verses, onClearVerse)
            ACTIVITY_TAB -> ActivityContent(activity)
        }
    }
}


/** The rolling quick periods' names, in [ROLLING_MONTHS] order. */
@Composable
internal fun rollingPeriodLabels(): List<String> = listOf(
    stringResource(Res.string.stats_period_last_3_months),
    stringResource(Res.string.stats_period_last_6_months),
    stringResource(Res.string.stats_period_last_12_months),
)

/** Names [period] for the confirmation prompts; hand-picked dates have no name. */
@Composable
private fun periodLabel(period: StatisticsPeriod?): String = when (period) {
    is StatisticsPeriod.AllTime -> stringResource(Res.string.stats_period_all_time)
    is StatisticsPeriod.LastMonths -> rollingPeriodLabels()[ROLLING_MONTHS.indexOf(period.months).coerceAtLeast(0)]
    is StatisticsPeriod.Year -> period.year.toString()
    null -> stringResource(Res.string.stats_period_custom)
}

/** What the report says when there is no event log to report on. */
@Composable
private fun ColumnScope.NoEventsMessage() {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.ccli_no_events),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

/** The last export's outcome, then the [exports], clear-all and close along the bottom. */
@Composable
private fun ReportFooter(
    statusMessage: String?,
    statusIsSuccess: Boolean,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    exports: @Composable RowScope.() -> Unit,
) {
    // ── Status message ───────────────────────────────────────
    if (statusMessage != null) {
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (statusIsSuccess) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
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
        exports()

        RaisedButton(
            shape = AppShape(6.dp),
            onClick = onClearAll,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) { Text(stringResource(Res.string.clear_statistics)) }

        Spacer(Modifier.weight(1f))

        RaisedButton(shape = AppShape(6.dp), onClick = onDismiss) {
            Text(stringResource(Res.string.close))
        }
    }
}

// ── Songs tab ─────────────────────────────────────────────────────────────────

/** The house-style destructive confirmation: error-tinted confirm, plain cancel. */
@Composable
private fun ConfirmClearDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.confirm_delete)) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            GhostButton(shape = AppShape(6.dp), onClick = onConfirm) {
                Text(stringResource(Res.string.delete_saved_string), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            GhostButton(shape = AppShape(6.dp), onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

// ── Charts ────────────────────────────────────────────────────────────────────

/** The CSV and the spreadsheet export, both over [range]. */
@Composable
private fun RowScope.ReportExports(
    statisticsManager: StatisticsManager,
    range: ReportRange,
    onExported: (Boolean) -> Unit,
) {
    ExportButton(
        label = stringResource(Res.string.ccli_export_csv),
        file = ExportFile(
            "ccli_report.csv", "csv",
            stringResource(Res.string.ccli_file_filter_csv), stringResource(Res.string.ccli_file_chooser_csv),
        ),
        onExported = onExported,
    ) { file -> statisticsManager.exportCcliCsv(file, range.fromMs(), range.toMs()) }
    ExportButton(
        label = stringResource(Res.string.ccli_export_xls),
        file = ExportFile(
            "ccli_report.xls", "xls",
            stringResource(Res.string.ccli_file_filter_xls), stringResource(Res.string.ccli_file_chooser_xls),
        ),
        onExported = onExported,
    ) { file -> statisticsManager.exportFilteredXls(file, range.fromMs(), range.toMs()) }
}

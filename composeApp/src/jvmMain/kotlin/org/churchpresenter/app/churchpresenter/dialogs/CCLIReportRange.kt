package org.churchpresenter.app.churchpresenter.dialogs

import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.ccli_from
import org.churchpresenter.strings.generated.resources.em_dash
import org.churchpresenter.strings.generated.resources.stats_filter_all_bibles
import org.churchpresenter.strings.generated.resources.stats_filter_all_songbooks
import org.churchpresenter.strings.generated.resources.stats_period_all_time
import org.churchpresenter.strings.generated.resources.stats_period_year
import org.churchpresenter.strings.generated.resources.ccli_to
import org.churchpresenter.strings.generated.resources.ccli_month_january
import org.churchpresenter.strings.generated.resources.ccli_month_february
import org.churchpresenter.strings.generated.resources.ccli_month_march
import org.churchpresenter.strings.generated.resources.ccli_month_april
import org.churchpresenter.strings.generated.resources.ccli_month_may
import org.churchpresenter.strings.generated.resources.ccli_month_june
import org.churchpresenter.strings.generated.resources.ccli_month_july
import org.churchpresenter.strings.generated.resources.ccli_month_august
import org.churchpresenter.strings.generated.resources.ccli_month_september
import org.churchpresenter.strings.generated.resources.ccli_month_october
import org.churchpresenter.strings.generated.resources.ccli_month_november
import org.churchpresenter.strings.generated.resources.ccli_month_december
import org.churchpresenter.strings.generated.resources.ccli_month_jan
import org.churchpresenter.strings.generated.resources.ccli_month_feb
import org.churchpresenter.strings.generated.resources.ccli_month_mar
import org.churchpresenter.strings.generated.resources.ccli_month_apr
import org.churchpresenter.strings.generated.resources.ccli_month_may_short
import org.churchpresenter.strings.generated.resources.ccli_month_jun
import org.churchpresenter.strings.generated.resources.ccli_month_jul
import org.churchpresenter.strings.generated.resources.ccli_month_aug
import org.churchpresenter.strings.generated.resources.ccli_month_sep
import org.churchpresenter.strings.generated.resources.ccli_month_oct
import org.churchpresenter.strings.generated.resources.ccli_month_nov
import org.churchpresenter.strings.generated.resources.ccli_month_dec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.ROLLING_MONTHS
import org.churchpresenter.app.churchpresenter.data.StatisticsPeriod
import org.churchpresenter.app.churchpresenter.data.resolveDates
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import java.time.ZoneId
import javax.swing.filechooser.FileNameExtensionFilter
import org.churchpresenter.sharedui.filechooser.FileChooser

/** The report's date range as the From and To pickers show it, and the quick period that set it. */
@Stable
internal class ReportRange(today: LocalDate, private val zone: ZoneId) {
    var fromYear by mutableStateOf(today.year)
    var fromMonth by mutableStateOf(1)
    var fromDay by mutableStateOf(1)
    var toYear by mutableStateOf(today.year)
    var toMonth by mutableStateOf(today.monthValue)
    var toDay by mutableStateOf(today.lengthOfMonth())

    /** The highlighted quick period, or null once a date is edited by hand. */
    var activePeriod by mutableStateOf<StatisticsPeriod?>(null)

    fun fromMs(): Long =
        LocalDate.of(fromYear, fromMonth, fromDay.coerceAtMost(LocalDate.of(fromYear, fromMonth, 1).lengthOfMonth()))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun toMs(): Long =
        LocalDate.of(toYear, toMonth, toDay.coerceAtMost(LocalDate.of(toYear, toMonth, 1).lengthOfMonth()))
            .atTime(LAST_HOUR, LAST_MINUTE, LAST_SECOND).atZone(zone).toInstant().toEpochMilli()

    /** Points the From/To fields at a quick period; both sides read [resolveDates] so they agree. */
    fun apply(period: StatisticsPeriod, today: LocalDate, earliestEvent: Long?) {
        val (from, to) = period.resolveDates(today, earliestEvent)
        activePeriod = period
        fromYear = from.year; fromMonth = from.monthValue; fromDay = from.dayOfMonth
        toYear = to.year; toMonth = to.monthValue; toDay = to.dayOfMonth
    }

    fun setFrom(year: Int, month: Int, day: Int) {
        activePeriod = null; fromYear = year; fromMonth = month; fromDay = day
    }

    fun setTo(year: Int, month: Int, day: Int) {
        activePeriod = null; toYear = year; toMonth = month; toDay = day
    }
}

/** Where an export is saved: the name offered, its extension, and the chooser's filter and title. */
internal data class ExportFile(
    val suggestedName: String,
    val extension: String,
    val filterDesc: String,
    val title: String,
)

/** One export: asks where to save, writes there off the main thread, and reports whether it worked. */
@Composable
internal fun ExportButton(
    label: String,
    file: ExportFile,
    onExported: (Boolean) -> Unit,
    export: (File) -> Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    KeyButton(
        shape = AppShape(6.dp),
        onClick = {
            coroutineScope.launch {
                val path = FileChooser.platformInstance.save(
                    location = null,
                    suggestedName = file.suggestedName,
                    filters = listOf(FileNameExtensionFilter(file.filterDesc, file.extension)),
                    title = file.title
                )
                if (path != null) onExported(withContext(Dispatchers.IO) { export(path.toFile()) })
            }
        }
    ) { Text(label) }
}

/** The quick periods, the year picker, and the From and To dates, with [filter] at the end of the date row. */
@Composable
internal fun ReportRangeHeader(
    range: ReportRange,
    yearRange: IntRange,
    years: List<Int>,
    onPeriod: (StatisticsPeriod) -> Unit,
    filter: @Composable RowScope.() -> Unit,
) {
    val rollingLabels = rollingPeriodLabels()
    val allTimeLabel = stringResource(Res.string.stats_period_all_time)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Preset buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ROLLING_MONTHS.forEachIndexed { index, months ->
                val period = StatisticsPeriod.LastMonths(months)
                PresetButton(rollingLabels[index], active = range.activePeriod == period) { onPeriod(period) }
            }
            PresetButton(allTimeLabel, active = range.activePeriod == StatisticsPeriod.AllTime) {
                onPeriod(StatisticsPeriod.AllTime)
            }

            Spacer(Modifier.width(6.dp))
            val selectedYear = (range.activePeriod as? StatisticsPeriod.Year)?.year
            DropdownPicker(
                buttonLabel = selectedYear?.toString() ?: stringResource(Res.string.stats_period_year),
                options = years.map { it.toString() },
                modifier = Modifier.width(96.dp).testTag(REPORT_YEAR_TAG),
                onSelected = { idx -> onPeriod(StatisticsPeriod.Year(years[idx])) }
            )
        }
        // Date pickers
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(Res.string.ccli_from),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DatePicker(
                year = range.fromYear, month = range.fromMonth, day = range.fromDay,
                yearRange = yearRange,
                onChanged = range::setFrom
            )
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(Res.string.ccli_to),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DatePicker(
                year = range.toYear, month = range.toMonth, day = range.toDay,
                yearRange = yearRange,
                onChanged = range::setTo
            )
            filter()
        }
    }
}

/** Narrows the songs tab to one songbook, or the Bible tab to one Bible ([songbooks] says which). */
@Composable
internal fun RowScope.ReportLibraryFilter(
    songbooks: Boolean,
    data: ReportData,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    Spacer(Modifier.weight(1f))
    val allLabel = if (songbooks) {
        stringResource(Res.string.stats_filter_all_songbooks)
    } else {
        stringResource(Res.string.stats_filter_all_bibles)
    }
    val groups = remember(data.songs, data.verses, songbooks) {
        if (songbooks) data.songs.map { it.songbook }.distinct().sorted()
        else data.verses.map { it.bibleName }.distinct().sorted()
    }
    val dash = stringResource(Res.string.em_dash)
    LibraryPicker(
        label = selected?.ifBlank { dash } ?: allLabel,
        allLabel = allLabel,
        groups = groups,
        blankLabel = dash,
        testTag = if (songbooks) REPORT_SONGBOOK_TAG else REPORT_BIBLE_TAG,
        onSelected = onSelected,
    )
}

/**
 * Narrows a report tab to one songbook or one Bible. The first option puts them all back.
 *
 * A library with no name shows as an em dash, the way the songs list renders it — an empty button
 * would otherwise look like a broken option.
 */
@Composable
private fun LibraryPicker(
    label: String,
    allLabel: String,
    groups: List<String>,
    blankLabel: String,
    testTag: String,
    onSelected: (String?) -> Unit,
) {
    DropdownPicker(
        buttonLabel = label,
        options = listOf(allLabel) + groups.map { it.ifBlank { blankLabel } },
        modifier = Modifier.width(LIBRARY_PICKER_WIDTH).testTag(testTag),
        onSelected = { index -> onSelected(if (index == 0) null else groups[index - 1]) }
    )
}

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
        KeyButton(
            shape = AppShape(6.dp),
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
        RaisedButton(
            shape = AppShape(PILL_CORNER_PERCENT),
            onClick = onClick,
            contentPadding = contentPadding,
            modifier = Modifier.height(32.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        KeyButton(
            shape = AppShape(PILL_CORNER_PERCENT),
            onClick = onClick,
            contentPadding = contentPadding,
            modifier = Modifier.height(32.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val LAST_HOUR = 23

private const val LAST_MINUTE = 59

private const val LAST_SECOND = 59

private val LIBRARY_PICKER_WIDTH = 190.dp

/** Test tags: the pickers, and the per-row clear, which appends its row's label. */
internal const val REPORT_YEAR_TAG = "reportYear"

internal const val REPORT_SONGBOOK_TAG = "reportSongbook"

internal const val REPORT_BIBLE_TAG = "reportBible"

private const val PILL_CORNER_PERCENT = 50

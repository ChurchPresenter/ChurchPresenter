package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_month_next
import org.churchpresenter.calendar.generated.resources.calendar_month_previous
import org.churchpresenter.calendar.generated.resources.calendar_service_types
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.monthGrid
import org.churchpresenter.calendar.model.monthHeading
import org.churchpresenter.calendar.model.weekdayName
import org.churchpresenter.calendar.model.weekdayOrder
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import java.time.YearMonth
import org.churchpresenter.theme.elevationPalette

private const val WEEK_LENGTH = 7
private const val MAX_DOTS = 3
private const val OUT_OF_MONTH_ALPHA = 0.38f
private const val CELL_TINT_ALPHA = 0.55f
private val CELL_GAP = 2.dp
private val PANE_PADDING = 12.dp

/**
 * The month grid, and the service-type legend under it.
 *
 * Hand-drawn rather than Material 3's `DatePicker`, and the reason is worth stating so it is not
 * "improved" into one later: `DatePicker` is a single-date *chooser*. This grid is the planner's
 * navigation surface — each day carries a dot per planned service in that service's own color, a
 * day can hold several, and the whole thing has to stay readable at a fifth of the window's width.
 * None of that is something a chooser exposes.
 *
 * Laid out to the design: `[<] month [>]` over a seven-column weekday row, a square-celled grid,
 * and the legend filling whatever height is left. The date arithmetic is all `java.time` and lives
 * in `CalendarTime.kt`, where it is testable without composing anything.
 */
@Composable
fun MonthPane(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    servicesOn: (LocalDate) -> List<PlannedService>,
    onSelect: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.background(scheme.surface)) {
        MonthHeader(month = month, onPrevious = onPreviousMonth, onNext = onNextMonth)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = PANE_PADDING, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            weekdayOrder().forEach { day ->
                Text(
                    text = weekdayName(day).uppercase(),
                    style = weekdayStyle(),
                    color = scheme.onSurfaceVariant.copy(alpha = OUT_OF_MONTH_ALPHA + 0.3f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(
            Modifier.padding(horizontal = PANE_PADDING),
            verticalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            monthGrid(month).chunked(WEEK_LENGTH).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    week.forEach { date ->
                        DayCell(
                            date = date,
                            inMonth = YearMonth.from(date) == month,
                            isSelected = date == selected,
                            isToday = date == today,
                            services = servicesOn(date),
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        KindLegend(
            month = month,
            servicesOn = servicesOn,
            modifier = Modifier
                .weight(1f)
                .padding(start = PANE_PADDING, end = PANE_PADDING, top = 10.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PANE_PADDING, end = PANE_PADDING, top = 11.dp, bottom = 9.dp),
    ) {
        NavButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            description = stringResource(Res.string.calendar_month_previous),
            onClick = onPrevious,
        )
        Text(
            text = monthHeading(month),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        NavButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            description = stringResource(Res.string.calendar_month_next),
            onClick = onNext,
        )
    }
}

/** The design's 24dp square, bordered, rounded month-navigation button. */
@Composable
private fun NavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val key = elevationPalette().key
    Hint(description) {
        Box(
            Modifier
                .size(CalendarMetrics.monthNavButton)
                .raisedKey(RoundedCornerShape(7.dp), key, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = key.ink,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    services: List<PlannedService>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val selectedFill = elevationPalette().selected
    val background = when {
        services.isNotEmpty() -> scheme.surfaceVariant.copy(alpha = CELL_TINT_ALPHA)
        else -> Color.Transparent
    }
    val border = scheme.outlineVariant.copy(alpha = CELL_TINT_ALPHA)
    val textColor = when {
        isSelected -> selectedFill.ink
        !inMonth -> scheme.onSurfaceVariant.copy(alpha = OUT_OF_MONTH_ALPHA)
        else -> scheme.onSurface
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                // The chosen day is a raised key, like a chosen segment everywhere else.
                if (isSelected) {
                    Modifier.raisedKey(CalendarMetrics.dayCellRadius, selectedFill, onClick = onClick)
                } else {
                    Modifier
                        .clip(CalendarMetrics.dayCellRadius)
                        .background(background)
                        .border(1.dp, border, CalendarMetrics.dayCellRadius)
                        .clickable(onClick = onClick)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
            )
            // A fixed-height strip whether or not there are dots, so a day gaining a service does
            // not shift its number within the cell.
            Row(
                Modifier.height(CalendarMetrics.dayDot),
                horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                services.take(MAX_DOTS).forEach { service ->
                    Box(
                        Modifier
                            .size(CalendarMetrics.dayDot)
                            .clip(CircleShape)
                            .background(kindColor(ServiceKind.from(service.kind)))
                    )
                }
            }
        }
        if (isToday) {
            // An underline bar rather than a ring: today is very often also the selected day, and a
            // ring around a filled cell is invisible.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = CELL_GAP)
                    .size(width = CalendarMetrics.todayBarWidth, height = CalendarMetrics.todayBarHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isSelected) scheme.onPrimaryContainer else scheme.primary)
            )
        }
    }
}

@Composable
private fun KindLegend(
    month: YearMonth,
    servicesOn: (LocalDate) -> List<PlannedService>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val counts = monthGrid(month)
        .filter { YearMonth.from(it) == month }
        .flatMap(servicesOn)
        .groupingBy { ServiceKind.from(it.kind) }
        .eachCount()

    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(Res.string.calendar_service_types).uppercase(),
            style = overlineStyle(),
            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(6.dp))
        ServiceKind.entries.forEach { kind ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.5.dp),
            ) {
                Box(Modifier.size(CalendarMetrics.legendDot).clip(CircleShape).background(kindColor(kind)))
                Text(
                    text = kindLabel(kind),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = (counts[kind] ?: 0).toString(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

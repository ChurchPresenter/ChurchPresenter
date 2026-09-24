package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_add_service
import org.churchpresenter.calendar.generated.resources.calendar_day_no_services
import org.churchpresenter.calendar.generated.resources.calendar_day_services_one
import org.churchpresenter.calendar.generated.resources.calendar_day_services_other
import org.churchpresenter.calendar.generated.resources.calendar_edit_service
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.ServiceRepeat
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raisedHover

/** The selected day's heading — `Sunday, 20 September 2026`, in the machine's own locale. */
private val DAY_HEADING: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

/**
 * The day's header and its service chips.
 *
 * A day can hold several services — a morning and an evening, a Sunday and a wedding — so the chips
 * are a scrolling row rather than one header. Each is the design's split control: the body selects
 * the service, the 26dp tail edits it.
 */
@Composable
fun DayPane(
    date: LocalDate,
    services: List<PlannedService>,
    selectedServiceId: String?,
    onSelectService: (String) -> Unit,
    onAddService: () -> Unit,
    onEditService: (PlannedService) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CalendarMetrics.dayHeaderHeight)
                .background(scheme.surface)
                .padding(horizontal = 13.dp, vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = date.format(DAY_HEADING.withLocale(Locale.getDefault())),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dayServicesLabel(services.size),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AddServiceButton(onClick = onAddService)
        }

        if (services.isEmpty()) return@Column

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            services.forEach { service ->
                ServiceChip(
                    service = service,
                    isSelected = service.id == selectedServiceId,
                    onClick = { onSelectService(service.id) },
                    onEdit = { onEditService(service) },
                )
            }
        }
    }
}

@Composable
internal fun AddServiceButton(onClick: () -> Unit) {
    val accent = elevationPalette().accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(CalendarMetrics.addServiceButtonHeight)
            .raisedKey(CalendarMetrics.buttonRadius, accent, onClick = onClick)
            .padding(horizontal = 11.dp),
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = accent.ink,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = stringResource(Res.string.calendar_add_service),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            fontWeight = FontWeight.Bold,
            color = accent.ink,
        )
    }
}

@Composable
private fun dayServicesLabel(count: Int): String = when (count) {
    0 -> stringResource(Res.string.calendar_day_no_services)
    1 -> stringResource(Res.string.calendar_day_services_one, count)
    else -> stringResource(Res.string.calendar_day_services_other, count)
}

/**
 * One service, as the design's split chip: an accent bar in its kind's color, the name and its
 * meta, and an edit tail divided from the body by a single rule.
 *
 * **One bordered container, not two.** The obvious build — a bordered body beside a bordered tail —
 * draws three lines where the design has two (a perimeter and one divider), and leaves the body
 * sized to its text while the tail is pinned to the chip height, so the tail hangs below it. The
 * perimeter is drawn once on the outer row and both halves fill its height.
 */
@Composable
private fun ServiceChip(
    service: PlannedService,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val kind = ServiceKind.from(service.kind)
    val palette = elevationPalette()
    // A raised key, lit in the selected fill while it is the service being shown.
    val fill = if (isSelected) palette.selected else palette.key
    val ink = fill.ink
    val meta = fill.ink.copy(alpha = META_ALPHA)
    val border = fill.ink.copy(alpha = DIVIDER_ALPHA)
    val shape = RoundedCornerShape(CalendarMetrics.chipRadius)

    Row(
        modifier = Modifier
            .height(CalendarMetrics.serviceChipHeight)
            .raisedHover(shape, fill, palette, lift = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier
                .fillMaxHeight()
                .clickable(onClick = onClick)
                .padding(start = 11.dp, end = 8.dp),
        ) {
            Box(
                Modifier
                    .size(width = CalendarMetrics.accentBarWidth, height = CalendarMetrics.chipAccentHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(kindColor(kind))
            )
            Column {
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = clockText(service.startTime, LocalUse24HourClock.current) +
                            " \u00b7 " + itemCountLabel(service.contentItems().size),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                        color = meta,
                        maxLines = 1,
                    )
                    // A series member says so in its meta line, the way the design marks it, so
                    // one Sunday looks different from the fourteen it was planned with.
                    if (service.isInSeries()) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = null,
                            tint = meta,
                            modifier = Modifier.size(REPEAT_MARK),
                        )
                        Text(
                            text = repeatLabel(ServiceRepeat.from(service.repeat)),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                            color = meta,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(border))
        Hint(stringResource(Res.string.calendar_edit_service)) {
            Box(
                Modifier
                    .width(EDIT_TAIL_WIDTH)
                    .fillMaxHeight()
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(Res.string.calendar_edit_service),
                    tint = ink,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

private val EDIT_TAIL_WIDTH = 26.dp
private const val META_ALPHA = 0.8f
private const val DIVIDER_ALPHA = 0.18f
private val REPEAT_MARK = 9.dp

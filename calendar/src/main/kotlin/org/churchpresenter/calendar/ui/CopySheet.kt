package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_cancel
import org.churchpresenter.calendar.generated.resources.calendar_copy_clash
import org.churchpresenter.calendar.generated.resources.calendar_copy_create
import org.churchpresenter.calendar.generated.resources.calendar_copy_creates_one
import org.churchpresenter.calendar.generated.resources.calendar_copy_creates_other
import org.churchpresenter.calendar.generated.resources.calendar_copy_footer_empty
import org.churchpresenter.calendar.generated.resources.calendar_copy_footer_once
import org.churchpresenter.calendar.generated.resources.calendar_copy_footer_repeat
import org.churchpresenter.calendar.generated.resources.calendar_copy_how_many
import org.churchpresenter.calendar.generated.resources.calendar_copy_in_two_weeks
import org.churchpresenter.calendar.generated.resources.calendar_copy_include_cues
import org.churchpresenter.calendar.generated.resources.calendar_copy_include_cues_sub
import org.churchpresenter.calendar.generated.resources.calendar_copy_include_run
import org.churchpresenter.calendar.generated.resources.calendar_copy_include_run_sub
import org.churchpresenter.calendar.generated.resources.calendar_copy_next_month
import org.churchpresenter.calendar.generated.resources.calendar_copy_next_week
import org.churchpresenter.calendar.generated.resources.calendar_copy_paste
import org.churchpresenter.calendar.generated.resources.calendar_copy_paste_on
import org.churchpresenter.calendar.generated.resources.calendar_copy_repeat
import org.churchpresenter.calendar.generated.resources.calendar_copy_same_weekday
import org.churchpresenter.calendar.generated.resources.calendar_copy_sub
import org.churchpresenter.calendar.generated.resources.calendar_copy_title
import org.churchpresenter.calendar.generated.resources.calendar_copy_tomorrow
import org.churchpresenter.calendar.generated.resources.calendar_count_down
import org.churchpresenter.calendar.generated.resources.calendar_count_up
import org.churchpresenter.calendar.generated.resources.calendar_include
import org.churchpresenter.calendar.model.CopyTarget
import org.churchpresenter.calendar.model.DEFAULT_REPEAT_COUNT
import org.churchpresenter.calendar.model.MAX_REPEAT_COUNT
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.calendar.model.ServiceRepeat
import org.churchpresenter.calendar.model.recurrenceDates
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import java.util.Locale
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.LocalContentColor
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.components.SegmentTrack
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter

private val SHEET_WIDTH = 450.dp
private val TARGET_HEIGHT = 30.dp
private val COUNT_FIELD = 34.dp
private val STEPPER_BUTTON = 14.dp
private val PREVIEW_MAX = 130.dp
private val PREVIEW_INDEX = 13.dp
private const val CLASH_TINT = 0.14f
private const val CARD_TINT = 0.4f
private const val CARD_BORDER = 0.6f

/** `Sunday, 27 September 2026` — the preview's rows, in the machine's own locale. */
private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

/**
 * The **Copy** sheet: paste a service onto one date, or repeat it weekly, fortnightly or monthly a
 * chosen number of times.
 *
 * Laid out to the design: `Repeat` as a segmented selector; for Once, four `Paste on` chips
 * relative to the service's date; for a repeat, a `How many times` stepper over a preview of the
 * dates it will create, each flagged when that day already has a service; then `Include`. The
 * footer says what the button will do before it is pressed.
 *
 * The design's Include list also offers per-service looks; the planner has none yet, so that box
 * is not drawn rather than wired to nothing.
 */
@Composable
fun CopySheet(
    service: PlannedService,
    date: LocalDate,
    hasServices: (LocalDate) -> Boolean,
    onCopy: (dates: List<LocalDate>, includeRunOfShow: Boolean, includeCues: Boolean, repeat: ServiceRepeat) -> Unit,
    onDismiss: () -> Unit,
) {
    var repeat by remember(service) { mutableStateOf(ServiceRepeat.NONE) }
    var target by remember(service) { mutableStateOf(CopyTarget.NEXT_WEEK) }
    var count by remember(service) { mutableStateOf(DEFAULT_REPEAT_COUNT) }
    var includeRunOfShow by remember(service) { mutableStateOf(true) }
    var includeCues by remember(service) { mutableStateOf(true) }

    val once = repeat == ServiceRepeat.NONE
    val dates = if (once) listOf(target.date(date)) else recurrenceDates(date, repeat, count)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetScaffold(
            title = stringResource(Res.string.calendar_copy_title, service.name),
            subtitle = stringResource(Res.string.calendar_copy_sub, shortDate(date), service.contentItems().size),
            icon = Icons.Filled.ContentCopy,
            width = SHEET_WIDTH,
            onDismiss = onDismiss,
            footer = {
                Text(
                    text = stringResource(
                        when {
                            !includeRunOfShow && !includeCues -> Res.string.calendar_copy_footer_empty
                            once -> Res.string.calendar_copy_footer_once
                            else -> Res.string.calendar_copy_footer_repeat
                        },
                        shortDate(date),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(label = stringResource(Res.string.calendar_cancel), onClick = onDismiss)
                PrimaryButton(
                    label = if (once) {
                        stringResource(Res.string.calendar_copy_paste)
                    } else {
                        stringResource(Res.string.calendar_copy_create, dates.size)
                    },
                    onClick = { onCopy(dates, includeRunOfShow, includeCues, repeat) },
                    enabled = includeRunOfShow || includeCues,
                )
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
            ) {
                Column {
                    FieldLabel(stringResource(Res.string.calendar_copy_repeat))
                    Spacer(Modifier.height(6.dp))
                    SegmentedSelector(
                        options = ServiceRepeat.entries,
                        selected = repeat,
                        label = { repeatLabel(it) },
                        onSelect = { repeat = it },
                    )
                }
                if (once) {
                    PasteOn(selected = target, onSelect = { target = it })
                } else {
                    CountStepper(count = count, onChange = { count = it })
                    DatePreview(dates = dates, monthly = repeat == ServiceRepeat.MONTHLY, hasServices = hasServices)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel(stringResource(Res.string.calendar_include))
                    Spacer(Modifier.height(2.dp))
                    IncludeRow(
                        label = stringResource(Res.string.calendar_copy_include_run),
                        sub = stringResource(
                            Res.string.calendar_copy_include_run_sub,
                            service.items.count { it !is ScheduleItem.CueItem },
                        ),
                        on = includeRunOfShow,
                        onToggle = { includeRunOfShow = !includeRunOfShow },
                    )
                    IncludeRow(
                        label = stringResource(Res.string.calendar_copy_include_cues),
                        sub = stringResource(Res.string.calendar_copy_include_cues_sub, service.cueRows().size),
                        on = includeCues,
                        onToggle = { includeCues = !includeCues },
                    )
                }
            }
        }
    }
}

/** The four `Paste on` chips — where a one-off copy can go, relative to the service's own date. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasteOn(selected: CopyTarget, onSelect: (CopyTarget) -> Unit) {
    Column {
        FieldLabel(stringResource(Res.string.calendar_copy_paste_on))
        Spacer(Modifier.height(6.dp))
        // One segmented control, the same sunken track the app's other either/or choices sit on.
        SegmentTrack(modifier = Modifier.fillMaxWidth().height(TARGET_HEIGHT)) {
            CopyTarget.entries.forEach { option ->
                val on = option == selected
                SegmentTrackItem(
                    selected = on,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Text(
                        text = targetLabel(option),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = LocalContentColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun targetLabel(target: CopyTarget): String = stringResource(
    when (target) {
        CopyTarget.NEXT_WEEK -> Res.string.calendar_copy_next_week
        CopyTarget.IN_TWO_WEEKS -> Res.string.calendar_copy_in_two_weeks
        CopyTarget.NEXT_MONTH -> Res.string.calendar_copy_next_month
        CopyTarget.TOMORROW -> Res.string.calendar_copy_tomorrow
    }
)

/** `How many times`, as a number with a stacked up/down pair — the design's stepper. */
@Composable
private fun CountStepper(count: Int, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = stringResource(Res.string.calendar_copy_how_many),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(TARGET_HEIGHT)
                .sunken(RoundedCornerShape(8.dp), elevationPalette())
                .padding(start = 6.dp, end = 4.dp),
        ) {
            CompactTextField(
                value = count.toString(),
                onValueChange = { typed -> typed.toIntOrNull()?.let { onChange(it.coerceIn(1, MAX_REPEAT_COUNT)) } },
                height = TARGET_HEIGHT - 4.dp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(COUNT_FIELD),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                StepButton(
                    icon = Icons.Filled.KeyboardArrowUp,
                    description = stringResource(Res.string.calendar_count_up),
                    enabled = count < MAX_REPEAT_COUNT,
                ) {
                    onChange(count + 1)
                }
                StepButton(Icons.Filled.KeyboardArrowDown, stringResource(Res.string.calendar_count_down), count > 1) {
                    onChange(count - 1)
                }
            }
        }
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(width = STEPPER_BUTTON, height = STEPPER_BUTTON - 2.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else CARD_TINT),
            modifier = Modifier.size(STEPPER_BUTTON),
        )
    }
}

/**
 * The dates a repeat will create, numbered, with a badge on any that already has a service.
 *
 * The badge is information, not a block: a second service on a day is allowed everywhere else in
 * the planner, and a Sunday that already has the morning is exactly where an evening series goes.
 */
@Composable
private fun DatePreview(dates: List<LocalDate>, monthly: Boolean, hasServices: (LocalDate) -> Boolean) {
    val scheme = MaterialTheme.colorScheme
    val heading = if (dates.size == 1) {
        stringResource(Res.string.calendar_copy_creates_one)
    } else {
        stringResource(Res.string.calendar_copy_creates_other, dates.size)
    } + if (monthly) " · " + stringResource(Res.string.calendar_copy_same_weekday) else ""
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SheetMetrics.cardRadius)
            .background(scheme.surfaceVariant.copy(alpha = CARD_TINT))
            .border(1.dp, scheme.outlineVariant.copy(alpha = CARD_BORDER), SheetMetrics.cardRadius),
    ) {
        SheetOverline(heading, Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
        ScrollableColumn(
            modifier = Modifier.heightIn(max = PREVIEW_MAX),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp),
        ) {
            dates.forEachIndexed { index, day ->
                val clash = hasServices(day)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (clash) scheme.tertiary.copy(alpha = CLASH_TINT) else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant.copy(alpha = CARD_BORDER),
                        modifier = Modifier.width(PREVIEW_INDEX),
                    )
                    Text(
                        text = day.format(LONG_DATE.withLocale(Locale.getDefault())),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (clash) {
                        Text(
                            text = stringResource(Res.string.calendar_copy_clash),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = scheme.tertiary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(scheme.tertiary.copy(alpha = CLASH_TINT))
                                .padding(horizontal = 5.dp, vertical = 1.5.dp),
                        )
                    }
                }
            }
        }
    }
}

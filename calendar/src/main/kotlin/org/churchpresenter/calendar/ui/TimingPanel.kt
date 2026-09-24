package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_duration_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_at_end
import org.churchpresenter.calendar.generated.resources.calendar_timing_blank
import org.churchpresenter.calendar.generated.resources.calendar_timing_cued
import org.churchpresenter.calendar.generated.resources.calendar_timing_times_hint_long
import org.churchpresenter.calendar.generated.resources.calendar_timing_own_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_next_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_minus_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_loop_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_hold_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_cued_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_follows
import org.churchpresenter.calendar.generated.resources.calendar_timing_follows_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_blank_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_before
import org.churchpresenter.calendar.generated.resources.calendar_timing_hold
import org.churchpresenter.calendar.generated.resources.calendar_timing_loop
import org.churchpresenter.calendar.generated.resources.calendar_timing_minus
import org.churchpresenter.calendar.generated.resources.calendar_timing_next
import org.churchpresenter.calendar.generated.resources.calendar_timing_on_time
import org.churchpresenter.calendar.generated.resources.calendar_timing_once
import org.churchpresenter.calendar.generated.resources.calendar_timing_or
import org.churchpresenter.calendar.generated.resources.calendar_timing_own
import org.churchpresenter.calendar.generated.resources.calendar_timing_repeats
import org.churchpresenter.calendar.generated.resources.calendar_timing_runs
import org.churchpresenter.calendar.generated.resources.calendar_timing_starts
import org.churchpresenter.calendar.generated.resources.calendar_timing_times_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_usually_hint
import org.churchpresenter.calendar.generated.resources.calendar_usually
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.formatDuration
import org.churchpresenter.calendar.model.parseStoredTime
import org.churchpresenter.core.models.schedule.RowEnd
import org.jetbrains.compose.resources.stringResource
import java.time.LocalTime
import org.churchpresenter.theme.elevationPalette

/** The start chips, in minutes before the service; `0` is on time. */
private val START_OFFSETS = listOf(60, 45, 30, 20, 15, 10, 5, 0)

/** The run-length chips, in seconds. */
private val RUN_CHOICES = listOf(300, 900, 1800, 2700, 3600)

private val REPEAT_CHOICES = listOf(1, 0, 2, 3)
private val LABEL_COLUMN = 58.dp
private val FIELD_WIDTH = 84.dp
private val REPEATS_FIELD = 44.dp
private val FIELD_HEIGHT = 28.dp
private val CHIP_PADDING = 9.dp
private const val DISABLED_ALPHA = 0.35f
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val MINUTES_FIELD_DIGITS = 3

/**
 * The panel under the picker's list: **Starts**, **Runs**, **Repeats**, **At end** -- the design's
 * four rows. Every change goes straight out through [onChange]; the caller decides whether that
 * lands on a row now (editing) or waits for the pick (adding).
 */
@Composable
fun TimingPanel(
    draft: TimingDraft,
    serviceStartTime: String,
    onChange: (TimingDraft) -> Unit,
    modifier: Modifier = Modifier,
    /** False for a row that has no timing -- a section heading: drawn dimmed and inert. */
    enabled: Boolean = true,
    /** What the row has actually taken on screen, offered as a **Runs** choice; null when unknown. */
    measuredSeconds: Int? = null,
) {
    val use24Hour = LocalUse24HourClock.current
    val start = parseStoredTime(serviceStartTime)
    val pickedStart = draft.startTime()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(if (enabled) Modifier else Modifier.swallowClicks()),
    ) {
        StartRows(draft = draft, start = start, pickedStart = pickedStart, use24Hour = use24Hour, onChange = onChange)
        RunRows(draft = draft, measuredSeconds = measuredSeconds, onChange = onChange)
        RepeatRows(draft = draft, onChange = onChange)
        EndRow(draft = draft, onChange = onChange)
    }
}

@Composable
private fun StartRows(
    draft: TimingDraft,
    start: LocalTime?,
    pickedStart: LocalTime?,
    use24Hour: Boolean,
    onChange: (TimingDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimingRow(label = stringResource(Res.string.calendar_timing_starts)) {
            TimingChip(
                label = stringResource(Res.string.calendar_timing_cued),
                hint = stringResource(Res.string.calendar_timing_cued_hint),
                selected = draft.startText.isBlank() && !draft.followsPrevious,
                onClick = { onChange(draft.copy(startText = "", followsPrevious = false)) },
            )
            TimingChip(
                label = stringResource(Res.string.calendar_timing_follows),
                hint = stringResource(Res.string.calendar_timing_follows_hint),
                selected = draft.followsPrevious && draft.startText.isBlank(),
                accent = true,
                onClick = { onChange(draft.copy(startText = "", followsPrevious = true)) },
            )
            if (start != null) {
                START_OFFSETS.forEach { minutes ->
                    val at = start.minusMinutes(minutes.toLong())
                    TimingChip(
                        label = if (minutes == 0) {
                            stringResource(Res.string.calendar_timing_on_time)
                        } else {
                            stringResource(Res.string.calendar_timing_minus, minutes)
                        },
                        // The clock time the offset works out to: the chips are relative, the
                        // service moves, and a hover answers "so when is that?" without arithmetic.
                        hint = clockText(at, use24Hour),
                        selected = pickedStart == at,
                        accent = true,
                        onClick = {
                            onChange(draft.copy(startText = clockText(at, use24Hour), followsPrevious = false))
                        },
                    )
                }
                // Any other offset, typed: the chips stop at 60 and step in fives.
                CompactTextField(
                    value = pickedStart
                        ?.let { start.toSecondOfDay() - it.toSecondOfDay() }
                        ?.takeIf { it >= 0 }
                        ?.let { (it / SECONDS_PER_MINUTE).toString() }
                        .orEmpty(),
                    onValueChange = { text ->
                        val typed = text.filter { it.isDigit() }.take(MINUTES_FIELD_DIGITS)
                        val minutes = typed.toIntOrNull()
                        onChange(
                            draft.copy(
                                startText = if (minutes == null) {
                                    ""
                                } else {
                                    clockText(start.minusMinutes(minutes.toLong()), use24Hour)
                                }
                            )
                        )
                    },
                    placeholder = stringResource(Res.string.calendar_timing_minus_hint),
                    height = FIELD_HEIGHT,
                    fontSize = 11f,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(REPEATS_FIELD),
                )
                Text(
                    text = stringResource(Res.string.calendar_timing_before),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            OrField(
                value = draft.startText,
                placeholder = clockText(start ?: LocalTime.NOON, use24Hour),
                onValueChange = { onChange(draft.copy(startText = it)) },
                errorBorder = draft.startText.isNotBlank() && pickedStart == null,
            )
        }
    }
}

@Composable
private fun RunRows(draft: TimingDraft, measuredSeconds: Int?, onChange: (TimingDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimingRow(label = stringResource(Res.string.calendar_timing_runs)) {
            TimingChip(
                label = stringResource(Res.string.calendar_timing_own),
                hint = stringResource(Res.string.calendar_timing_own_hint),
                selected = draft.durationText.isBlank(),
                onClick = { onChange(draft.copy(durationText = "")) },
            )
            // What it has actually taken here, first among the choices: the one length that is
            // about this row rather than a round number.
            if (measuredSeconds != null) {
                TimingChip(
                    label = stringResource(Res.string.calendar_usually, formatDuration(measuredSeconds)),
                    hint = stringResource(Res.string.calendar_timing_usually_hint),
                    selected = draft.runSeconds() == measuredSeconds,
                    accent = true,
                    onClick = { onChange(draft.copy(durationText = formatDuration(measuredSeconds))) },
                )
            }
            RUN_CHOICES.forEach { seconds ->
                TimingChip(
                    label = runChipLabel(seconds),
                    selected = draft.runSeconds() == seconds,
                    onClick = { onChange(draft.copy(durationText = formatDuration(seconds))) },
                )
            }
            OrField(
                value = draft.durationText,
                placeholder = stringResource(Res.string.calendar_duration_hint),
                onValueChange = { onChange(draft.copy(durationText = it)) },
                errorBorder = draft.durationText.isNotBlank() && draft.runSeconds() == null,
            )
        }
    }
}

@Composable
private fun RepeatRows(draft: TimingDraft, onChange: (TimingDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimingRow(label = stringResource(Res.string.calendar_timing_repeats)) {
            REPEAT_CHOICES.forEach { count ->
                TimingChip(
                    label = when (count) {
                        1 -> stringResource(Res.string.calendar_timing_once)
                        0 -> stringResource(Res.string.calendar_timing_loop)
                        else -> count.toString()
                    },
                    hint = stringResource(
                        if (count == 0) {
                            Res.string.calendar_timing_loop_hint
                        } else {
                            Res.string.calendar_timing_times_hint_long
                        },
                        count,
                    ),
                    selected = draft.repeats == count,
                    accent = count == 0,
                    onClick = {
                        onChange(draft.copy(repeats = count, repeatsText = if (count > 1) count.toString() else ""))
                    },
                )
            }
            CompactTextField(
                value = draft.repeatsText,
                onValueChange = { text ->
                    val typed = text.filter { it.isDigit() }.take(2)
                    val count = typed.toIntOrNull()?.takeIf { it > 1 } ?: draft.repeats
                    onChange(draft.copy(repeatsText = typed, repeats = count))
                },
                placeholder = stringResource(Res.string.calendar_timing_times_hint),
                height = FIELD_HEIGHT,
                fontSize = 11f,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(REPEATS_FIELD),
            )
        }
    }
}

@Composable
private fun EndRow(draft: TimingDraft, onChange: (TimingDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimingRow(label = stringResource(Res.string.calendar_timing_at_end)) {
            listOf(RowEnd.HOLD, RowEnd.NEXT, RowEnd.BLANK).forEach { end ->
                TimingChip(
                    label = stringResource(
                        when (end) {
                            RowEnd.NEXT -> Res.string.calendar_timing_next
                            RowEnd.BLANK -> Res.string.calendar_timing_blank
                            else -> Res.string.calendar_timing_hold
                        }
                    ),
                    hint = stringResource(
                        when (end) {
                            RowEnd.NEXT -> Res.string.calendar_timing_next_hint
                            RowEnd.BLANK -> Res.string.calendar_timing_blank_hint
                            else -> Res.string.calendar_timing_hold_hint
                        }
                    ),
                    selected = draft.atEnd == end,
                    accent = end == RowEnd.NEXT,
                    onClick = { onChange(draft.copy(atEnd = end)) },
                )
            }
        }
    }
}

/**
 * Takes every pointer event, so a dimmed panel's chips cannot be pressed.
 *
 * In the initial pass, and consumed: the main pass reaches the chips first, so merely watching
 * the events there let every click through to them.
 */
private fun Modifier.swallowClicks(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** `5m` … `1h`, as the design labels the run chips. */
private fun runChipLabel(seconds: Int): String =
    if (seconds >= SECONDS_PER_HOUR) "${seconds / SECONDS_PER_HOUR}h" else "${seconds / SECONDS_PER_MINUTE}m"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimingRow(label: String, chips: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label.uppercase(),
            style = overlineStyle().copy(fontSize = 9.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.width(LABEL_COLUMN).padding(top = 7.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f),
        ) {
            chips()
        }
    }
}

/** `or [ 9:45 AM ]` at the end of a chip row: the typed alternative to the chips. */
@Composable
private fun OrField(value: String, placeholder: String, onValueChange: (String) -> Unit, errorBorder: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = stringResource(Res.string.calendar_timing_or),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CompactTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            height = FIELD_HEIGHT,
            fontSize = 11f,
            textAlign = TextAlign.Center,
            errorBorder = errorBorder,
            modifier = Modifier.width(FIELD_WIDTH),
        )
    }
}

/** One chip of the panel: the picker's chip, in the automation color when [accent]. */
@Composable
private fun TimingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Boolean = false,
    /** What the chip does, on hover -- for a chip whose label is an offset or a word like `Loop`. */
    hint: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    val palette = elevationPalette()
    // A raised key, lit when chosen: the selected fill, or the tertiary key for an accent chip.
    val fill = when {
        !selected -> palette.key
        accent -> palette.tinted(scheme.tertiary, scheme.onTertiary)
        else -> palette.selected
    }
    Hint(hint) {
        Box(
            Modifier
                .height(FIELD_HEIGHT)
                .raisedKey(RoundedCornerShape(7.dp), fill, onClick = onClick)
                .padding(horizontal = CHIP_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = fill.ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

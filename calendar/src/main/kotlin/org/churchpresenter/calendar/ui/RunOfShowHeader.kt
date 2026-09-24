package org.churchpresenter.calendar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.RaisedSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.foundation.PointerMatcher
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_all_manual
import org.churchpresenter.calendar.generated.resources.calendar_arm_tip
import org.churchpresenter.calendar.generated.resources.calendar_auto_start_one
import org.churchpresenter.calendar.generated.resources.calendar_auto_starts
import org.churchpresenter.calendar.generated.resources.calendar_armed
import org.churchpresenter.calendar.generated.resources.calendar_armed_off
import org.churchpresenter.calendar.generated.resources.calendar_copy_service_tip
import org.churchpresenter.calendar.generated.resources.calendar_lay_out_times_tip
import org.churchpresenter.calendar.generated.resources.calendar_needs_attention
import org.churchpresenter.calendar.generated.resources.calendar_needs_attention_one
import org.churchpresenter.calendar.generated.resources.calendar_needs_attention_tip
import org.churchpresenter.calendar.generated.resources.calendar_run_clock
import org.churchpresenter.calendar.generated.resources.calendar_run_clock_reset
import org.churchpresenter.calendar.generated.resources.calendar_run_clock_tip
import org.churchpresenter.calendar.generated.resources.calendar_run_clock_tip_stepped
import org.churchpresenter.calendar.generated.resources.calendar_run_meta
import org.churchpresenter.calendar.generated.resources.calendar_save_template_tip
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.parseStoredTime
import org.jetbrains.compose.resources.stringResource
import java.time.LocalTime
import org.churchpresenter.theme.elevationPalette

/**
 * The pane's header, as the design has it: the service's span (`10:00 – 11:09 AM · 69 min`), then
 * the clock chip and the arm switch, then -- past a rule -- **Copy** and **Template**, the two
 * things done to a run of show as a whole rather than to a row.
 *
 * The clock chip is what the cues' `Fired` / `Next` marks are judged against. It reads the wall
 * clock when the service is today, and can be stepped by hand to walk through the timeline of any
 * service in advance -- which is how a Sunday's cues get checked on a Wednesday.
 */
@Composable
internal fun RunOfShowHeader(
    service: PlannedService,
    now: LocalTime?,
    previewing: Boolean,
    actions: RunOfShowHeaderActions,
    /** How many rows will not go on screen on the day -- see `preflight`. Zero draws nothing. */
    problemCount: Int = 0,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(CalendarMetrics.sectionHeaderHeight)
            .padding(horizontal = 13.dp),
    ) {
        Text(
            text = runMeta(service),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (problemCount > 0) AttentionChip(count = problemCount)
        ClockChip(
            now = now,
            previewing = previewing,
            onStep = actions.onClockStep,
            onReset = actions.onClockReset,
        )
        ArmSwitch(armed = service.armed, onArmed = actions.onArmed)
        HeaderIcon(
            icon = Icons.Filled.Timeline,
            tooltip = stringResource(Res.string.calendar_lay_out_times_tip),
            tint = scheme.tertiary,
            onClick = actions.onLayOutTimes,
        )
        Box(Modifier.width(1.dp).height(HEADER_RULE_HEIGHT).background(scheme.outlineVariant))
        HeaderIcon(
            icon = Icons.Filled.ContentCopy,
            tooltip = stringResource(Res.string.calendar_copy_service_tip),
            tint = scheme.onSurfaceVariant,
            onClick = actions.onCopy,
        )
        HeaderIcon(
            icon = Icons.Filled.Dashboard,
            tooltip = stringResource(Res.string.calendar_save_template_tip),
            tint = scheme.onSurfaceVariant,
            onClick = actions.onSaveTemplate,
        )
    }
}

/**
 * `Run clock` until there is one; then the time, in the automation color. Clicking steps it on.
 *
 * A stepped clock carries an × to put it back: the right-click that used to be the only way out
 * left a preview time on screen with nothing saying how to clear it, and switching days was the
 * only escape. Right-click still works.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockChip(now: LocalTime?, previewing: Boolean, onStep: () -> Unit, onReset: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val key = elevationPalette().key
    val live = now != null
    // The chip is a raised key; a live clock keeps its tertiary on the icon, the text stays key ink.
    val ink = key.ink
    val iconInk = if (live) scheme.tertiary else key.ink
    val hint = if (previewing) Res.string.calendar_run_clock_tip_stepped else Res.string.calendar_run_clock_tip
    Hint(stringResource(hint)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .height(HEADER_ACTION_HEIGHT)
                .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onReset)
                .raisedKey(RoundedCornerShape(6.dp), key, onClick = onStep)
                .padding(horizontal = 8.dp),
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = stringResource(Res.string.calendar_run_clock),
                tint = iconInk,
                modifier = Modifier.size(10.dp),
            )
            Text(
                text = now?.let { clockText(it, LocalUse24HourClock.current) }
                    ?: stringResource(Res.string.calendar_run_clock),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
            if (previewing) {
                Box(
                    Modifier
                        .size(CLOCK_RESET_BOX)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onReset),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.calendar_run_clock_reset),
                        tint = ink,
                        modifier = Modifier.size(9.dp),
                    )
                }
            }
        }
    }
}

/** `Armed` and the switch that arms or disarms every cue at once. */
@Composable
private fun ArmSwitch(armed: Boolean, onArmed: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(start = 2.dp),
    ) {
        Text(
            text = stringResource(if (armed) Res.string.calendar_armed else Res.string.calendar_armed_off),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = if (armed) scheme.tertiary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
        // M3's switch is 52×32; the design's is 27×16. Scaled rather than rebuilt so it keeps
        // the theme's colors and its accessibility role.
        val tip = stringResource(Res.string.calendar_arm_tip)
        Box(Modifier.height(20.dp).width(36.dp), contentAlignment = Alignment.Center) {
            RaisedSwitch(
                checked = armed,
                onCheckedChange = onArmed,
                modifier = Modifier.scale(SWITCH_SCALE).semantics { contentDescription = tip },
            )
        }
    }
}

/**
 * One of the header's small raised icon keys, at the design's 21dp.
 *
 * [tooltip] is both the hover hint and the accessible name: an icon this small says nothing about
 * itself, and Copy and Template were reaching the screen with neither.
 */
@Composable
private fun HeaderIcon(icon: ImageVector, tooltip: String, tint: Color, onClick: () -> Unit) {
    Hint(tooltip) {
        Box(
            modifier = Modifier
                .size(HEADER_ACTION_HEIGHT)
                .raisedKey(RoundedCornerShape(6.dp), elevationPalette().key, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = tooltip, tint = tint, modifier = Modifier.size(11.dp))
        }
    }
}

/**
 * `10:00 – 11:09 AM · 69 min · 3 auto starts`, or the item count when nothing has a planned
 * length yet.
 */
@Composable
private fun runMeta(service: PlannedService): String {
    val total = service.plannedTotalSeconds()
    val start = parseStoredTime(service.startTime)
    val auto = when (val count = service.autoStartCount()) {
        0 -> stringResource(Res.string.calendar_all_manual)
        1 -> stringResource(Res.string.calendar_auto_start_one)
        else -> stringResource(Res.string.calendar_auto_starts, count)
    }
    // "0 min" would be noise on a service nobody has estimated yet.
    if (total == 0 || start == null) return itemCountLabel(service.contentItems().size) + " · " + auto
    val use24Hour = LocalUse24HourClock.current
    return stringResource(
        Res.string.calendar_run_meta,
        clockText(start, use24Hour),
        clockText(start.plusSeconds(total.toLong()), use24Hour),
        total / SECONDS_PER_MINUTE,
    ) + " · " + auto
}

/** `2 rows need attention` -- the count of rows the pre-flight check marked, in the plan's own red. */
@Composable
private fun AttentionChip(count: Int) {
    // A badge, not a control: flat, in the palette's danger colors, whose red clears 4.5:1 where the
    // raw error red on its own tint does not.
    val danger = elevationPalette().danger
    Hint(stringResource(Res.string.calendar_needs_attention_tip)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .height(HEADER_ACTION_HEIGHT)
                .clip(RoundedCornerShape(CalendarMetrics.chipRadius))
                .background(danger.bottom)
                .padding(horizontal = 7.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = danger.ink, modifier = Modifier.size(10.dp))
            Text(
                text = if (count == 1) {
                    stringResource(Res.string.calendar_needs_attention_one)
                } else {
                    stringResource(Res.string.calendar_needs_attention, count)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                fontWeight = FontWeight.Bold,
                color = danger.ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private const val SWITCH_SCALE = 0.7f
private const val SECONDS_PER_MINUTE = 60
private val HEADER_ACTION_HEIGHT = 21.dp
private val CLOCK_RESET_BOX = 13.dp
private val HEADER_RULE_HEIGHT = 14.dp

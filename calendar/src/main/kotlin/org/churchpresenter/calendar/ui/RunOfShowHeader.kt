package org.churchpresenter.calendar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import org.churchpresenter.calendar.generated.resources.calendar_edit_cues_tip
import org.churchpresenter.calendar.generated.resources.calendar_run_clock
import org.churchpresenter.calendar.generated.resources.calendar_run_clock_tip
import org.churchpresenter.calendar.generated.resources.calendar_run_meta
import org.churchpresenter.calendar.generated.resources.calendar_save_template_tip
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.parseStoredTime
import org.jetbrains.compose.resources.stringResource
import java.time.LocalTime

/**
 * The pane's header, as the design has it: the service's span (`10:00 – 11:09 AM · 69 min`), then
 * the clock chip, the arm switch and the cue settings, then -- past a rule -- **Copy** and
 * **Template**, the two things done to a run of show as a whole rather than to a row.
 *
 * The clock chip is what the cues' `Fired` / `Next` marks are judged against. It reads the wall
 * clock when the service is today, and can be stepped by hand to walk through the timeline of any
 * service in advance -- which is how a Sunday's cues get checked on a Wednesday.
 */
@Composable
internal fun RunOfShowHeader(service: PlannedService, now: LocalTime?, actions: RunOfShowHeaderActions) {
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
        ClockChip(now = now, onStep = actions.onClockStep, onReset = actions.onClockReset)
        ArmSwitch(armed = service.armed, onArmed = actions.onArmed)
        HeaderIcon(
            icon = Icons.Filled.Settings,
            tooltip = stringResource(Res.string.calendar_edit_cues_tip),
            tint = scheme.primary,
            onClick = actions.onOpenAutomation,
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

/** `Run clock` until there is one; then the time, in the automation color. Right-click resets. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockChip(now: LocalTime?, onStep: () -> Unit, onReset: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val live = now != null
    val ink = if (live) scheme.tertiary else scheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .height(HEADER_ACTION_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .background(if (live) scheme.tertiary.copy(alpha = CLOCK_TINT) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (live) scheme.tertiary.copy(alpha = CLOCK_BORDER) else scheme.outlineVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onReset)
            .clickable(onClick = onStep)
            .padding(horizontal = 8.dp),
    ) {
        Icon(
            Icons.Filled.Schedule,
            contentDescription = stringResource(Res.string.calendar_run_clock_tip),
            tint = ink,
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
            Switch(
                checked = armed,
                onCheckedChange = onArmed,
                modifier = Modifier.scale(SWITCH_SCALE).semantics { contentDescription = tip },
            )
        }
    }
}

/** One of the header's small bordered icon buttons, at the design's 21dp. */
@Composable
private fun HeaderIcon(icon: ImageVector, tooltip: String, tint: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(HEADER_ACTION_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.surfaceVariant.copy(alpha = ROW_ALPHA))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = tooltip, tint = tint, modifier = Modifier.size(11.dp))
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

private const val ROW_ALPHA = 0.45f
private const val SWITCH_SCALE = 0.7f
private const val CLOCK_TINT = 0.14f
private const val CLOCK_BORDER = 0.5f
private const val SECONDS_PER_MINUTE = 60
private val HEADER_ACTION_HEIGHT = 21.dp
private val HEADER_RULE_HEIGHT = 14.dp

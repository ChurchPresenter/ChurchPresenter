package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_cue_fire_now
import org.churchpresenter.calendar.generated.resources.calendar_cue_fired_at
import org.churchpresenter.calendar.generated.resources.calendar_cue_skipped_at
import org.churchpresenter.calendar.generated.resources.calendar_cue_next_in
import org.churchpresenter.calendar.generated.resources.calendar_cue_skip_tip
import org.churchpresenter.calendar.model.CueStatus
import org.churchpresenter.calendar.model.PreflightProblem
import org.churchpresenter.calendar.model.ProblemFix
import org.churchpresenter.calendar.model.fix
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.elevationPalette

/*
 * A cue row of the run of show -- what a cue looks like inside the list.
 */

private const val ROW_ALPHA = 0.45f
private const val DIM_ALPHA = 0.45f
private const val CUE_TINT = 0.08f
private const val CUE_BORDER = 0.3f
private const val CHIP_TINT = 0.16f
private val TICK_BOX = 15.dp
private val CUE_ICON = 17.dp
private val CUE_ACCENT = 2.dp

/** `Fired 9:45 AM` once the clock has passed a cue, `Next · in 12 min` on the one coming up. */
@Composable
internal fun CueStatusChip(status: CueStatus, cue: ScheduleItem.CueItem, startTime: String?) {
    if (!status.fired && !status.isNext && !status.skipped) return
    val scheme = MaterialTheme.colorScheme
    val color = when {
        status.skipped -> scheme.error
        status.fired -> scheme.tertiary
        else -> scheme.primary
    }
    val text = when {
        status.skipped -> stringResource(Res.string.calendar_cue_skipped_at, cueTimeText(cue, startTime))
        status.fired -> stringResource(Res.string.calendar_cue_fired_at, cueTimeText(cue, startTime))
        else -> stringResource(Res.string.calendar_cue_next_in, status.minutesUntil)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(CalendarMetrics.smallRadius)
            .background(color.copy(alpha = CHIP_TINT))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** The `skip just for today` tick every cue row carries. */
@Composable
private fun CueTick(enabled: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Hint(stringResource(Res.string.calendar_cue_skip_tip)) {
        Box(
            Modifier
                .size(TICK_BOX)
                .then(
                    if (enabled) {
                        Modifier.raised(
                            RoundedCornerShape(5.dp),
                            elevationPalette().accent,
                            elevationPalette(),
                            lift = 2.dp,
                        )
                    } else {
                        Modifier.sunken(RoundedCornerShape(5.dp), elevationPalette())
                    }
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (enabled) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(Res.string.calendar_cue_skip_tip),
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

/** The small `▶` that fires a cue by hand. */
@Composable
private fun FireButton(onFire: () -> Unit) {
    val accent = elevationPalette().accent
    Hint(stringResource(Res.string.calendar_cue_fire_now)) {
        Box(
            Modifier
                .size(CalendarMetrics.rowAction)
                .raisedKey(CalendarMetrics.smallRadius, accent, onClick = onFire),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(Res.string.calendar_cue_fire_now),
                tint = accent.ink,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** The small badge a cue row carries for its action, or nothing. */
@Composable
private fun CueBadge(action: String, ink: Float) {
    val badge = cueBadge(action) ?: return
    val scheme = MaterialTheme.colorScheme
    Text(
        text = badge.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, letterSpacing = 0.4.sp),
        fontWeight = FontWeight.ExtraBold,
        color = scheme.tertiary.copy(alpha = ink),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.tertiary.copy(alpha = CHIP_TINT))
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
    )
}

/**
 * A cue as a row: `[time] [tick] [bolt] [title / what it does] [status] [badge] [fire]`. The tick
 * skips it for today and the fire button runs it now; nothing here edits the cue.
 */
@Composable
internal fun CueRow(
    cue: ScheduleItem.CueItem,
    startTime: String?,
    armed: Boolean,
    status: CueStatus?,
    onToggle: () -> Unit,
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
    /** Why the cue's payload will not go on screen, or null -- see `preflight`. */
    problem: PreflightProblem? = null,
    /** The fix for [problem]; a cue has no editor, so only a moved file can be found from here. */
    onFixProblem: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val on = cue.enabled && armed
    val ink = if (on) 1f else DIM_ALPHA
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(CalendarMetrics.rowRadius)
            .background(
                if (on) scheme.primary.copy(alpha = CUE_TINT) else scheme.surfaceVariant.copy(alpha = ROW_ALPHA),
            )
            .border(
                width = 1.dp,
                color = if (on) {
                    scheme.primary.copy(alpha = CUE_BORDER)
                } else {
                    scheme.outlineVariant.copy(alpha = ROW_ALPHA)
                },
                shape = CalendarMetrics.rowRadius,
            )
            .padding(end = 9.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            Modifier
                .width(CUE_ACCENT)
                .height(CalendarMetrics.rowIcon + 6.dp)
                .background(if (on) scheme.primary else Color.Transparent),
        )
        Text(
            text = cueTimeText(cue, startTime),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            fontWeight = FontWeight.Bold,
            color = if (on) scheme.primary else scheme.onSurfaceVariant.copy(alpha = DIM_ALPHA),
            maxLines = 1,
            modifier = Modifier.width(rowTimeColumnWidth()),
        )
        CueTick(enabled = cue.enabled, onToggle = onToggle)
        Box(
            Modifier
                .size(CUE_ICON)
                .clip(RoundedCornerShape(5.dp))
                .background(scheme.tertiary.copy(alpha = CHIP_TINT)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = scheme.tertiary,
                modifier = Modifier.size(10.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (problem != null) {
                    // A cue's song or verse is chosen on the row it was copied from, so only a
                    // moved file is fixable from here; the mark still says what is wrong.
                    ProblemMark(problem, onFix = onFixProblem.takeIf { problem.fix != ProblemFix.PICK_AGAIN })
                }
                Text(
                    text = cue.label.ifBlank { cueActionLabel(cue.action) },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (problem == null) scheme.onSurface.copy(alpha = ink) else scheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = cueSubtitle(cue),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                color = scheme.onSurfaceVariant.copy(alpha = ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        status?.let { CueStatusChip(it, cue, startTime) }
        CueBadge(cue.action, ink)
        FireButton(onFire = onFire)
    }
}

package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
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
import org.churchpresenter.calendar.FiredCue
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_cue_fired_title
import org.churchpresenter.calendar.generated.resources.calendar_cue_loops
import org.churchpresenter.calendar.generated.resources.calendar_cue_times
import org.churchpresenter.calendar.generated.resources.calendar_cue_toast_close
import org.churchpresenter.core.models.schedule.LOOP_FOREVER
import org.churchpresenter.calendar.model.canPlayRepeatedly
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.jetbrains.compose.resources.stringResource

private val TOAST_WIDTH = 310.dp
private val TOAST_ICON = 24.dp
private val TOAST_ACCENT = 3.dp
private const val TOAST_TINT = 0.14f
private const val TOAST_BORDER = 0.55f
private const val SUB_ALPHA = 0.8f

/**
 * The card that says a cue went off: `CUE FIRED 9:45 AM`, the cue, and -- when it put something
 * on screen -- what, with its play count. Drawn in the window's corner over whatever is there.
 *
 * It stays until dismissed. A cue firing is something the operator planned and may want to check
 * happened; a card that fades on its own is one they have to have been looking at.
 */
@Composable
fun CueToast(event: FiredCue, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val row = event.row
    val cue = row as? ScheduleItem.CueItem
    val payload = cue?.payload
    Row(
        modifier = modifier
            .width(TOAST_WIDTH)
            // Min intrinsic height, so the accent bar can fill whatever height the text makes.
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(11.dp))
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.tertiary.copy(alpha = TOAST_BORDER), RoundedCornerShape(11.dp)),
    ) {
        Box(Modifier.width(TOAST_ACCENT).fillMaxHeight().background(scheme.tertiary))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.weight(1f).padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        ) {
            Box(
                Modifier
                    .size(TOAST_ICON)
                    .clip(RoundedCornerShape(7.dp))
                    .background(scheme.tertiary.copy(alpha = TOAST_TINT)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = scheme.tertiary,
                    modifier = Modifier.size(13.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.calendar_cue_fired_title).uppercase(),
                        style = overlineStyle().copy(fontSize = 9.sp),
                        color = scheme.tertiary,
                        maxLines = 1,
                    )
                    Text(
                        text = clockText(event.at, LocalUse24HourClock.current),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = if (cue != null) cue.label.ifBlank { cueActionLabel(cue.action) } else row.displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (payload != null && cue != null) ToastTarget(payload, cue.plays)
            }
            Box(
                Modifier
                    .size(SheetMetrics.smallButton)
                    .clip(CalendarMetrics.smallRadius)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.calendar_cue_toast_close),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

/** `loops` or `×3` for a payload with a run to play; nothing for a single play or an item without one. */
@Composable
private fun playsLabel(plays: Int, canRepeat: Boolean): String? = when {
    !canRepeat || plays == 1 -> null
    plays == LOOP_FOREVER -> stringResource(Res.string.calendar_cue_loops)
    else -> stringResource(Res.string.calendar_cue_times, plays)
}

/** `→ Sunday announcements · loops`: what the cue put on screen, and how many times it plays. */
@Composable
private fun ToastTarget(payload: ScheduleItem, plays: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text = payload.displayText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
            color = scheme.onSurfaceVariant.copy(alpha = SUB_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        playsLabel(plays, payload.canPlayRepeatedly())?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
                color = scheme.tertiary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(scheme.tertiary.copy(alpha = TOAST_TINT))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

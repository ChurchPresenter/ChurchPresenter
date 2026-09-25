package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.schedule_load_confirm_append
import churchpresenter.composeapp.generated.resources.schedule_load_confirm_body
import churchpresenter.composeapp.generated.resources.schedule_load_confirm_replace
import churchpresenter.composeapp.generated.resources.schedule_load_confirm_title
import churchpresenter.composeapp.generated.resources.ic_save
import churchpresenter.composeapp.generated.resources.schedule_calendar_add
import churchpresenter.composeapp.generated.resources.schedule_calendar_not_in
import churchpresenter.composeapp.generated.resources.schedule_calendar_save
import churchpresenter.composeapp.generated.resources.schedule_calendar_unsaved
import churchpresenter.composeapp.generated.resources.schedule_autoload_days_hours
import churchpresenter.composeapp.generated.resources.schedule_autoload_hours_minutes
import churchpresenter.composeapp.generated.resources.schedule_autoload_load_now
import churchpresenter.composeapp.generated.resources.schedule_autoload_minutes
import churchpresenter.composeapp.generated.resources.schedule_autoload_notice
import churchpresenter.composeapp.generated.resources.schedule_autoload_notice_now
import kotlinx.coroutines.delay
import org.churchpresenter.calendar.ScheduleServiceLink
import org.churchpresenter.calendar.model.UpcomingLoad
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.time.Duration
import java.time.LocalDateTime

/**
 * `Loading Sunday Morning in 1 h 20 min` and a **Load now** button: the calendar's next planned
 * service, which it will put into the Schedule by itself on its day, and a way to have it now
 * instead -- days ahead, to rehearse it.
 */
@Composable
internal fun ScheduleAutoLoadNotice(
    upcoming: UpcomingLoad,
    onLoadNow: () -> Unit,
    modifier: Modifier = Modifier,
    now: () -> LocalDateTime = LocalDateTime::now,
) {
    // Minutes are all the text shows, so a refresh every half minute is never a minute late.
    val minutesLeft by produceState(minutesUntil(upcoming.loadAt, now()), upcoming) {
        while (true) {
            value = minutesUntil(upcoming.loadAt, now())
            delay(AUTO_LOAD_NOTICE_REFRESH_MILLIS)
        }
    }
    NoticeRow(
        icon = rememberVectorPainter(Icons.Filled.CalendarMonth),
        text = if (minutesLeft > 0) {
            stringResource(Res.string.schedule_autoload_notice, upcoming.serviceName, timeLeftText(minutesLeft))
        } else {
            stringResource(Res.string.schedule_autoload_notice_now, upcoming.serviceName)
        },
        action = stringResource(Res.string.schedule_autoload_load_now),
        onAction = onLoadNow,
        modifier = modifier,
    )
}

/**
 * `Changes not saved to Sunday Morning` and a **Save to calendar** button: the Schedule holds a
 * planned service and rows have been added, removed or moved there since it was loaded.
 */
@Composable
internal fun ScheduleSaveToCalendarNotice(
    service: ScheduleServiceLink,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NoticeRow(
        icon = painterResource(Res.drawable.ic_save),
        text = stringResource(Res.string.schedule_calendar_unsaved, service.serviceName),
        action = stringResource(Res.string.schedule_calendar_save),
        onAction = onSave,
        modifier = modifier,
    )
}

/**
 * `Not in the calendar` and an **Add to calendar…** button: a Schedule built here by hand, which
 * the Calendar Manager can take as a new service.
 */
@Composable
internal fun ScheduleAddToCalendarNotice(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    NoticeRow(
        icon = rememberVectorPainter(Icons.Filled.CalendarMonth),
        text = stringResource(Res.string.schedule_calendar_not_in),
        action = stringResource(Res.string.schedule_calendar_add),
        onAction = onAdd,
        modifier = modifier,
    )
}

/**
 * What **Load now** asks when the Schedule already holds something: clear it and load the service,
 * or add the service after it. The same question the Calendar Manager's Load into Schedule asks.
 */
@Composable
internal fun LoadServiceNowConfirm(
    serviceName: String,
    itemCount: Int,
    onChoose: (replace: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.schedule_load_confirm_title, serviceName)) },
        text = { Text(stringResource(Res.string.schedule_load_confirm_body, itemCount)) },
        confirmButton = {
            RaisedButton(shape = RoundedCornerShape(6.dp), onClick = { onChoose(true) }) {
                Text(stringResource(Res.string.schedule_load_confirm_replace))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
                GhostButton(shape = RoundedCornerShape(6.dp), onClick = { onChoose(false) }) {
                    Text(stringResource(Res.string.schedule_load_confirm_append))
                }
            }
        },
    )
}

/** An icon, a line or two of text, and one small button on the right. */
@Composable
private fun NoticeRow(icon: Painter, text: String, action: String, onAction: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(painter = icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        KeyButton(
            onClick = onAction,
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(26.dp)
        ) {
            Text(text = action, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** `2 d 3 h` a day or more ahead, `1 h 20 min` within the day, `20 min` under an hour. */
@Composable
private fun timeLeftText(minutes: Long): String {
    val days = minutes / MINUTES_PER_DAY
    val hours = minutes % MINUTES_PER_DAY / MINUTES_PER_HOUR
    return when {
        days > 0 -> stringResource(Res.string.schedule_autoload_days_hours, days.toInt(), hours.toInt())
        hours > 0 -> stringResource(
            Res.string.schedule_autoload_hours_minutes, hours.toInt(), (minutes % MINUTES_PER_HOUR).toInt(),
        )
        else -> stringResource(Res.string.schedule_autoload_minutes, minutes.toInt())
    }
}

/**
 * Whole minutes from [now] until [at], rounded **up** -- 30 seconds left reads `1 min`, never
 * `0 min` -- and 0 once [at] has come.
 */
internal fun minutesUntil(at: LocalDateTime, now: LocalDateTime): Long {
    val seconds = Duration.between(now, at).seconds
    return if (seconds <= 0) 0 else (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
}

private const val AUTO_LOAD_NOTICE_REFRESH_MILLIS = 30_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

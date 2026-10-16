package org.churchpresenter.serverui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.churchpresenter.server.CalendarSyncStatus
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.calendar_sync_details
import org.churchpresenter.strings.generated.resources.calendar_sync_error_body
import org.churchpresenter.strings.generated.resources.calendar_sync_error_title
import org.churchpresenter.strings.generated.resources.calendar_sync_hide_details
import org.churchpresenter.strings.generated.resources.calendar_sync_pill_off
import org.churchpresenter.strings.generated.resources.calendar_sync_pill_offline
import org.churchpresenter.strings.generated.resources.calendar_sync_pill_unpaired
import org.churchpresenter.strings.generated.resources.calendar_sync_retry
import org.churchpresenter.strings.generated.resources.calendar_sync_status_failed
import org.churchpresenter.strings.generated.resources.calendar_sync_status_other_desktop
import org.churchpresenter.strings.generated.resources.calendar_sync_status_synced
import org.churchpresenter.strings.generated.resources.calendar_sync_status_synced_changes
import org.churchpresenter.strings.generated.resources.calendar_sync_status_syncing
import org.churchpresenter.strings.generated.resources.calendar_sync_status_timed_out
import org.churchpresenter.strings.generated.resources.calendar_sync_status_unauthorized
import org.churchpresenter.strings.generated.resources.calendar_sync_status_unpaired
import org.churchpresenter.strings.generated.resources.calendar_sync_status_unresolved
import org.churchpresenter.strings.generated.resources.media_subtitles_off
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StatusPill(enabled: Boolean, status: CalendarSyncStatus, clock: LocalTimeText) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        !enabled || status == CalendarSyncStatus.Off ->
            Pill(stringResource(Res.string.calendar_sync_pill_off), muted, dot = true)
        status == CalendarSyncStatus.Unpaired ->
            Pill(stringResource(Res.string.calendar_sync_pill_unpaired), muted, dot = true)
        status == CalendarSyncStatus.Syncing ->
            Pill(stringResource(Res.string.calendar_sync_status_syncing), MaterialTheme.semantic.info, dot = true)
        status is CalendarSyncStatus.Synced ->
            Pill(syncedText(status, clock), MaterialTheme.semantic.success, dot = true)
        else -> Pill(stringResource(Res.string.calendar_sync_pill_offline), MaterialTheme.colorScheme.error, dot = true)
    }
}

/** The sync status in words, as the invite dialog says it. */
@Composable
internal fun calendarSyncStatusText(status: CalendarSyncStatus, clock: LocalTimeText = LocalTimeText.system()): String =
    when (status) {
        CalendarSyncStatus.Off -> stringResource(Res.string.media_subtitles_off)
        CalendarSyncStatus.Unpaired -> stringResource(Res.string.calendar_sync_status_unpaired)
        CalendarSyncStatus.Syncing -> stringResource(Res.string.calendar_sync_status_syncing)
        is CalendarSyncStatus.Synced -> syncedText(status, clock)
        is CalendarSyncStatus.Failed -> stringResource(Res.string.calendar_sync_status_failed, status.message)
        CalendarSyncStatus.TimedOut -> stringResource(Res.string.calendar_sync_status_timed_out)
        CalendarSyncStatus.Unauthorized -> stringResource(Res.string.calendar_sync_status_unauthorized)
        is CalendarSyncStatus.OtherDesktop -> stringResource(Res.string.calendar_sync_status_other_desktop)
    }

/**
 * What the pill cannot say: a failure as a banner in plain words, with the relay's own words one
 * click away and a retry beside it; the rows a sync could not match; the first-registration note.
 */
@Composable
internal fun StatusBanner(status: CalendarSyncStatus, onRetry: () -> Unit) {
    val error = MaterialTheme.colorScheme.error
    when (status) {
        is CalendarSyncStatus.Failed -> {
            var details by remember { mutableStateOf(false) }
            Banner(
                tint = error,
                icon = Icons.Outlined.WarningAmber,
                title = stringResource(Res.string.calendar_sync_error_title),
                body = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(Res.string.calendar_sync_error_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            stringResource(
                                if (details) {
                                    Res.string.calendar_sync_hide_details
                                } else {
                                    Res.string.calendar_sync_details
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { details = !details },
                        )
                    }
                },
                trailing = { RetryButton(onRetry) },
                below = {
                    if (details) {
                        CodeBox(modifier = Modifier.padding(start = 37.dp)) {
                            Text(
                                status.message,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
        CalendarSyncStatus.TimedOut, CalendarSyncStatus.Unauthorized, is CalendarSyncStatus.OtherDesktop ->
            Banner(
                tint = error,
                icon = Icons.Outlined.WarningAmber,
                title = calendarSyncStatusText(status),
                trailing = { RetryButton(onRetry) },
            )
        is CalendarSyncStatus.Synced -> if (status.outcome.unresolvedRows > 0) {
            Banner(
                tint = MaterialTheme.semantic.warning,
                icon = Icons.Outlined.WarningAmber,
                title = stringResource(Res.string.calendar_sync_status_unresolved, status.outcome.unresolvedRows),
            )
        }
        CalendarSyncStatus.Unpaired -> Text(
            text = stringResource(Res.string.calendar_sync_status_unpaired),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Unit
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    CompactKey(stringResource(Res.string.calendar_sync_retry), onRetry)
}

@Composable
private fun syncedText(status: CalendarSyncStatus.Synced, clock: LocalTimeText): String {
    val at = clock.format(status.at)
    val changes = status.outcome.phoneChanges
    return if (changes > 0) {
        stringResource(Res.string.calendar_sync_status_synced_changes, at, changes)
    } else {
        stringResource(Res.string.calendar_sync_status_synced, at)
    }
}

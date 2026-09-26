package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_auto_load_off
import org.churchpresenter.calendar.generated.resources.calendar_auto_load_turn_on
import org.churchpresenter.calendar.generated.resources.calendar_dismiss
import org.churchpresenter.calendar.generated.resources.calendar_next_sync_in
import org.churchpresenter.calendar.generated.resources.calendar_syncing
import org.churchpresenter.calendar.model.formatDuration
import org.churchpresenter.theme.components.GhostButton
import org.jetbrains.compose.resources.stringResource

// What the window says about the app around it: when it next syncs, and whether it loads services.

/**
 * `Next sync in 4:12`, counting down to the app's next pull from the relay, then `Syncing…` until
 * the pull is done and the next one is scheduled. Nothing while no pull is scheduled.
 */
@Composable
internal fun SyncCountdown(nextSyncAt: () -> Long?) {
    val secondsLeft by produceState<Long?>(initialValue = null, nextSyncAt) {
        while (true) {
            // Rounded up, so the last second reads 0:01 rather than already saying it is syncing.
            value = nextSyncAt()?.let { at ->
                (at - System.currentTimeMillis() + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
            }
            delay(MILLIS_PER_SECOND)
        }
    }
    val left = secondsLeft ?: return
    Text(
        text = if (left > 0) {
            stringResource(Res.string.calendar_next_sync_in, formatDuration(left.toInt()))
        } else {
            stringResource(Res.string.calendar_syncing)
        },
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        maxLines = 1,
    )
}

/**
 * Says that planned services stay here until someone loads them, and offers to change that: with
 * *Load the service automatically* off -- the default -- nothing on the calendar reaches the
 * Schedule tab by itself, and a planner who expected it to finds out on the morning. Information,
 * not a fault, so in the quiet colours rather than the recovery banner's.
 */
@Composable
internal fun AutoLoadOffBanner(onTurnOn: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(Res.string.calendar_auto_load_off),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        GhostButton(onClick = onTurnOn) { Text(stringResource(Res.string.calendar_auto_load_turn_on)) }
        GhostButton(onClick = onDismiss) { Text(stringResource(Res.string.calendar_dismiss)) }
    }
}

private const val MILLIS_PER_SECOND = 1_000L

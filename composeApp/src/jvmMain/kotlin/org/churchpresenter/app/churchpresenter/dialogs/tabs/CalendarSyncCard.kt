package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.calendar_sync_description
import churchpresenter.composeapp.generated.resources.calendar_sync_devices
import churchpresenter.composeapp.generated.resources.calendar_sync_devices_hint
import churchpresenter.composeapp.generated.resources.calendar_sync_enroll_hint
import churchpresenter.composeapp.generated.resources.calendar_sync_enable
import churchpresenter.composeapp.generated.resources.calendar_sync_instance
import churchpresenter.composeapp.generated.resources.calendar_sync_no_devices
import churchpresenter.composeapp.generated.resources.calendar_sync_relay_url
import churchpresenter.composeapp.generated.resources.calendar_sync_revoke
import churchpresenter.composeapp.generated.resources.calendar_sync_status_failed
import churchpresenter.composeapp.generated.resources.calendar_sync_status_off
import churchpresenter.composeapp.generated.resources.calendar_sync_status_other_desktop
import churchpresenter.composeapp.generated.resources.calendar_sync_status_synced
import churchpresenter.composeapp.generated.resources.calendar_sync_status_synced_changes
import churchpresenter.composeapp.generated.resources.calendar_sync_status_syncing
import churchpresenter.composeapp.generated.resources.calendar_sync_status_timed_out
import churchpresenter.composeapp.generated.resources.calendar_sync_status_unauthorized
import churchpresenter.composeapp.generated.resources.calendar_sync_status_unpaired
import churchpresenter.composeapp.generated.resources.calendar_sync_status_unresolved
import churchpresenter.composeapp.generated.resources.calendar_sync_sync_now
import churchpresenter.composeapp.generated.resources.calendar_sync_title
import churchpresenter.composeapp.generated.resources.calendar_sync_unpair
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.server.CalendarSyncService
import org.churchpresenter.app.churchpresenter.server.CalendarSyncStatus
import org.churchpresenter.calendar.sync.PairedDevice
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The card that enrolls phones to this computer's calendar and says how the sync is doing. */
@Composable
internal fun CalendarSyncCard(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    sync: CalendarSyncService,
    /** The operator's own name for a device, by its id — the Remote Clients card's labels. */
    labelFor: (String) -> String = { "" },
) {
    val scope = rememberCoroutineScope()
    val status by sync.status.collectAsState()
    val devices by sync.devices.collectAsState()
    val current = settings.calendarSync

    SettingsSection(title = stringResource(Res.string.calendar_sync_title)) {
        Text(
            text = stringResource(Res.string.calendar_sync_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(
                checked = current.enabled,
                onCheckedChange = { on -> onSettingsChange { it.copy(calendarSync = it.calendarSync.copy(enabled = on)) } },
            )
            Text(stringResource(Res.string.calendar_sync_enable), style = MaterialTheme.typography.bodyMedium)
        }
        if (current.enabled) {
            StatusLine(status)
            Text(
                text = stringResource(Res.string.calendar_sync_enroll_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (current.isPaired) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        onClick = { scope.launch { sync.syncNow() } },
                    ) { Text(stringResource(Res.string.calendar_sync_sync_now), style = MaterialTheme.typography.labelSmall) }
                    TextButton(
                        onClick = { sync.unpair() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(Res.string.calendar_sync_unpair), style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (current.isPaired) {
                DevicesList(devices = devices, labelFor = labelFor, onRevoke = { id -> scope.launch { sync.revokeDevice(id) } })
                Text(
                    text = "${stringResource(Res.string.calendar_sync_relay_url)}: ${current.relayUrl} · " +
                        "${stringResource(Res.string.calendar_sync_instance)}: ${current.instanceId}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(status: CalendarSyncStatus) {
    val (text, color) = when (status) {
        CalendarSyncStatus.Off -> stringResource(Res.string.calendar_sync_status_off) to MaterialTheme.colorScheme.onSurfaceVariant
        CalendarSyncStatus.Unpaired -> stringResource(Res.string.calendar_sync_status_unpaired) to MaterialTheme.colorScheme.onSurfaceVariant
        CalendarSyncStatus.Syncing -> stringResource(Res.string.calendar_sync_status_syncing) to MaterialTheme.colorScheme.onSurfaceVariant
        is CalendarSyncStatus.Synced -> syncedText(status) to MaterialTheme.colorScheme.primary
        is CalendarSyncStatus.Failed -> stringResource(Res.string.calendar_sync_status_failed, status.message) to MaterialTheme.colorScheme.error
        CalendarSyncStatus.TimedOut -> stringResource(Res.string.calendar_sync_status_timed_out) to MaterialTheme.colorScheme.error
        CalendarSyncStatus.Unauthorized -> stringResource(Res.string.calendar_sync_status_unauthorized) to MaterialTheme.colorScheme.error
        is CalendarSyncStatus.OtherDesktop -> stringResource(Res.string.calendar_sync_status_other_desktop) to MaterialTheme.colorScheme.error
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
    if (status is CalendarSyncStatus.Synced && status.outcome.unresolvedRows > 0) {
        Text(
            text = stringResource(Res.string.calendar_sync_status_unresolved, status.outcome.unresolvedRows),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun syncedText(status: CalendarSyncStatus.Synced): String {
    val at = localTime(status.at)
    val changes = status.outcome.phoneChanges
    return if (changes > 0) {
        stringResource(Res.string.calendar_sync_status_synced_changes, at, changes)
    } else {
        stringResource(Res.string.calendar_sync_status_synced, at)
    }
}

@Composable
private fun DevicesList(devices: List<PairedDevice>, labelFor: (String) -> String, onRevoke: (String) -> Unit) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(Res.string.calendar_sync_devices),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(Res.string.calendar_sync_devices_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (devices.isEmpty()) {
        Text(
            text = stringResource(Res.string.calendar_sync_no_devices),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
    devices.forEach { device ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Whose it is comes first, from the label the operator gave the device in Remote
            // Clients; the name the phone calls itself follows, so "Anna · Anna's iPhone".
            val label = labelFor(device.id)
            val shown = listOf(label, device.name).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { device.id }
            Column(modifier = Modifier.weight(1f)) {
                Text(shown, style = MaterialTheme.typography.bodyMedium)
                if (device.lastSeen.isNotBlank()) {
                    Text(localTime(device.lastSeen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(
                onClick = { onRevoke(device.id) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.calendar_sync_revoke), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private fun localTime(iso: String): String = runCatching {
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault(iso)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.minimumInteractiveComponentSize
import org.churchpresenter.theme.components.toggleRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedSwitch
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import churchpresenter.composeapp.generated.resources.calendar_sync_invite
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
import org.churchpresenter.app.churchpresenter.server.RelayEndpoints
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.dialogs.CalendarEnrollQrDialog
import org.churchpresenter.app.churchpresenter.server.CalendarInvite
import org.churchpresenter.app.churchpresenter.server.asInvite
import org.churchpresenter.app.churchpresenter.server.CalendarSyncService
import org.churchpresenter.app.churchpresenter.server.CalendarSyncStatus
import org.churchpresenter.calendar.sync.PairedDevice
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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
    // The invite QR this card opens; the card owns it so the Server tab needs no plumbing for it.
    var invite by remember { mutableStateOf<CalendarInvite?>(null) }
    CalendarSyncCardContent(
        settings = settings,
        onSettingsChange = onSettingsChange,
        status = status,
        devices = devices,
        labelFor = labelFor,
        onSyncNow = { scope.launch { sync.syncNow() } },
        onUnpair = sync::unpair,
        onRevoke = { id -> scope.launch { sync.revokeDevice(id) } },
        onInvite = { scope.launch { invite = sync.invitePhone().asInvite(sync) } },
    )
    invite?.let { CalendarEnrollQrDialog(invite = it, onDismiss = { invite = null }) }
}

/**
 * The card as drawn from what it shows -- the status, the devices -- and what its buttons do.
 * [zone] and [locale] are how the times are written; the defaults are the machine's, and a test
 * pins both so the picture does not depend on where it was taken.
 */
@Composable
internal fun CalendarSyncCardContent(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    status: CalendarSyncStatus,
    devices: List<PairedDevice>,
    labelFor: (String) -> String,
    onSyncNow: () -> Unit,
    onUnpair: () -> Unit,
    onRevoke: (String) -> Unit,
    onInvite: () -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    val current = settings.calendarSync
    val clock = remember(zone, locale) { LocalTimeText(zone, locale) }

    SettingsSection(title = stringResource(Res.string.calendar_sync_title)) {
        Text(
            text = stringResource(Res.string.calendar_sync_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        val setEnabled: (Boolean) -> Unit = { on ->
            onSettingsChange { it.copy(calendarSync = it.calendarSync.copy(enabled = on)) }
        }
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier.fillMaxWidth().toggleRow(current.enabled, setEnabled, interaction),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RaisedSwitch(
                checked = current.enabled,
                onCheckedChange = null,
                interactionSource = interaction,
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
            Text(stringResource(Res.string.calendar_sync_enable), style = MaterialTheme.typography.bodyMedium)
        }
        if (current.enabled) {
            StatusLine(status, clock)
            Text(
                text = stringResource(Res.string.calendar_sync_enroll_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (current.isPaired) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RaisedButton(
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        onClick = onInvite,
                    ) {
                        Text(
                            stringResource(Res.string.calendar_sync_invite),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    KeyButton(
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        onClick = onSyncNow,
                    ) {
                        Text(
                            stringResource(Res.string.calendar_sync_sync_now),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    GhostButton(
                        onClick = onUnpair,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(
                            stringResource(Res.string.calendar_sync_unpair),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (current.isPaired) {
                DevicesList(devices = devices, labelFor = labelFor, clock = clock, onRevoke = onRevoke)
                Text(
                    text = "${stringResource(Res.string.calendar_sync_relay_url)}: " +
                        "${current.relayUrl.ifBlank { RelayEndpoints.BUILT_IN.relayUrl }} · " +
                        "${stringResource(Res.string.calendar_sync_instance)}: ${current.instanceId}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The sync status in words, as the card and the invite dialog both say it. */
@Composable
internal fun calendarSyncStatusText(status: CalendarSyncStatus, clock: LocalTimeText = LocalTimeText.system()): String =
    when (status) {
        CalendarSyncStatus.Off -> stringResource(Res.string.calendar_sync_status_off)
        CalendarSyncStatus.Unpaired -> stringResource(Res.string.calendar_sync_status_unpaired)
        CalendarSyncStatus.Syncing -> stringResource(Res.string.calendar_sync_status_syncing)
        is CalendarSyncStatus.Synced -> syncedText(status, clock)
        is CalendarSyncStatus.Failed -> stringResource(Res.string.calendar_sync_status_failed, status.message)
        CalendarSyncStatus.TimedOut -> stringResource(Res.string.calendar_sync_status_timed_out)
        CalendarSyncStatus.Unauthorized -> stringResource(Res.string.calendar_sync_status_unauthorized)
        is CalendarSyncStatus.OtherDesktop -> stringResource(Res.string.calendar_sync_status_other_desktop)
    }

@Composable
private fun StatusLine(status: CalendarSyncStatus, clock: LocalTimeText) {
    val color = when (status) {
        is CalendarSyncStatus.Synced -> MaterialTheme.colorScheme.primary
        CalendarSyncStatus.Off, CalendarSyncStatus.Unpaired, CalendarSyncStatus.Syncing ->
            MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    val text = calendarSyncStatusText(status, clock)
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
private fun syncedText(status: CalendarSyncStatus.Synced, clock: LocalTimeText): String {
    val at = clock.format(status.at)
    val changes = status.outcome.phoneChanges
    return if (changes > 0) {
        stringResource(Res.string.calendar_sync_status_synced_changes, at, changes)
    } else {
        stringResource(Res.string.calendar_sync_status_synced, at)
    }
}

@Composable
private fun DevicesList(
    devices: List<PairedDevice>,
    labelFor: (String) -> String,
    clock: LocalTimeText,
    onRevoke: (String) -> Unit,
) {
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
                    Text(
                        clock.format(device.lastSeen),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            GhostButton(
                onClick = { onRevoke(device.id) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.calendar_sync_revoke), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** An ISO instant as a short local time, or the text itself when it is not one. */
internal class LocalTimeText(private val zone: ZoneId, locale: Locale) {
    private val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    companion object {
        fun system(): LocalTimeText = LocalTimeText(ZoneId.systemDefault(), Locale.getDefault())
    }

    fun format(iso: String): String =
        runCatching { formatter.format(Instant.parse(iso).atZone(zone)) }.getOrDefault(iso)
}

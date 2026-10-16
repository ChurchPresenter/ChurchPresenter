package org.churchpresenter.serverui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.calendar.sync.PairedDevice
import org.churchpresenter.profiles.LocalSettingsDevMode
import org.churchpresenter.server.CalendarInvite
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.CalendarSyncStatus
import org.churchpresenter.server.asInvite
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CalendarSyncSettings
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.calendar_sync_connection_details
import org.churchpresenter.strings.generated.resources.calendar_sync_description
import org.churchpresenter.strings.generated.resources.calendar_sync_description_computers
import org.churchpresenter.strings.generated.resources.calendar_sync_devices_hint
import org.churchpresenter.strings.generated.resources.calendar_sync_enable
import org.churchpresenter.strings.generated.resources.calendar_sync_instance
import org.churchpresenter.strings.generated.resources.calendar_sync_invite
import org.churchpresenter.strings.generated.resources.calendar_sync_phones
import org.churchpresenter.strings.generated.resources.calendar_sync_phones_hint
import org.churchpresenter.strings.generated.resources.calendar_sync_phones_none
import org.churchpresenter.strings.generated.resources.calendar_sync_relay_url
import org.churchpresenter.strings.generated.resources.calendar_sync_revoke
import org.churchpresenter.strings.generated.resources.calendar_sync_start_over
import org.churchpresenter.strings.generated.resources.calendar_sync_status_syncing
import org.churchpresenter.strings.generated.resources.calendar_sync_sync_now
import org.churchpresenter.strings.generated.resources.calendar_sync_card_title
import org.churchpresenter.strings.generated.resources.calendar_sync_unpair_available_on
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import java.net.InetAddress
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
    /** The relay this build reaches when settings name none; the app passes its built-in one. */
    builtInRelayUrl: String = "",
) {
    val scope = rememberCoroutineScope()
    val status by sync.status.collectAsState()
    val devices by sync.devices.collectAsState()
    val computers by sync.computers.list.collectAsState()
    // Other computers on one calendar are not ready for production: dev mode only, here and in the service.
    val multiComputer = LocalSettingsDevMode.current
    // The invite QR this card opens; the card owns it so the Server tab needs no plumbing for it.
    var invite by remember { mutableStateOf<CalendarInvite?>(null) }
    var addingComputer by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    val ownName = rememberComputerName()
    CalendarSyncCardContent(
        settings = settings,
        onSettingsChange = onSettingsChange,
        builtInRelayUrl = builtInRelayUrl,
        status = status,
        devices = devices,
        labelFor = labelFor,
        onSyncNow = { scope.launch { sync.syncNow() } },
        onUnpair = { sync.unpair() },
        onRevoke = { id -> scope.launch { sync.revokeDevice(id) } },
        onInvite = { scope.launch { invite = sync.invitePhone().asInvite(sync) } },
        computerActions = if (multiComputer) {
            ComputerActions(
                computers = computers,
                onAdd = { addingComputer = true },
                onRemove = { id -> scope.launch { sync.computers.remove(id) } },
                onMakeMain = { id -> scope.launch { sync.computers.promote(id, ownName) } },
                onJoin = { joining = true },
                onTakeOver = { scope.launch { sync.computers.takeOver() } },
            )
        } else {
            null
        },
    )
    invite?.let { CalendarEnrollQrDialog(invite = it, onDismiss = { invite = null }) }
    if (addingComputer) {
        AddComputerDialog(
            onCreate = { role, name -> sync.computers.invite(role, name, ownName) },
            onDismiss = { addingComputer = false },
        )
    }
    if (joining) {
        val current = settings.calendarSync
        JoinCalendarDialog(
            replacesOwn = current.isPaired && !current.isJoined,
            onJoin = { join -> sync.computers.join(join) },
            onDismiss = { joining = false },
        )
    }
}

/** What this computer is called, for the other computer's status line; read off the UI thread. */
@Composable
private fun rememberComputerName(): String {
    val name by produceState(initialValue = System.getenv("COMPUTERNAME").orEmpty()) {
        if (value.isBlank()) {
            value = withContext(Dispatchers.IO) { runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("") }
                .substringBefore('.')
        }
    }
    return name
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
    now: Instant = Instant.now(),
    /** The relay shown when settings name none -- see [CalendarSyncCard]. */
    builtInRelayUrl: String = "",
    /** The other computers on this calendar and what to do about them; null outside dev mode, for now. */
    computerActions: ComputerActions? = null,
) {
    val current = settings.calendarSync
    val clock = remember(zone, locale) { LocalTimeText(zone, locale) }
    val setEnabled: (Boolean) -> Unit = { on ->
        onSettingsChange { it.copy(calendarSync = it.calendarSync.copy(enabled = on)) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, AppShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(12.dp))
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = if (current.enabled) 18.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardHeader(
            enabled = current.enabled,
            status = status,
            clock = clock,
            computers = computerActions != null,
            onEnabledChange = setEnabled,
        )
        if (current.enabled) {
            StatusBanner(status, onRetry = onSyncNow)
            if (current.isJoined) {
                if (computerActions != null) {
                    JoinedSection(
                        settings = current,
                        mainLastSeen = (status as? CalendarSyncStatus.Synced)?.outcome?.mainLastSeen.orEmpty(),
                        now = now,
                        locale = locale,
                        zone = zone,
                        onSyncNow = onSyncNow,
                        onLeave = onUnpair,
                        onTakeOver = computerActions.onTakeOver,
                    )
                }
            } else {
                if (current.isPaired) {
                    PhonesCard(
                        devices = devices,
                        labelFor = labelFor,
                        clock = clock,
                        syncing = status == CalendarSyncStatus.Syncing,
                        onInvite = onInvite,
                        onSyncNow = onSyncNow,
                        onRevoke = onRevoke,
                    )
                }
                if (computerActions != null) {
                    ComputersSection(actions = computerActions, clock = clock, canAdd = current.isPaired)
                }
                if (current.isPaired) {
                    CardFooter(
                        current = current,
                        nextRotation = current.nextRotationAt(now),
                        zone = zone,
                        locale = locale,
                        builtInRelayUrl = builtInRelayUrl,
                        onUnpair = onUnpair,
                    )
                }
            }
        }
    }
}

/** The card's top: its chip, title and status pill, what it is for, and the switch. */
@Composable
private fun CardHeader(
    enabled: Boolean,
    status: CalendarSyncStatus,
    clock: LocalTimeText,
    /** Other computers can join -- dev mode, for now -- so the description names them too. */
    computers: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconChip(Icons.Outlined.CalendarMonth, MaterialTheme.semantic.info, size = 40.dp, iconSize = 20.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.calendar_sync_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                StatusPill(enabled, status, clock)
            }
            Text(
                text = stringResource(
                    if (computers) {
                        Res.string.calendar_sync_description_computers
                    } else {
                        Res.string.calendar_sync_description
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val switchName = stringResource(Res.string.calendar_sync_enable)
        RaisedSwitch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier
                .guideTarget(GuideTargets.CALENDAR_SYNC)
                .semantics { contentDescription = switchName },
        )
    }
}

/** The phones on this calendar, Sync now and the phone invite. */
@Composable
private fun PhonesCard(
    devices: List<PairedDevice>,
    labelFor: (String) -> String,
    clock: LocalTimeText,
    syncing: Boolean,
    onInvite: () -> Unit,
    onSyncNow: () -> Unit,
    onRevoke: (String) -> Unit,
) {
    SubCard {
        SubCardHeader(
            icon = Icons.Outlined.PhoneIphone,
            tint = MaterialTheme.semantic.success,
            title = stringResource(Res.string.calendar_sync_phones),
            count = devices.size,
            hint = stringResource(Res.string.calendar_sync_phones_hint),
        ) {
            GhostButton(
                onClick = onSyncNow,
                enabled = !syncing,
                shape = AppShape(8.dp),
                contentPadding = COMPACT_PADDING,
                modifier = Modifier.compactButton(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        if (syncing) Res.string.calendar_sync_status_syncing else Res.string.calendar_sync_sync_now,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            CompactPrimary(stringResource(Res.string.calendar_sync_invite), onInvite)
        }
        if (devices.isEmpty()) {
            EmptyNote(stringResource(Res.string.calendar_sync_phones_none))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                devices.forEach { device ->
                    // Whose it is comes first, from the label the operator gave the device in Remote
                    // Clients; the name the phone calls itself follows, so "Anna · Anna's iPhone".
                    val label = labelFor(device.id)
                    val shown = listOf(label, device.name).filter { it.isNotBlank() }.joinToString(" · ")
                        .ifBlank { device.id }
                    ListRow(
                        dot = if (device.lastSeen.isBlank()) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.semantic.success
                        },
                        title = shown,
                        subtitle = if (device.lastSeen.isBlank()) "" else clock.format(device.lastSeen),
                    ) { RemoveButton(stringResource(Res.string.calendar_sync_revoke)) { onRevoke(device.id) } }
                }
            }
        }
        Text(
            text = stringResource(Res.string.calendar_sync_devices_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A quiet button that turns red under the pointer: Remove, Revoke. */
@Composable
internal fun RemoveButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    GhostButton(
        onClick = onClick,
        enabled = enabled,
        shape = AppShape(8.dp),
        contentPadding = COMPACT_PADDING,
        modifier = Modifier.compactButton(),
        // Disabled, it reads in the muted ink: the theme's 38% all but vanished on the card.
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

/** Connection details, folded away, and starting over -- kept apart from the everyday buttons. */
@Composable
private fun ColumnScope.CardFooter(
    current: CalendarSyncSettings,
    nextRotation: Instant?,
    zone: ZoneId,
    locale: Locale,
    builtInRelayUrl: String,
    onUnpair: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostButton(
            onClick = { open = !open },
            shape = AppShape(8.dp),
            contentPadding = COMPACT_PADDING,
            modifier = Modifier.compactButton(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp).rotate(if (open) CHEVRON_OPEN_DEGREES else 0f),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(Res.string.calendar_sync_connection_details),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.weight(1f))
        RemoveButton(
            stringResource(Res.string.calendar_sync_start_over),
            enabled = nextRotation == null,
            onClick = onUnpair,
        )
    }
    if (nextRotation != null) {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(nextRotation.atZone(zone))
        Text(
            text = stringResource(Res.string.calendar_sync_unpair_available_on, date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
    }
    if (open) {
        CodeBox {
            DetailLine(stringResource(Res.string.calendar_sync_relay_url), current.relayUrl.ifBlank { builtInRelayUrl })
            DetailLine(stringResource(Res.string.calendar_sync_instance), current.instanceId)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.width(64.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private const val CHEVRON_OPEN_DEGREES = 90f

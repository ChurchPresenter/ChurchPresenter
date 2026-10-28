package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.churchpresenter.signage.core.CecState
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_device_address
import org.churchpresenter.strings.generated.resources.signage_device_offline
import org.churchpresenter.strings.generated.resources.signage_free_space
import org.churchpresenter.strings.generated.resources.signage_player_version
import org.churchpresenter.strings.generated.resources.signage_synced_at
import org.churchpresenter.strings.generated.resources.signage_tv_control_unavailable
import org.churchpresenter.strings.generated.resources.signage_tv_control_working
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*
 * What a screen's settings dialog is opened on and saves to, and the device details it shows: moved
 * off the card, which keeps only what is needed at a glance.
 */

/** Saves what changed in the screen's settings dialog. */
internal fun saveSettings(
    id: String,
    name: String,
    plan: ScreenPlan,
    edit: ScreenSettingsEdit,
    actions: ScreenActions,
) {
    if (edit.name != name) actions.onRename(id, edit.name)
    if (edit.orientation != plan.orientation) actions.onOrientation(id, edit.orientation)
    if (edit.gesture != plan.kioskGesture || edit.gesturePlaylistId != plan.kioskGesturePlaylistId) {
        actions.onGesture(id, edit.gesture, edit.gesturePlaylistId)
    }
    when (val pin = edit.pin) {
        is PinChange.Set -> actions.onKioskPin(id, pin.pin)
        PinChange.Remove -> actions.onKioskPin(id, null)
        PinChange.Keep -> Unit
    }
}

/**
 * The playlists with a touch page: a link people use on the touchscreen, and a rotation with one
 * among its steps.
 */
internal fun touchPlaylists(folder: SignageFolderState): List<PlaylistSummary> {
    val added = folder.settings.added
    val pages = added.filterIsInstance<LinkPlaylist>().filter { it.interactive != null && it.kind == LinkKind.WEB }
        .map { it.id }.toSet()
    val rotations = added.filterIsInstance<RotationPlaylist>()
        .filter { rotation -> rotation.steps.any { it.sourceId in pages } }.map { it.id }.toSet()
    return folder.playlists.filter { it.id in pages || it.id in rotations }
}

/** The device as the settings dialog shows it: where it is, what it is, how it is doing. */
@Composable
internal fun DeviceDetails(row: ScreenRowState) {
    val address = row.link?.address?.removePrefix(IPV4_MAPPED)
    Text(
        address?.let { stringResource(Res.string.signage_device_address, it) }
            ?: stringResource(Res.string.signage_device_offline),
        style = MaterialTheme.typography.bodyMedium,
    )
    // Short lines, each about one thing, rather than one that wraps.
    detailLines(row).forEach { line ->
        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ScreenHealthLine(row.link?.status)
}

/** What the device is; how its content stands; whether it can turn the TV on and off. */
@Composable
private fun detailLines(row: ScreenRowState): List<String> {
    val screen = row.screen
    val status = row.link?.status
    val what = listOf(
        stringResource(platformName(screen.platform)),
        "${screen.screenWidth}×${screen.screenHeight}",
        stringResource(Res.string.signage_player_version, screen.agentVersion),
    )
    val content = listOfNotNull(
        status?.lastSyncMs?.let { stringResource(Res.string.signage_synced_at, timeOf(it)) },
        status?.freeDiskBytes?.let { stringResource(Res.string.signage_free_space, sizeOf(it)) },
    )
    val tv = when (status?.cec) {
        CecState.WORKING -> stringResource(Res.string.signage_tv_control_working)
        CecState.UNAVAILABLE -> stringResource(Res.string.signage_tv_control_unavailable)
        CecState.UNKNOWN, null -> null
    }
    return listOfNotNull(what.joinToString(SEPARATOR), content.joinToString(SEPARATOR).ifEmpty { null }, tv)
}

private fun timeOf(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private fun sizeOf(bytes: Long): String = "%.1f GB".format(bytes / BYTES_PER_GB)

/** How an IPv4 address can arrive from a socket that speaks IPv6. */
private const val IPV4_MAPPED = "::ffff:"
private const val BYTES_PER_GB = 1_000_000_000.0

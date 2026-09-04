/*
 * Choosing the camera a background draws.
 *
 * Its own file rather than a block inside the background controls: a camera needs a device, and
 * then a format or a DeckLink input to go with it, which is three pickers where every other type
 * needs one field or none.
 */
package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_camera_auto
import churchpresenter.composeapp.generated.resources.background_camera_connection
import churchpresenter.composeapp.generated.resources.background_camera_device
import churchpresenter.composeapp.generated.resources.background_camera_format
import churchpresenter.composeapp.generated.resources.canvas_decklink_device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.dialogs.PanelCaption
import org.churchpresenter.app.churchpresenter.composables.cameraHintStringRes
import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.app.churchpresenter.composables.CameraDeviceCatalog
import org.churchpresenter.app.churchpresenter.composables.CameraFormat
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.isFfmpegAvailable
import org.churchpresenter.app.churchpresenter.composables.listCameraFormats
import org.churchpresenter.app.churchpresenter.composables.selectedConnectionName
import org.churchpresenter.app.churchpresenter.composables.selectedFormatName
import org.churchpresenter.app.churchpresenter.composables.selectedModeName
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.settings.BackgroundConfig
import org.jetbrains.compose.resources.stringResource

/**
 * The device this background draws, and the format it is asked for.
 *
 * **The format picker is not optional.** The capture behind a camera is shared and ref-counted on
 * the device's identity, format included, so a background left on "auto" beside a Canvas layer
 * pinned to `1920x1080@30` is two different keys for one piece of hardware — two captures, and the
 * second one fails, because a camera opens once.
 *
 * Every enumeration here goes through [CameraDeviceCatalog] or an IO dispatcher. `listCameraFormats`
 * and `listCameraDevicesWithDeckLink` both shell out to ffmpeg; the Canvas property panel calls them
 * straight from composition and this deliberately does not copy that.
 */
@Composable
internal fun CameraPickerRow(config: BackgroundConfig, onConfigChange: (BackgroundConfig) -> Unit) {
    val deckLinkLabel = stringResource(Res.string.canvas_decklink_device)
    val autoLabel = stringResource(Res.string.background_camera_auto)
    val devices by CameraDeviceCatalog.devices.collectAsState()
    LaunchedEffect(Unit) { CameraDeviceCatalog.refresh(deckLinkLabel) }

    // Probed off the composition thread. This file's own note above says it deliberately does not
    // copy the Canvas panel's habit of shelling out from composition — but this call did exactly
    // that, and `isFfmpegAvailable()` runs `ffmpeg -version` against each candidate install path in
    // turn with a five-second timeout each. Starts `true` so no hint flashes on a machine that has
    // ffmpeg.
    var ffmpegAvailable by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { ffmpegAvailable = withContext(Dispatchers.IO) { isFfmpegAvailable() } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelCaption(stringResource(Res.string.background_camera_device))
        val found = devices.orEmpty()

        // Above the dropdown rather than instead of it: on Windows without ffmpeg the PnP fallback
        // fills the list with names that cannot be opened, so a hint shown only when the list is
        // empty is a hint the operator who needs it never sees.
        cameraHintStringRes(
            System.getProperty("os.name", ""),
            devices,
            ffmpegAvailable,
            CameraDeviceCatalog.lastEnumeration?.enumerator,
        ).forEach { hint ->
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (found.isEmpty()) return@Column

        DropdownSelector(
            label = stringResource(Res.string.background_camera_device),
            items = found.map { it.displayName },
            selected = selectedBackgroundCameraName(found, config.camera),
            onSelectedChange = { name ->
                found.firstOrNull { it.displayName == name }?.let {
                    onConfigChange(config.copy(camera = cameraRefOn(config.camera, it)))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (config.camera.isDeckLink && config.camera.deckLinkIndex >= 0) {
            DeckLinkFormatRows(config, onConfigChange, autoLabel)
        } else if (config.camera.isSet) {
            CameraFormatRow(config, onConfigChange, autoLabel)
        }
    }
}

/** The format list for an ordinary capture device, loaded off the composition thread. */
@Composable
private fun CameraFormatRow(
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit,
    autoLabel: String,
) {
    var formats by remember { mutableStateOf<List<CameraFormat>>(emptyList()) }
    LaunchedEffect(config.camera.devicePath) {
        formats = withContext(Dispatchers.IO) {
            listCameraFormats(config.camera.devicePath, config.camera.deviceName)
        }
    }
    if (formats.isEmpty()) return
    DropdownSelector(
        label = stringResource(Res.string.background_camera_format),
        items = listOf(autoLabel) + formats.map { it.displayName },
        selected = selectedFormatName(formats, config.camera.videoFormat, autoLabel),
        onSelectedChange = { name ->
            val chosen = formats.firstOrNull { it.displayName == name }?.encodedValue.orEmpty()
            onConfigChange(config.copy(camera = config.camera.copy(videoFormat = chosen)))
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A DeckLink names its input and its mode instead of a format; both come from the card. */
@Composable
private fun DeckLinkFormatRows(
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit,
    autoLabel: String,
) {
    var connections by remember { mutableStateOf<List<DeckLinkManager.VideoConnection>>(emptyList()) }
    var modes by remember { mutableStateOf<List<DeckLinkManager.InputMode>>(emptyList()) }
    LaunchedEffect(config.camera.deckLinkIndex) {
        withContext(Dispatchers.IO) {
            connections = DeckLinkManager.listVideoConnections(config.camera.deckLinkIndex)
            modes = DeckLinkManager.listInputModes(config.camera.deckLinkIndex)
        }
    }
    if (connections.isNotEmpty()) {
        DropdownSelector(
            label = stringResource(Res.string.background_camera_connection),
            items = connections.map { it.name },
            selected = selectedConnectionName(connections, config.camera.videoConnection),
            onSelectedChange = { name ->
                connections.firstOrNull { it.name == name }?.let {
                    onConfigChange(config.copy(camera = config.camera.copy(videoConnection = it.value)))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (modes.isNotEmpty()) {
        DropdownSelector(
            label = stringResource(Res.string.background_camera_format),
            items = listOf(autoLabel) + modes.map { it.name },
            selected = selectedModeName(modes, config.camera.videoFormat, autoLabel),
            onSelectedChange = { name ->
                val chosen = modes.firstOrNull { it.name == name }?.encodedValue.orEmpty()
                onConfigChange(config.copy(camera = config.camera.copy(videoFormat = chosen)))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What the device dropdown shows for [camera], falling back to the first device. */
internal fun selectedBackgroundCameraName(devices: List<CameraDevice>, camera: CameraDeviceRef): String =
    if (camera.isDeckLink && camera.deckLinkIndex >= 0) {
        devices.find { it.isDeckLink && it.deckLinkIndex == camera.deckLinkIndex }?.displayName
            ?: devices.first().displayName
    } else {
        devices.find { !it.isDeckLink && it.path == camera.devicePath }?.displayName
            ?: camera.devicePath.ifEmpty { devices.first().displayName }
    }

/**
 * [camera] pointed at [device].
 *
 * Format and connection are **reset**, exactly as `cameraSourceOn` resets them for a Canvas layer:
 * a mode enumerated from one device means nothing on another, and carrying it over would ask the
 * new device for a format it may not have.
 */
internal fun cameraRefOn(camera: CameraDeviceRef, device: CameraDevice): CameraDeviceRef = camera.copy(
    devicePath = device.path,
    deviceName = device.name,
    videoFormat = "",
    videoConnection = 0,
    isDeckLink = device.isDeckLink,
    deckLinkIndex = device.deckLinkIndex,
)

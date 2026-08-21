package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_source_camera
import churchpresenter.composeapp.generated.resources.canvas_source_screen_capture
import churchpresenter.composeapp.generated.resources.canvas_camera_device
import churchpresenter.composeapp.generated.resources.canvas_camera_ffmpeg_hint
import churchpresenter.composeapp.generated.resources.canvas_camera_v4l2_hint
import churchpresenter.composeapp.generated.resources.canvas_camera_none_found
import churchpresenter.composeapp.generated.resources.canvas_camera_refresh
import churchpresenter.composeapp.generated.resources.canvas_camera_format
import churchpresenter.composeapp.generated.resources.canvas_camera_format_auto
import churchpresenter.composeapp.generated.resources.canvas_camera_connection
import churchpresenter.composeapp.generated.resources.canvas_camera_mode
import churchpresenter.composeapp.generated.resources.canvas_camera_mode_auto
import churchpresenter.composeapp.generated.resources.canvas_capture_x
import churchpresenter.composeapp.generated.resources.canvas_capture_y
import churchpresenter.composeapp.generated.resources.canvas_capture_width
import churchpresenter.composeapp.generated.resources.canvas_capture_height
import churchpresenter.composeapp.generated.resources.canvas_capture_mode
import churchpresenter.composeapp.generated.resources.canvas_capture_mode_region
import churchpresenter.composeapp.generated.resources.canvas_capture_mode_window
import churchpresenter.composeapp.generated.resources.canvas_capture_window
import churchpresenter.composeapp.generated.resources.canvas_capture_refresh_windows
import churchpresenter.composeapp.generated.resources.canvas_capture_interval
import churchpresenter.composeapp.generated.resources.canvas_decklink_io_warning
import churchpresenter.composeapp.generated.resources.canvas_decklink_device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.models.scene.SceneSource

private const val MIN_CAPTURE_INTERVAL_MS = 33f
private const val MAX_CAPTURE_INTERVAL_MS = 1000f

/**
 * The scene sources that come from hardware or another window: a camera and a screen capture.
 */

@Composable
internal fun CameraProperties(source: SceneSource.CameraSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_camera),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val deckLinkDeviceFormat = stringResource(Res.string.canvas_decklink_device)
    var devices by remember { mutableStateOf(listCameraDevicesWithDeckLink(deckLinkDeviceFormat)) }
    val noCamerasLabel = stringResource(Res.string.canvas_camera_none_found)

    Button(
        onClick = { devices = listCameraDevicesWithDeckLink(deckLinkDeviceFormat) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(stringResource(Res.string.canvas_camera_refresh), style = MaterialTheme.typography.labelSmall)
    }

    if (devices.isNotEmpty()) {
        val items = devices.map { it.displayName }
        DropdownSelector(
            label = stringResource(Res.string.canvas_camera_device),
            items = items,
            selected = selectedCameraName(devices, source),
            onSelectedChange = { selected ->
                val device = devices.find { it.displayName == selected }
                if (device != null) {
                    onUpdate(cameraSourceOn(source, device))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (source.isDeckLink && source.deckLinkIndex >= 0) {

            if (DeckLinkManager.isOutputActive(source.deckLinkIndex)) {
                Text(
                    text = stringResource(Res.string.canvas_decklink_io_warning),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            var connections by remember { mutableStateOf<List<DeckLinkManager.VideoConnection>>(emptyList()) }
            var modes by remember { mutableStateOf<List<DeckLinkManager.InputMode>>(emptyList()) }

            LaunchedEffect(source.deckLinkIndex) {
                withContext(Dispatchers.IO) {
                    connections = DeckLinkManager.listVideoConnections(source.deckLinkIndex)
                    modes = DeckLinkManager.listInputModes(source.deckLinkIndex)
                }
            }

            LaunchedEffect(connections, source.videoConnection) {
                if (source.videoConnection == 0 && connections.isNotEmpty()) {
                    onUpdate(source.copy(videoConnection = connections.first().value))
                }
            }

            if (connections.isNotEmpty()) {
                val connItems = connections.map { it.name }
                DropdownSelector(
                    label = stringResource(Res.string.canvas_camera_connection),
                    items = connItems,
                    selected = selectedConnectionName(connections, source.videoConnection),
                    onSelectedChange = { selected ->
                        val conn = connections.find { it.name == selected }
                        if (conn != null) {
                            onUpdate(source.copy(videoConnection = conn.value))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val autoLabel = stringResource(Res.string.canvas_camera_mode_auto)
            val modeItems = listOf(autoLabel) + modes.map { it.name }
            DropdownSelector(
                label = stringResource(Res.string.canvas_camera_mode),
                items = modeItems,
                selected = selectedModeName(modes, source.videoFormat, autoLabel),
                onSelectedChange = { selected ->
                    if (selected == autoLabel) {
                        onUpdate(source.copy(videoFormat = ""))
                    } else {
                        val mode = modes.find { it.name == selected }
                        if (mode != null) {
                            onUpdate(source.copy(videoFormat = mode.encodedValue))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else if (source.devicePath.isNotEmpty() && !source.isDeckLink) {

            var formats by remember { mutableStateOf<List<CameraFormat>>(emptyList()) }
            LaunchedEffect(source.devicePath) {
                formats = withContext(Dispatchers.IO) {
                    listCameraFormats(source.devicePath, source.deviceName)
                }
            }

            val autoLabel = stringResource(Res.string.canvas_camera_format_auto)
            val formatItems = listOf(autoLabel) + formats.map { it.displayName }
            DropdownSelector(
                label = stringResource(Res.string.canvas_camera_format),
                items = formatItems,
                selected = selectedFormatName(formats, source.videoFormat, autoLabel),
                onSelectedChange = { selected ->
                    if (selected == autoLabel) {
                        onUpdate(source.copy(videoFormat = ""))
                    } else {
                        val fmt = formats.find { it.displayName == selected }
                        if (fmt != null) {
                            onUpdate(source.copy(videoFormat = fmt.encodedValue))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Text(
            noCamerasLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val osName = System.getProperty("os.name", "").lowercase()
    if (osName.contains("linux") && devices.isEmpty()) {
        Text(
            stringResource(Res.string.canvas_camera_v4l2_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (!osName.contains("linux")) {
        val ffmpegAvailable by remember { mutableStateOf(isFfmpegAvailable()) }
        if (!ffmpegAvailable) {
            Text(
                stringResource(Res.string.canvas_camera_ffmpeg_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ScreenCaptureProperties(source: SceneSource.ScreenCaptureSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_screen_capture),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val regionLabel = stringResource(Res.string.canvas_capture_mode_region)
    val windowLabel = stringResource(Res.string.canvas_capture_mode_window)
    DropdownSelector(
        label = stringResource(Res.string.canvas_capture_mode),
        items = listOf(regionLabel, windowLabel),
        selected = if (source.captureMode == "window") windowLabel else regionLabel,
        onSelectedChange = {
            val mode = if (it == windowLabel) "window" else "region"
            onUpdate(source.copy(captureMode = mode))
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (source.captureMode == "window") {
        var windows by remember { mutableStateOf(listOpenWindows()) }
        val windowTitles = windows.map { it.title }

        Button(
            onClick = { windows = listOpenWindows() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(Res.string.canvas_capture_refresh_windows), style = MaterialTheme.typography.labelSmall)
        }

        if (windowTitles.isNotEmpty()) {
            DropdownSelector(
                label = stringResource(Res.string.canvas_capture_window),
                items = windowTitles,
                selected = if (source.windowTitle in windowTitles) source.windowTitle else windowTitles.first(),
                onSelectedChange = { selected ->
                    val win = windows.find { it.title == selected }
                    val idStr = if (win != null && win.id != 0L) "0x%x".format(win.id) else ""
                    onUpdate(source.copy(windowTitle = selected, windowId = idStr))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        PropertyTextField(stringResource(Res.string.canvas_capture_x), source.captureX.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(captureX = it.coerceAtLeast(0))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_capture_y), source.captureY.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(captureY = it.coerceAtLeast(0))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_capture_width), source.captureWidth.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(captureWidth = it.coerceAtLeast(1))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_capture_height), source.captureHeight.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(captureHeight = it.coerceAtLeast(1))) }
        }
    }
    PropertySliderWithInput(stringResource(Res.string.canvas_capture_interval), source.captureInterval.toFloat(), MIN_CAPTURE_INTERVAL_MS, MAX_CAPTURE_INTERVAL_MS, "ms") { v ->
        onUpdate(source.copy(captureInterval = v.toInt()))
    }
}

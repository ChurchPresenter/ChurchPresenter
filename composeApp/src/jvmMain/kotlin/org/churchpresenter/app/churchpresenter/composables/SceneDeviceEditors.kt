package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_source_camera
import churchpresenter.composeapp.generated.resources.canvas_source_screen_capture
import churchpresenter.composeapp.generated.resources.canvas_camera_device
import churchpresenter.composeapp.generated.resources.canvas_camera_open_privacy_settings
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
import churchpresenter.composeapp.generated.resources.canvas_source_ndi
import churchpresenter.composeapp.generated.resources.canvas_ndi_source
import churchpresenter.composeapp.generated.resources.canvas_ndi_refresh
import churchpresenter.composeapp.generated.resources.canvas_ndi_none_found
import churchpresenter.composeapp.generated.resources.canvas_ndi_searching
import churchpresenter.composeapp.generated.resources.canvas_ndi_low_bandwidth
import churchpresenter.composeapp.generated.resources.canvas_ndi_low_bandwidth_help
import churchpresenter.composeapp.generated.resources.canvas_ndi_runtime_missing
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.presenter.NdiManager
import org.churchpresenter.ndi.NdiSourceInfo
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.app.churchpresenter.utils.UrlOpener

private const val MIN_CAPTURE_INTERVAL_MS = 33f
private const val MAX_CAPTURE_INTERVAL_MS = 1000f

/**
 * The scene sources that come from hardware, another window, or the network: a camera, a screen
 * capture and an NDI source.
 */

/**
 * Where an NDI layer's source is chosen.
 *
 * Discovery runs only while this panel is on screen — [SharedNdiSources] is acquired here and let
 * go on dispose — because a finder that is never closed keeps answering mDNS for a panel that was
 * shut an hour ago. The first look is done off the composition: it waits on the network, and doing
 * that inline would freeze the properties panel for a second every time a layer is selected.
 *
 * The configured source is always in the list even when discovery cannot see it. A source that is
 * powered off between services must not silently un-select itself from the scene the operator
 * built.
 */
@Composable
internal fun NdiProperties(source: SceneSource.NdiSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_ndi),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Collected rather than read once: an operator who installs the runtime and presses "check
    // again" in Settings should see this panel come to life, not have to reselect the layer.
    val runtimeStatus by NdiManager.status.collectAsState()
    if (!runtimeStatus.isReady) {
        Text(
            text = stringResource(Res.string.canvas_ndi_runtime_missing),
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }

    val scope = rememberCoroutineScope()
    var discovered by remember { mutableStateOf<List<NdiSourceInfo>>(emptyList()) }
    var looked by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        SharedNdiSources.acquire()
        onDispose { SharedNdiSources.release() }
    }
    LaunchedEffect(Unit) {
        discovered = withContext(Dispatchers.IO) { SharedNdiSources.sources() }
        looked = true
    }

    Button(
        onClick = {
            scope.launch {
                discovered = withContext(Dispatchers.IO) { SharedNdiSources.sources() }
                looked = true
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(stringResource(Res.string.canvas_ndi_refresh), style = MaterialTheme.typography.labelSmall)
    }

    val names = ndiSourceChoices(discovered, source.sourceName)
    if (names.isEmpty()) {
        Text(
            text = if (looked) stringResource(Res.string.canvas_ndi_none_found)
                   else stringResource(Res.string.canvas_ndi_searching),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    } else {
        DropdownSelector(
            label = stringResource(Res.string.canvas_ndi_source),
            items = names,
            selected = source.sourceName,
            onSelectedChange = { chosen -> onUpdate(ndiSourceOn(source, discovered, chosen)) },
            modifier = Modifier.fillMaxWidth()
        )
    }

    LabeledCheckbox(
        checked = source.lowBandwidth,
        onCheckedChange = { onUpdate(source.copy(lowBandwidth = it)) },
        label = stringResource(Res.string.canvas_ndi_low_bandwidth),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        spacing = 4.dp,
    )
    Text(
        text = stringResource(Res.string.canvas_ndi_low_bandwidth_help),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
    )
}

/**
 * The names the picker offers: what discovery found, plus [configured] when it is not among them.
 *
 * The second half is the point. A source that is switched off, or that discovery has not seen yet,
 * would otherwise vanish from its own dropdown and the panel would show the layer as pointing at
 * nothing.
 */
internal fun ndiSourceChoices(discovered: List<NdiSourceInfo>, configured: String): List<String> {
    val names = discovered.map { it.name }
    return if (configured.isBlank() || configured in names) names else names + configured
}

/**
 * [source] pointed at [chosen], keeping the address discovery reported for it.
 *
 * The address is stored beside the name rather than instead of it: it is what lets a receiver reach
 * a source on another subnet, and it is the half that goes stale when DHCP moves the sender.
 */
internal fun ndiSourceOn(
    source: SceneSource.NdiSource,
    discovered: List<NdiSourceInfo>,
    chosen: String,
): SceneSource.NdiSource {
    val match = discovered.find { it.name == chosen }
    return source.copy(sourceName = chosen, sourceAddress = match?.address.orEmpty())
}

@Composable
internal fun CameraProperties(source: SceneSource.CameraSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_camera),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val deckLinkDeviceFormat = stringResource(Res.string.canvas_decklink_device)

    // Through the catalog, never `listCameraDevicesWithDeckLink` directly: that shells out to
    // ffmpeg, and on Windows to a PowerShell `Get-CimInstance` as well, so calling it from
    // composition — or from a click handler — blocked the UI thread for as long as those took. A
    // panel that hangs the app for seconds every time a camera source is selected is the reported
    // "Canvas tab is very hanging"; the catalog does the same work on IO and caches it.
    val known by CameraDeviceCatalog.devices.collectAsState()
    val devices = known.orEmpty()
    val scope = rememberCoroutineScope()

    LaunchedEffect(deckLinkDeviceFormat) { CameraDeviceCatalog.refresh(deckLinkDeviceFormat) }

    Button(
        onClick = { scope.launch { CameraDeviceCatalog.refresh(deckLinkDeviceFormat) } },
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
    }

    val osName = System.getProperty("os.name", "").lowercase()
    // Probed off the composition thread: `isFfmpegAvailable()` runs `ffmpeg -version` against each
    // candidate install path in turn, with a five-second timeout each. It starts `true` so the
    // "install ffmpeg" sentence never flashes up on a machine that has it.
    var ffmpegAvailable by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { ffmpegAvailable = withContext(Dispatchers.IO) { isFfmpegAvailable() } }

    // `known`, not `devices`: null means the first enumeration has not answered yet, which the hint
    // list treats as "say nothing" and must not be flattened into "no cameras found".
    CameraToolHints(osName, known, ffmpegAvailable)

    if (osName.contains("mac") || osName.contains("darwin")) {
        MacCameraPrivacyHint()
    }
    if (osName.contains("win")) {
        WindowsCameraPrivacyHint()
    }
}

/** Whatever [cameraHintStringRes] decides is worth saying about this machine's camera tooling. */
@Composable
private fun CameraToolHints(osName: String, devices: List<CameraDevice>?, ffmpegAvailable: Boolean) {
    cameraHintStringRes(osName, devices, ffmpegAvailable).forEach { hint ->
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The Windows Camera privacy page, opened for the same reason as [MAC_CAMERA_PRIVACY_URI]. */
internal const val WINDOWS_CAMERA_PRIVACY_URI = "ms-settings:privacy-webcam"

/**
 * The way out of a privacy refusal on Windows — the twin of [MacCameraPrivacyHint], and there for
 * the same reasons, which are written out at that one.
 *
 * Windows has two separate switches on that page (camera access at all, and desktop apps in
 * particular) and blocks with either off, which is why the button leads there rather than the text
 * trying to describe the sequence.
 */
@Composable
private fun WindowsCameraPrivacyHint(
    onOpenPrivacySettings: () -> Unit = { UrlOpener.open(WINDOWS_CAMERA_PRIVACY_URI) },
) {
    Button(onClick = onOpenPrivacySettings, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.canvas_camera_open_privacy_settings), fontSize = 12.sp)
    }
}

/** The macOS Camera privacy pane, opened from here because the canvas is also the live output. */
internal const val MAC_CAMERA_PRIVACY_URI =
    "x-apple.systempreferences:com.apple.preference.security?Privacy_Camera"

/**
 * The way out of a privacy refusal, shown beside the camera picker on macOS.
 *
 * It lives in the properties panel rather than on the canvas because the canvas composable is also
 * what the presenter output draws — a button there would be painted onto the screen the
 * congregation is looking at. The canvas says *what* is wrong; this is where it is acted on.
 *
 * No accompanying warning text: a camera that is working needs no explanation, and a panel that
 * announces macOS is blocking something whenever it is running on macOS is a panel operators learn
 * to read past. The button is a plain affordance, and the canvas carries the diagnosis.
 */
@Composable
private fun MacCameraPrivacyHint(
    onOpenPrivacySettings: () -> Unit = { UrlOpener.open(MAC_CAMERA_PRIVACY_URI) }
) {
    Button(onClick = onOpenPrivacySettings, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.canvas_camera_open_privacy_settings), fontSize = 12.sp)
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

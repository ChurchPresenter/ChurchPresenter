package org.churchpresenter.canvas

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import org.churchpresenter.theme.AppShape
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
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.background_camera_option
import org.churchpresenter.strings.generated.resources.canvas_source_screen_capture
import org.churchpresenter.strings.generated.resources.canvas_camera_device
import org.churchpresenter.strings.generated.resources.canvas_camera_open_privacy_settings
import org.churchpresenter.strings.generated.resources.canvas_camera_is_screen_capture
import org.churchpresenter.strings.generated.resources.canvas_camera_refresh
import org.churchpresenter.strings.generated.resources.canvas_camera_format
import org.churchpresenter.strings.generated.resources.canvas_camera_format_auto
import org.churchpresenter.strings.generated.resources.canvas_camera_connection
import org.churchpresenter.strings.generated.resources.canvas_clock_mode
import org.churchpresenter.strings.generated.resources.canvas_camera_mode_auto
import org.churchpresenter.strings.generated.resources.canvas_capture_x
import org.churchpresenter.strings.generated.resources.canvas_capture_y
import org.churchpresenter.strings.generated.resources.canvas_capture_width
import org.churchpresenter.strings.generated.resources.canvas_capture_height
import org.churchpresenter.strings.generated.resources.canvas_capture_mode
import org.churchpresenter.strings.generated.resources.canvas_capture_mode_region
import org.churchpresenter.strings.generated.resources.canvas_capture_mode_window
import org.churchpresenter.strings.generated.resources.canvas_capture_refresh_windows
import org.churchpresenter.strings.generated.resources.canvas_capture_interval
import org.churchpresenter.strings.generated.resources.canvas_decklink_io_warning
import org.churchpresenter.strings.generated.resources.canvas_decklink_device
import org.churchpresenter.strings.generated.resources.canvas_source_ndi
import org.churchpresenter.strings.generated.resources.canvas_ndi_source
import org.churchpresenter.strings.generated.resources.canvas_ndi_refresh
import org.churchpresenter.strings.generated.resources.canvas_ndi_none_found
import org.churchpresenter.strings.generated.resources.canvas_ndi_searching
import org.churchpresenter.strings.generated.resources.canvas_ndi_low_bandwidth
import org.churchpresenter.strings.generated.resources.canvas_ndi_low_bandwidth_help
import org.churchpresenter.strings.generated.resources.canvas_ndi_runtime_missing
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.ndi.NdiSourceInfo
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.sharedui.utils.UrlOpener
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.sharedui.composables.LabeledCheckbox
import org.churchpresenter.sharedui.composables.PropertySliderWithInput
import org.churchpresenter.sharedui.composables.PropertyTextField
import org.churchpresenter.sharedui.composables.mode

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
internal fun NdiProperties(
    source: SceneSource.NdiSource,
    onUpdate: (SceneSource) -> Unit,
    /** The NDI runtime the panel reports on; a test passes its own. */
    inputs: NetworkInputs = NetworkInputs.current,
    /** Where discovery is shared from; a test passes one over a fake library. */
    directory: NdiSourceDirectory = SharedNdiSources,
) {
    Text(
        stringResource(Res.string.canvas_source_ndi),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Collected rather than read once: an operator who installs the runtime and presses "check
    // again" in Settings should see this panel come to life, not have to reselect the layer.
    val runtimeStatus by inputs.ndiStatus.collectAsState()
    if (!runtimeStatus.isReady) {
        Text(
            text = stringResource(Res.string.canvas_ndi_runtime_missing),
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }

    var discovered by remember { mutableStateOf<List<NdiSourceInfo>>(emptyList()) }
    var looked by remember { mutableStateOf(false) }
    // The panel looks the moment it opens, so it starts out busy — otherwise Refresh is live during
    // the first second, which is exactly when an impatient operator reaches for it.
    var looking by remember { mutableStateOf(true) }
    var looks by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        directory.acquire()
        onDispose { directory.release() }
    }
    // One effect for the opening look and every refresh, so the two cannot drift apart — and so
    // both are cancelled with the panel rather than outliving it on a scope of their own.
    LaunchedEffect(looks) {
        looking = true
        discovered = withContext(Dispatchers.IO) { directory.sources() }
        looked = true
        looking = false
    }

    RaisedButton(
        onClick = { looks++ },
        // Looks are serialised on the one finder, so a second press only queues another second of
        // waiting behind the first, with nothing on screen to say anything happened.
        enabled = !looking,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape(8.dp)
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
internal fun CameraProperties(
    source: SceneSource.CameraSource,
    onUpdate: (SceneSource) -> Unit,
    /**
     * The machine this panel describes, or null to ask the real one.
     *
     * A test passes one: enumeration reports whatever hardware the recording machine happens to
     * have, so the committed image of this panel said "MacBook Pro Camera" on one and "Capture
     * screen 0" on another. Same seam, same reason, as `SongBackgroundLibrary`'s. Given a host, the
     * panel asks the machine nothing — not its cameras, not its ffmpeg, not the last enumeration.
     */
    host: CameraHost? = null,
) {
    Text(
        stringResource(Res.string.background_camera_option),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val deckLinkDeviceFormat = stringResource(Res.string.canvas_decklink_device)

    // Through the catalog, never `listCameraDevicesWithDeckLink` directly: that shells out to
    // ffmpeg, and on Windows to a PowerShell `Get-CimInstance` as well, so calling it from
    // composition — or from a click handler — blocked the UI thread for as long as those took. A
    // panel that hangs the app for seconds every time a camera source is selected is the reported
    // "Canvas tab is very hanging"; the catalog does the same work on IO and caches it.
    val catalog by CameraDeviceCatalog.devices.collectAsState()
    // Null only while the first enumeration has not answered; a supplied host has always answered.
    val known = host?.devices ?: catalog
    // Displays are dropped here rather than in the catalog: the catalog is what decides where a
    // *saved* device is now, and it has to keep seeing them. See `selectableCameras`.
    val offered = known.orEmpty().selectableCameras(keeping = source.deviceName)
    val scope = rememberCoroutineScope()

    // Never enumerated when the caller supplied the host, or the real hardware would land a moment
    // later and replace what the caller pinned.
    LaunchedEffect(deckLinkDeviceFormat) {
        if (host == null) CameraDeviceCatalog.refresh(deckLinkDeviceFormat)
    }

    RaisedButton(
        onClick = { scope.launch { CameraDeviceCatalog.refresh(deckLinkDeviceFormat) } },
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape(8.dp)
    ) {
        Text(stringResource(Res.string.canvas_camera_refresh), style = MaterialTheme.typography.labelSmall)
    }

    if (offered.isNotEmpty()) {
        val items = offered.map { it.displayName }
        DropdownSelector(
            label = stringResource(Res.string.canvas_camera_device),
            items = items,
            selected = selectedCameraName(offered, source),
            onSelectedChange = { selected ->
                val device = offered.find { it.displayName == selected }
                if (device != null) {
                    onUpdate(cameraSourceOn(source, device))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Only reachable for a source saved before displays were dropped from the list, or one
        // hand-edited into a scene file. It keeps drawing — blacking out a working configuration
        // mid-service would be worse — but it says what it actually is.
        if (!source.isDeckLink && isScreenCaptureDevice(source.deviceName)) {
            Text(
                text = stringResource(Res.string.canvas_camera_is_screen_capture),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (source.isDeckLink && source.deckLinkIndex >= 0) {
            DeckLinkInputSettings(source, onUpdate, host?.deckLink ?: DeckLinkManagerInputs)
        } else if (source.devicePath.isNotEmpty() && !source.isDeckLink) {
            CameraFormatSettings(source, onUpdate)
        }
    }

    val osName = System.getProperty("os.name", "").lowercase()
    // Probed off the composition thread: `isFfmpegAvailable()` runs `ffmpeg -version` against each
    // candidate install path in turn, with a five-second timeout each. It starts `true` so the
    // "install ffmpeg" sentence never flashes up on a machine that has it.
    var probedFfmpeg by remember { mutableStateOf(true) }
    LaunchedEffect(host == null) {
        if (host == null) probedFfmpeg = withContext(Dispatchers.IO) { isFfmpegAvailable() }
    }

    // `known`, not `offered`: null means the first enumeration has not answered yet, which the hint
    // list treats as "say nothing" and must not be flattened into "no cameras found".
    CameraToolHints(
        osName = osName,
        devices = known,
        ffmpegAvailable = host?.ffmpegAvailable ?: probedFfmpeg,
        enumerator = if (host == null) CameraDeviceCatalog.lastEnumeration?.enumerator else null,
    )

    CameraPrivacyHint(osName)
}

/** A DeckLink input's connector and video mode, read off the card. */
@Composable
private fun DeckLinkInputSettings(
    source: SceneSource.CameraSource,
    onUpdate: (SceneSource) -> Unit,
    deckLink: DeckLinkInputs,
) {
    if (deckLink.isOutputActive(source.deckLinkIndex)) {
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
            connections = deckLink.videoConnections(source.deckLinkIndex)
            modes = deckLink.inputModes(source.deckLinkIndex)
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
        label = stringResource(Res.string.canvas_clock_mode),
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
}

/** A webcam's capture format, from what the device reports it offers. */
@Composable
private fun CameraFormatSettings(source: SceneSource.CameraSource, onUpdate: (SceneSource) -> Unit) {
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

/** Whatever [cameraHintStringRes] decides is worth saying about this machine's camera tooling. */
@Composable
private fun CameraToolHints(
    osName: String,
    devices: List<CameraDevice>?,
    ffmpegAvailable: Boolean,
    enumerator: CameraEnumerator?,
) {
    cameraHintStringRes(osName, devices, ffmpegAvailable, enumerator).forEach { hint ->
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The macOS Camera privacy pane, opened from a panel because a panel is where it can be acted on. */
internal const val MAC_CAMERA_PRIVACY_URI =
    "x-apple.systempreferences:com.apple.preference.security?Privacy_Camera"

/** The Windows Camera privacy page, opened for the same reason as [MAC_CAMERA_PRIVACY_URI]. */
internal const val WINDOWS_CAMERA_PRIVACY_URI = "ms-settings:privacy-webcam"

/**
 * Where [osName] keeps its camera permission, or null on a platform with no such page to open.
 *
 * Windows has two separate switches there — camera access at all, and desktop apps in particular —
 * and blocks with either off, which is why the button leads to the page rather than the text trying
 * to describe the sequence.
 */
internal fun cameraPrivacyUri(osName: String): String? {
    val name = osName.lowercase()
    return when {
        name.contains("mac") || name.contains("darwin") -> MAC_CAMERA_PRIVACY_URI
        name.contains("win") -> WINDOWS_CAMERA_PRIVACY_URI
        else -> null
    }
}

/**
 * The way out of a privacy refusal, shown beside a camera picker.
 *
 * It lives in a panel rather than on the canvas because the canvas composable is also what the
 * presenter output draws — a button there would be painted onto the screen the congregation is
 * looking at. The canvas says *what* is wrong; this is where it is acted on.
 *
 * No accompanying warning text: a camera that is working needs no explanation, and a panel that
 * announces the OS is blocking something whenever it runs on that OS is a panel operators learn to
 * read past. The button is a plain affordance, and the diagnosis is carried beside it.
 *
 * Shared with the background camera picker rather than copied to it: a background is opened by the
 * presenter window before any picker has been looked at, so it is the *more* likely of the two to
 * be refused, and it had no way out at all.
 *
 * [openUri] is a parameter so a test can press the button without launching a browser.
 */
@Composable
fun CameraPrivacyHint(
    osName: String = System.getProperty("os.name", ""),
    openUri: (String) -> Unit = { UrlOpener.open(it) },
) {
    val uri = cameraPrivacyUri(osName) ?: return
    RaisedButton(onClick = { openUri(uri) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.canvas_camera_open_privacy_settings), fontSize = 12.sp)
    }
}

@Composable
internal fun ScreenCaptureProperties(
    source: SceneSource.ScreenCaptureSource,
    onUpdate: (SceneSource) -> Unit,
    /** The desktop's open windows, as the picker lists them; a test passes its own. */
    listWindows: () -> List<WindowInfo> = ::listOpenWindows,
) {
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
        var windows by remember { mutableStateOf(listWindows()) }
        val windowTitles = windows.map { it.title }

        RaisedButton(
            onClick = { windows = listWindows() },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShape(8.dp)
        ) {
            Text(stringResource(Res.string.canvas_capture_refresh_windows), style = MaterialTheme.typography.labelSmall)
        }

        if (windowTitles.isNotEmpty()) {
            DropdownSelector(
                label = stringResource(Res.string.canvas_capture_mode_window),
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
    PropertySliderWithInput(
        stringResource(Res.string.canvas_capture_interval),
        source.captureInterval.toFloat(),
        MIN_CAPTURE_INTERVAL_MS,
        MAX_CAPTURE_INTERVAL_MS,
        "ms"
    ) { v ->
        onUpdate(source.copy(captureInterval = v.toInt()))
    }
}

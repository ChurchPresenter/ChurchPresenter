package org.churchpresenter.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.core.models.scene.SceneSource

internal fun selectedCameraName(devices: List<CameraDevice>, source: SceneSource.CameraSource): String =
    if (source.isDeckLink) {
        devices.find { it.isDeckLink && it.deckLinkIndex == source.deckLinkIndex }?.displayName
            ?: devices.first().displayName
    } else {
        devices.find { !it.isDeckLink && it.path == source.devicePath }?.displayName
            ?: if (source.devicePath.isNotEmpty()) source.devicePath else devices.first().displayName
    }

internal fun cameraSourceOn(
    source: SceneSource.CameraSource,
    device: CameraDevice
): SceneSource.CameraSource = source.copy(
    devicePath = device.path,
    deviceName = device.name,
    videoFormat = "",
    videoConnection = 0,
    isDeckLink = device.isDeckLink,
    deckLinkIndex = device.deckLinkIndex
)

fun selectedConnectionName(
    connections: List<DeckLinkManager.VideoConnection>,
    videoConnection: Int
): String = connections.find { it.value == videoConnection }?.name ?: connections.first().name

fun selectedModeName(
    modes: List<DeckLinkManager.InputMode>,
    videoFormat: String,
    autoLabel: String
): String =
    if (videoFormat.isEmpty()) autoLabel
    else modes.find { it.encodedValue == videoFormat }?.name ?: autoLabel

fun selectedFormatName(
    formats: List<CameraFormat>,
    videoFormat: String,
    autoLabel: String
): String =
    if (videoFormat.isEmpty()) autoLabel
    else formats.find { it.encodedValue == videoFormat }?.displayName ?: autoLabel

/** How AVFoundation names the displays it offers alongside the real cameras. */
private const val SCREEN_CAPTURE_PREFIX = "capture screen"

/**
 * True for AVFoundation's screen-grab pseudo-devices, which share one index space with the cameras.
 *
 * `ffmpeg -f avfoundation -list_devices true` prints the attached displays as ordinary numbered
 * video devices at the end of the same list — `[4] Capture screen 0` — and opening one is a screen
 * recording, which is what macOS raises its Screen Recording prompt for. Nothing distinguishes them
 * but the name.
 */
fun isScreenCaptureDevice(name: String): Boolean =
    name.trim().lowercase().startsWith(SCREEN_CAPTURE_PREFIX)

/**
 * The devices worth **offering** as a camera: this machine's list without the displays.
 *
 * Picking a display from the camera list records the operator's screen, and a camera background
 * opens as the presenter window does, so macOS raises its Screen Recording prompt on every launch.
 * Nothing is taken away by hiding them: `SceneSource.ScreenCaptureSource` is the supported route to
 * put a screen on the Canvas, with its own region and window modes.
 *
 * [keeping] is the name a source already points at, and it survives the filter. A configuration
 * someone set deliberately keeps working and stays visible — dropping it from the list would only
 * strand it, since the dropdown would then fall back to showing the raw `avfoundation://4` path.
 *
 * **Offering only.** The catalog itself stays unfiltered: [cameraResolves],
 * [resolveAvfoundationDevice] and [avfDeviceNameAt] all read it to decide what a *saved* device is
 * now, and the last of those treats list position as ffmpeg's index.
 */
fun List<CameraDevice>.selectableCameras(keeping: String? = null): List<CameraDevice> =
    filter { !isScreenCaptureDevice(it.name) || it.name == keeping }

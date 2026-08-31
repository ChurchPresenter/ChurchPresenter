/*
 * The Cameras tab of the song background panel.
 *
 * Its own file rather than a branch inside the swatch grid: a camera is picked from what the
 * machine has rather than from a library, so it brings its own enumeration and its own idea of
 * which tile is the selected one.
 */
package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_decklink_device
import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.app.churchpresenter.composables.CameraDeviceCatalog
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.jetbrains.compose.resources.stringResource

/**
 * One tile per camera this machine has.
 *
 * Black, like a clip's: a tile is small, there may be several, and each live one would hold its
 * device open for as long as the panel is on screen.
 */
internal fun LazyGridScope.cameraTiles(
    devices: List<CameraDevice>,
    background: SongBackground,
    onChange: (SongBackground) -> Unit,
) {
    items(devices) { device ->
        SwatchTile(
            label = device.displayName,
            selected = device.selects(background.camera),
            badge = SwatchBadge.NONE,
            onClick = { onChange(cameraBackground(background, device)) },
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}

/** The cameras to offer: what the caller supplied, or this machine's, enumerated off-thread. */
@Composable
internal fun rememberCameras(category: String, supplied: List<CameraDevice>?): List<CameraDevice> {
    if (category != SongBackgroundType.CAMERA) return emptyList()
    if (supplied != null) return supplied
    val deckLinkLabel = stringResource(Res.string.canvas_decklink_device)
    val found by CameraDeviceCatalog.devices.collectAsState()
    LaunchedEffect(category) { CameraDeviceCatalog.refresh(deckLinkLabel) }
    return found.orEmpty()
}

/** Whether this device is the one [camera] names — by card index, or by the path it was listed at. */
private fun CameraDevice.selects(camera: CameraDeviceRef): Boolean =
    if (isDeckLink) camera.isDeckLink && camera.deckLinkIndex == deckLinkIndex
    else !camera.isDeckLink && camera.devicePath == path

/**
 * [background] pointed at [device].
 *
 * Format and connection are reset, exactly as `cameraSourceOn` resets them for a Canvas layer: a
 * mode enumerated from one device means nothing on another.
 */
internal fun cameraBackground(background: SongBackground, device: CameraDevice): SongBackground =
    background.copy(
        type = SongBackgroundType.CAMERA,
        camera = background.camera.copy(
            devicePath = device.path,
            deviceName = device.name,
            videoFormat = "",
            videoConnection = 0,
            isDeckLink = device.isDeckLink,
            deckLinkIndex = device.deckLinkIndex,
        ),
    )


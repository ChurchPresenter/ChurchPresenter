/*
 * A live camera drawn as a background, behind lyrics or a verse.
 *
 * The Canvas equivalent is `SceneSourceRenderer`'s `CameraSourceContent`, and this deliberately
 * mirrors it rather than sharing with it: that one is typed against a scene layer, offers the
 * operator a diagnostic when a device will not open, and draws a named placeholder. None of those
 * belong on an output the congregation is looking at.
 */
package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.core.models.camera.asCameraSource

/**
 * [camera]'s live picture, filling the space, black whenever there is no frame.
 *
 * **Black is the whole failure story**: a device that is missing, one another application already
 * holds, and one unplugged mid-service all arrive here as a null frame and all draw black. The
 * shared cache clears its frame on release, so an unplugged camera cannot leave its last picture
 * frozen on the output — which, on a projector in front of a congregation, is the failure that
 * actually matters.
 *
 * There is deliberately no `showDiagnostics` parameter. The Canvas editor's copy of this draws a
 * camera's failure in red so the operator can see it; a parameter here would only ever be one
 * defaulted the wrong way on an output that must never carry text of its own.
 */
@Composable
fun CameraBackground(camera: CameraDeviceRef, modifier: Modifier = Modifier) {
    // Acquire and release must use the same source object, and be keyed on the same four fields the
    // cache hashes: `release` is keyed by what it is handed, so a format changing between the two
    // releases a key that was never acquired and leaves the real capture running for ever.
    val source = remember(camera) { camera.asCameraSource() }
    val flows = remember(
        camera.devicePath, camera.videoFormat, camera.videoConnection, camera.deckLinkIndex
    ) { SharedCameraFrameCache.acquire(source) }
    DisposableEffect(
        camera.devicePath, camera.videoFormat, camera.videoConnection, camera.deckLinkIndex
    ) { onDispose { SharedCameraFrameCache.release(source) } }

    val frame by flows.frame.collectAsState()
    val current = frame
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            // Crop, matching every other background type: a background fills the output.
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Box(modifier.fillMaxSize().background(Color.Black))
    }
}

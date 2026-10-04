package org.churchpresenter.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.canvas_placeholder_camera
import org.churchpresenter.strings.generated.resources.background_camera_option
import org.churchpresenter.strings.generated.resources.canvas_placeholder_ndi
import org.churchpresenter.strings.generated.resources.canvas_placeholder_ndi_default
import org.churchpresenter.strings.generated.resources.canvas_placeholder_ndi_waiting
import org.churchpresenter.strings.generated.resources.canvas_placeholder_omt
import org.churchpresenter.strings.generated.resources.canvas_placeholder_omt_default
import org.churchpresenter.strings.generated.resources.canvas_placeholder_screen_capture
import org.churchpresenter.core.models.scene.SceneSource

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource


@Composable
internal fun CameraSourceContent(
    source: SceneSource.CameraSource,
    modifier: Modifier,
    showDiagnostics: Boolean,
) {
    if (source.devicePath.isBlank()) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (source.deviceName.isNotEmpty()) {
                    stringResource(Res.string.canvas_placeholder_camera, source.deviceName)
                } else {
                    stringResource(Res.string.background_camera_option)
                },
                color = Color.White,
                fontSize = 14.sp
            )
        }
        return
    }

    // Use shared cache so canvas preview and presenter output share one capture process.
    //
    // Acquired in the effect rather than in `remember`: a composition that is abandoned before its
    // effects run still discards what `remember` produced, and it does so without calling any
    // `onDispose`. Acquiring there leaked the refcount — and with it the ffmpeg process holding the
    // device open, which the *next* acquire then found busy. That is the reported
    // "Error opening input: Input/output error" on a camera nothing else is using.
    var cameraFlows by remember { mutableStateOf<SharedCameraFrameCache.CameraFlows?>(null) }
    DisposableEffect(source.devicePath, source.videoFormat, source.videoConnection, source.deckLinkIndex) {
        cameraFlows = SharedCameraFrameCache.acquire(source)
        onDispose {
            cameraFlows = null
            SharedCameraFrameCache.release(source)
        }
    }

    // Stand-ins for the one composition pass before the effect has acquired: collecting needs a
    // flow, and a conditional `collectAsState` would move with the acquire.
    val noFrame = remember { MutableStateFlow<ImageBitmap?>(null) }
    val noFailure = remember { MutableStateFlow<CameraFailure?>(null) }
    val frame by (cameraFlows?.frame ?: noFrame).collectAsState()
    val error by (cameraFlows?.error ?: noFailure).collectAsState()

    if (frame != null) {
        Image(
            bitmap = frame!!,
            contentDescription = source.deviceName,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            val shownError = error?.takeIf { showDiagnostics }
            Text(
                text = shownError?.let { stringResource(cameraFailureStringRes(it)) }
                    ?: if (source.deviceName.isNotEmpty()) {
                        stringResource(Res.string.canvas_placeholder_camera, source.deviceName)
                    } else {
                        stringResource(Res.string.background_camera_option)
                    },
                color = if (shownError != null) Color(SOURCE_ERROR_TEXT_COLOR) else Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * A live NDI source from the network.
 *
 * Drawn with [ContentScale.Fit] rather than the camera's `Crop`: an NDI source is as often a
 * graphics or slide feed from another machine as it is a camera, and cropping one of those loses
 * content the operator put there on purpose. The layer's own box is how they choose the framing.
 */
@Composable
internal fun NdiSourceContent(source: SceneSource.NdiSource, modifier: Modifier) {
    if (source.sourceName.isBlank() && source.sourceAddress.isBlank()) {
        NetworkSourcePlaceholder(stringResource(Res.string.canvas_placeholder_ndi_default), modifier)
        return
    }

    // Acquired in the effect, for the reason spelled out in [CameraSourceContent].
    var flows by remember { mutableStateOf<ReceivedFrameCache.Flows?>(null) }
    DisposableEffect(source.sourceName, source.sourceAddress, source.lowBandwidth) {
        flows = SharedNdiFrameCache.acquire(source)
        onDispose {
            flows = null
            SharedNdiFrameCache.release(source)
        }
    }

    val noFrame = remember { MutableStateFlow<ImageBitmap?>(null) }
    val notConnected = remember { MutableStateFlow(false) }
    val frame by (flows?.frame ?: noFrame).collectAsState()
    val connected by (flows?.connected ?: notConnected).collectAsState()
    val label = source.sourceName.ifBlank { source.sourceAddress }

    val shown = frame
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize()
        )
    } else {
        // Connected but with nothing on the wire yet is "waiting"; not connected is a runtime that
        // is not installed or a source that has gone away, and the two read differently on purpose.
        NetworkSourcePlaceholder(
            text = if (connected) stringResource(Res.string.canvas_placeholder_ndi_waiting, label)
                   else stringResource(Res.string.canvas_placeholder_ndi, label),
            modifier = modifier,
        )
    }
}

/**
 * A live OMT source from the network — [NdiSourceContent]'s twin, fitted rather than cropped for the
 * reason that one gives, and drawing the same placeholders.
 */
@Composable
internal fun OmtSourceContent(source: SceneSource.OmtSource, modifier: Modifier) {
    if (source.sourceAddress.isBlank()) {
        NetworkSourcePlaceholder(stringResource(Res.string.canvas_placeholder_omt_default), modifier)
        return
    }

    // Acquired in the effect, for the reason spelled out in [CameraSourceContent].
    var flows by remember { mutableStateOf<ReceivedFrameCache.Flows?>(null) }
    DisposableEffect(source.sourceAddress, source.preview) {
        flows = SharedOmtFrameCache.acquire(source)
        onDispose {
            flows = null
            SharedOmtFrameCache.release(source)
        }
    }

    val noFrame = remember { MutableStateFlow<ImageBitmap?>(null) }
    val notConnected = remember { MutableStateFlow(false) }
    val frame by (flows?.frame ?: noFrame).collectAsState()
    val connected by (flows?.connected ?: notConnected).collectAsState()

    val shown = frame
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = source.sourceAddress,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize()
        )
    } else {
        NetworkSourcePlaceholder(
            text = if (connected) stringResource(Res.string.canvas_placeholder_ndi_waiting, source.sourceAddress)
                   else stringResource(Res.string.canvas_placeholder_omt, source.sourceAddress),
            modifier = modifier,
        )
    }
}

@Composable
private fun NetworkSourcePlaceholder(text: String, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ScreenCaptureSourceContent(source: SceneSource.ScreenCaptureSource, modifier: Modifier) {
    // Through the shared cache: this composable is mounted once in the canvas editor, once in each
    // sidebar live preview and once on each presenter output, and each instance used to run its own
    // `Robot.createScreenCapture` loop over the same pixels at up to 30fps.
    var frames by remember { mutableStateOf<StateFlow<ImageBitmap?>?>(null) }
    DisposableEffect(ScreenCaptureSpec.of(source)) {
        frames = SharedScreenCaptureCache.acquire(source)
        onDispose {
            frames = null
            SharedScreenCaptureCache.release(source)
        }
    }
    val noFrame = remember { MutableStateFlow<ImageBitmap?>(null) }
    val frame by (frames ?: noFrame).collectAsState()

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val currentFrame = frame
        if (currentFrame != null) {
            Image(
                painter = BitmapPainter(currentFrame),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(stringResource(Res.string.canvas_placeholder_screen_capture), color = Color.White, fontSize = 14.sp)
        }
    }
}

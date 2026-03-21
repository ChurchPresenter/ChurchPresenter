package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel

/**
 * Renders Compose content to a DeckLink device via an offscreen Window.
 *
 * Uses GraphicsLayer to capture rendered pixels from the Compose draw pipeline,
 * then pushes them to the DeckLink device via DisplayVideoFrameSync.
 * Each instance drives one DeckLink device independently.
 */
@Composable
fun DeckLinkComposeOutput(
    deviceIndex: Int,
    outputRole: String,
    appSettings: AppSettings,
    mediaViewModel: MediaViewModel,
    isLowerThird: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val isKeyRole = outputRole == Constants.OUTPUT_ROLE_KEY

    var outputWidth by remember { mutableStateOf(0) }
    var outputHeight by remember { mutableStateOf(0) }
    var deviceReady by remember { mutableStateOf(false) }

    DisposableEffect(deviceIndex) {
        if (!DeckLinkManager.isAvailable()) return@DisposableEffect onDispose {}
        val opened = DeckLinkManager.open(deviceIndex)
        if (!opened) return@DisposableEffect onDispose {}

        val info = DeckLinkManager.getOutputInfo(deviceIndex)
        outputWidth = info?.width ?: 1920
        outputHeight = info?.height ?: 1080
        System.err.println("[DeckLink] Device $deviceIndex: ${outputWidth}x${outputHeight} @ ${info?.fps} fps")
        deviceReady = true

        onDispose {
            deviceReady = false
            DeckLinkManager.close(deviceIndex)
        }
    }

    if (!deviceReady || outputWidth <= 0 || outputHeight <= 0) return

    val w = outputWidth
    val h = outputHeight

    // Position the window just outside the total virtual screen bounds.
    // We find the leftmost edge across ALL monitors and place it one pixel further left.
    // Windows DWM still renders windows near the visible area but not at extreme offsets.
    val windowState = remember(w, h) {
        val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        val virtualBounds = ge.screenDevices.fold(java.awt.Rectangle()) { acc, sd ->
            acc.union(sd.defaultConfiguration.bounds)
        }
        // Place just outside the leftmost edge of all monitors
        WindowState(
            position = WindowPosition((virtualBounds.x - w).dp, virtualBounds.y.dp),
            size = DpSize(w.dp, h.dp)
        )
    }

    Window(
        visible = true,
        onCloseRequest = {},
        state = windowState,
        undecorated = true,
        resizable = false,
        alwaysOnTop = false,
        focusable = false,
        title = "DeckLink Output $deviceIndex"
    ) {
        val graphicsLayer = rememberGraphicsLayer()

        // Capture loop: toImageBitmap() reads the recorded GraphicsLayer content.
        // sendFrame (DisplayVideoFrameSync) blocks until the frame is displayed,
        // providing natural pacing at the device's configured frame rate.
        LaunchedEffect(graphicsLayer, w, h) {
            val pixels = IntArray(w * h)

            while (isActive) {
                try {
                    val bitmap = graphicsLayer.toImageBitmap()
                    bitmap.readPixels(pixels)

                    if (isKeyRole) {
                        convertToKeySignal(pixels)
                    }

                    withContext(Dispatchers.IO) {
                        DeckLinkManager.sendFrame(deviceIndex, pixels, w, h)
                    }
                } catch (_: Throwable) {
                    // GraphicsLayer not ready yet on first frames
                    kotlinx.coroutines.delay(16)
                }
            }
        }

        CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
            PresenterScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        graphicsLayer.record(
                            size = IntSize(size.width.toInt(), size.height.toInt())
                        ) {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    },
                appSettings = appSettings,
                outputRole = outputRole,
                isLowerThird = isLowerThird
            ) {
                content()
            }
        }
    }
}

/**
 * Converts pixels to a key signal: white with original alpha.
 */
private fun convertToKeySignal(pixels: IntArray) {
    for (i in pixels.indices) {
        val a = (pixels[i] shr 24) and 0xFF
        pixels[i] = (a shl 24) or 0x00FFFFFF
    }
}

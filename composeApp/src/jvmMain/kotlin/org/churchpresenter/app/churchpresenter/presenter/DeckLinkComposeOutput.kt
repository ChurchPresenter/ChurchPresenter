package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_app_icon
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Renders Compose content to a DeckLink device using ComposePanel + SkiaLayer.screenshot().
 *
 * Uses ComposePanel in an offscreen JFrame for rendering, then captures frames
 * via SkiaLayer.screenshot() which safely reads the Skia backing surface
 * without the race conditions that affect GraphicsLayer.toImageBitmap().
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
    val currentAppSettings by rememberUpdatedState(appSettings)
    val currentOutputRole by rememberUpdatedState(outputRole)
    val currentIsLowerThird by rememberUpdatedState(isLowerThird)

    // Render the same vector icon used by all Compose Windows to a BufferedImage for the JFrame
    val iconPainter = painterResource(Res.drawable.ic_app_icon)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val appIconImage = remember(iconPainter) {
        try {
            val size = 32
            val sizeF = Size(size.toFloat(), size.toFloat())
            val bitmap = ImageBitmap(size, size)
            val canvas = Canvas(bitmap)
            CanvasDrawScope().draw(density, layoutDirection, canvas, sizeF) {
                with(iconPainter) { draw(sizeF) }
            }
            bitmap.toAwtImage()
        } catch (_: Exception) { null }
    }

    DisposableEffect(deviceIndex) {
        if (!DeckLinkManager.isAvailable()) return@DisposableEffect onDispose {}
        val opened = DeckLinkManager.open(deviceIndex)
        if (!opened) return@DisposableEffect onDispose {}

        val info = DeckLinkManager.getOutputInfo(deviceIndex)
        val w = info?.width ?: 1920
        val h = info?.height ?: 1080
        System.err.println("[DeckLink] Device $deviceIndex: ${w}x${h} @ ${info?.fps} fps, role=$outputRole")

        // Create offscreen JFrame with ComposePanel
        val jframe = JFrame("DeckLink Output $deviceIndex").apply {
            isUndecorated = true
            appIconImage?.let { iconImage = it }
            setSize(w, h)
            // Position just outside visible area — close enough for DWM to render
            val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
            val virtualBounds = ge.screenDevices.fold(java.awt.Rectangle()) { acc, sd ->
                acc.union(sd.defaultConfiguration.bounds)
            }
            setLocation(virtualBounds.x - w, virtualBounds.y)
        }

        val composePanel = ComposePanel().apply {
            preferredSize = Dimension(w, h)
            setSize(w, h)
        }

        // For DeckLink key output: render as FILL (black bg + normal content)
        // and convert to key signal at pixel level. The Compose keySignal() modifier
        // doesn't capture correctly via SkiaLayer.screenshot().
        val renderRole = if (outputRole == Constants.OUTPUT_ROLE_KEY) Constants.OUTPUT_ROLE_FILL else outputRole
        val isKeyCapture = outputRole == Constants.OUTPUT_ROLE_KEY

        composePanel.setContent {
            CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                PresenterScreen(
                    modifier = Modifier.fillMaxSize(),
                    appSettings = currentAppSettings,
                    outputRole = renderRole,
                    isLowerThird = currentIsLowerThird
                ) {
                    content()
                }
            }
        }

        jframe.contentPane.add(composePanel)
        jframe.isVisible = true

        // Find the SkiaLayer inside the ComposePanel
        fun findSkiaLayer(container: Container): SkiaLayer? {
            for (comp in container.components) {
                if (comp is SkiaLayer) return comp
                if (comp is Container) {
                    val found = findSkiaLayer(comp)
                    if (found != null) return found
                }
            }
            return null
        }

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val captureJob = scope.launch {
            // Wait for ComposePanel to initialize and create SkiaLayer
            Thread.sleep(1000)

            var skiaLayer: SkiaLayer? = null
            // Try to find SkiaLayer (may take a moment to be added)
            repeat(20) {
                skiaLayer = findSkiaLayer(composePanel)
                if (skiaLayer != null) return@repeat
                Thread.sleep(100)
            }

            if (skiaLayer == null) {
                System.err.println("[DeckLink] Device $deviceIndex: Could not find SkiaLayer")
                return@launch
            }
            System.err.println("[DeckLink] Device $deviceIndex: SkiaLayer found, starting capture, role=$outputRole")

            val pixels = IntArray(w * h)
            val byteBuf = ByteArray(w * h * 4)
            var framesSent = 0L

            while (isActive) {
                try {
                    // screenshot() safely reads the Skia backing surface
                    val bitmap = skiaLayer!!.screenshot()
                    if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                        // peekPixels gives direct access to the bitmap buffer
                        val pixmap = bitmap.peekPixels()
                        if (pixmap != null) {
                            val data = pixmap.buffer
                            val bytes = data.getBytes(0, (w * h * 4).coerceAtMost(data.size))
                            System.arraycopy(bytes, 0, byteBuf, 0, bytes.size)

                            // Convert BGRA/RGBA bytes to ARGB IntArray for the JNI bridge
                            // Skia on Windows uses BGRA_8888 natively
                            for (i in 0 until w * h) {
                                val off = i * 4
                                val b = byteBuf[off].toInt() and 0xFF
                                val g = byteBuf[off + 1].toInt() and 0xFF
                                val r = byteBuf[off + 2].toInt() and 0xFF
                                val a = byteBuf[off + 3].toInt() and 0xFF
                                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                            }

                            // For key output: convert rendered fill to key signal.
                            // Content on black bg → white where content is, black where it isn't.
                            if (isKeyCapture) {
                                convertToKeySignal(pixels)
                            }

                            framesSent++
                            DeckLinkManager.sendFrame(deviceIndex, pixels, w, h)
                        } else {
                            Thread.sleep(16)
                        }
                        bitmap.close()
                    } else {
                        bitmap?.close()
                        Thread.sleep(16)
                    }
                } catch (_: Throwable) {
                    Thread.sleep(16)
                }
            }
        }

        onDispose {
            captureJob.cancel()
            scope.cancel()
            // Send black frames to clear the output before closing.
            // Multiple frames + delay ensures the device displays them
            // before DisableVideoOutput is called.
            try {
                val blackPixels = IntArray(w * h)
                repeat(3) {
                    DeckLinkManager.sendFrame(deviceIndex, blackPixels, w, h)
                }
                Thread.sleep(100)
            } catch (_: Exception) {}
            DeckLinkManager.close(deviceIndex)
            SwingUtilities.invokeLater {
                jframe.isVisible = false
                jframe.dispose()
            }
        }
    }
}

/**
 * Converts a rendered fill frame to a key signal.
 * Takes the max of R,G,B (luminance) as the key value.
 * Content on black background → white where content is, black where it isn't.
 */
private fun convertToKeySignal(pixels: IntArray) {
    for (i in pixels.indices) {
        val r = (pixels[i] shr 16) and 0xFF
        val g = (pixels[i] shr 8) and 0xFF
        val b = pixels[i] and 0xFF
        val key = maxOf(r, g, b)
        pixels[i] = (0xFF shl 24) or (key shl 16) or (key shl 8) or key
    }
}

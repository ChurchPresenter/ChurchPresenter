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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val isKeyRole = outputRole == Constants.OUTPUT_ROLE_KEY
    val currentAppSettings by androidx.compose.runtime.rememberUpdatedState(appSettings)
    val currentOutputRole by androidx.compose.runtime.rememberUpdatedState(outputRole)
    val currentIsLowerThird by androidx.compose.runtime.rememberUpdatedState(isLowerThird)

    DisposableEffect(deviceIndex) {
        if (!DeckLinkManager.isAvailable()) return@DisposableEffect onDispose {}
        val opened = DeckLinkManager.open(deviceIndex)
        if (!opened) return@DisposableEffect onDispose {}

        val info = DeckLinkManager.getOutputInfo(deviceIndex)
        val w = info?.width ?: 1920
        val h = info?.height ?: 1080
        System.err.println("[DeckLink] Device $deviceIndex: ${w}x${h} @ ${info?.fps} fps")

        // Create offscreen JFrame with ComposePanel
        val jframe = JFrame("DeckLink Output $deviceIndex").apply {
            isUndecorated = true
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

        composePanel.setContent {
            CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                PresenterScreen(
                    modifier = Modifier.fillMaxSize(),
                    appSettings = currentAppSettings,
                    outputRole = currentOutputRole,
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
            System.err.println("[DeckLink] Device $deviceIndex: SkiaLayer found, starting capture")

            val pixels = IntArray(w * h)
            val byteBuf = ByteArray(w * h * 4)

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

                            if (isKeyRole) {
                                convertToKeySignal(pixels)
                            }

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
            DeckLinkManager.close(deviceIndex)
            SwingUtilities.invokeLater {
                jframe.isVisible = false
                jframe.dispose()
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

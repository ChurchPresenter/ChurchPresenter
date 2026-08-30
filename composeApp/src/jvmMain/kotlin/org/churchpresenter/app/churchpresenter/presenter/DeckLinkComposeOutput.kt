package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.skiko.SkiaLayer
import java.awt.Container
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities

private const val OPEN_RETRY_ATTEMPTS = 3
private const val OPEN_RETRY_DELAY_MS = 100L
private const val OPAQUE_ALPHA = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

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
    val currentIsLowerThird by rememberUpdatedState(isLowerThird)

    val appIconImage = rememberAppIconImage()

    DisposableEffect(deviceIndex) {
        if (!DeckLinkManager.isAvailable()) return@DisposableEffect onDispose {}
        val opened = DeckLinkManager.open(deviceIndex)
        if (!opened) return@DisposableEffect onDispose {}

        val info = DeckLinkManager.getOutputInfo(deviceIndex)
        val w = info?.width ?: 1920
        val h = info?.height ?: 1080
        System.err.println("[DeckLink] Device $deviceIndex: ${w}x${h} @ ${info?.fps} fps, role=$outputRole")

        val jframe = offscreenFrame(deviceIndex, w, h, appIconImage)

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

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val captureJob = scope.launch {
            val layer = awaitSkiaLayer(composePanel)
            if (layer == null) {
                System.err.println("[DeckLink] Device $deviceIndex: Could not find SkiaLayer")
                CrashReporter.reportWarning(
                    "DeckLink: Could not find SkiaLayer for device $deviceIndex output",
                    tags = mapOf("subsystem" to "decklink")
                )
                return@launch
            }
            System.err.println("[DeckLink] Device $deviceIndex: SkiaLayer found, starting capture, role=$outputRole")

            val pixels = IntArray(w * h)
            val byteBuf = ByteArray(w * h * 4)
            var framesSent = 0L

            while (isActive) {
                try {
                    // screenshot() safely reads the Skia backing surface
                    val bitmap = layer.screenshot()
                    if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                        // peekPixels gives direct access to the bitmap buffer
                        val pixmap = bitmap.peekPixels()
                        if (pixmap != null) {
                            val data = pixmap.buffer
                            val bytes = data.getBytes(0, (w * h * 4).coerceAtMost(data.size))
                            System.arraycopy(bytes, 0, byteBuf, 0, bytes.size)

                            skiaBgraToArgbPixels(byteBuf, pixels, w * h)

                            // For key output: convert rendered fill to key signal.
                            // Content on black bg → white where content is, black where it isn't.
                            if (isKeyCapture) {
                                convertToKeySignal(pixels)
                            }

                            framesSent++
                            DeckLinkManager.sendFrame(deviceIndex, pixels, w, h)
                        } else {
                            delay(16)
                        }
                        bitmap.close()
                    } else {
                        bitmap?.close()
                        delay(16)
                    }
                } catch (_: Throwable) {
                    delay(16)
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
                repeat(OPEN_RETRY_ATTEMPTS) {
                    DeckLinkManager.sendFrame(deviceIndex, blackPixels, w, h)
                }
                Thread.sleep(OPEN_RETRY_DELAY_MS)
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
internal fun convertToKeySignal(pixels: IntArray) {
    for (i in pixels.indices) {
        val r = (pixels[i] shr 16) and 0xFF
        val g = (pixels[i] shr 8) and 0xFF
        val b = pixels[i] and 0xFF
        val key = maxOf(r, g, b)
        pixels[i] = (OPAQUE_ALPHA shl ALPHA_SHIFT) or (key shl RED_SHIFT) or (key shl GREEN_SHIFT) or key
    }
}

/**
 * Converts Skia's native BGRA_8888 byte buffer into packed ARGB ints for the DeckLink JNI bridge.
 * [byteBuf] must hold at least `pixelCount * 4` bytes; result is written into [pixels].
 */
internal fun skiaBgraToArgbPixels(byteBuf: ByteArray, pixels: IntArray, pixelCount: Int) {
    for (i in 0 until pixelCount) {
        val off = i * 4
        val b = byteBuf[off].toInt() and 0xFF
        val g = byteBuf[off + 1].toInt() and 0xFF
        val r = byteBuf[off + 2].toInt() and 0xFF
        val a = byteBuf[off + 3].toInt() and 0xFF
        pixels[i] = (a shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
    }
}

/** The same vector icon every Compose Window uses, rendered for the off-screen `JFrame`'s title. */
@Composable
private fun rememberAppIconImage(): java.awt.image.BufferedImage? {
    val iconPainter = painterResource(Res.drawable.ic_app_icon)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return remember(iconPainter) {
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
}

/**
 * The frame the device's content is composed into, parked just off the left edge of the virtual
 * desktop — far enough not to be seen, close enough that DWM still renders it.
 */
private fun offscreenFrame(
    deviceIndex: Int,
    width: Int,
    height: Int,
    icon: java.awt.image.BufferedImage?,
): JFrame = JFrame("DeckLink Output $deviceIndex").apply {
    isUndecorated = true
    icon?.let { iconImage = it }
    setSize(width, height)
    val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
    val virtualBounds = ge.screenDevices.fold(java.awt.Rectangle()) { acc, sd ->
        acc.union(sd.defaultConfiguration.bounds)
    }
    setLocation(virtualBounds.x - width, virtualBounds.y)
}

/** The `SkiaLayer` a `ComposePanel` builds itself around, wherever in its tree it ended up. */
private fun findSkiaLayer(container: Container): SkiaLayer? {
    for (comp in container.components) {
        if (comp is SkiaLayer) return comp
        if (comp is Container) {
            val found = findSkiaLayer(comp)
            if (found != null) return found
        }
    }
    return null
}

/** How long a `ComposePanel` is given to come up before its layer is first looked for. */
private const val SKIA_LAYER_WARMUP_MS = 1000L

/** How many times, and how far apart, that look is repeated before giving up. */
private const val SKIA_LAYER_ATTEMPTS = 20
private const val SKIA_LAYER_RETRY_MS = 100L

/**
 * The panel's [SkiaLayer] once it exists — a `ComposePanel` builds it asynchronously, so the first
 * look after `isVisible` finds nothing. Null if it never appears.
 */
private suspend fun awaitSkiaLayer(panel: Container): SkiaLayer? {
    delay(SKIA_LAYER_WARMUP_MS)
    repeat(SKIA_LAYER_ATTEMPTS) {
        findSkiaLayer(panel)?.let { return it }
        delay(SKIA_LAYER_RETRY_MS)
    }
    return findSkiaLayer(panel)
}

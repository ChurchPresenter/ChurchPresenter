package org.churchpresenter.liveoutput

import org.churchpresenter.presenter.MergedTile
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import org.churchpresenter.settings.ResolvedMerge
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
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.diagnostics.Log
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.strings.generated.resources.decklink_output_title
import org.churchpresenter.icons.generated.resources.ic_app_icon
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberUpdatedState
import org.churchpresenter.canvas.DeckLinkManager
import org.churchpresenter.canvas.DeckLinkOutputSessions
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.skiko.SkiaLayer
import java.awt.Container
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities

private const val OPAQUE_ALPHA = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/** How long the capture loop waits before trying again when the layer has no picture to read. */
private const val FRAME_RETRY_MS = 16L

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
    /** The merged picture this device shows a tile of, and its output key in it -- see [mergeTile]. */
    merge: ResolvedMerge? = null,
    mergeOutput: String = "",
    /** Where the device is opened and closed; the app's own [DeckLinkManager.outputs] unless a test gives a fake. */
    outputs: DeckLinkOutputSessions = DeckLinkManager.outputs,
    content: @Composable BoxScope.() -> Unit
) {
    val currentAppSettings by rememberUpdatedState(appSettings)
    val currentIsLowerThird by rememberUpdatedState(isLowerThird)
    val currentMerge by rememberUpdatedState(merge)

    val appIconImage = rememberAppIconImage()
    val frameTitle = stringResource(Res.string.decklink_output_title, deviceIndex)

    // For DeckLink key output: render as FILL (black bg + normal content)
    // and convert to key signal at pixel level. The Compose keySignal() modifier
    // doesn't capture correctly via SkiaLayer.screenshot().
    val renderRole = if (outputRole == Constants.OUTPUT_ROLE_KEY) Constants.OUTPUT_ROLE_FILL else outputRole
    val isKeyCapture = outputRole == Constants.OUTPUT_ROLE_KEY

    DisposableEffect(deviceIndex, outputs) {
        // Opened, and later closed, on the DeckLink output thread: the card is never waited on here.
        val session = outputs.open(deviceIndex)
        // Read and written on the event thread only: built there once the device is open.
        var jframe: JFrame? = null

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val captureJob = scope.launch {
            val info = session.awaitOpened() ?: return@launch
            val w = info.width
            val h = info.height
            Log.info("DeckLink", "Device $deviceIndex: ${w}x$h @ ${info.fps} fps, role=$outputRole")

            // Not run once the effect is disposed: the dispose cancels this job on the same thread.
            val composePanel = withContext(Dispatchers.Main) {
                val panel = deckLinkPanel(w, h) {
                    CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                        // The background too is part of the picture, so the whole screen is the tile.
                        MergedTile(currentMerge, mergeOutput) {
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
                }
                jframe = offscreenFrame(frameTitle, w, h, appIconImage).apply {
                    contentPane.add(panel)
                    isVisible = true
                }
                panel
            }

            val layer = awaitSkiaLayer(composePanel)
            if (layer == null) {
                Log.warn("DeckLink", "Device $deviceIndex: Could not find SkiaLayer")
                CrashReporter.reportWarning(
                    "DeckLink: Could not find SkiaLayer for device $deviceIndex output",
                    tags = mapOf("subsystem" to "decklink")
                )
                return@launch
            }
            Log.info("DeckLink", "Device $deviceIndex: SkiaLayer found, starting capture, role=$outputRole")
            captureFrames(layer, w, h, isKeyCapture) { pixels -> session.sendFrame(pixels, w, h) }
        }

        onDispose {
            captureJob.cancel()
            // The close waits for the frame in flight, sends black and closes the device, all on the
            // output thread; the frame goes once the device has closed.
            session.close(captureJob) {
                SwingUtilities.invokeLater {
                    jframe?.isVisible = false
                    jframe?.dispose()
                    scope.cancel()
                }
            }
        }
    }
}

/** The panel the device's content is composed in, at the device's own size. */
private fun deckLinkPanel(width: Int, height: Int, content: @Composable () -> Unit) = ComposePanel().apply {
    preferredSize = Dimension(width, height)
    setSize(width, height)
    setContent(content)
}

/**
 * Reads [layer]'s picture into packed ARGB -- converted to a key signal when [isKeyCapture] -- and
 * hands each frame to [send], until the job is cancelled.
 */
private suspend fun captureFrames(
    layer: SkiaLayer,
    w: Int,
    h: Int,
    isKeyCapture: Boolean,
    send: (IntArray) -> Unit,
) = coroutineScope {
    val pixels = IntArray(w * h)
    val byteBuf = ByteArray(w * h * 4)
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

                    send(pixels)
                } else {
                    delay(FRAME_RETRY_MS)
                }
                bitmap.close()
            } else {
                bitmap?.close()
                delay(FRAME_RETRY_MS)
            }
        } catch (_: Throwable) {
            delay(FRAME_RETRY_MS)
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
    val iconPainter = painterResource(IconRes.drawable.ic_app_icon)
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
    title: String,
    width: Int,
    height: Int,
    icon: java.awt.image.BufferedImage?,
): JFrame = JFrame(title).apply {
    isUndecorated = true
    icon?.let { iconImage = it }
    setSize(width, height)
    val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
    val virtualBounds = ge.screenDevices.fold(java.awt.Rectangle()) { acc, sd ->
        acc.union(sd.defaultConfiguration.bounds)
    }
    setLocation(virtualBounds.x - width, virtualBounds.y)
}

/**
 * The `SkiaLayer` a `ComposePanel` or a Compose window builds itself around, wherever in its tree it
 * ended up -- for reading the pixels it last drew, as DeckLink and the output guard's stills do.
 */
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

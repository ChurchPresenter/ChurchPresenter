package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skiko.SkiaLayer
import java.awt.Container
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Renders a Lottie animation to ARGB pixel frames off-screen.
 *
 * Uses the same ComposePanel + SkiaLayer.screenshot() technique as DeckLinkComposeOutput
 * to capture Compose-rendered frames without requiring a visible window.
 *
 * Usage:
 *   val renderer = LowerThirdOffscreenRenderer(1920, 1080)
 *   val frame = renderer.renderStill(lottieJson, progress = 0.5f)   // single frame
 *   val frames = renderer.renderClip(lottieJson, durationMs = 3000L, fps = 30) { p -> }
 *
 * Returned pixels are ARGB IntArray: each int = (A shl 24) or (R shl 16) or (G shl 8) or B.
 * This matches the format that DeckLinkComposeOutput produces and that AtemClient.argbToBytes expects.
 */
class LowerThirdOffscreenRenderer(
    private val width: Int,
    private val height: Int
) {
    /**
     * Renders the Lottie animation at a single progress value (0f..1f).
     *
     * @param lottieJson  raw JSON string of the Lottie animation
     * @param progress    animation progress 0f (start) to 1f (end); default 0.5f (midpoint)
     * @return            ARGB IntArray of size width*height
     */
    suspend fun renderStill(lottieJson: String, progress: Float = 0.5f): IntArray =
        withSession(lottieJson) { renderFrame -> renderFrame(progress).copyOf() }

    /**
     * Opens an off-screen render session and runs [block] with a frame-render function.
     *
     * The render function returns an INTERNAL BUFFER that is overwritten by the next
     * call — consume each frame (convert/upload) before requesting the next one and do
     * not retain references. This keeps memory flat regardless of clip length: a single
     * 1080p frame is ~8 MB, so buffering a whole clip of frames would exhaust the heap.
     */
    suspend fun <T> withSession(
        lottieJson: String,
        block: suspend (renderFrame: suspend (Float) -> IntArray) -> T
    ): T = withContext(Dispatchers.Default) {
        // Must NOT run on Dispatchers.Main: invokeAndWait throws java.lang.Error when
        // called from the EDT. Capture runs on Default like DeckLinkComposeOutput.
        var currentProgress by mutableStateOf(0f)

        // Create the off-screen JFrame and ComposePanel on the EDT
        var jframe: JFrame? = null
        var composePanel: ComposePanel? = null

        SwingUtilities.invokeAndWait {
            jframe = JFrame("ATEM Renderer").apply {
                isUndecorated = true
                setSize(width, height)
                // Position outside visible screen area
                val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                val virtualBounds = ge.screenDevices.fold(java.awt.Rectangle()) { acc, sd ->
                    acc.union(sd.defaultConfiguration.bounds)
                }
                setLocation(virtualBounds.x - width - 10, virtualBounds.y)
            }
            composePanel = ComposePanel().apply {
                preferredSize = Dimension(width, height)
                setSize(width, height)
            }
            jframe!!.contentPane.add(composePanel!!)
            jframe!!.isVisible = true
        }

        val panel = composePanel ?: throw Exception("Off-screen renderer failed to initialize (no ComposePanel)")
        val frame = jframe ?: throw Exception("Off-screen renderer failed to initialize (no window)")

        try {
            // Set Compose content on the EDT
            SwingUtilities.invokeAndWait {
                panel.setContent {
                    val composition by rememberLottieComposition {
                        LottieCompositionSpec.JsonString(lottieJson.ifBlank { "{}" })
                    }
                    Image(
                        painter = rememberLottiePainter(
                            composition = composition,
                            progress = { currentProgress }
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Wait for ComposePanel to initialize and SkiaLayer to appear
            delay(1500)
            var skiaLayer: SkiaLayer? = findSkiaLayer(panel)
            var attempts = 0
            while (skiaLayer == null && attempts < 30) {
                delay(100)
                skiaLayer = findSkiaLayer(panel)
                attempts++
            }
            val layer = skiaLayer
                ?: throw Exception("Off-screen renderer failed to initialize (no SkiaLayer)")

            // Buffers reused for every frame — see the contract in the kdoc above
            val byteBuf = ByteArray(width * height * 4)
            val intBuf = IntArray(width * height)
            block { progress ->
                currentProgress = progress
                // Allow Compose to re-render at the new progress value
                delay(150)
                captureFrame(layer, byteBuf, intBuf)
            }
        } finally {
            SwingUtilities.invokeLater {
                frame.isVisible = false
                frame.dispose()
            }
        }
    }

    private fun captureFrame(layer: SkiaLayer, byteBuf: ByteArray, pixels: IntArray): IntArray {
        return try {
            val bitmap = layer.screenshot() ?: return pixels
            val pixmap = bitmap.peekPixels()
            if (pixmap != null) {
                val data = pixmap.buffer
                val byteCount = (width * height * 4).coerceAtMost(data.size)
                val bytes = data.getBytes(0, byteCount)
                System.arraycopy(bytes, 0, byteBuf, 0, bytes.size)

                // Skia on Windows uses BGRA_8888 natively.
                // Convert BGRA → ARGB so pixels match DeckLinkComposeOutput output format.
                for (i in 0 until width * height) {
                    val off = i * 4
                    val b = byteBuf[off].toInt() and 0xFF
                    val g = byteBuf[off + 1].toInt() and 0xFF
                    val r = byteBuf[off + 2].toInt() and 0xFF
                    val a = byteBuf[off + 3].toInt() and 0xFF
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.close()
            pixels
        } catch (_: Throwable) {
            pixels
        }
    }

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
}

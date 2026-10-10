package org.churchpresenter.lottiegen.render

import org.churchpresenter.lottiegen.lottie.LottieTextShaping
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * One still of a Lottie, rendered off screen, and the arithmetic for cutting the drawn part out of
 * it.
 *
 * Shared by `DumpStyleReview` (the review PNGs) and the style menu's thumbnails: both need a
 * settled frame of a lower third that occupies a corner of a mostly transparent canvas, and both
 * would be useless without cropping to what was drawn.
 */
object StillFrame {

    private const val COMPOSITION_LOAD_TIMEOUT_MS = 10_000L
    private const val FRAME_INTERVAL_MS = 16L
    private const val FRAME_NANOS = 16_666_667L

    /** Where ARGB packs the alpha channel: `(argb ushr ALPHA_SHIFT_BITS) and ALPHA_MASK`. */
    private const val ALPHA_SHIFT_BITS = 24
    private const val ALPHA_MASK = 0xFF

    private const val WHITE_ARGB = 0xFFFFFFFF.toInt()

    data class Bounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int)

    data class CropRegion(val x0: Int, val y0: Int, val width: Int, val height: Int)

    /**
     * [lottieJson] drawn at [progress] (0..1) into a [width] x [height] ARGB buffer.
     *
     * The same windowless-scene recipe as `LowerThirdOffscreenRenderer` in the main app: an
     * `ImageComposeScene` pumped until the asynchronous Lottie parse completes, then one render.
     * The Lottie is fitted to the buffer, so a buffer half the canvas's size is a half-scale still.
     *
     * **On the event queue**, like every off-screen scene in the app: a scene composed on another
     * thread registers its snapshot observer beside the on-screen window's, and the two can take
     * each other's locks in opposite orders and deadlock (see `ComposeScenePump`, #498). The wait for
     * the parse suspends rather than blocks, so the queue is only held for each short render.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    suspend fun render(
        lottieJson: String,
        width: Int,
        height: Int,
        progress: Float,
        loadTimeoutMs: Long = COMPOSITION_LOAD_TIMEOUT_MS,
    ): IntArray =
        withContext(Dispatchers.Swing) {
            var compositionLoaded by mutableStateOf(false)
            val grouped = LottieTextShaping.groupsText(lottieJson)

            val scene = ImageComposeScene(width, height, Density(1f)) {
                val composition by rememberLottieComposition {
                    LottieCompositionSpec.JsonString(lottieJson.ifBlank { "{}" })
                }
                val loaded = composition != null
                SideEffect { if (loaded) compositionLoaded = true }
                Image(
                    painter = rememberShapedLottiePainter(composition, { progress }, grouped),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            try {
                var timeNanos = 0L
                val deadline = System.currentTimeMillis() + loadTimeoutMs
                while (!compositionLoaded && System.currentTimeMillis() < deadline) {
                    timeNanos += FRAME_NANOS
                    scene.render(timeNanos).close()
                    delay(FRAME_INTERVAL_MS)
                }
                check(compositionLoaded) { "Lottie composition failed to load for off-screen rendering" }

                timeNanos += FRAME_NANOS
                val img = scene.render(timeNanos)
                val pixels = IntArray(width * height)
                try {
                    img.toComposeImageBitmap().readPixels(pixels)
                } finally {
                    img.close()
                }
                pixels
            } finally {
                scene.close()
            }
        }

    /** One frame's non-transparent bounds, or null when the frame is fully transparent. */
    fun frameBounds(pixels: IntArray, width: Int, height: Int): Bounds? {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val rowBase = y * width
            for (x in 0 until width) {
                if ((pixels[rowBase + x] ushr ALPHA_SHIFT_BITS) and ALPHA_MASK == 0) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return if (maxX < minX || maxY < minY) null else Bounds(minX, minY, maxX, maxY)
    }

    /**
     * The union of every frame's non-transparent bounds, padded by [padding] — deliberately NOT
     * clamped to the canvas. A left- or right-aligned badge sits flush against its margin, i.e. near
     * the canvas edge, so clamping made its crop touch the cell edge with no framing margin on that
     * side while a center-aligned badge kept its full margin. [cropRegion] fills whatever falls
     * outside the real canvas, so every crop gets the same visual frame regardless of alignment.
     */
    fun contentCropRegion(frames: List<IntArray>, width: Int, height: Int, padding: Int): CropRegion {
        val bounds = frames.mapNotNull { frameBounds(it, width, height) }
        if (bounds.isEmpty()) return CropRegion(0, 0, width, height)
        val x0 = bounds.minOf { it.minX } - padding
        val y0 = bounds.minOf { it.minY } - padding
        val x1 = bounds.maxOf { it.maxX } + padding
        val y1 = bounds.maxOf { it.maxY } + padding
        return CropRegion(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    /** Expands/repositions [base] to [targetW] x [targetH], centered on its own center. */
    fun centeredRegion(base: CropRegion, targetW: Int, targetH: Int): CropRegion {
        val centerX = base.x0 + base.width / 2
        val centerY = base.y0 + base.height / 2
        return CropRegion(centerX - targetW / 2, centerY - targetH / 2, targetW, targetH)
    }

    /** Copies [region] out of [pixels], filling anything outside the real canvas with [fill]. */
    fun cropRegion(
        pixels: IntArray,
        fullWidth: Int,
        fullHeight: Int,
        region: CropRegion,
        fill: Int = WHITE_ARGB,
    ): IntArray {
        val out = IntArray(region.width * region.height) { fill }
        val srcXStart = region.x0.coerceAtLeast(0)
        val srcXEnd = (region.x0 + region.width).coerceAtMost(fullWidth)
        val srcYStart = region.y0.coerceAtLeast(0)
        val srcYEnd = (region.y0 + region.height).coerceAtMost(fullHeight)
        if (srcXStart >= srcXEnd || srcYStart >= srcYEnd) return out
        val copyWidth = srcXEnd - srcXStart
        val destXOffset = srcXStart - region.x0
        for (srcY in srcYStart until srcYEnd) {
            val destY = srcY - region.y0
            System.arraycopy(pixels, srcY * fullWidth + srcXStart, out, destY * region.width + destXOffset, copyWidth)
        }
        return out
    }
}

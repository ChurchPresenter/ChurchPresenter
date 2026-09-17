package org.churchpresenter.lottiegen.tools

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
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.model.StyleCatalog
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders a before (Detail off, classic two-line) and after (Detail on, three-line) still of one
 * or more styles, across all three alignments, into a single reviewable PNG grid per style — the
 * same headless render-to-PNG technique that caught Style 14's overflowing decoration, now
 * reusable for rolling the Detail line out across the rest of the catalog batch by batch.
 *
 * Usage: `./gradlew :lottieGenerator:dumpStyleReview -Pstyles=14,15,16 [-Pout=/dir]`
 *        `./gradlew :lottieGenerator:dumpStyleReview -Pstyles=all`
 */
object DumpStyleReview {

    private const val CANVAS_W = 1920
    private const val CANVAS_H = 1080
    private const val HOLD_PROGRESS = 0.6f
    private const val COMPOSITION_LOAD_TIMEOUT_MS = 10_000L
    private const val FRAME_INTERVAL_MS = 16L
    private const val FRAME_NANOS = 16_666_667L
    private const val SAMPLE_DETAIL_TEXT = "First Baptist Church"
    private const val DEFAULT_OUT_DIR = "build/style-review"
    private val ALIGNS = listOf("left", "center", "right")

    /**
     * A lower third occupies a small corner of the full 1920x1080 canvas — stacking full canvases
     * left a real fix (a few dozen px of padding) invisible once the image was viewed at any
     * reasonable size. Cropping to content is what makes a batch actually reviewable.
     */
    private const val CROP_PADDING_PX = 40

    /** Gap between grid cells, and between the grid and the image edge. */
    private const val CELL_GAP_PX = 24

    /** Where ARGB packs the alpha channel: `(argb ushr ALPHA_SHIFT_BITS) and ALPHA_MASK`. */
    private const val ALPHA_SHIFT_BITS = 24
    private const val ALPHA_MASK = 0xFF

    private const val WHITE_ARGB = 0xFFFFFFFF.toInt()

    internal data class CropRegion(val x0: Int, val y0: Int, val width: Int, val height: Int)

    @JvmStatic
    fun main(args: Array<String>) {
        val stylesArg = args.getOrNull(0)
        if (stylesArg.isNullOrBlank()) {
            System.err.println("usage: dumpStyleReview -Pstyles=<id,id,...|all> [-Pout=/dir]")
            return
        }
        val outDir = File(args.getOrNull(1) ?: DEFAULT_OUT_DIR)
        outDir.mkdirs()

        val entries = if (stylesArg == "all") {
            StyleCatalog.entries
        } else {
            val requested = stylesArg.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            requested.mapNotNull { id ->
                StyleCatalog.entries.firstOrNull { it.id == id }
                    ?: run { System.err.println("Skipping unknown style id '$id'"); null }
            }
        }

        if (entries.isEmpty()) {
            System.err.println("No known styles requested from '$stylesArg'")
            return
        }

        runBlocking {
            for (entry in entries) {
                val outFile = File(outDir, "style${entry.id}_${safeLabel(entry.label)}.png")
                ImageIO.write(renderReviewGrid(entry.id), "png", outFile)
                println("Wrote ${outFile.path}")
            }
        }
    }

    internal fun safeLabel(label: String) = label.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')

    /**
     * Renders before/after x left/center/right (six stills) for [styleId]. Left- and
     * right-aligned badges sit in opposite corners of the canvas, so each alignment gets its own
     * crop region (shared between its before/after pair so that row lines up) rather than one
     * region shared across all three — a union of all three would span nearly the full canvas
     * and defeat the point of cropping. Every region is then padded to the same cell size so the
     * three columns line up into a clean 3 (align) x 2 (before/after) grid.
     */
    private suspend fun renderReviewGrid(styleId: String): BufferedImage {
        val base = LottieGenConfig(canvasW = CANVAS_W, canvasH = CANVAS_H, style = styleId)

        val beforePixels = ALIGNS.map { align ->
            renderStill(toJsonString(LottieGenerator.generate(base.copy(align = align))))
        }
        val afterPixels = ALIGNS.map { align ->
            renderStill(
                toJsonString(
                    LottieGenerator.generate(
                        base.copy(align = align, hideDetail = false, detailText = SAMPLE_DETAIL_TEXT)
                    )
                )
            )
        }

        val perAlignRegions = ALIGNS.indices.map { i ->
            contentCropRegion(listOf(beforePixels[i], afterPixels[i]), CANVAS_W, CANVAS_H)
        }
        val cellW = perAlignRegions.maxOf { it.width }
        val cellH = perAlignRegions.maxOf { it.height }
        val regions = perAlignRegions.map { centeredRegion(it, cellW, cellH) }

        val croppedBefore = ALIGNS.indices.map { i -> cropRegion(beforePixels[i], CANVAS_W, CANVAS_H, regions[i]) }
        val croppedAfter = ALIGNS.indices.map { i -> cropRegion(afterPixels[i], CANVAS_W, CANVAS_H, regions[i]) }

        return buildGrid(listOf(croppedBefore, croppedAfter), cellW, cellH)
    }

    private fun toJsonString(json: JsonObject): String = Json.encodeToString(JsonObject.serializer(), json)

    /**
     * Renders a single hold-frame still of [lottieJson] to ARGB pixels, using the same
     * windowless-scene recipe as `LowerThirdOffscreenRenderer` in the main app: an
     * `ImageComposeScene` pumped until the async Lottie parse completes, then one render
     * at [HOLD_PROGRESS] so a settled frame (not the entrance animation) is what gets reviewed.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private suspend fun renderStill(lottieJson: String): IntArray = withContext(Dispatchers.Default) {
        var compositionLoaded by mutableStateOf(false)

        val scene = ImageComposeScene(CANVAS_W, CANVAS_H, Density(1f)) {
            val composition by rememberLottieComposition {
                LottieCompositionSpec.JsonString(lottieJson.ifBlank { "{}" })
            }
            val loaded = composition != null
            SideEffect { if (loaded) compositionLoaded = true }
            Image(
                painter = rememberLottiePainter(composition = composition, progress = { HOLD_PROGRESS }),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        try {
            var timeNanos = 0L
            val deadline = System.currentTimeMillis() + COMPOSITION_LOAD_TIMEOUT_MS
            while (!compositionLoaded && System.currentTimeMillis() < deadline) {
                timeNanos += FRAME_NANOS
                scene.render(timeNanos).close()
                delay(FRAME_INTERVAL_MS)
            }
            check(compositionLoaded) { "Lottie composition failed to load for off-screen rendering" }

            timeNanos += FRAME_NANOS
            val img = scene.render(timeNanos)
            val pixels = IntArray(CANVAS_W * CANVAS_H)
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

    internal data class Bounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int)

    /** One frame's non-transparent bounds, or null when the frame is fully transparent. */
    internal fun frameBounds(pixels: IntArray, width: Int, height: Int): Bounds? {
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
     * The union of every frame's non-transparent bounds, padded — deliberately NOT clamped to
     * the canvas. A left- or right-aligned badge sits flush against its margin, i.e. near the
     * canvas edge, so clamping here made its crop touch the cell edge with no framing margin on
     * that side while a center-aligned badge (nowhere near an edge) kept its full margin — the
     * two looked inconsistent even though the actual padding inside each badge was the same.
     * [cropRegion] fills whatever falls outside the real canvas with white, so every column gets
     * the same visual frame regardless of alignment.
     */
    internal fun contentCropRegion(frames: List<IntArray>, width: Int, height: Int): CropRegion {
        val bounds = frames.mapNotNull { frameBounds(it, width, height) }
        if (bounds.isEmpty()) return CropRegion(0, 0, width, height)
        val minX = bounds.minOf { it.minX }
        val minY = bounds.minOf { it.minY }
        val maxX = bounds.maxOf { it.maxX }
        val maxY = bounds.maxOf { it.maxY }
        val x0 = minX - CROP_PADDING_PX
        val y0 = minY - CROP_PADDING_PX
        val x1 = maxX + CROP_PADDING_PX
        val y1 = maxY + CROP_PADDING_PX
        return CropRegion(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    /** Expands/repositions [base] to [targetW] x [targetH], centered on its own center. */
    internal fun centeredRegion(base: CropRegion, targetW: Int, targetH: Int): CropRegion {
        val centerX = base.x0 + base.width / 2
        val centerY = base.y0 + base.height / 2
        return CropRegion(centerX - targetW / 2, centerY - targetH / 2, targetW, targetH)
    }

    /** Copies [region] out of [pixels], filling anything outside the real canvas with white. */
    internal fun cropRegion(pixels: IntArray, fullWidth: Int, fullHeight: Int, region: CropRegion): IntArray {
        val out = IntArray(region.width * region.height) { WHITE_ARGB }
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

    /** Arranges [rows] of same-size ARGB frames (each [cellW] x [cellH]) into a labeled grid. */
    /**
     * Flattens each cell onto opaque white before placing it in the grid. `setRGB` on an
     * ARGB image writes the raw alpha through unchanged, so the canvas's transparent margin
     * stayed transparent in the exported PNG — and depending on how a viewer composites that,
     * it can render as black, indistinguishable from the near-black background fill this batch
     * uses. Drawing through `Graphics2D` onto an opaque `TYPE_INT_RGB` canvas lets Java2D alpha-
     * composite it for real, so the file itself is unambiguous no matter what opens it.
     */
    internal fun buildGrid(rows: List<List<IntArray>>, cellW: Int, cellH: Int): BufferedImage {
        val cols = rows.first().size
        val totalW = cols * cellW + (cols + 1) * CELL_GAP_PX
        val totalH = rows.size * cellH + (rows.size + 1) * CELL_GAP_PX
        val out = BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, totalW, totalH)
        rows.forEachIndexed { rowIndex, cells ->
            cells.forEachIndexed { colIndex, pixels ->
                val x = CELL_GAP_PX + colIndex * (cellW + CELL_GAP_PX)
                val y = CELL_GAP_PX + rowIndex * (cellH + CELL_GAP_PX)
                val cellImage = BufferedImage(cellW, cellH, BufferedImage.TYPE_INT_ARGB)
                cellImage.setRGB(0, 0, cellW, cellH, pixels, 0, cellW)
                g.drawImage(cellImage, x, y, null)
            }
        }
        g.dispose()
        return out
    }
}

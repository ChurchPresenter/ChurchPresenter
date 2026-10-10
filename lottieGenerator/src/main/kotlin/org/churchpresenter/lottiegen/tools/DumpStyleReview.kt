package org.churchpresenter.lottiegen.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.model.StyleCatalog
import org.churchpresenter.lottiegen.render.StillFrame
import org.churchpresenter.lottiegen.render.StillFrame.CropRegion
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

    @JvmStatic
    fun main(args: Array<String>) = review(args)

    /** [main] at a canvas of [canvasW] x [canvasH]; the task always renders at 1920 x 1080. */
    internal fun review(args: Array<String>, canvasW: Int = CANVAS_W, canvasH: Int = CANVAS_H) {
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
                ImageIO.write(renderReviewGrid(entry.id, canvasW, canvasH), "png", outFile)
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
    private suspend fun renderReviewGrid(styleId: String, canvasW: Int, canvasH: Int): BufferedImage {
        val base = LottieGenConfig(canvasW = canvasW, canvasH = canvasH, style = styleId)

        val beforePixels = ALIGNS.map { align ->
            renderStill(toJsonString(LottieGenerator.generate(base.copy(align = align))), canvasW, canvasH)
        }
        val afterPixels = ALIGNS.map { align ->
            renderStill(
                toJsonString(
                    LottieGenerator.generate(
                        base.copy(align = align, hideDetail = false, detailText = SAMPLE_DETAIL_TEXT)
                    )
                ),
                canvasW,
                canvasH,
            )
        }

        val perAlignRegions = ALIGNS.indices.map { i ->
            contentCropRegion(listOf(beforePixels[i], afterPixels[i]), canvasW, canvasH)
        }
        val cellW = perAlignRegions.maxOf { it.width }
        val cellH = perAlignRegions.maxOf { it.height }
        val regions = perAlignRegions.map { StillFrame.centeredRegion(it, cellW, cellH) }

        val croppedBefore = ALIGNS.indices.map { i ->
            StillFrame.cropRegion(beforePixels[i], canvasW, canvasH, regions[i])
        }
        val croppedAfter = ALIGNS.indices.map { i ->
            StillFrame.cropRegion(afterPixels[i], canvasW, canvasH, regions[i])
        }

        return buildGrid(listOf(croppedBefore, croppedAfter), cellW, cellH)
    }

    private fun toJsonString(json: JsonObject): String = Json.encodeToString(JsonObject.serializer(), json)

    private suspend fun renderStill(lottieJson: String, width: Int, height: Int): IntArray =
        StillFrame.render(lottieJson, width, height, HOLD_PROGRESS)

    private fun contentCropRegion(frames: List<IntArray>, width: Int, height: Int): CropRegion =
        StillFrame.contentCropRegion(frames, width, height, CROP_PADDING_PX)

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

package org.churchpresenter.lottiegen.tools

import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct tests of `dumpStyleReview`'s pure geometry/formatting helpers — the review tool's
 * `main()`/render path needs a real headless Lottie composition and is exercised by actually
 * running the task, not a unit test; these cover the crop-region math that produced the review
 * bugs (clipped padding, transparent-margin-as-black) this session.
 */
class DumpStyleReviewTest {

    private companion object {
        const val TRANSPARENT = 0
        const val OPAQUE_BLACK = -0x1000000 // 0xFF000000
    }

    // ------------------------------------------------------- safeLabel

    @Test
    fun safeLabelReplacesNonAlphanumericsWithUnderscoreAndTrims() {
        assertEquals("Style_14_Split_Shutter", DumpStyleReview.safeLabel("Style 14 — Split Shutter"))
        assertEquals("abc123", DumpStyleReview.safeLabel("abc123"))
        assertEquals("a_b", DumpStyleReview.safeLabel("__a  b__"))
    }

    // ------------------------------------------------------- frameBounds

    @Test
    fun frameBoundsIsNullForAFullyTransparentFrame() {
        val pixels = IntArray(10 * 10) { TRANSPARENT }
        assertNull(DumpStyleReview.frameBounds(pixels, 10, 10))
    }

    @Test
    fun frameBoundsFindsASinglePixel() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { TRANSPARENT }
        pixels[3 * width + 7] = OPAQUE_BLACK
        val bounds = DumpStyleReview.frameBounds(pixels, width, height)
        assertEquals(DumpStyleReview.Bounds(7, 3, 7, 3), bounds)
    }

    @Test
    fun frameBoundsSpansARectangleOfOpaquePixels() {
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { TRANSPARENT }
        for (y in 4..8) {
            for (x in 2..6) {
                pixels[y * width + x] = OPAQUE_BLACK
            }
        }
        val bounds = DumpStyleReview.frameBounds(pixels, width, height)
        assertEquals(DumpStyleReview.Bounds(2, 4, 6, 8), bounds)
    }

    // ------------------------------------------------------- contentCropRegion

    @Test
    fun contentCropRegionFallsBackToTheFullCanvasWhenEveryFrameIsBlank() {
        val blank = IntArray(10 * 10) { TRANSPARENT }
        val region = DumpStyleReview.contentCropRegion(listOf(blank), 10, 10)
        assertEquals(DumpStyleReview.CropRegion(0, 0, 10, 10), region)
    }

    @Test
    fun contentCropRegionPadsAroundTheUnionOfAllFrameBoundsAndIsNotClampedToTheCanvas() {
        val width = 100
        val height = 100
        val frameA = IntArray(width * height) { TRANSPARENT }.also { it[10 * width + 10] = OPAQUE_BLACK }
        val frameB = IntArray(width * height) { TRANSPARENT }.also { it[20 * width + 30] = OPAQUE_BLACK }

        val region = DumpStyleReview.contentCropRegion(listOf(frameA, frameB), width, height)

        // Union bbox is x:[10,30] y:[10,20]; the crop pads by a fixed margin on every side and,
        // per the regression this session found, must NOT clamp that padding to the canvas edges.
        val pad = region.x0.let { 10 - it } // recovers the tool's own padding constant
        assertTrue(pad > 0, "expected positive padding, got $pad")
        assertEquals(10 - pad, region.x0)
        assertEquals(10 - pad, region.y0)
        assertEquals((30 + pad) - (10 - pad) + 1, region.width)
        assertEquals((20 + pad) - (10 - pad) + 1, region.height)
    }

    // ------------------------------------------------------- centeredRegion

    @Test
    fun centeredRegionExpandsSymmetricallyAroundTheBasesOwnCenter() {
        val base = DumpStyleReview.CropRegion(x0 = 10, y0 = 20, width = 10, height = 10)
        // base center is (15, 25)
        val expanded = DumpStyleReview.centeredRegion(base, targetW = 20, targetH = 20)
        assertEquals(DumpStyleReview.CropRegion(5, 15, 20, 20), expanded)
    }

    // ------------------------------------------------------- cropRegion

    @Test
    fun cropRegionCopiesExactlyWhenFullyInsideTheCanvas() {
        val width = 5
        val height = 5
        val pixels = IntArray(width * height) { i -> i } // distinct marker values
        val region = DumpStyleReview.CropRegion(1, 1, 2, 2)

        val out = DumpStyleReview.cropRegion(pixels, width, height, region)

        assertEquals(pixels[1 * width + 1], out[0])
        assertEquals(pixels[1 * width + 2], out[1])
        assertEquals(pixels[2 * width + 1], out[2])
        assertEquals(pixels[2 * width + 2], out[3])
    }

    @Test
    fun cropRegionFillsWhatFallsOutsideTheCanvasWithWhite() {
        val width = 4
        val height = 4
        val pixels = IntArray(width * height) { OPAQUE_BLACK }
        // Region starts one pixel left of the canvas and extends past its right/bottom edge.
        val region = DumpStyleReview.CropRegion(-1, -1, 6, 6)

        val out = DumpStyleReview.cropRegion(pixels, width, height, region)

        val white = 0xFFFFFFFF.toInt()
        // Top-left corner of the output is off-canvas -> white.
        assertEquals(white, out[0])
        // The pixel mapping to canvas (0,0) sits at output (1,1).
        assertEquals(OPAQUE_BLACK, out[1 * region.width + 1])
        // Bottom-right corner is off-canvas -> white.
        assertEquals(white, out[out.size - 1])
    }

    // ------------------------------------------------------- buildGrid

    @Test
    fun buildGridFlattensTransparentPixelsToOpaqueWhiteAndKeepsOpaqueContent() {
        val cellW = 2
        val cellH = 2
        // Mirrors the tool's own private CELL_GAP_PX; a real value the layout math depends on,
        // not a magic number invented for this test.
        val gap = 24
        val transparentCell = IntArray(cellW * cellH) { TRANSPARENT }
        val opaqueRed = Color.RED.rgb or (0xFF shl 24)
        val redCell = IntArray(cellW * cellH) { opaqueRed }

        val grid = DumpStyleReview.buildGrid(listOf(listOf(transparentCell), listOf(redCell)), cellW, cellH)

        assertEquals(gap * 2 + cellW, grid.width)
        assertEquals(gap * 3 + cellH * 2, grid.height)
        // Row 0 (transparent cell) flattened onto opaque white.
        assertEquals(Color.WHITE.rgb, grid.getRGB(gap, gap))
        // Row 1 (opaque red cell) keeps its color, unaffected by the white flatten.
        val row1Y = gap + cellH + gap
        assertEquals(opaqueRed, grid.getRGB(gap, row1Y))
    }
}

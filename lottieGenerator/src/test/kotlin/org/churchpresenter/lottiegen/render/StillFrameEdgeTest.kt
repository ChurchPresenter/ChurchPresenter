package org.churchpresenter.lottiegen.render

import kotlinx.coroutines.runBlocking
import org.churchpresenter.lottiegen.render.StillFrame.CropRegion
import org.churchpresenter.lottiegen.tools.DumpStyleReview
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StillFrameEdgeTest {

    private val temp: File = Files.createTempDirectory("still-frame-edge-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun frame(w: Int, h: Int, vararg points: Pair<Int, Int>) =
        IntArray(w * h).also { px -> points.forEach { (x, y) -> px[y * w + x] = -1 } }

    @Test
    fun `the crop region is the union of every frame's content, whichever frame holds each edge`() {
        val frames = listOf(
            frame(10, 10, 5 to 5),
            frame(10, 10, 2 to 7),
            frame(10, 10, 8 to 1),
            frame(10, 10),
        )
        assertEquals(CropRegion(1, 0, 9, 9), StillFrame.contentCropRegion(frames, 10, 10, 1))
        assertEquals(CropRegion(0, 0, 10, 10), StillFrame.contentCropRegion(listOf(frame(10, 10)), 10, 10, 1))
    }

    @Test
    fun `a crop wholly off the canvas, either way, is all fill`() {
        val pixels = frame(4, 4, 1 to 1)
        assertTrue(StillFrame.cropRegion(pixels, 4, 4, CropRegion(10, 0, 2, 2), fill = 7).all { it == 7 })
        assertTrue(StillFrame.cropRegion(pixels, 4, 4, CropRegion(0, 10, 2, 2), fill = 7).all { it == 7 })
    }

    @Test
    fun `a composition that never loads fails the render instead of hanging`() {
        assertFailsWith<IllegalStateException> { runBlocking { StillFrame.render("", 8, 8, 0f, loadTimeoutMs = 50) } }
    }

    @Test
    fun `the review tool writes a cropped before-and-after grid for each style asked for`() {
        val out = File(temp, "review")
        DumpStyleReview.review(arrayOf("1, 2", out.absolutePath), canvasW = 192, canvasH = 108)
        val written = out.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(2, written.size)
        assertTrue(written.all { it.endsWith(".png") } && written[0].startsWith("style1_"))
    }
}

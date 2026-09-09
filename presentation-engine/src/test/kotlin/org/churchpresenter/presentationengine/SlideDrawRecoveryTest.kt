package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.pptx.PoiLimits
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The recovery policy behind a degraded slide render, without a deck or a rasterizer.
 *
 * What matters is that a shape failing part-way through does not cost the shapes after it — the
 * production bug was one oversized picture taking the whole slide with it.
 */
class SlideDrawRecoveryTest {

    @Test
    fun `draws every item and skips none when nothing throws`() {
        val drawn = mutableListOf<String>()
        val skipped = drawEachSkippingFailures(listOf("a", "b", "c")) { drawn += it }
        assertEquals(0, skipped)
        assertEquals(listOf("a", "b", "c"), drawn)
    }

    @Test
    fun `a failing item costs only itself and the rest still draw in order`() {
        val drawn = mutableListOf<String>()
        val skipped = drawEachSkippingFailures(listOf("a", "bad", "c")) {
            if (it == "bad") error("this shape cannot be drawn")
            drawn += it
        }
        assertEquals(1, skipped)
        assertEquals(listOf("a", "c"), drawn, "the items after the failure must still be drawn")
    }

    @Test
    fun `counts every failure, not just the first`() {
        val drawn = mutableListOf<String>()
        val skipped = drawEachSkippingFailures(listOf("bad", "bad", "good", "bad")) {
            if (it == "bad") error("no")
            drawn += it
        }
        assertEquals(3, skipped)
        assertEquals(listOf("good"), drawn)
    }

    @Test
    fun `an Error is skipped too, not propagated`() {
        // The drawing path raises OutOfMemoryError on an absurd declared length, and an
        // Exception-only catch would let it through and fail the slide anyway.
        val drawn = mutableListOf<String>()
        val skipped = drawEachSkippingFailures(listOf("a", "boom", "b")) {
            if (it == "boom") throw OutOfMemoryError("declared length")
            drawn += it
        }
        assertEquals(1, skipped)
        assertEquals(listOf("a", "b"), drawn)
    }

    @Test
    fun `an empty list skips nothing`() {
        assertEquals(0, drawEachSkippingFailures(emptyList<String>()) { error("must not be called") })
    }

    @Test
    fun `the POI byte-array ceiling is raised above the default that failed in the field`() {
        assertTrue(
            PoiLimits.MAX_RECORD_BYTES > PoiLimits.DEFAULT_LIMIT_BYTES,
            "the whole point is to clear POI's own default",
        )
    }

    @Test
    fun `the raised POI ceiling stays finite`() {
        // -1 disables the check outright, which trades a blank slide for an OOM that takes the
        // live service down. The bound must remain a real, allocatable number.
        assertTrue(PoiLimits.MAX_RECORD_BYTES > 0)
    }

    @Test
    fun `applying the POI limits latches after the first call`() {
        PoiLimits.resetForTest()
        assertFalse(PoiLimits.hasApplied)
        PoiLimits.apply()
        assertTrue(PoiLimits.hasApplied)
        // Repeated application is what every deck open relies on being free and safe.
        PoiLimits.apply()
        PoiLimits.apply()
        assertTrue(PoiLimits.hasApplied)
    }

    @Test
    fun `opening a deck applies the POI limits`() {
        // The wiring itself: PowerPointDeckSupport.open is the module's single chokepoint for
        // creating a SlideShow, and it is what makes the raised ceiling deterministic rather than
        // dependent on a startup thread winning a race.
        PoiLimits.resetForTest()
        val dir = Files.createTempDirectory("cp-poi-limits").toFile()
        try {
            val pptx = Fixtures.createPptx(dir, listOf("Slide one" to ""))
            PresentationLoader.load(pptx)
        } finally {
            dir.deleteRecursively()
        }
        assertTrue(PoiLimits.hasApplied, "loading a PowerPoint deck must have applied the limits")
    }
}

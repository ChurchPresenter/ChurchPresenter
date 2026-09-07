package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How big an output is, and therefore what shape a preview of it must be.
 *
 * The point of these: every preview in the app used to derive its shape from the first non-primary
 * physical monitor, whichever output it was actually previewing. These pin the replacement --
 * that the answer comes from the output itself, and that the three kinds read the three different
 * fields they actually store their size in.
 */
class OutputGeometryTest {

    @Test
    fun `a screen reports its own stored bounds`() {
        val assignment = ScreenAssignment(targetBoundsW = 1024, targetBoundsH = 768)
        assertEquals(OutputSize(1024, 768), outputSizeOf(assignment, OutputKind.SCREEN))
    }

    @Test
    fun `a browser source reports its configured resolution, not the screen fields`() {
        val output = ScreenAssignment(
            targetBoundsW = 1024, targetBoundsH = 768,
            browserSourceWidth = 2560, browserSourceHeight = 1080,
        )
        assertEquals(OutputSize(2560, 1080), outputSizeOf(output, OutputKind.BROWSER_SOURCE))
    }

    @Test
    fun `an NDI output reports its own resolution rather than the browser source one`() {
        // An NDI output carries the browser-source fields too (they are all one data class, and
        // the NDI card even passes ContentOutputsDialog isBrowserSource = true, meaning only "not a
        // physical screen"). So the kind, not the assignment, has to decide which pair is read.
        val output = ScreenAssignment(
            browserSourceWidth = 3840, browserSourceHeight = 2160,
            ndiWidth = 1280, ndiHeight = 720,
        )
        assertEquals(OutputSize(1280, 720), outputSizeOf(output, OutputKind.NDI))
    }

    @Test
    fun `a slot with no display bounds reports its dev window size`() {
        // A dev fallback slot drives no display, so it has no bounds. It used to fall through to
        // the PRIMARY monitor's shape — a screen nothing is ever sent to.
        val devSlot = ScreenAssignment(devWindowWidth = 1080, devWindowHeight = 1920)
        assertEquals(OutputSize(1080, 1920), outputSizeOf(devSlot, OutputKind.SCREEN))
    }

    @Test
    fun `a zero size falls back to 1080p rather than reporting an unusable shape`() {
        val broken = ScreenAssignment(
            devWindowWidth = 0, devWindowHeight = 0,
            browserSourceWidth = 0, browserSourceHeight = 0,
            ndiWidth = 0, ndiHeight = 0,
        )
        OutputKind.entries.forEach { kind ->
            assertEquals(FallbackOutputSize, outputSizeOf(broken, kind), "$kind must not report 0x0")
        }
    }

    @Test
    fun `aspect ratio comes out of the two dimensions`() {
        assertEquals(16f / 9f, OutputSize(1920, 1080).aspectRatio)
        assertEquals(4f / 3f, OutputSize(1024, 768).aspectRatio)
        assertTrue(OutputSize(1080, 1920).aspectRatio < 1f, "a portrait output is taller than wide")
    }

    @Test
    fun `the offered resolutions are not all 16 by 9`() {
        // The Browser Source and NDI lists this replaced were 16:9 only, which is why neither could
        // stand in for the 4:3 projector or the ultrawide it was feeding.
        val ratios = OUTPUT_RESOLUTIONS.map { it.aspectRatio }.toSet()
        assertTrue(ratios.size > 1, "several shapes must be on offer, not just widescreen")
        assertTrue(
            OUTPUT_RESOLUTIONS.any { it.aspectRatio < 1f },
            "including a portrait one, for a rotated confidence display",
        )
        assertTrue(OUTPUT_RESOLUTIONS.contains(FallbackOutputSize), "1920x1080 must be pickable")
    }
}

package org.churchpresenter.liveoutput.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** How an output's resolution is written in the Projection pages. */
class FormatResolutionTest {

    @Test
    fun `a resolution is written with a multiplication sign, not a letter x`() {
        assertEquals("1920×1080", formatResolution(1920, 1080))
    }

    @Test
    fun `a portrait resolution keeps the order it was given in`() {
        assertEquals("1080×1920", formatResolution(1080, 1920))
    }

    @Test
    fun `an unset resolution is written as it stands rather than hidden`() {
        assertEquals("0×0", formatResolution(0, 0))
    }
}

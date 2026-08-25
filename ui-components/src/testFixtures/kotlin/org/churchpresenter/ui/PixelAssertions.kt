package org.churchpresenter.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Asserts the pixel at ([x], [y]) is [expected], within [tolerance] per channel.
 *
 * Rendering is not exact — antialiasing, gamma and the platform's rasteriser all move a colour by a
 * little — so an equality check on a drawn pixel fails for reasons that have nothing to do with the
 * code under test. The default tolerance is what the presenter suites settled on.
 *
 * Lives here rather than beside any one suite because three modules assert on drawn colour: the
 * presentation presenter in `:composeApp`, the website presenter in `:web-tab`, and anything else
 * that renders and then reads pixels back.
 */
fun assertColorAt(pixelMap: PixelMap, x: Int, y: Int, expected: Color, tolerance: Float = 0.02f) {
    val actual = pixelMap[x, y]
    assertTrue(
        abs(actual.red - expected.red) < tolerance &&
            abs(actual.green - expected.green) < tolerance &&
            abs(actual.blue - expected.blue) < tolerance,
        "expected $expected at ($x, $y) but was $actual",
    )
}

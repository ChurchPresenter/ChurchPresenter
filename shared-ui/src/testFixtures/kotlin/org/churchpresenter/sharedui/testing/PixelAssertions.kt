package org.churchpresenter.sharedui.testing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import kotlin.math.abs
import kotlin.test.assertTrue

/** That the pixel at ([x], [y]) is [expected], to within [tolerance] on each channel. */
fun assertColorAt(pixelMap: PixelMap, x: Int, y: Int, expected: Color, tolerance: Float = 0.02f) {
    val actual = pixelMap[x, y]
    assertTrue(
        abs(actual.red - expected.red) < tolerance &&
            abs(actual.green - expected.green) < tolerance &&
            abs(actual.blue - expected.blue) < tolerance,
        "expected $expected at ($x, $y) but was $actual",
    )
}

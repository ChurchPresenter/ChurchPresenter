package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import org.churchpresenter.settings.OutputScaleMode
import java.awt.image.BufferedImage
import kotlin.test.assertTrue

/**
 * A 4:3 source drawn into a 16:9 output, and what each [OutputScaleMode] must make of it.
 *
 * The source is red with a blue band across its top tenth. Two points of the drawn output then tell
 * all three modes apart without depending on exact pixel sizes:
 *
 * | mode    | left edge, half way down | top edge, centre              |
 * |---------|--------------------------|-------------------------------|
 * | FIT     | black -- the side bar    | blue -- the band, whole       |
 * | FILL    | red                      | red -- the band cropped off   |
 * | STRETCH | red                      | blue -- the band, squashed    |
 *
 * The band is 10% of the source's height, and FILL crops 12.5% off the top of a 4:3 source on a
 * 16:9 output, so it is gone entirely rather than half visible.
 */
internal object OutputScaleFixture {
    const val SOURCE_WIDTH = 400
    const val SOURCE_HEIGHT = 300
    private const val BAND_HEIGHT = SOURCE_HEIGHT / 10

    /** The source, as an AWT image, for the tests that need it written to disk. */
    fun source(): BufferedImage =
        BufferedImage(SOURCE_WIDTH, SOURCE_HEIGHT, BufferedImage.TYPE_INT_RGB).also {
            it.createGraphics().apply {
                color = java.awt.Color.RED
                fillRect(0, 0, SOURCE_WIDTH, SOURCE_HEIGHT)
                color = java.awt.Color.BLUE
                fillRect(0, 0, SOURCE_WIDTH, BAND_HEIGHT)
                dispose()
            }
        }

    /** Asserts the drawn [output] is what [mode] promises, per the table above. */
    fun assertDrawnAs(mode: OutputScaleMode, output: PixelMap) {
        val side = output[1, output.height / 2]
        val top = output[output.width / 2, 1]
        when (mode) {
            OutputScaleMode.FIT -> {
                assertTrue(side.isBlack(), "FIT leaves a bar at the side, got $side")
                assertTrue(top.isBlue(), "FIT shows the whole source, band included, got $top")
            }
            OutputScaleMode.FILL -> {
                assertTrue(side.isRed(), "FILL reaches the side, got $side")
                assertTrue(top.isRed(), "FILL crops the top off rather than squashing it, got $top")
            }
            OutputScaleMode.STRETCH -> {
                assertTrue(side.isRed(), "STRETCH reaches the side, got $side")
                assertTrue(top.isBlue(), "STRETCH keeps the whole source, band included, got $top")
            }
        }
    }

    private fun Color.isBlack() = red < 0.1f && green < 0.1f && blue < 0.1f
    private fun Color.isRed() = red > 0.9f && green < 0.1f && blue < 0.1f
    private fun Color.isBlue() = red < 0.1f && green < 0.1f && blue > 0.9f
}

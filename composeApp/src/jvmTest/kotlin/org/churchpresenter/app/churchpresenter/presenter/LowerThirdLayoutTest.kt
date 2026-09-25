package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.LowerThirdPlacement
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.PlaceableContent
import org.churchpresenter.settings.utils.Constants
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where band-less content lands on a lower-third output, and what the standalone Lottie lower third
 * gets behind it — read off the pixels of one rendered output, since "where it sits" is the whole
 * point and a layout that reports the right bounds can still draw somewhere else.
 */
@OptIn(ExperimentalTestApi::class)
class LowerThirdLayoutTest {

    private companion object {
        const val W = 400
        const val H = 300
        const val BAND_PERCENT = 30
        const val ABOVE_BAND_Y = 40
        const val IN_BAND_Y = 285
        const val X = 200
        val OUTSIDE = Color.Red
        val CONTENT = Color.Green
    }

    private val lowerThird = OutputProfile(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)

    private fun settings(background: BackgroundSettings = BackgroundSettings()) = AppSettings(
        backgroundSettings = background,
        bibleSettings = BibleSettings(lowerThirdHeightPercent = BAND_PERCENT),
    )

    /** The colours at the two sample points: above the band, then inside it. */
    private fun render(
        mode: Presenting,
        profile: OutputProfile,
        appSettings: AppSettings = settings(),
        showBackground: Boolean = true,
        content: Color? = CONTENT,
        awaitAbove: ((Color) -> Boolean)? = null,
    ): Pair<Color, Color> {
        lateinit var sampled: Pair<Color, Color>
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag("output").size(W.dp, H.dp).background(OUTSIDE)) {
                    LowerThirdLayout(mode, profile, appSettings, showBackground) {
                        if (content != null) Box(Modifier.fillMaxSize().background(content))
                    }
                }
            }
            if (awaitAbove != null) {
                waitUntil("the picture above the band finished loading", timeoutMillis = 5_000L) {
                    awaitAbove(onNodeWithTag("output").captureToImage().toPixelMap()[X, ABOVE_BAND_Y])
                }
            }
            val pixels = onNodeWithTag("output").captureToImage().toPixelMap()
            sampled = pixels[X, ABOVE_BAND_Y] to pixels[X, IN_BAND_Y]
        }
        return sampled
    }

    private fun assertRgb(expected: Color, actual: Color, message: String) {
        assertEquals(expected.red, actual.red, 0.03f, "$message — red, got $actual")
        assertEquals(expected.green, actual.green, 0.03f, "$message — green, got $actual")
        assertEquals(expected.blue, actual.blue, 0.03f, "$message — blue, got $actual")
    }

    @Test
    fun `only band-less content is placeable`() {
        assertEquals(PlaceableContent.MEDIA, Presenting.MEDIA.placeableContent())
        assertEquals(PlaceableContent.PRESENTATION, Presenting.PRESENTATION.placeableContent())
        assertEquals(PlaceableContent.PICTURES, Presenting.PICTURES.placeableContent())
        assertEquals(PlaceableContent.WEBSITE, Presenting.WEBSITE.placeableContent())
        assertEquals(PlaceableContent.CANVAS, Presenting.CANVAS.placeableContent())
        listOf(Presenting.BIBLE, Presenting.LYRICS, Presenting.LOWER_THIRD, Presenting.NONE).forEach {
            assertNull(it.placeableContent(), "$it draws its own band, or nothing")
        }
    }

    @Test
    fun `media on a lower third covers the whole output by default`() {
        val (above, band) = render(Presenting.MEDIA, lowerThird)
        assertRgb(CONTENT, above, "full screen is what a lower third always did")
        assertRgb(CONTENT, band, "and the band area too")
    }

    @Test
    fun `media placed in the band leaves the area above it alone`() {
        val profile = lowerThird.copy(
            lowerThirdPlacements = mapOf(PlaceableContent.MEDIA to LowerThirdPlacement.IN_BAND),
        )
        val (above, band) = render(Presenting.MEDIA, profile)
        assertRgb(OUTSIDE, above, "nothing of the video may reach above the band")
        assertRgb(CONTENT, band, "the video sits in the band")
    }

    @Test
    fun `a full-screen output ignores an in-band placement`() {
        val profile = OutputProfile(
            lowerThirdPlacements = mapOf(PlaceableContent.MEDIA to LowerThirdPlacement.IN_BAND),
        )
        val (above, _) = render(Presenting.MEDIA, profile)
        assertRgb(CONTENT, above, "a full screen has no band to shrink into")
    }

    @Test
    fun `the Lottie lower third gets the Default Lower Third's wash behind it`() {
        val wash = settings(
            BackgroundSettings(
                defaultLowerThirdAboveBandType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdAboveBandColor = "#0000FF",
            ),
        )
        val (above, _) = render(Presenting.LOWER_THIRD, lowerThird, wash, content = null)
        assertRgb(Color.Blue, above, "the two thirds above the animation were black before")
    }

    @Test
    fun `a blanked background draws nothing behind the Lottie lower third`() {
        val wash = settings(
            BackgroundSettings(
                defaultLowerThirdAboveBandType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdAboveBandColor = "#0000FF",
            ),
        )
        val (above, _) = render(Presenting.LOWER_THIRD, lowerThird, wash, showBackground = false, content = null)
        assertRgb(OUTSIDE, above, "a blank means nothing, not the wash")
    }

    @Test
    fun `a picture above the band fills the area above it`() {
        val picture = File.createTempFile("cp-above-band", ".png").apply {
            val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
            for (x in 0 until 4) for (y in 0 until 4) image.setRGB(x, y, 0xFFFFFF00.toInt())
            ImageIO.write(image, "png", this)
            deleteOnExit()
        }
        val withPicture = settings(
            BackgroundSettings(
                defaultLowerThirdAboveBandType = Constants.BACKGROUND_IMAGE,
                defaultLowerThirdAboveBandImage = picture.absolutePath,
            ),
        )
        val (above, _) = render(
            Presenting.LOWER_THIRD, lowerThird, withPicture, content = null,
            awaitAbove = { it.blue < 0.1f && it.green > 0.9f },
        )
        assertRgb(Color.Yellow, above, "the picture must be drawn above the band")
    }
}

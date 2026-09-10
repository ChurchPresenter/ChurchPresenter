package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.utils.Constants
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a lower-third output actually puts on screen: a band across the bottom and nothing above it.
 *
 * The whole stack in one composition, because the bug this pins was a seam between two layers of
 * it — [PresenterScreen] painted the Default Lower Third card over the full output while the
 * presenter drew the band on top, so the card's color covered the two thirds that are meant to
 * stay blank, and survived as a hairline along the band's top edge once it was confined.
 */
@OptIn(ExperimentalTestApi::class)
class LowerThirdBandBackgroundRenderTest {

    private companion object {
        const val W = 400
        const val H = 300
        const val BAND_PERCENT = 30
        /** Well inside the blank area, and well inside the band. */
        const val ABOVE_BAND_Y = 40
        const val IN_BAND_Y = 285
        const val EDGE_X = 4
    }

    private fun verse() = SelectedVerse(
        translationFileName = "",
        bibleAbbreviation = "KJV",
        bibleName = "KJV",
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = "For God so loved the world",
    )

    /** A picture in three horizontal stripes, so which one a crop keeps identifies the anchor. */
    private fun stripedPng(top: Int, middle: Int, bottom: Int): File =
        File.createTempFile("cp-band-stripes", ".png").apply {
            val image = BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB)
            for (x in 0 until 9) for (y in 0 until 9) {
                image.setRGB(x, y, if (y < 3) top else if (y < 6) middle else bottom)
            }
            ImageIO.write(image, "png", this)
            deleteOnExit()
        }

    private fun settings(background: BackgroundSettings, bandPercent: Int = BAND_PERCENT) = AppSettings(
        backgroundSettings = background,
        bibleSettings = BibleSettings(lowerThirdHeightPercent = bandPercent),
    )

    private fun render(appSettings: AppSettings, sample: List<Pair<Int, Int>>): List<Color> {
        lateinit var colors: List<Color>
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag("output").size(W.dp, H.dp).background(Color.Red)) {
                    PresenterScreen(appSettings = appSettings, isLowerThird = true) {
                        BiblePresenter(
                            selectedVerses = listOf(verse()),
                            appSettings = appSettings,
                            isLowerThird = true,
                        )
                    }
                }
            }
            val pixels = onNodeWithTag("output").captureToImage().toPixelMap()
            colors = sample.map { (x, y) -> pixels[x, y] }
        }
        return colors
    }

    private fun assertRgb(expected: Color, actual: Color, message: String) {
        assertEquals(expected.red, actual.red, 0.03f, "$message — red, got $actual")
        assertEquals(expected.green, actual.green, 0.03f, "$message — green, got $actual")
        assertEquals(expected.blue, actual.blue, 0.03f, "$message — blue, got $actual")
    }

    private fun inheritedBand(defaults: BackgroundSettings) = defaults.copy(
        bibleLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_DEFAULT),
    )

    @Test
    fun `the band takes the default lower third card when its surface says Default`() {
        val appSettings = settings(
            inheritedBand(
                BackgroundSettings(
                    defaultLowerThirdBackgroundType = Constants.BACKGROUND_COLOR,
                    defaultLowerThirdBackgroundColor = "#AABBCC",
                    defaultLowerThirdBackgroundOpacity = 1f,
                ),
            ),
        )
        val (band) = render(appSettings, listOf(EDGE_X to IN_BAND_Y))
        assertRgb(Color(0xFFAABBCC), band, "the band must draw the default lower third card")
    }

    @Test
    fun `nothing above the band is painted by the default lower third card`() {
        val appSettings = settings(
            inheritedBand(
                BackgroundSettings(
                    defaultLowerThirdBackgroundType = Constants.BACKGROUND_COLOR,
                    defaultLowerThirdBackgroundColor = "#AABBCC",
                    defaultLowerThirdBackgroundOpacity = 1f,
                ),
            ),
        )
        val (above) = render(appSettings, listOf(EDGE_X to ABOVE_BAND_Y))
        assertRgb(Color.Black, above, "everything above the band must stay blank")
    }

    @Test
    fun `an explicit band color leaves no seam of the default along the band's top edge`() {
        val appSettings = settings(
            BackgroundSettings(
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdBackgroundColor = "#FF9900",
                defaultLowerThirdBackgroundOpacity = 1f,
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_COLOR,
                    backgroundColor = "#003366",
                    blur = 40,
                ),
            ),
        )
        val bandTop = H - H * BAND_PERCENT / 100
        val samples = (0..4).map { EDGE_X to bandTop + it }
        render(appSettings, samples).forEachIndexed { offset, pixel ->
            assertRgb(Color(0xFF003366), pixel, "row ${bandTop + offset} must be the band's own color")
        }
    }

    @Test
    fun `a lower third whose default card says FollowDefault takes the full-screen card`() {
        val appSettings = settings(
            inheritedBand(
                BackgroundSettings(
                    defaultBackgroundType = Constants.BACKGROUND_COLOR,
                    defaultBackgroundColor = "#112233",
                    defaultBackgroundOpacity = 1f,
                    defaultLowerThirdBackgroundType = Constants.BACKGROUND_FOLLOW_DEFAULT,
                    defaultLowerThirdBackgroundColor = "#FFFFFF",
                ),
            ),
        )
        val (band) = render(appSettings, listOf(EDGE_X to IN_BAND_Y))
        assertRgb(Color(0xFF112233), band, "FollowDefault must hand the band the full-screen card")
    }

    @Test
    fun `a wash paints above the band and leaves the band itself alone`() {
        val appSettings = settings(
            BackgroundSettings(
                defaultLowerThirdAboveBandType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdAboveBandColor = "#227744",
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_COLOR,
                    backgroundColor = "#003366",
                    backgroundOpacity = 1f,
                ),
            ),
        )
        val (above, band) = render(appSettings, listOf(EDGE_X to ABOVE_BAND_Y, EDGE_X to IN_BAND_Y))
        assertRgb(Color(0xFF227744), above, "the wash must cover the two thirds above the band")
        assertRgb(Color(0xFF003366), band, "and must not reach into the band")
    }

    @Test
    fun `a surface carrying its own band still takes the default's wash`() {
        val appSettings = settings(
            BackgroundSettings(
                defaultLowerThirdAboveBandType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdAboveBandColor = "#227744",
                // Its own band, so it is not inheriting anything the band's type row can express.
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_COLOR,
                    backgroundColor = "#003366",
                    backgroundOpacity = 1f,
                    aboveBandType = Constants.BACKGROUND_DEFAULT,
                ),
            ),
        )
        val (above) = render(appSettings, listOf(EDGE_X to ABOVE_BAND_Y))
        assertRgb(Color(0xFF227744), above, "the wash falls through on its own type")
    }

    @Test
    fun `a surface washing itself outranks the default's wash`() {
        val appSettings = settings(
            BackgroundSettings(
                defaultLowerThirdAboveBandType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdAboveBandColor = "#227744",
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_COLOR,
                    backgroundColor = "#003366",
                    backgroundOpacity = 1f,
                    aboveBandType = Constants.BACKGROUND_COLOR,
                    aboveBandColor = "#884400",
                ),
            ),
        )
        val (above) = render(appSettings, listOf(EDGE_X to ABOVE_BAND_Y))
        assertRgb(Color(0xFF884400), above, "the surface's own wash wins")
    }

    @Test
    fun `a surface on Transparent above the band paints nothing over the default's wash`() {
        val appSettings = settings(
            BackgroundSettings(
                defaultLowerThirdAboveBandType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdAboveBandColor = "#227744",
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_COLOR,
                    backgroundColor = "#003366",
                    backgroundOpacity = 1f,
                    aboveBandType = Constants.BACKGROUND_TRANSPARENT,
                ),
            ),
        )
        val (above) = render(appSettings, listOf(EDGE_X to ABOVE_BAND_Y))
        assertRgb(Color.Black, above, "Transparent must stop the fall-through, not continue it")
    }

    @Test
    fun `the wash meets the band with no gap between them`() {
        val appSettings = settings(
            BackgroundSettings(
                defaultLowerThirdAboveBandType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdAboveBandColor = "#227744",
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_COLOR,
                    backgroundColor = "#003366",
                    backgroundOpacity = 1f,
                ),
            ),
        )
        // Every row across the seam is one or the other; a rounding gap would show the red the
        // output box is painted with, which is neither.
        val bandTop = H - H * BAND_PERCENT / 100
        val samples = (-2..2).map { EDGE_X to bandTop + it }
        render(appSettings, samples).forEachIndexed { offset, pixel ->
            val wash = pixel.red < 0.5f && pixel.green > 0.3f
            assertTrue(
                wash || pixel.blue > 0.3f,
                "row ${'$'}{bandTop - 2 + offset} is neither the wash nor the band — got ${'$'}pixel",
            )
        }
    }

    @Test
    fun `the band crops a picture from its middle, not its bottom edge`() {
        val striped = stripedPng(
            top = 0xFFFF0000.toInt(),
            middle = 0xFF00FF00.toInt(),
            bottom = 0xFF0000FF.toInt(),
        )
        val appSettings = settings(
            BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_IMAGE,
                    backgroundImage = striped.absolutePath,
                    backgroundOpacity = 1f,
                ),
            ),
        )
        val (band) = render(appSettings, listOf(EDGE_X to IN_BAND_Y))
        assertRgb(Color.Green, band, "a band anchored to the bottom would show the last stripe")
    }
}

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The song number pinned to a corner rather than drawn in the row it shares with the title.
 *
 * Where it lands is the whole of this feature, so these assert on measured bounds rather than on the
 * number merely existing -- every one of them passes against the old row layout if you only check
 * that "42" is on screen somewhere.
 */
@OptIn(ExperimentalTestApi::class)
class SongPresenterNumberCornerTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun section(number: Int = 42) = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        songNumber = number,
        type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("Amazing grace how sweet the sound"),
    )

    private fun settings(
        corner: String,
        show: String = Constants.EVERY_PAGE,
        position: String = Constants.BELOW_VERSE,
        lowerThird: Boolean = false,
    ) = AppSettings(
        songSettings = if (lowerThird) {
            SongSettings(
                showNumberLowerThird = show,
                songNumberLowerThirdCorner = corner,
                songNumberLowerThirdPosition = position,
            )
        } else {
            SongSettings(showNumber = show, songNumberCorner = corner, songNumberPosition = position)
        },
    )

    private fun present(
        appSettings: AppSettings,
        lyricSection: LyricSection = section(),
        isLowerThird: Boolean = false,
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) {
                    SongPresenter(
                        lyricSection = lyricSection,
                        appSettings = appSettings,
                        isLowerThird = isLowerThird,
                    )
                }
            }
        }
        block()
    }

    /** Where the number ended up, and the output it was drawn on -- both in root coordinates. */
    private fun ComposeUiTest.numberBounds(): Rect =
        onNodeWithText("42", substring = true).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.outputBounds(): Rect = onRoot().fetchSemanticsNode().boundsInRoot

    private fun assertIn(quadrant: String, number: Rect, output: Rect) {
        val wantTop = quadrant.startsWith("top")
        val wantLeft = quadrant.endsWith("left")
        val isTop = number.center.y < output.center.y
        val isLeft = number.center.x < output.center.x
        val got = (if (isTop) "top" else "bottom") + (if (isLeft) "-left" else "-right")
        assertTrue(
            isTop == wantTop && isLeft == wantLeft,
            "expected the number in the $quadrant of $output, got $got at $number",
        )
    }

    @Test
    fun `each corner draws the number in that quarter of the screen`() {
        val corners = mapOf(
            Constants.TOP_LEFT to "top-left",
            Constants.TOP_RIGHT to "top-right",
            Constants.BOTTOM_LEFT to "bottom-left",
            Constants.BOTTOM_RIGHT to "bottom-right",
        )
        for ((corner, quadrant) in corners) {
            present(settings(corner)) {
                assertIn(quadrant, numberBounds(), outputBounds())
            }
        }
    }

    @Test
    fun `a corner overrides the position the number would otherwise take`() {
        // Above the verse and left aligned, but cornered bottom right: the corner wins outright,
        // which is what the settings tab's dropdown promises and why it hides the ordering switch.
        val above = settings(Constants.BOTTOM_RIGHT, position = Constants.ABOVE_VERSE).let {
            it.copy(
                songSettings = it.songSettings.copy(songNumberHorizontalAlignment = Constants.LEFT),
            )
        }
        present(above) {
            assertIn("bottom-right", numberBounds(), outputBounds())
        }
    }

    @Test
    fun `no corner leaves the number in the row its position asks for`() {
        present(settings(Constants.NONE, position = Constants.ABOVE_VERSE)) {
            val number = numberBounds()
            val output = outputBounds()
            assertTrue(
                number.center.y < output.center.y,
                "above-verse must still put the number in the top half: $number of $output",
            )
        }
    }

    @Test
    fun `on the lower third the corners are the band's, not the screen's`() {
        // Both are drawn inside the band -- the bottom third of the output -- so a number cornered
        // top-left there sits well below the top of the screen, and above the bottom-left one.
        var topLeftY = 0f
        var bottomLeftY = 0f
        var screenMiddle = 0f
        present(settings(Constants.TOP_LEFT, lowerThird = true), isLowerThird = true) {
            topLeftY = numberBounds().center.y
            screenMiddle = outputBounds().center.y
        }
        present(settings(Constants.BOTTOM_LEFT, lowerThird = true), isLowerThird = true) {
            bottomLeftY = numberBounds().center.y
        }

        assertTrue(
            topLeftY > screenMiddle,
            "the band's top corner is still in the lower half of the output: $topLeftY vs $screenMiddle",
        )
        assertTrue(
            topLeftY < bottomLeftY,
            "and it is the band's own top rather than its bottom: $topLeftY vs $bottomLeftY",
        )
    }

    @Test
    fun `a corner does not put a number on screen that is switched off`() {
        present(settings(Constants.BOTTOM_RIGHT, show = Constants.NONE)) {
            onNodeWithText("42", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `an unnumbered song draws nothing in the corner`() {
        present(settings(Constants.BOTTOM_RIGHT), lyricSection = section(number = 0)) {
            onNodeWithText("0", substring = true).assertDoesNotExist()
        }
    }
}

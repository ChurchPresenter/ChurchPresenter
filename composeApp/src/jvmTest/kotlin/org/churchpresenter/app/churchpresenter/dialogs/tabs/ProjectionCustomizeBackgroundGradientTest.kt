@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Background pane's gradient, which only the two lower-third surfaces offer.
 *
 * **Gradient is never clicked here, only started from.** It is the last of six segments in the type
 * row, and that row overflows its pane: on a 1024x768 test window Transparent is clipped to 26px
 * and Gradient to a zero-sized rect, so a click on it lands on nothing. That is a layout fault, not
 * a testing obstacle.
 */
class ProjectionCustomizeBackgroundGradientTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(
        mode: String = band,
        config: BackgroundConfig = BackgroundConfig(
            backgroundType = Constants.BACKGROUND_GRADIENT,
            gradientTopColor = "#101010",
            gradientBottomColor = "#202020",
            gradientPosition = 0.37f,
        ),
    ) = AppSettings(
        backgroundSettings = BackgroundSettings(
            bibleLowerThirdBackground = config,
            bibleBackground = config.copy(backgroundType = Constants.BACKGROUND_COLOR),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.storedBand(): BackgroundConfig =
        assertNotNull(
            projectionSettings.screenAssignments[0].backgroundOverride,
            "the output must have its own Backgrounds",
        ).bibleLowerThirdBackground

    // ── Which surfaces offer a gradient ─────────────────────────────────────────────────────────

    @Test
    fun `a band's Bible surface offers a gradient`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            onNodeWithText("Gradient").assertExists()
        }
    }

    @Test
    fun `a full screen's Bible surface does not`() {
        projectionTab(output(mode = Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            // A gradient is a band's background; the full screen's surfaces do not list it.
            onNodeWithText("Gradient").assertDoesNotExist()
        }
    }

    @Test
    fun `a content surface can fall back to the default above it`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            assertTrue(
                onAllNodesWithText("Default").fetchSemanticsNodes().isNotEmpty(),
                "the type row must offer the surface above",
            )
            // And the chip beside it names *which* Default that is. This output is a band, so the
            // surface above it is the lower-third Default — the chip used to read a bare "Default"
            // because it asked for the full-screen scope no matter what the output was.
            assertTrue(
                onAllNodesWithText("Default Lower Third").fetchSemanticsNodes().isNotEmpty(),
                "the chip must name the lower-third surface it writes",
            )
        }
    }

    // ── The gradient's own rows ─────────────────────────────────────────────────────────────────

    @Test
    fun `a gradient shows both of its ends and where it turns over`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            // The two color fields are named by the colors they hold: "Top" and "Bottom" are also
            // the captions of the Window Position card on the tab behind the dialog, and the
            // finder spans every root.
            onNodeWithText("#101010").assertExists()
            onNodeWithText("#202020").assertExists()
            onNodeWithText("POSITION").assertExists()
        }
    }

    @Test
    fun `retyping the position stores it as a fraction`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            retypeNumberField(37, 80)

            assertEquals(0.8f, get().storedBand().gradientPosition, "the row shows a percent and stores a fraction")
        }
    }

    @Test
    fun `a position outside the range is not stored`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            retypeNumberField(37, 140)

            assertEquals(0.37f, get().storedBand().gradientPosition, "the field withholds a value it cannot store")
        }
    }

    @Test
    fun `a gradient still carries the opacity, dim and blur every drawn background has`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            onNodeWithText("Opacity").assertExists()
            onNodeWithText("Dim").assertExists()
            onNodeWithText("Blur").assertExists()
        }
    }

    // ── The other types ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a surface drawing a color shows no gradient rows`() {
        val colored = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR, gradientPosition = 0.37f)
        projectionTab(output(config = colored)) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            onNodeWithText("POSITION").assertDoesNotExist()
        }
    }

    @Test
    fun `choosing an image over a gradient stores the type`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            chooseSegment("Video")

            assertEquals(Constants.BACKGROUND_VIDEO, get().storedBand().backgroundType)
            // The gradient's rows go with its type.
            onNodeWithText("POSITION").assertDoesNotExist()
        }
    }

    @Test
    fun `a transparent surface has nothing to make more or less transparent`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            chooseSegment("Transparent")
            onNodeWithText("Opacity").assertDoesNotExist()
            onNodeWithText("Dim").assertDoesNotExist()
            onNodeWithText("Blur").assertDoesNotExist()
        }
    }

    @Test
    fun `choosing transparent stores the type`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            chooseSegment("Transparent")

            assertEquals(Constants.BACKGROUND_TRANSPARENT, get().storedBand().backgroundType)
        }
    }

    @Test
    fun `leaving a gradient for a color puts the color row back`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            chooseSegment("Color")

            onNodeWithText("POSITION").assertDoesNotExist()
            assertEquals(Constants.BACKGROUND_COLOR, get().storedBand().backgroundType)
        }
    }

    @Test
    fun `the gradient's ends are kept while another type is drawn`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            chooseSegment("Color")

            val stored = get().storedBand()
            assertEquals(Constants.BACKGROUND_COLOR, stored.backgroundType)
            assertEquals("#101010", stored.gradientTopColor, "the ends outlive the type that hides them")
            assertEquals("#202020", stored.gradientBottomColor)
            assertEquals(0.37f, stored.gradientPosition)
        }
    }
}

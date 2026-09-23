@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Background pane's gradient, which only the two lower-third surfaces offer.
 *
 * Ported from `ProjectionCustomizeBackgroundGradientTest`. Every test here starts *on* a gradient
 * rather than clicking one: Gradient is the last of six segments in the type row, and that row
 * overflows a narrow pane, so a click on it can land on a clipped node.
 */
class ProfilesCustomizeBackgroundGradientTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(
        mode: String = band,
        config: BackgroundConfig = BackgroundConfig(
            backgroundType = Constants.BACKGROUND_GRADIENT,
            gradientTopColor = "#101010",
            gradientBottomColor = "#202020",
            gradientPosition = 0.37f,
            // Distinct, and neither at a default: each track is found by the number beside it.
            gradientTopOpacity = 0.8f,
            gradientBottomOpacity = 0.4f,
        ),
    ) = profileDocument(mode = mode).copy(
        backgroundSettings = BackgroundSettings(
            bibleLowerThirdBackground = config,
            bibleBackground = config.copy(backgroundType = Constants.BACKGROUND_COLOR),
        ),
    )

    private fun AppSettings.storedBand(): BackgroundConfig =
        backgroundFor(BackgroundScope.BIBLE_LOWER_THIRD)

    // ── Which surfaces offer a gradient ─────────────────────────────────────────────────────────

    @Test
    fun `a band's Bible surface offers a gradient`() {
        profilesTab(output()) { _ ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE, own = false)
            onNodeWithText("Gradient").assertExists()
        }
    }

    @Test
    fun `a full screen's Bible surface offers one too`() {
        profilesTab(output(mode = Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE, own = false)
            // It was withheld here only because `backgroundModifier` drew nothing for a gradient.
            onNodeWithText("Gradient").assertExists()
        }
    }

    @Test
    fun `a content surface can fall back to the default above it`() {
        profilesTab(output()) { _ ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE, own = false)
            assertTrue(
                onAllNodesWithText("Default").fetchSemanticsNodes().isNotEmpty(),
                "the type row must offer the surface above",
            )
            // And the caption names *which* Default that is. This profile is a band, so the surface
            // above it is the lower-third Default.
            assertTrue(
                onAllNodesWithText("Default Lower Third").fetchSemanticsNodes().isNotEmpty(),
                "the caption must name the lower-third surface it writes",
            )
        }
    }

    // ── The gradient's own rows ─────────────────────────────────────────────────────────────────

    @Test
    fun `a gradient shows both of its ends and where it turns over`() {
        profilesTab(output()) { _ ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE, own = false)
            // The two colour fields are named by the colours they hold: "Top" and "Bottom" are also
            // captions elsewhere, and the finder spans every root.
            onNodeWithText("#101010").assertExists()
            onNodeWithText("#202020").assertExists()
            onNodeWithText("POSITION").assertExists()
        }
    }

    @Test
    fun `recolouring one end leaves the other alone`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE)
            recolor("#101010", "#ABCDEF")

            assertEquals("#ABCDEF", get().storedBand().gradientTopColor)
            assertEquals("#202020", get().storedBand().gradientBottomColor, "the other end must not move")
        }
    }

    @Test
    fun `the two ends have opacities of their own`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE)
            val before = get().storedBand().gradientBottomOpacity
            tapSliderTrack("Top Opacity", "80%", fraction = 0.5f)

            assertEquals(0.5f, get().storedBand().gradientTopOpacity)
            assertEquals(before, get().storedBand().gradientBottomOpacity, "the lower end must not move")
        }
    }

    @Test
    fun `a gradient keeps its ends through a trip to Color and back`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE)
            chooseSegment("Color")

            assertEquals(Constants.BACKGROUND_COLOR, get().storedBand().backgroundType)
            assertEquals("#101010", get().storedBand().gradientTopColor, "the ends outlive the type")
            assertEquals("#202020", get().storedBand().gradientBottomColor)
        }
    }

    /**
     * `gradientEnabled` is a second, older flag saying the same thing as the type. It has to ride
     * along with the type change, or a surface set to Gradient draws its flat colour instead.
     */
    @Test
    fun `leaving Gradient clears the flag that says it is one`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE)
            chooseSegment("Color")

            assertEquals(false, get().storedBand().gradientEnabled)
        }
    }

    @Test
    fun `recolouring the lower end leaves the upper one alone`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE)
            recolor("#202020", "#FEDCBA")

            assertEquals("#FEDCBA", get().storedBand().gradientBottomColor)
            assertEquals("#101010", get().storedBand().gradientTopColor, "the other end must not move")
        }
    }

    @Test
    fun `the lower end's opacity moves on its own`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE)
            val before = get().storedBand().gradientTopOpacity
            tapSliderTrack("Bottom Opacity", "40%", fraction = 0.75f)

            assertEquals(0.75f, get().storedBand().gradientBottomOpacity, absoluteTolerance = 0.02f)
            assertEquals(before, get().storedBand().gradientTopOpacity, "the upper end must not move")
        }
    }

    /**
     * Where the gradient turns over, which is a number field rather than a slider.
     *
     * It is the one control here that changes the *shape* of the wash rather than its colour: at
     * 100 the lower colour never arrives, at 0 the upper one never does, and the default sits in
     * the middle.
     */
    @Test
    fun `the turnover point is stored as a fraction of the height`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE)
            assertEquals(0.37f, get().storedBand().gradientPosition, "the seeded value is what is shown")

            onAllNodesWithText("37")[0].performTextReplacement("80")
            waitForIdle()

            assertEquals(0.8f, get().storedBand().gradientPosition, absoluteTolerance = 0.02f)
        }
    }
}

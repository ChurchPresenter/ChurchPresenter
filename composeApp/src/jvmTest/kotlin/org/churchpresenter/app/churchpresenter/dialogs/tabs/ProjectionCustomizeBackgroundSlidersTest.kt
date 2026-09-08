@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

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
 * The three sliders every drawn background carries — how opaque it is, how much black is washed
 * over it, and how far it is blurred.
 *
 * They are nudged until the picture reads well behind text rather than typed to a number, so they
 * are sliders and not fields, and the fixture gives each a distinct reading so the track can be
 * found by the number beside it.
 *
 * Driven across three surfaces rather than one: which of the six a pane edits is decided by the
 * chip *and* the output's shape before a control is ever drawn, and all six go through the one
 * `configFor`/`withConfigFor` pair.
 */
class ProjectionCustomizeBackgroundSlidersTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private val tuned = BackgroundConfig(
        backgroundType = Constants.BACKGROUND_COLOR,
        backgroundColor = "#123456",
        backgroundOpacity = 0.8f,
        dim = 45,
        blur = 6,
    )

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        backgroundSettings = BackgroundSettings(
            defaultBackgroundType = Constants.BACKGROUND_COLOR,
            defaultBackgroundColor = "#123456",
            defaultBackgroundOpacity = 0.8f,
            defaultBackgroundDim = 45,
            defaultBackgroundBlur = 6,
            songBackground = tuned,
            bibleLowerThirdBackground = tuned,
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.storedBackgrounds(): BackgroundSettings = assertNotNull(
        projectionSettings.screenAssignments[0].backgroundOverride,
        "the output must have its own Backgrounds",
    )

    // ── The default full-screen surface ─────────────────────────────────────────────────────────

    @Test
    fun `all three readouts show what the surface has stored`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_DEFAULT, override = false)
            onNodeWithText("80%").assertExists("the opacity")
            onNodeWithText("45%").assertExists("the dim")
            onNodeWithText("6px").assertExists("the blur")
        }
    }

    @Test
    fun `the opacity slider writes the default surface`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_DEFAULT)
            tapSliderTrack("Opacity", "80%", fraction = 0.5f)

            val stored = get().storedBackgrounds()
            assertEquals(0.5f, stored.defaultBackgroundOpacity, "a percent on the track, a fraction in the file")
            assertEquals(45, stored.defaultBackgroundDim, "the sliders under it must not move")
            assertEquals(6, stored.defaultBackgroundBlur)
        }
    }

    @Test
    fun `the dim slider writes the default surface`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_DEFAULT)
            tapSliderTrack("Dim", "45%", fraction = 0.5f)

            val stored = get().storedBackgrounds()
            assertEquals(50, stored.defaultBackgroundDim)
            assertEquals(0.8f, stored.defaultBackgroundOpacity, "the slider above it must not move")
        }
    }

    @Test
    fun `the blur slider writes the default surface`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_DEFAULT)
            tapSliderTrack("Blur", "6px", fraction = 0.5f)

            val stored = get().storedBackgrounds()
            assertEquals(50, stored.defaultBackgroundBlur)
            assertEquals(45, stored.defaultBackgroundDim, "the slider above it must not move")
        }
    }

    @Test
    fun `a background can be dimmed to all but black`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_DEFAULT)
            tapSliderTrack("Dim", "45%", fraction = 1f)

            // 99 rather than 100: the track's right edge cannot be tapped, so the last reachable
            // stop is a pixel short of it. See [tapSliderTrack].
            assertTrue(
                get().storedBackgrounds().defaultBackgroundDim >= 99,
                "the far end of the track must all but black the picture out",
            )
        }
    }

    @Test
    fun `a background can be taken to no blur at all`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_DEFAULT)
            tapSliderTrack("Blur", "6px", fraction = 0f)

            assertEquals(0, get().storedBackgrounds().defaultBackgroundBlur)
        }
    }

    @Test
    fun `the readout follows the handle`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_DEFAULT)
            tapSliderTrack("Dim", "45%", fraction = 0.5f)
            onNodeWithText("50%").assertExists("the readout must show what was stored")
        }
    }

    // ── A content surface, and the band's ───────────────────────────────────────────────────────

    @Test
    fun `the Songs chip on a full screen writes the Songs surface`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_SONG)
            tapSliderTrack("Dim", "45%", fraction = 0.5f)

            val stored = get().storedBackgrounds()
            assertEquals(50, stored.songBackground.dim)
            assertEquals(45, stored.defaultBackgroundDim, "the Default surface must be untouched")
        }
    }

    @Test
    fun `the Bible chip on a band writes the band's Bible surface`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            tapSliderTrack("Blur", "6px", fraction = 0.5f)

            val stored = get().storedBackgrounds()
            assertEquals(50, stored.bibleLowerThirdBackground.blur)
            assertEquals(6, stored.songBackground.blur, "the full screen's Songs surface must be untouched")
        }
    }

    @Test
    fun `a surface's opacity is stored as the fraction the renderer wants`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            tapSliderTrack("Opacity", "80%", fraction = 0f)

            assertEquals(0f, get().storedBackgrounds().bibleLowerThirdBackground.backgroundOpacity)
        }
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The opacity, dim and blur sliders on a background surface: what they read, what they write, and
 * that each writes only its own surface.
 *
 * Ported from `ProjectionCustomizeBackgroundSlidersTest`. Every surface starts with a distinct
 * reading so its track can be found by the number beside it.
 */
class ProfilesCustomizeBackgroundSlidersTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private val tuned = BackgroundConfig(
        backgroundType = Constants.BACKGROUND_COLOR,
        backgroundColor = "#123456",
        backgroundOpacity = 0.8f,
        dim = 45,
        blur = 6,
    )

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) =
        profileDocument(mode = mode).copy(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_COLOR,
                defaultBackgroundColor = "#123456",
                defaultBackgroundOpacity = 0.8f,
                defaultBackgroundDim = 45,
                defaultBackgroundBlur = 6,
                songBackground = tuned,
                bibleLowerThirdBackground = tuned,
            ),
        )

    private fun AppSettings.at(scope: BackgroundScope): BackgroundConfig = backgroundFor(scope)

    // ── The default full-screen surface ─────────────────────────────────────────────────────────

    @Test
    fun `all three readouts show what the surface has stored`() {
        profilesTab(output()) { _ ->
            // Taken over, which starts from what the app default holds: its editor then shows it.
            openBackgroundSurface(CustomizeElement.BACKGROUND_DEFAULT)
            onNode(hasSetTextAction() and hasText("80") and outsideDefaultsCard).assertExists()
            onNode(hasSetTextAction() and hasText("45") and outsideDefaultsCard).assertExists()
            onNode(hasSetTextAction() and hasText("6") and outsideDefaultsCard).assertExists()
        }
    }

    @Test
    fun `the opacity slider writes the default surface`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_DEFAULT)
            setProfileStepper("Opacity", "80%", fraction = 0.5f)

            assertEquals(
                0.5f,
                get().at(BackgroundScope.DEFAULT).backgroundOpacity,
                "a percent on the track, a fraction in the file",
            )
            assertEquals(45, get().at(BackgroundScope.DEFAULT).dim, "the sliders under it must not move")
            assertEquals(6, get().at(BackgroundScope.DEFAULT).blur)
        }
    }

    @Test
    fun `the dim slider writes the default surface`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_DEFAULT)
            setProfileStepper("Dim", "45%", fraction = 0.5f)

            assertEquals(50, get().at(BackgroundScope.DEFAULT).dim)
            assertEquals(
                0.8f,
                get().at(BackgroundScope.DEFAULT).backgroundOpacity,
                "the slider above it must not move",
            )
        }
    }

    @Test
    fun `the blur slider writes the default surface`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_DEFAULT)
            setProfileStepper("Blur", "6px", fraction = 0.5f)

            assertEquals(50, get().at(BackgroundScope.DEFAULT).blur)
            assertEquals(45, get().at(BackgroundScope.DEFAULT).dim, "the slider above it must not move")
        }
    }

    @Test
    fun `a background can be dimmed to all but black`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_DEFAULT)
            setProfileStepper("Dim", "45%", fraction = 1f)

            // 99 rather than 100: the track's right edge cannot be tapped, so the last reachable
            // stop is a pixel short of it. See [tapSliderTrack].
            assertTrue(
                get().at(BackgroundScope.DEFAULT).dim >= 99,
                "the far end of the track must all but black the picture out",
            )
        }
    }

    @Test
    fun `a background can be taken to no blur at all`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_DEFAULT)
            setProfileStepper("Blur", "6px", fraction = 0f)

            assertEquals(0, get().at(BackgroundScope.DEFAULT).blur)
        }
    }

    @Test
    fun `the readout follows the handle`() {
        profilesTab(output()) { _ ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_DEFAULT)
            setProfileStepper("Dim", "45%", fraction = 0.5f)
            onNode(hasSetTextAction() and hasText("50") and outsideDefaultsCard).assertExists()
        }
    }

    // ── A content surface, and the band's ───────────────────────────────────────────────────────

    @Test
    fun `the Songs chip on a full screen writes the Songs surface`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_SONG)
            setProfileStepper("Dim", "45%", fraction = 0.5f)

            assertEquals(50, get().at(BackgroundScope.SONG).dim)
            assertEquals(45, get().at(BackgroundScope.DEFAULT).dim, "the Default surface must be untouched")
        }
    }

    @Test
    fun `the Bible chip on a band writes the band's Bible surface`() {
        profilesTab(output(band)) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE)
            setProfileStepper("Blur", "6px", fraction = 0.5f)

            assertEquals(50, get().at(BackgroundScope.BIBLE_LOWER_THIRD).blur)
            assertEquals(
                6,
                get().at(BackgroundScope.SONG).blur,
                "the full screen's Songs surface must be untouched",
            )
        }
    }

    @Test
    fun `a surface's opacity is stored as the fraction the renderer wants`() {
        profilesTab(output(band)) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_BIBLE)
            setProfileStepper("Opacity", "80%", fraction = 0f)

            assertEquals(0f, get().at(BackgroundScope.BIBLE_LOWER_THIRD).backgroundOpacity)
        }
    }

    @Test
    fun `taking one surface over leaves the rest of them following`() {
        profilesTab(output()) { get ->
            openBackgroundSurface(CustomizeElement.BACKGROUND_SONG)
            setProfileStepper("Dim", "45%", fraction = 0.5f)

            assertTrue(get().overrides(BackgroundScope.SONG))
            assertTrue(!get().overrides(BackgroundScope.DEFAULT), "one surface at a time")
            assertEquals(
                45,
                get().backgroundSettings.defaultBackgroundDim,
                "and the Background tab keeps its own value",
            )
        }
    }
}

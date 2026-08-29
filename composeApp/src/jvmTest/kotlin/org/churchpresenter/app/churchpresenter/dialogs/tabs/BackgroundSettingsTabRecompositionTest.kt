@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.StockPhotoSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The tab redrawing from its inputs rather than from anything it holds itself.
 *
 * The one piece of state it *does* own is which surface the rail has open, and these check that a
 * settings change arriving from outside neither loses that nor stops the editor following it.
 */
class BackgroundSettingsTabRecompositionTest {

    /** Drives the tab from a settings value the test can swap under it. */
    private fun withSwappableSettings(
        initial: AppSettings,
        block: ComposeUiTest.(set: (AppSettings) -> Unit) -> Unit,
    ) = runComposeUiTest {
        var state by mutableStateOf(initial)
        setContent {
            MaterialTheme {
                BackgroundSettingsTab(settings = state, onSettingsChange = { state = it(state) })
            }
        }
        block { next -> state = next; waitForIdle() }
    }

    private val imageSettings = AppSettings(
        backgroundSettings = BackgroundSettings(
            defaultBackgroundType = Constants.BACKGROUND_IMAGE,
            defaultBackgroundImage = "/library/stage.jpg",
        ),
    )

    @Test
    fun `the tab survives a recomposition that changes none of its inputs`() =
        withSwappableSettings(AppSettings()) { set ->
            assertEquals(1, headerCount(Surface.DEFAULT.title), "the Default surface is open")
            set(AppSettings())
            assertEquals(1, headerCount(Surface.DEFAULT.title), "and still is afterwards")
            assertEquals(1, controlsCount("COLOR"), "and its controls must still be there")
        }

    @Test
    fun `a stored colour change reaches the field without any interaction`() =
        withSwappableSettings(AppSettings()) { set ->
            set(
                AppSettings(
                    backgroundSettings = BackgroundSettings(defaultBackgroundColor = "#abcdef"),
                ),
            )
            assertColorFieldShows("#abcdef", "the colour field")
        }

    @Test
    fun `a stored image path change reaches the picker without any interaction`() =
        withSwappableSettings(imageSettings) { set ->
            onNodeWithText("stage.jpg", substring = true).assertIsDisplayed()
            set(
                imageSettings.copy(
                    backgroundSettings = imageSettings.backgroundSettings.copy(
                        defaultBackgroundImage = "/library/song.png",
                    ),
                ),
            )
            onNodeWithText("song.png", substring = true).assertIsDisplayed()
        }

    @Test
    fun `a stored look change reaches the sliders without any interaction`() =
        withSwappableSettings(AppSettings()) { set ->
            assertSliderShows(SliderCaption.DIM, 0, "the dim slider")
            set(
                AppSettings(
                    backgroundSettings = BackgroundSettings(
                        defaultBackgroundDim = 55,
                        defaultBackgroundBlur = 9,
                    ),
                ),
            )
            assertSliderShows(SliderCaption.DIM, 55, "the dim slider")
            assertSliderShows(SliderCaption.BLUR, 9, "the blur slider")
        }

    @Test
    fun `configuring an ATEM host adds the upload buttons`() =
        withSwappableSettings(imageSettings) { set ->
            set(imageSettings.copy(atemSettings = AtemSettings(host = "10.0.0.5")))
            onNodeWithContentDescription("Upload to Background Slot 1").assertIsDisplayed()
            onNodeWithText("stage.jpg", substring = true).assertIsDisplayed()
        }

    @Test
    fun `clearing the ATEM host removes the upload buttons`() =
        withSwappableSettings(imageSettings.copy(atemSettings = AtemSettings(host = "10.0.0.5"))) { set ->
            onNodeWithContentDescription("Upload to Background Slot 1").assertIsDisplayed()
            set(imageSettings.copy(atemSettings = AtemSettings(host = "")))
            assertEquals(
                0,
                onAllNodesWithContentDescription("Upload to Background Slot 1")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).size,
                "with nowhere to send it, the button goes",
            )
            onNodeWithText("stage.jpg", substring = true).assertIsDisplayed()
        }

    @Test
    fun `changing a stock photo API key leaves the row standing`() =
        withSwappableSettings(imageSettings) { set ->
            set(imageSettings.copy(stockPhotoSettings = StockPhotoSettings(pexelsApiKey = "abc123")))
            onNodeWithText("stage.jpg", substring = true).assertIsDisplayed()
            assertEquals(1, controlsCount("IMAGE FILE"), "the picker row must survive it")
        }

    @Test
    fun `changing a type re-renders the editor and leaves the rail intact`() = backgroundTab { _ ->
        setSurfaceType(Surface.DEFAULT, TypeLabel.IMAGE)
        assertEquals(1, controlsCount("IMAGE FILE"), "the editor follows the type")
        assertEquals(1, railCount("Default"), "and the rail is untouched")
        assertEquals(2, railCount("Full Screen"), "with both content rows still listed")
    }

    @Test
    fun `the editor adds and drops its gradient controls as the type changes`() = backgroundTab { _ ->
        setSurfaceType(Surface.BIBLE_LOWER_THIRD, TypeLabel.GRADIENT)
        assertEquals(1, controlsCount("TOP OPACITY"), "a gradient brings its own sliders")
        chooseBackgroundType(TypeLabel.COLOR)
        assertEquals(0, controlsCount("TOP OPACITY"), "and takes them away again")
        assertEquals(1, controlsCount("COLOR"), "leaving the colour field behind")
    }

    @Test
    fun `a fully populated tab survives a recomposition that changes nothing`() {
        val populated = AppSettings(
            atemSettings = AtemSettings(host = "10.0.0.5"),
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                defaultBackgroundImage = "/library/stage.jpg",
                defaultBackgroundDim = 30,
                defaultBackgroundBlur = 8,
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    gradientEnabled = true,
                ),
            ),
        )
        withSwappableSettings(populated) { set ->
            onNodeWithText("stage.jpg", substring = true).assertIsDisplayed()
            set(populated)
            onNodeWithText("stage.jpg", substring = true).assertIsDisplayed()
            assertSliderShows(SliderCaption.DIM, 30, "the dim slider")
            onNodeWithContentDescription("Upload to Background Slot 1").assertIsDisplayed()
        }
    }

    @Test
    fun `the open surface survives a settings change from outside`() =
        withSwappableSettings(AppSettings()) { set ->
            openSurface(Surface.SONG_LOWER_THIRD)
            set(AppSettings(backgroundSettings = BackgroundSettings(defaultBackgroundColor = "#abcdef")))
            assertEquals(1, headerCount(Surface.SONG_LOWER_THIRD.title), "the open surface must survive it")
        }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The two Default surfaces: what their controls write, and what the tab reads back. */
class BackgroundSettingsTabTest {

    @Test
    fun `the tab composes and opens on the default surface`() = backgroundTab { _ ->
        onNodeWithText("BACKGROUNDS").assertIsDisplayed()
        assertEquals(1, headerCount(Surface.DEFAULT.title), "the Default surface is open")
    }

    @Test
    fun `the default background starts on Color`() = backgroundTab { settings ->
        assertEquals(Constants.BACKGROUND_COLOR, settings().backgroundSettings.defaultBackgroundType)
        assertEquals(1, controlsCount("COLOR"), "so its colour field is showing")
    }

    @Test
    fun `choosing Transparent stores it against the default`() = backgroundTab { settings ->
        setSurfaceType(Surface.DEFAULT, TypeLabel.TRANSPARENT)
        assertEquals(
            Constants.BACKGROUND_TRANSPARENT,
            settings().backgroundSettings.defaultBackgroundType,
        )
    }

    @Test
    fun `choosing Image stores it and reveals the picker`() = backgroundTab { settings ->
        setSurfaceType(Surface.DEFAULT, TypeLabel.IMAGE)
        assertEquals(Constants.BACKGROUND_IMAGE, settings().backgroundSettings.defaultBackgroundType)
        assertEquals(1, controlsCount("IMAGE FILE"), "the picker must appear with it")
    }

    @Test
    fun `switching back to Color restores the colour field`() = backgroundTab { settings ->
        setSurfaceType(Surface.DEFAULT, TypeLabel.TRANSPARENT)
        chooseBackgroundType(TypeLabel.COLOR)
        assertEquals(Constants.BACKGROUND_COLOR, settings().backgroundSettings.defaultBackgroundType)
        assertEquals(1, controlsCount("COLOR"), "and the field comes back with it")
    }

    @Test
    fun `the Video segment is pickable only where VLC is installed`() = backgroundTab { settings ->
        openSurface(Surface.DEFAULT)
        assertEquals(1, controlsCount(TypeLabel.VIDEO), "the segment is always offered")
        chooseBackgroundType(TypeLabel.VIDEO)
        val stored = settings().backgroundSettings.defaultBackgroundType
        if (videoSegmentEnabled) {
            assertEquals(Constants.BACKGROUND_VIDEO, stored, "with VLC the segment stores its type")
        } else {
            assertNotEquals(Constants.BACKGROUND_VIDEO, stored, "without VLC it is inert")
        }
    }

    @Test
    fun `the default colour picker stores the confirmed hex`() = backgroundTab { settings ->
        openSurface(Surface.DEFAULT)
        recolor(AppSettings().backgroundSettings.defaultBackgroundColor, "#123456")
        assertEquals("#123456", settings().backgroundSettings.defaultBackgroundColor)
    }

    @Test
    fun `cancelling the colour dialog leaves the colour alone`() = backgroundTab { settings ->
        val before = settings().backgroundSettings.defaultBackgroundColor
        openSurface(Surface.DEFAULT)
        openColorField(before)
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(before, settings().backgroundSettings.defaultBackgroundColor)
    }

    @Test
    fun `the default lower third offers Follow Default and stores it`() = backgroundTab { settings ->
        setSurfaceType(Surface.DEFAULT_LOWER_THIRD, TypeLabel.FOLLOW_DEFAULT)
        assertEquals(
            Constants.BACKGROUND_FOLLOW_DEFAULT,
            settings().backgroundSettings.defaultLowerThirdBackgroundType,
        )
    }

    @Test
    fun `the full-screen default is where the chain ends`() = backgroundTab { _ ->
        openSurface(Surface.DEFAULT)
        assertEquals(0, controlsCount(TypeLabel.FOLLOW_DEFAULT), "it has nothing above it to follow")
    }

    @Test
    fun `the lower-third default keeps its colour apart from the full-screen one`() =
        backgroundTab { settings ->
            setSurfaceType(Surface.DEFAULT_LOWER_THIRD, TypeLabel.COLOR)
            recolor(AppSettings().backgroundSettings.defaultLowerThirdBackgroundColor, "#131415")
            assertEquals("#131415", settings().backgroundSettings.defaultLowerThirdBackgroundColor)
            assertNotEquals(
                "#131415",
                settings().backgroundSettings.defaultBackgroundColor,
                "the full-screen default must be untouched",
            )
        }

    @Test
    fun `the opacity slider stores a new opacity`() = backgroundTab { settings ->
        openSurface(Surface.DEFAULT)
        val shown = dragSlider(SliderCaption.OPACITY, 0.25f)
        assertBetween("the opacity the slider reports", shown, 10, 45)
        assertEquals(
            shown,
            (settings().backgroundSettings.defaultBackgroundOpacity * 100).toInt(),
            "the stored opacity must match the readout",
        )
    }

    @Test
    fun `the dim slider stores a new dim`() = backgroundTab { settings ->
        openSurface(Surface.DEFAULT)
        val shown = dragSlider(SliderCaption.DIM, 0.5f)
        assertBetween("the dim the slider reports", shown, 35, 65)
        assertEquals(shown, settings().backgroundSettings.defaultBackgroundDim)
    }

    @Test
    fun `the blur slider stores a new blur`() = backgroundTab { settings ->
        openSurface(Surface.DEFAULT)
        val shown = dragSlider(SliderCaption.BLUR, 0.5f)
        assertBetween("the blur the slider reports", shown, 8, 16)
        assertEquals(shown, settings().backgroundSettings.defaultBackgroundBlur)
    }

    @Test
    fun `dim and blur do not overwrite each other`() = backgroundTab { settings ->
        openSurface(Surface.DEFAULT)
        val dim = dragSlider(SliderCaption.DIM, 0.5f)
        val blur = dragSlider(SliderCaption.BLUR, 0.75f)
        assertEquals(dim, settings().backgroundSettings.defaultBackgroundDim, "the dim must survive")
        assertEquals(blur, settings().backgroundSettings.defaultBackgroundBlur, "and so must the blur")
        assertSliderShows(SliderCaption.DIM, dim, "the dim slider")
    }

    @Test
    fun `the sliders read back what is stored`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundOpacity = 0.6f,
                defaultBackgroundDim = 40,
                defaultBackgroundBlur = 12,
            ),
        )
        backgroundTab(settings) { _ ->
            assertSliderShows(SliderCaption.OPACITY, 60, "the opacity slider")
            assertSliderShows(SliderCaption.DIM, 40, "the dim slider")
            assertSliderShows(SliderCaption.BLUR, 12, "the blur slider")
        }
    }

    @Test
    fun `the opacity slider works while the surface is on Image`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                defaultBackgroundImage = "/library/stage.jpg",
            ),
        )
        backgroundTab(settings) { current ->
            val shown = dragSlider(SliderCaption.OPACITY, 0.75f)
            assertBetween("the opacity the slider reports", shown, 55, 90)
            assertEquals(
                shown,
                (current().backgroundSettings.defaultBackgroundOpacity * 100).toInt(),
            )
        }
    }

    @Test
    fun `a stored image path is shown by its picker`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                defaultBackgroundImage = "/library/backdrops/stage.jpg",
            ),
        )
        backgroundTab(settings) { _ ->
            onNodeWithText("stage.jpg", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `a stored video path is shown by its picker`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_VIDEO,
                defaultBackgroundVideo = "/library/clips/loop.mp4",
            ),
        )
        backgroundTab(settings) { _ ->
            onNodeWithText("loop.mp4", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `the values the controls write survive a settings json round trip`() = backgroundTab { settings ->
        setSurfaceType(Surface.DEFAULT, TypeLabel.COLOR)
        recolor(AppSettings().backgroundSettings.defaultBackgroundColor, "#654321")
        val dim = dragSlider(SliderCaption.DIM, 0.5f)
        setSurfaceType(Surface.DEFAULT_LOWER_THIRD, TypeLabel.TRANSPARENT)

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restored = json.decodeFromString<AppSettings>(json.encodeToString(settings()))
        assertEquals("#654321", restored.backgroundSettings.defaultBackgroundColor)
        assertEquals(dim, restored.backgroundSettings.defaultBackgroundDim)
        assertEquals(
            Constants.BACKGROUND_TRANSPARENT,
            restored.backgroundSettings.defaultLowerThirdBackgroundType,
        )
    }

    @Test
    fun `the quick-tray override is not written to settings json`() = backgroundTab { settings ->
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val encoded = json.encodeToString(settings())
        assertEquals(
            false,
            encoded.contains("quickBackground\""),
            "a live pick is a control, not a setting — it must not reach the file",
        )
    }
}

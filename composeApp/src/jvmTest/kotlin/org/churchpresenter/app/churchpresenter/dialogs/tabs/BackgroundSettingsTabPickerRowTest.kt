@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The image and video picker rows, and the two ATEM upload buttons that hang off an image one.
 *
 * Only the open surface has a picker row now, so where the old tab had one row per slot on screen
 * at once these count what a *single* surface shows.
 */
class BackgroundSettingsTabPickerRowTest {

    private val slot1 = "Upload to Background Slot 1"
    private val slot2 = "Upload to Background Slot 2"

    private fun imageSettings(
        path: String = "/library/backdrops/stage.jpg",
        atemHost: String = "",
    ) = AppSettings(
        atemSettings = AtemSettings(host = atemHost),
        backgroundSettings = BackgroundSettings(
            defaultBackgroundType = Constants.BACKGROUND_IMAGE,
            defaultBackgroundImage = path,
        ),
    )

    private fun videoSettings(path: String = "/library/clips/loop.mp4") = AppSettings(
        backgroundSettings = BackgroundSettings(
            defaultBackgroundType = Constants.BACKGROUND_VIDEO,
            defaultBackgroundVideo = path,
        ),
    )

    @Test
    fun `an image row is captioned and names its file`() = backgroundTab(imageSettings()) { _ ->
        assertEquals(1, controlsCount("IMAGE FILE"), "the row must be captioned")
        onNodeWithText("stage.jpg", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an image row names the stored file rather than its whole path`() =
        backgroundTab(imageSettings()) { _ ->
            onNodeWithText("stage.jpg", substring = true).assertIsDisplayed()
            assertEquals(
                0,
                onAllNodesWithText("/library/backdrops/stage.jpg")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).size,
                "the field shows a name, not a path",
            )
        }

    @Test
    fun `a video row is captioned and names its clip`() = backgroundTab(videoSettings()) { _ ->
        assertEquals(1, controlsCount("VIDEO FILE"), "the row must be captioned")
        onNodeWithText("loop.mp4", substring = true).assertIsDisplayed()
    }

    @Test
    fun `only the open surface gets a picker row`() = backgroundTab(imageSettings()) { _ ->
        assertEquals(1, controlsCount("IMAGE FILE"), "the Default surface has one")
        openSurface(Surface.BIBLE)
        assertEquals(0, controlsCount("IMAGE FILE"), "an inheriting surface has none")
    }

    @Test
    fun `the ATEM uploads appear once a switcher is configured`() =
        backgroundTab(imageSettings(atemHost = "10.0.0.5")) { _ ->
            onNodeWithContentDescription(slot1).assertIsDisplayed()
            onNodeWithContentDescription(slot2).assertIsDisplayed()
        }

    @Test
    fun `an image row with no file offers no ATEM upload`() =
        backgroundTab(imageSettings(path = "", atemHost = "10.0.0.5")) { _ ->
            assertEquals(0, uploadButtonCount(), "there is nothing to send")
        }

    @Test
    fun `a blank switcher host offers no ATEM upload`() = backgroundTab(imageSettings()) { _ ->
        assertEquals(0, uploadButtonCount(), "there is nowhere to send it")
    }

    @Test
    fun `a video row offers no ATEM upload, since a clip cannot be a still`() {
        val settings = videoSettings().copy(atemSettings = AtemSettings(host = "10.0.0.5"))
        backgroundTab(settings) { _ ->
            assertEquals(0, uploadButtonCount(), "the media pool takes a frame, not a clip")
        }
    }

    @Test
    fun `the ATEM upload buttons are enabled and clickable when shown`() =
        backgroundTab(imageSettings(atemHost = "10.0.0.5")) { _ ->
            onNodeWithContentDescription(slot1).assertIsEnabled()
            onNodeWithContentDescription(slot2).assertIsEnabled()
            assertEquals(
                2,
                onAllNodesWithContentDescription(slot1).fetchSemanticsNodes(atLeastOneRootRequired = false).size +
                    onAllNodesWithContentDescription(slot2).fetchSemanticsNodes(atLeastOneRootRequired = false).size,
                "one button per slot",
            )
        }

    @Test
    fun `a colour surface has no picker row at all`() = backgroundTab { _ ->
        assertEquals(0, controlsCount("IMAGE FILE"), "a colour needs no file")
        assertEquals(0, controlsCount("VIDEO FILE"), "nor a clip")
    }

    @Test
    fun `a gradient surface has no picker row either`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    gradientEnabled = true,
                ),
            ),
        )
        backgroundTab(settings) { _ ->
            openSurface(Surface.BIBLE_LOWER_THIRD)
            assertEquals(0, controlsCount("IMAGE FILE"), "a gradient is drawn, not loaded")
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.uploadButtonCount(): Int =
        onAllNodesWithContentDescription(slot1).fetchSemanticsNodes(atLeastOneRootRequired = false).size +
            onAllNodesWithContentDescription(slot2).fetchSemanticsNodes(atLeastOneRootRequired = false).size
}

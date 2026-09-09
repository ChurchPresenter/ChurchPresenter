@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.StockPhotoSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Which row the Background pane draws for each type it can be set to, and what picking a type
 * stores. The file pickers open a real chooser, so they are asserted as rendered, not driven.
 */
class ProjectionCustomizeBackgroundTypesTest {

    private fun output(type: String, image: String = "", video: String = "") = AppSettings(
        backgroundSettings = BackgroundSettings(
            songBackground = BackgroundConfig(
                backgroundType = type,
                backgroundColor = "#123456",
                backgroundImage = image,
                backgroundVideo = video,
            ),
        ),
        stockPhotoSettings = StockPhotoSettings(pexelsApiKey = "pex", pixabayApiKey = "pix"),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
        ),
    )

    private fun AppSettings.stored(): BackgroundConfig = assertNotNull(
        projectionSettings.screenAssignments[0].backgroundOverride,
        "the output must have its own Backgrounds",
    ).songBackground

    private fun openSongSurface(test: androidx.compose.ui.test.ComposeUiTest, override: Boolean = true) =
        test.openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_SONG, override = override)

    // ── What each type draws ──────────────────────────────────────────────────

    @Test
    fun `a color surface draws a color field`() {
        projectionTab(output(Constants.BACKGROUND_COLOR)) { _ ->
            openSongSurface(this, override = false)
            onNodeWithText("#123456").assertExists()
        }
    }

    @Test
    fun `an image surface draws the image picker`() {
        projectionTab(output(Constants.BACKGROUND_IMAGE)) { _ ->
            openSongSurface(this, override = false)
            onNodeWithText("Image File").assertExists()
            onNodeWithText("No image selected").assertExists()
        }
    }

    @Test
    fun `an image surface names the file it points at`() {
        projectionTab(output(Constants.BACKGROUND_IMAGE, image = "/photos/sunrise.jpg")) { _ ->
            openSongSurface(this, override = false)
            onNodeWithText("sunrise.jpg").assertExists()
        }
    }

    @Test
    fun `a video surface draws the video picker`() {
        projectionTab(output(Constants.BACKGROUND_VIDEO)) { _ ->
            openSongSurface(this, override = false)
            onNodeWithText("Video File").assertExists()
            onNodeWithText("No video selected").assertExists()
        }
    }

    @Test
    fun `a video surface names the clip it points at`() {
        projectionTab(output(Constants.BACKGROUND_VIDEO, video = "/clips/loop.mp4")) { _ ->
            openSongSurface(this, override = false)
            onNodeWithText("loop.mp4").assertExists()
        }
    }

    @Test
    fun `a transparent surface draws no source row at all`() {
        projectionTab(output(Constants.BACKGROUND_TRANSPARENT)) { _ ->
            openSongSurface(this, override = false)
            onNodeWithText("Image File").assertDoesNotExist()
            onNodeWithText("Video File").assertDoesNotExist()
            onNodeWithText("#123456").assertDoesNotExist()
        }
    }

    @Test
    fun `a surface following the default draws no source row either`() {
        projectionTab(output(Constants.BACKGROUND_DEFAULT)) { _ ->
            openSongSurface(this, override = false)
            onNodeWithText("Image File").assertDoesNotExist()
            onNodeWithText("#123456").assertDoesNotExist()
        }
    }

    // ── Switching between them ────────────────────────────────────────────────

    @Test
    fun `choosing Image stores the type and swaps the row in`() {
        projectionTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openSongSurface(this)
            chooseSegment("Image")

            assertEquals(Constants.BACKGROUND_IMAGE, get().stored().backgroundType)
            onNodeWithText("No image selected").assertExists()
            onNodeWithText("#123456").assertDoesNotExist()
        }
    }

    @Test
    fun `choosing Video stores the type and swaps the row in`() {
        projectionTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openSongSurface(this)
            chooseSegment("Video")

            assertEquals(Constants.BACKGROUND_VIDEO, get().stored().backgroundType)
            onNodeWithText("No video selected").assertExists()
        }
    }

    @Test
    fun `choosing Default hands the surface back to the one above it`() {
        projectionTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openSongSurface(this)
            // "Default" is also the Default *chip* in the strip above, so the segment is the one
            // that is not that chip.
            onNode(
                hasText("Default") and hasClickAction() and
                    !hasTestTag(elementChipTag(CustomizeElement.BACKGROUND_DEFAULT.name)),
            ).performScrollTo().performClick()
            waitForIdle()

            assertEquals(Constants.BACKGROUND_DEFAULT, get().stored().backgroundType)
        }
    }

    @Test
    fun `the color a surface had survives a trip through Image`() {
        projectionTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openSongSurface(this)
            chooseSegment("Image")
            chooseSegment("Color")

            val stored = get().stored()
            assertEquals(Constants.BACKGROUND_COLOR, stored.backgroundType)
            assertEquals("#123456", stored.backgroundColor)
        }
    }

    @Test
    fun `the file a surface had survives a trip through Color`() {
        projectionTab(output(Constants.BACKGROUND_IMAGE, image = "/photos/sunrise.jpg")) { get ->
            openSongSurface(this)
            chooseSegment("Color")

            assertEquals("/photos/sunrise.jpg", get().stored().backgroundImage, "the file outlives the type")
        }
    }

    @Test
    fun `a drawn surface keeps its opacity, dim and blur whatever type it is`() {
        for (type in listOf(Constants.BACKGROUND_COLOR, Constants.BACKGROUND_IMAGE, Constants.BACKGROUND_VIDEO)) {
            projectionTab(output(type)) { _ ->
                openSongSurface(this, override = false)
                onNodeWithText("Opacity").assertExists()
                onNodeWithText("Dim").assertExists()
                onNodeWithText("Blur").assertExists()
            }
        }
    }
}

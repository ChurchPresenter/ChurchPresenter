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
import org.churchpresenter.settings.StockPhotoSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which row the Background pane draws for each type a surface can be set to, and what picking a
 * type stores. The file pickers open a real chooser, so they are asserted as rendered, not driven.
 *
 * Ported from `ProjectionCustomizeBackgroundTypesTest`. The pane is the same; what changed is that
 * a profile follows the Background tab surface by surface until it is taken over, so the tests that
 * only look leave the surface following and see the inherited values, and the ones that click take
 * it over first.
 */
class ProfilesCustomizeBackgroundTypesTest {

    private fun output(type: String, image: String = "", video: String = ""): AppSettings {
        val backgrounds = BackgroundSettings(
            songBackground = BackgroundConfig(
                backgroundType = type,
                backgroundColor = "#123456",
                backgroundImage = image,
                backgroundVideo = video,
            ),
        )
        return profileDocument().copy(
            backgroundSettings = backgrounds,
            stockPhotoSettings = StockPhotoSettings(pexelsApiKey = "pex", pixabayApiKey = "pix"),
        )
    }

    private fun AppSettings.stored(): BackgroundConfig = backgroundFor(BackgroundScope.SONG)

    // ── What each type draws ──────────────────────────────────────────────────

    @Test
    fun `a color surface draws a color field`() {
        profilesTab(output(Constants.BACKGROUND_COLOR)) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("#123456").assertExists()
        }
    }

    @Test
    fun `an image surface draws the image picker`() {
        profilesTab(output(Constants.BACKGROUND_IMAGE)) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("Image File").assertExists()
            onNodeWithText("No image selected").assertExists()
        }
    }

    @Test
    fun `an image surface names the file it points at`() {
        profilesTab(output(Constants.BACKGROUND_IMAGE, image = "/photos/sunrise.jpg")) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("sunrise.jpg").assertExists()
        }
    }

    @Test
    fun `a video surface draws the video picker`() {
        profilesTab(output(Constants.BACKGROUND_VIDEO)) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("Video File").assertExists()
            onNodeWithText("No video selected").assertExists()
        }
    }

    @Test
    fun `a video surface names the clip it points at`() {
        profilesTab(output(Constants.BACKGROUND_VIDEO, video = "/clips/loop.mp4")) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("loop.mp4").assertExists()
        }
    }

    @Test
    fun `a transparent surface draws no source row at all`() {
        profilesTab(output(Constants.BACKGROUND_TRANSPARENT)) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("Image File").assertDoesNotExist()
            onNodeWithText("Video File").assertDoesNotExist()
            onNodeWithText("#123456").assertDoesNotExist()
        }
    }

    @Test
    fun `a surface following the default draws no source row either`() {
        profilesTab(output(Constants.BACKGROUND_DEFAULT)) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("Image File").assertDoesNotExist()
            onNodeWithText("#123456").assertDoesNotExist()
        }
    }

    // ── Switching between them ────────────────────────────────────────────────

    @Test
    fun `choosing Image stores the type and swaps the row in`() {
        profilesTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openBackgroundSurface()
            chooseSegment("Image")

            assertEquals(Constants.BACKGROUND_IMAGE, get().stored().backgroundType)
            onNodeWithText("No image selected").assertExists()
            onNodeWithText("#123456").assertDoesNotExist()
        }
    }

    @Test
    fun `choosing Video stores the type and swaps the row in`() {
        profilesTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openBackgroundSurface()
            chooseSegment("Video")

            assertEquals(Constants.BACKGROUND_VIDEO, get().stored().backgroundType)
            onNodeWithText("No video selected").assertExists()
        }
    }

    @Test
    fun `choosing Default hands the surface back to the one above it`() {
        profilesTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openBackgroundSurface()
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
        profilesTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openBackgroundSurface()
            chooseSegment("Image")
            chooseSegment("Color")

            val stored = get().stored()
            assertEquals(Constants.BACKGROUND_COLOR, stored.backgroundType)
            assertEquals("#123456", stored.backgroundColor)
        }
    }

    @Test
    fun `the file a surface had survives a trip through Color`() {
        profilesTab(output(Constants.BACKGROUND_IMAGE, image = "/photos/sunrise.jpg")) { get ->
            openBackgroundSurface()
            chooseSegment("Color")

            assertEquals("/photos/sunrise.jpg", get().stored().backgroundImage, "the file outlives the type")
        }
    }

    @Test
    fun `a drawn surface keeps its opacity, dim and blur whatever type it is`() {
        for (type in listOf(Constants.BACKGROUND_COLOR, Constants.BACKGROUND_IMAGE, Constants.BACKGROUND_VIDEO)) {
            profilesTab(output(type)) { _ ->
                openBackgroundSurface(own = false)
                onNodeWithText("Opacity").assertExists()
                onNodeWithText("Dim").assertExists()
                onNodeWithText("Blur").assertExists()
            }
        }
    }

    // ── Following, which is new here ──────────────────────────────────────────

    @Test
    fun `a surface follows the Background tab until it is taken over`() {
        profilesTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openBackgroundSurface(own = false)
            assertEquals(false, get().overrides(BackgroundScope.SONG), "profiles start out following")

            takeOverBackground()
            assertEquals(true, get().overrides(BackgroundScope.SONG))
        }
    }

    @Test
    fun `taking a surface over starts from the picture already on screen`() {
        profilesTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openBackgroundSurface()

            // Not a black default: the operator keeps what they could already see and edits from there.
            assertEquals("#123456", get().stored().backgroundColor)
            assertEquals(Constants.BACKGROUND_COLOR, get().stored().backgroundType)
        }
    }

    @Test
    fun `handing a surface back restores the Background tab's own values`() {
        profilesTab(output(Constants.BACKGROUND_COLOR)) { get ->
            openBackgroundSurface()
            chooseSegment("Video")
            assertEquals(Constants.BACKGROUND_VIDEO, get().stored().backgroundType, "the profile's own")

            followBackground()

            assertEquals(false, get().overrides(BackgroundScope.SONG))
            assertEquals(Constants.BACKGROUND_COLOR, get().stored().backgroundType, "the tab's again")
            assertEquals(
                Constants.BACKGROUND_COLOR,
                get().backgroundSettings.songBackground.backgroundType,
                "and the tab itself was never written to",
            )
        }
    }
}

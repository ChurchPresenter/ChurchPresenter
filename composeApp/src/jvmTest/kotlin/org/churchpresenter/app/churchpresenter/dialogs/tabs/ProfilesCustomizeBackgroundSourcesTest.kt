@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.StockPhotoSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a background surface keeps when its type changes, and the one setting this pane edits that
 * does not belong to the profile.
 *
 * Asserted on stored state rather than on the rows themselves. The camera row reads the machine's
 * device list and the Lottie row reads a templates directory, so what either draws is a property of
 * whoever runs the suite; what they *store* is not.
 */
class ProfilesCustomizeBackgroundSourcesTest {

    private fun output(
        type: String,
        config: BackgroundConfig.() -> BackgroundConfig = { this },
        stock: StockPhotoSettings = StockPhotoSettings(),
    ): AppSettings = profileDocument().copy(
        backgroundSettings = BackgroundSettings(
            songBackground = BackgroundConfig(backgroundType = type).config(),
        ),
        stockPhotoSettings = stock,
    )

    private fun AppSettings.stored(): BackgroundConfig = backgroundFor(BackgroundScope.SONG)

    // ── The rows each source type draws ─────────────────────────────────────────────────────────

    /**
     * The row's own caption, not what is inside it: the camera dropdown enumerates the machine and
     * the template picker reads a directory, so their contents are a property of whoever runs the
     * suite. The caption is the pane's.
     */
    @Test
    fun `a camera surface draws the device row`() {
        profilesTab(output(Constants.BACKGROUND_CAMERA)) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("CAMERA DEVICE").assertExists()
        }
    }

    @Test
    fun `a Lottie surface draws the template row`() {
        profilesTab(output(Constants.BACKGROUND_LOTTIE)) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("Template").assertExists()
            onNodeWithText("No template selected").assertExists()
        }
    }

    @Test
    fun `an image surface draws neither`() {
        profilesTab(output(Constants.BACKGROUND_IMAGE)) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("CAMERA DEVICE").assertDoesNotExist()
            onNodeWithText("Template").assertDoesNotExist()
        }
    }

    /** The gradient the full screen gained: offered here, not only on a band. */
    @Test
    fun `a full-screen surface offers Gradient among its types`() {
        profilesTab(output(Constants.BACKGROUND_COLOR)) { _ ->
            openBackgroundSurface(own = false)
            onNodeWithText("Gradient").assertExists()
        }
    }

    // ── The stock media keys ────────────────────────────────────────────────────────────────────

    /**
     * The Pexels and Pixabay keys are the one thing this pane edits that is *not* the profile's:
     * they belong to the install, so they go to the document rather than onto the profile.
     */
    @Test
    fun `the stock media keys are the profile pane's one global setting`() {
        val doc = output(
            Constants.BACKGROUND_IMAGE,
            stock = StockPhotoSettings(pexelsApiKey = "pex", pixabayApiKey = "pix"),
        )
        profilesTab(doc) { get ->
            openBackgroundSurface()

            assertEquals("pex", get().stockPhotoSettings.pexelsApiKey, "the keys survive the pane")
            assertEquals("pix", get().stockPhotoSettings.pixabayApiKey)
        }
    }

    // ── What each type keeps ────────────────────────────────────────────────────────────────────

    @Test
    fun `a camera surface keeps its device through a trip to Color`() {
        val doc = output(Constants.BACKGROUND_CAMERA)
        profilesTab(doc) { get ->
            openBackgroundSurface()
            val before = get().stored().camera
            chooseSegment("Color")

            assertEquals(before, get().stored().camera, "the device outlives the type")
        }
    }

    @Test
    fun `a Lottie surface keeps its file through a trip to Color`() {
        val doc = output(Constants.BACKGROUND_LOTTIE, config = { copy(backgroundLottie = "/bands/wipe.json") })
        profilesTab(doc) { get ->
            openBackgroundSurface()
            chooseSegment("Color")

            assertEquals(
                "/bands/wipe.json",
                get().stored().backgroundLottie,
                "the animation outlives the type",
            )
        }
    }
}

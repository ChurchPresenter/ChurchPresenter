@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.presenter.LottieBandTestSupport
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A lower-third profile that has never explicitly taken over its Bible band -- it is only showing a
 * Lottie template because the app-wide Settings -> Background band happens to be one -- used to lose
 * a template picked from the dropdown on the very next recomposition: the edit went into
 * `profile.backgroundSettings` but never into `profile.backgroundOverrides`, and
 * `resolveBackgroundSurfaces` only reads a profile's own copy of a surface actually listed there.
 * "Picking a different template snaps back to the old one" (#693).
 *
 * The fix reuses [ContentBackgroundGroup]'s existing Own/App default mechanism -- already correct
 * for every other background type -- for the band too, instead of `BandGroup`'s own separate,
 * narrower ownership path.
 */
class LowerThirdBandOwnershipTest {

    private val templatesDir = SettingsManager.bibleLowerThirdsDir().apply { mkdirs() }
    private val oldFile = LottieBandTestSupport.writeTemplate(templatesDir, name = "old.json")
    private val newFile = LottieBandTestSupport.writeTemplate(templatesDir, name = "new.json")

    @AfterTest
    fun cleanUp() {
        oldFile.delete()
        newFile.delete()
    }

    private val globalLottie = BackgroundConfig(
        backgroundType = Constants.BACKGROUND_LOTTIE,
        backgroundLottie = oldFile.absolutePath,
    )

    /** A band profile inheriting the app-wide Bible band, exactly as it never took it over. */
    private fun doc() = AppSettings(
        backgroundSettings = BackgroundSettings(bibleLowerThirdBackground = globalLottie),
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(
                OutputProfile(
                    id = PROFILE_ID,
                    name = "Band",
                    displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                    // Not owned: backgroundOverrides is empty, so this profile's own (default)
                    // backgroundSettings is never read for BIBLE_LOWER_THIRD.
                ),
            ),
        ),
    )

    @Test
    fun `an inherited Lottie band gets the same App default - Own row every other type has`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            // Not owned yet -- this used to hide the row entirely and jump straight to the
            // template picker, with no way to tell "app-wide" and "this profile's own" apart.
            assertEquals(emptySet(), get().profile().backgroundOverrides)
            onNodeWithTag(BG_PROFILE_DEFAULT_TAG, useUnmergedTree = true).assertExists()
            onNodeWithTag(BG_OWN_TAG, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `picking Custom claims the surface, and a template picked after that persists`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            assertEquals(emptySet(), get().profile().backgroundOverrides)

            onNodeWithTag(BG_OWN_TAG, useUnmergedTree = true).performClick()
            waitForIdle()
            assertEquals(setOf(BackgroundScope.BIBLE_LOWER_THIRD.name), get().profile().backgroundOverrides)
            assertEquals(
                oldFile.absolutePath,
                get().profile().backgroundSettings.configFor(BackgroundScope.BIBLE_LOWER_THIRD).backgroundLottie,
                "own copy seeded from what was already showing",
            )

            // Pick a different template from the dropdown, the way the reported bug did.
            onAllNodesWithText("old").onLast().performScrollTo().performClick()
            waitForIdle()
            onAllNodesWithText("new").onLast().performClick()
            waitForIdle()

            // Several more render passes -- the exact thing that used to lose the edit.
            repeat(5) { waitForIdle() }
            assertEquals(
                newFile.absolutePath,
                get().profile().backgroundSettings.configFor(BackgroundScope.BIBLE_LOWER_THIRD).backgroundLottie,
                "still the newly picked template after several more render passes",
            )
        }
    }

    @Test
    fun `the band's own Uses row takes the band over, and hands it back`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)

            onNodeWithTag(BAND_SOURCE_OWN_TAG, useUnmergedTree = true).performScrollTo().performClick()
            waitForIdle()
            assertEquals(setOf(BackgroundScope.BIBLE_LOWER_THIRD.name), get().profile().backgroundOverrides)

            onNodeWithTag(BAND_SOURCE_APP_DEFAULT_TAG, useUnmergedTree = true).performScrollTo().performClick()
            waitForIdle()
            assertEquals(emptySet(), get().profile().backgroundOverrides)
        }
    }
}

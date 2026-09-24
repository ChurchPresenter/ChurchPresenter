@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That an edit made in the Profiles editor actually reaches the document.
 *
 * Every settings control in `ProfileEditor` writes through one shared lambda, and that lambda used
 * to make *two* writes into the same synchronous sink: the first put the edit on the profile, and
 * the second handed back a whole pre-edit snapshot -- carrying its stale `projectionSettings`
 * straight over the write just made. The document came out byte-identical, so every control in the
 * editor silently snapped back, in both the pane and the preview strip.
 *
 * Nothing caught it. A screenshot cannot see a value that reverts, and no test drove an edit
 * through this editor and read the document back out, which is exactly what these do.
 */
class ProfileEditorWriteBackTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        // Applying writes settings.json, and SettingsManager resolves its path from user.home.
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-profile-editor-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private class Saved {
        var settings: AppSettings? = null
    }

    /** Two profiles, so a write to the first can be shown not to reach the second. */
    private fun twoProfiles() = AppSettings(
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(
                OutputProfile(id = "main", name = "Main"),
                OutputProfile(id = "foyer", name = "Foyer"),
            ),
        ),
    )

    /**
     * Opens the real dialog on the Profiles tab and runs [block], which edits and then applies.
     *
     * **The scene is sized, not left at the default 1024x768.** The Profiles editor spends 260dp on
     * the rail, 176dp on the category rail and 426dp on the preview column before the pane gets
     * anything, and its controls are fixed-size cells that a row too narrow *clips rather than
     * shrinks*. A clipped control keeps its semantics, so the finder still matches it and the click
     * lands on nothing -- which looks exactly like a broken write path and is not one.
     *
     * The tab is reached by clicking it rather than by passing its index: the index is a private
     * constant that has already been renumbered once, and a stale number here would silently test a
     * different tab.
     */
    private fun profilesTab(initial: AppSettings, block: SkikoComposeUiTest.(Saved) -> Unit) {
        val saved = Saved()
        runSkikoComposeUiTest(size = Size(DIALOG_WIDTH, DIALOG_HEIGHT), density = Density(1f)) {
            setContent {
                OptionsDialogContent(
                    theme = ThemeMode.LIGHT,
                    settingsManager = SettingsManager(),
                    companionServer = CompanionServer(),
                    remoteClientManager = RemoteClientManager(),
                    onDismiss = {},
                    onSave = { saved.settings = it },
                    initialSettings = initial,
                    detectScreens = { emptyList() },
                )
            }
            onNode(hasText("Profiles") and isSelectable()).performClick()
            block(saved)
        }
    }

    /** The saved document, or a failure naming what was missing rather than an NPE. */
    private fun Saved.applied(): AppSettings =
        settings ?: error("Apply never fired, so nothing was saved")

    private fun SkikoComposeUiTest.apply() = onNodeWithText("Apply").performClick()

    /**
     * Moves the Bible pane's verse block to the top -- a control drawn under the pane's default
     * element with no gating at all, so this stays a test of the write path rather than of whichever
     * conditions happen to make some other control appear.
     *
     * **Top, specifically.** [Constants.BOTTOM] is the default, so clicking Bottom writes the value
     * already there and every assertion below passes whether the write path works or not. The first
     * version of this test did exactly that and passed against the bug it was written to catch; any
     * replacement control must likewise be moved to a value it does not already hold.
     *
     * `hasClickAction()` as well as the description: the description belongs to the `Image` and the
     * click to the `OutlinedButton` around it.
     */
    private fun SkikoComposeUiTest.alignVerseBlockTop() =
        onNode(hasContentDescription("Align Top") and hasClickAction())
            .performScrollTo()
            .performClick()

    @Test
    fun `an edit in the profile pane reaches the profile`() = profilesTab(twoProfiles()) { saved ->
        alignVerseBlockTop()
        apply()

        val profile = saved.applied().projectionSettings.outputProfiles.first { it.id == "main" }
        assertEquals(Constants.TOP, profile.bibleSettings.verticalAlignment)
    }

    @Test
    fun `an edit in the profile pane leaves the global document alone`() =
        profilesTab(twoProfiles()) { saved ->
            alignVerseBlockTop()
            apply()

            // The profile overrides the document; it must not write through to it, or every output
            // without an override of its own would move too.
            assertEquals(Constants.BOTTOM, saved.applied().bibleSettings.verticalAlignment)
        }

    @Test
    fun `an edit in one profile leaves the others alone`() = profilesTab(twoProfiles()) { saved ->
        alignVerseBlockTop()
        apply()

        val other = saved.applied().projectionSettings.outputProfiles.first { it.id == "foyer" }
        assertEquals(Constants.BOTTOM, other.bibleSettings.verticalAlignment)
    }

    private companion object {
        /** `OptionsDialog`'s own preferred window size, at density 1. */
        const val DIALOG_WIDTH = 1400f
        const val DIALOG_HEIGHT = 900f
    }
}

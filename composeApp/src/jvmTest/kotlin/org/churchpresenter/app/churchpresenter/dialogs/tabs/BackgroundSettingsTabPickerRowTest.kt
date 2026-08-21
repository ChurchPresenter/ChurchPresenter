@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * Covers the picker rows that appear under a slot set to Image or Video Loop: the file field itself,
 * the two browse buttons beside it, and the two ATEM upload buttons that join them once a switcher
 * is configured.
 *
 * **Nothing in these rows is clicked**, and each button is out of reach for its own reason:
 *
 *  * The file field opens a **native** file chooser, which would block the run.
 *  * "Browse downloaded library" and "Browse stock photos/videos" open `DialogWindow`s — real AWT
 *    windows — which throw `HeadlessException` under the suite's headless JVM. (The colour picker's
 *    dialog is testable because it uses the in-composition `Dialog` instead, and it is driven in
 *    `BackgroundSettingsTabTest`.) Reaching these two would mean either running the suite with a
 *    display or moving them to `Dialog`; neither is in scope here, so the gap is recorded rather
 *    than papered over with a test that cannot run.
 *  * The ATEM upload buttons open a TCP connection to the configured switcher.
 *
 * What is left, and what this class asserts, is that each row is assembled correctly and that every
 * button is present, enabled and clickable for the configuration that should show it.
 */
class BackgroundSettingsTabPickerRowTest {

    private fun settingsWith(change: BackgroundSettings.() -> BackgroundSettings): AppSettings =
        AppSettings().let { it.copy(backgroundSettings = it.backgroundSettings.change()) }

    private fun withAtem(host: String, settings: AppSettings): AppSettings =
        settings.copy(atemSettings = settings.atemSettings.copy(host = host))

    // ── Image rows ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an image row offers a file field and both browse buttons`() {
        backgroundTab(initial = settingsWith { copy(defaultBackgroundType = Constants.BACKGROUND_IMAGE) }) { _ ->
            onNodeWithText("Background Image:").assertExists("the row must be captioned")
            onNodeWithText("No image selected").assertHasClickAction()
            onNodeWithContentDescription("Browse downloaded library").assertIsEnabled().assertHasClickAction()
            onNodeWithContentDescription("Browse stock photos/videos").assertIsEnabled().assertHasClickAction()
        }
    }

    @Test
    fun `each image slot gets its own picker row`() {
        val threeImageSlots = settingsWith {
            copy(
                defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_IMAGE,
                bibleBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE),
            )
        }
        backgroundTab(initial = threeImageSlots) { _ ->
            onAllNodesWithText("Background Image:").assertCountEquals(3)
            onAllNodesWithContentDescription("Browse downloaded library").assertCountEquals(3)
            onAllNodesWithContentDescription("Browse stock photos/videos").assertCountEquals(3)
        }
    }

    @Test
    fun `an image row names the stored file rather than its whole path`() {
        backgroundTab(
            initial = settingsWith {
                copy(
                    defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                    defaultBackgroundImage = "/Users/someone/Pictures/backdrops/mountain view.jpeg",
                )
            },
        ) { _ ->
            onNodeWithText("mountain view.jpeg").assertExists("the row must show just the file name")
            onAllNodesWithText("No image selected").assertCountEquals(0)
        }
    }

    // ── Video rows ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a video row offers a file field and both browse buttons`() {
        backgroundTab(initial = settingsWith { copy(defaultBackgroundType = Constants.BACKGROUND_VIDEO) }) { _ ->
            onNodeWithText("Background Video:").assertExists("the row must be captioned")
            onNodeWithText("No video selected").assertHasClickAction()
            onNodeWithContentDescription("Browse downloaded library").assertIsEnabled().assertHasClickAction()
            onNodeWithContentDescription("Browse stock photos/videos").assertIsEnabled().assertHasClickAction()
        }
    }

    @Test
    fun `a video row names the stored clip rather than its whole path`() {
        backgroundTab(
            initial = settingsWith {
                copy(
                    defaultBackgroundType = Constants.BACKGROUND_VIDEO,
                    defaultBackgroundVideo = "/Users/someone/Movies/loops/slow clouds.mp4",
                )
            },
        ) { _ ->
            onNodeWithText("slow clouds.mp4").assertExists("the row must show just the file name")
            onAllNodesWithText("No video selected").assertCountEquals(0)
        }
    }

    // ── ATEM upload buttons ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the ATEM uploads appear per image row once a switcher is configured`() {
        val twoImageSlots = withAtem(
            host = "10.0.0.5",
            settings = settingsWith {
                copy(
                    defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                    defaultBackgroundImage = "/tmp/a.png",
                    bibleBackground = BackgroundConfig(
                        backgroundType = Constants.BACKGROUND_IMAGE,
                        backgroundImage = "/tmp/b.png",
                    ),
                )
            },
        )
        backgroundTab(initial = twoImageSlots) { _ ->
            onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(2)
            onAllNodesWithContentDescription("Upload to Background Slot 2").assertCountEquals(2)
            onAllNodesWithText("1").assertCountEquals(2)
            onAllNodesWithText("2").assertCountEquals(2)
        }
    }

    @Test
    fun `an image row with no file offers no ATEM upload even with a switcher configured`() {
        val noFile = withAtem(
            host = "10.0.0.5",
            settings = settingsWith { copy(defaultBackgroundType = Constants.BACKGROUND_IMAGE) },
        )
        backgroundTab(initial = noFile) { _ ->
            onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(0)
            onNodeWithText("No image selected").assertExists("the row is otherwise complete")
        }
    }

    @Test
    fun `a blank switcher host offers no ATEM upload even with a file chosen`() {
        val blankHost = withAtem(
            host = "   ",
            settings = settingsWith {
                copy(defaultBackgroundType = Constants.BACKGROUND_IMAGE, defaultBackgroundImage = "/tmp/a.png")
            },
        )
        backgroundTab(initial = blankHost) { _ ->
            onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(0)
        }
    }

    @Test
    fun `the ATEM upload buttons are enabled and clickable when shown`() {
        val configured = withAtem(
            host = "10.0.0.5",
            settings = settingsWith {
                copy(defaultBackgroundType = Constants.BACKGROUND_IMAGE, defaultBackgroundImage = "/tmp/a.png")
            },
        )
        backgroundTab(initial = configured) { _ ->
            onNodeWithContentDescription("Upload to Background Slot 1").assertIsEnabled().assertHasClickAction()
            onNodeWithContentDescription("Upload to Background Slot 2").assertIsEnabled().assertHasClickAction()
        }
    }
}

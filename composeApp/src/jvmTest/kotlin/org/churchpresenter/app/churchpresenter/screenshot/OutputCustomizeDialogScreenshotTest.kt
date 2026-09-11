@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.app.churchpresenter.dialogs.tabs.CustomizeElement
import org.churchpresenter.app.churchpresenter.dialogs.tabs.CustomizePane
import org.churchpresenter.app.churchpresenter.dialogs.tabs.LocalApplySettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.OutputCustomizeDialog
import org.churchpresenter.app.churchpresenter.dialogs.tabs.elementChipTag
import org.churchpresenter.app.churchpresenter.dialogs.tabs.railTag
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The per-screen Customize dialog (Display Settings), on its Songs pane's Title Slide chip.
 *
 * The chip carries the same controls the global Song tab's title-slide view does, one element at a
 * time, for this one output -- and the preview shows this screen's own title slide. Composed under
 * an Options draft, as it is in the app, so the Apply button is drawn beside Done.
 *
 * Light and dark are separate images: the dialog is taller than a stacked pair reads well at.
 */
class OutputCustomizeDialogScreenshotTest {

    /** The Title Slide chip as it opens: the slide-wide switches, then the Title element. */
    @Test
    fun `the title slide chip`() = shoot("song_title_slide")

    /** A credit, with the profile it is drawn from and its own show switch. */
    @Test
    fun `the title slide's author`() = shoot("song_title_slide_author") {
        onAllNodesWithText("Author")[0].performClick()
        waitForIdle()
    }

    /** The band: the same chip, but no vertical alignment, and the band's own smaller profiles. */
    @Test
    fun `the title slide on a lower third`() =
        shoot("song_title_slide_lower_third", mode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)

    /** Following the global settings: the controls dimmed and inert, the preview not. */
    @Test
    fun `the title slide chip following the global settings`() =
        shoot("song_title_slide_following", overridden = false)

    private fun shoot(
        name: String,
        mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
        overridden: Boolean = true,
        drive: SkikoComposeUiTest.() -> Unit = {},
    ) = separateThemes(SECTION, name) { theme, file ->
        runSkikoComposeUiTest(size = Size(WINDOW_WIDTH, WINDOW_HEIGHT), density = Density(1f)) {
            setContent {
                ChurchPresenterTheme(themeMode = theme) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            CompositionLocalProvider(LocalApplySettings provides {}) {
                                OutputCustomizeDialog(
                                    screenLabel = "Screen 1",
                                    assignment = ScreenAssignment(
                                        displayMode = mode,
                                        songOverride = if (overridden) titleSlideSong() else null,
                                    ),
                                    globalSettings = AppSettings(songSettings = titleSlideSong()),
                                    onApply = {},
                                    onDismiss = {},
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag(railTag(CustomizePane.SONGS.name)).performClick()
            waitForIdle()
            onNodeWithTag(elementChipTag(CustomizeElement.SONG_TITLE_SLIDE.name)).performClick()
            waitForIdle()
            drive()
            waitForIdle()
            // The dialog is a popup: its root is the last one composed.
            captureTo(file, rootIndex = onAllNodes(isRoot()).fetchSemanticsNodes().lastIndex)
        }
    }

    private fun titleSlideSong() = SongSettings(
        titleSlideEnabled = true,
        titleSlideShowCcli = true,
        titleSlideShowTempo = true,
    )

    private companion object {
        const val SECTION = "outputCustomizeDialog"
        const val WINDOW_WIDTH = 1100f
        const val WINDOW_HEIGHT = 800f
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.models.songs.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers how the tab behaves when its *surroundings* recompose rather than its controls being used.
 *
 * The settings dialog re-renders the whole tab whenever anything above it changes — a presenter
 * being connected, another tab's state moving — and the tab is a 1,400-line tree of eight nearly
 * identical styled-text blocks. Re-running all of that on every unrelated frame would be felt, so
 * the sections have to drop out of recomposition when their own inputs have not moved. These tests
 * hold the settings instance and the change callback fixed, move something else, and assert the tab
 * still shows what it should — exercising the skip paths the control tests never reach, because
 * those change the settings on every interaction.
 */
class SongSettingsTabRecompositionTest {

    /** A change callback with a stable identity, so recomposing the caller does not disturb the tab. */
    private val inertCallback: (((AppSettings) -> AppSettings) -> Unit) = { }

    @Test
    fun `the tab survives a recomposition that changes none of its inputs`() = runComposeUiTest {
        val settings = AppSettings()
        var tick by mutableStateOf(0)
        setContent {
            MaterialTheme {
                Column {
                    Text("tick $tick")
                    SongSettingsTab(
                        settings = settings,
                        onSettingsChange = inertCallback,
                        presenterManager = null,
                    )
                }
            }
        }
        onNodeWithText("tick 0").assertExists("the surrounding content must render")
        onNodeWithText("Song Title Slide").assertExists("and so must the tab")

        runOnIdle { tick = 1 }
        waitForIdle()

        onNodeWithText("tick 1").assertExists("the surrounding content must have re-rendered")
        onNodeWithText("Song Title Slide").assertExists("the tab must still be on screen after skipping")
        onNodeWithTag("song_titleSlideEnabled").assertExists("and still hold its controls")
        numberFields().assertCountEquals(15)
    }

    @Test
    fun `connecting a presenter adds the auto-fit buttons without disturbing the rest of the tab`() =
        runComposeUiTest {
            val settings = AppSettings().let {
                it.copy(
                    projectionSettings = it.projectionSettings.copy(
                        screenAssignments = listOf(
                            ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN),
                            ScreenAssignment(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL),
                        ),
                    ),
                )
            }
            var manager by mutableStateOf<PresenterManager?>(null)
            setContent {
                MaterialTheme {
                    SongSettingsTab(
                        settings = settings,
                        onSettingsChange = inertCallback,
                        presenterManager = manager,
                    )
                }
            }
            autoFitButtons().assertCountEquals(0)
            numberFields().assertCountEquals(15)

            // Only the presenter changes: the lyrics sections must pick it up, and every other
            // section — title slide, song number, title, margins, look ahead — is free to skip.
            runOnIdle {
                manager = PresenterManager().apply {
                    setLyricSection(LyricSection(title = "Be Thou My Vision", lines = listOf("Be thou my vision")))
                    setPresentingMode(Presenting.LYRICS)
                }
            }
            waitForIdle()

            autoFitButtons().assertCountEquals(2)
            onNodeWithText("Song Title Slide").assertExists("the title-slide section must be untouched")
            onNodeWithText("Look Ahead Next Section (Lower Third)")
                .assertExists("the look-ahead sections must be untouched")
            numberFields().assertCountEquals(15)
        }

    @Test
    fun `disconnecting a presenter removes the auto-fit buttons again`() = runComposeUiTest {
        val settings = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
                ),
            )
        }
        var manager by mutableStateOf<PresenterManager?>(PresenterManager())
        setContent {
            MaterialTheme {
                SongSettingsTab(
                    settings = settings,
                    onSettingsChange = inertCallback,
                    presenterManager = manager,
                )
            }
        }
        autoFitButtons().assertCountEquals(2)

        runOnIdle { manager = null }
        waitForIdle()

        autoFitButtons().assertCountEquals(0)
        onNodeWithText("Fullscreen Display").assertExists("the section itself must remain")
    }

    /**
     * Going live is a state change *inside* the presenter rather than a new settings object, so the
     * tab is not re-rendered from the top: the derived "are lyrics live" state drives the buttons on
     * its own.
     */
    @Test
    fun `the auto-fit buttons enable themselves when the presenter goes live`() = runComposeUiTest {
        val settings = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
                ),
            )
        }
        val manager = PresenterManager()
        setContent {
            MaterialTheme {
                SongSettingsTab(
                    settings = settings,
                    onSettingsChange = inertCallback,
                    presenterManager = manager,
                )
            }
        }
        autoFitButtons()[0].performScrollTo().assertIsNotEnabled()

        runOnIdle {
            manager.setLyricSection(LyricSection(
                title = "Here Is Love",
                lines = listOf("Here is love vast as the ocean"),
            ))
            manager.setPresentingMode(Presenting.LYRICS)
        }
        waitForIdle()

        autoFitButtons()[0].assertIsEnabled()
    }

    /**
     * A settings change re-renders the tab from the top. The section that changed must show the new
     * value, and every other section must still be intact afterwards.
     */
    @Test
    fun `a settings change re-renders the tab around the control that caused it`() = runComposeUiTest {
        var current = AppSettings()
        setContent {
            MaterialTheme {
                var state by mutableStateOf(current)
                SongSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                    presenterManager = null,
                )
            }
        }
        onNodeWithTag("song_crossfade").performScrollTo().performClick()
        waitForIdle()

        assertEquals(true, current.songSettings.crossfade, "the click must have produced a new settings object")
        onNodeWithText("Song Title Slide").assertExists("the left column must survive the re-render")
        onNodeWithText("Look Ahead (Lower Third)").assertExists("and so must the look-ahead column")
        numberFields().assertCountEquals(15)
        colorFields().assertCountEquals(8)
    }
}

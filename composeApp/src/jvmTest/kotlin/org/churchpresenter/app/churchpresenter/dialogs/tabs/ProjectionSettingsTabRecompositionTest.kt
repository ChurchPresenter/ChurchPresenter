@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers what the tab does when its *surroundings* recompose, and when the machine's displays change
 * underneath it, rather than when its controls are used.
 *
 * Both matter here more than on most tabs. The settings dialog re-renders the whole tab whenever
 * anything above it moves, and this one builds a grid row per output plus a browser-source table on
 * every pass — work that has to drop out when nothing it reads has changed. And a projector being
 * plugged in or unplugged is a normal event mid-service: the tab has to grow or shrink its grid and
 * fill in the new slot without disturbing what was already configured.
 */
class ProjectionSettingsTabRecompositionTest {

    /** A change callback with a stable identity, so recomposing the caller does not disturb the tab. */
    private val inertCallback: (((AppSettings) -> AppSettings) -> Unit) = { }

    private fun ComposeUiTest.assertGridIsIntact(rows: Int) {
        gridButtons().assertCountEquals(1 + rows * Grid.CONTROLS_PER_ROW + Grid.trailing)
        for (row in 0 until rows) {
            gridButton(Grid.displayMode(row)).assertTextEquals("Full Screen")
            gridButton(Grid.contentOutputs(row)).assertTextEquals("15 of 16 enabled")
        }
    }

    @Test
    fun `the tab survives a recomposition that changes none of its inputs`() = runComposeUiTest {
        val settings = AppSettings()
        var tick by mutableStateOf(0)
        setContent {
            MaterialTheme {
                Column {
                    Text("tick $tick")
                    ProjectionSettingsTab(
                        settings = settings,
                        onSettingsChange = inertCallback,
                        companionServer = CompanionServer(),
                        detectScreens = { twoExternalScreens() },
                        ffmpegProbe = { PINNED_FFMPEG },
                    )
                }
            }
        }
        onNodeWithText("tick 0").assertExists()
        assertGridIsIntact(rows = 2)

        runOnIdle { tick = 1 }
        waitForIdle()

        onNodeWithText("tick 1").assertExists("the surrounding content must have re-rendered")
        onNodeWithText("Screen Assignment").assertExists("the tab must still be on screen after skipping")
        assertGridIsIntact(rows = 2)
    }

    /**
     * A fully populated tab — every card carrying content — skipping a recomposition. The plain
     * fixture above has no browser sources, so it says nothing about whether that table drops out
     * of recomposition correctly.
     */
    @Test
    fun `a populated tab survives a recomposition that changes nothing`() = runComposeUiTest {
        val settings = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    browserSourceOutputs = listOf(ScreenAssignment(), ScreenAssignment(browserSourceEnabled = false)),
                ),
            )
        }
        var tick by mutableStateOf(0)
        setContent {
            MaterialTheme {
                Column {
                    Text("tick $tick")
                    ProjectionSettingsTab(
                        settings = settings,
                        onSettingsChange = inertCallback,
                        companionServer = CompanionServer(),
                        detectScreens = { twoExternalScreens() },
                        ffmpegProbe = { PINNED_FFMPEG },
                    )
                }
            }
        }
        onAllNodesWithText("Remove").assertCountEquals(2)
        onNodeWithText("Browser Source 2").assertExists()

        runOnIdle { tick = 1 }
        waitForIdle()

        onNodeWithText("tick 1").assertExists()
        onAllNodesWithText("Remove").assertCountEquals(2)
        onNodeWithText("Browser Source 1").assertExists()
        onNodeWithText("Browser Source 2").assertExists()
        onAllNodesWithText("Require API Key").assertCountEquals(2)
    }

    // ── Displays coming and going ───────────────────────────────────────────────────────────────

    /**
     * The display list is read once per composition — `remember { detectScreens() }` has no key — so
     * a projector plugged in while the settings dialog is open is not noticed until the dialog is
     * reopened. This pins that behaviour rather than wishing it away: the grid does not move when
     * the detected screens change underneath it, and a fresh composition picks the new list up.
     *
     * (Pre-existing: the tab cached `GraphicsEnvironment.screenDevices` the same way before the
     * detection call was hoisted into a parameter.)
     */
    @Test
    fun `the display list is read once per composition`() = runComposeUiTest {
        var screens by mutableStateOf(oneExternalScreen())
        setContent {
            MaterialTheme {
                var state by mutableStateOf(AppSettings())
                ProjectionSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state) },
                    companionServer = CompanionServer(),
                    detectScreens = { screens },
                    ffmpegProbe = { PINNED_FFMPEG },
                )
            }
        }
        onNodeWithText("Presenter windows: 1").assertExists()

        runOnIdle { screens = twoExternalScreens() }
        waitForIdle()

        onNodeWithText("Presenter windows: 1")
            .assertExists("a display appearing mid-session is not picked up until the tab is reopened")
        onAllNodesWithText("Screen 2").assertCountEquals(0)
    }

    @Test
    fun `reopening the tab picks up a display that appeared`() {
        // The same settings rendered against a longer screen list — what happens on reopen.
        projectionTab(screens = oneExternalScreen()) { _ ->
            onNodeWithText("Presenter windows: 1").assertExists()
        }
        projectionTab(screens = twoExternalScreens()) { get ->
            onNodeWithText("Presenter windows: 2").assertExists("the new display must now have a row")
            gridButton(Grid.targetDisplay(row = 1)).assertTextEquals("D2 (3840x2160)")
            assertEquals(2, get().projectionSettings.screenAssignments[1].targetDisplay)
            assertEquals(3840, get().projectionSettings.screenAssignments[1].targetBoundsW)
        }
    }

    @Test
    fun `reopening with no external display falls back to the dev window layout`() {
        projectionTab(screens = noExternalScreens()) { _ ->
            onNodeWithText("Dev Window").assertExists("the fallback row must take over")
            onNodeWithText("SIMULATE OUTPUTS").assertExists("and bring its stepper with it")
            onAllNodesWithText("Screen 1").assertCountEquals(0)
        }
    }

    /**
     * The Content Outputs dialog builds sixteen toggle cells, two language cells and a live preview
     * every time it composes. With the dialog open and nothing it reads changing, all of that has to
     * drop out — none of the other recomposition tests has the dialog on screen, so none of them say
     * anything about those cells.
     */
    @Test
    fun `the content outputs dialog survives a recomposition that changes nothing`() = runComposeUiTest {
        val settings = AppSettings()
        var tick by mutableStateOf(0)
        setContent {
            MaterialTheme {
                Column {
                    Text("tick $tick")
                    ProjectionSettingsTab(
                        settings = settings,
                        onSettingsChange = inertCallback,
                        companionServer = CompanionServer(),
                        detectScreens = { twoExternalScreens() },
                        ffmpegProbe = { PINNED_FFMPEG },
                    )
                }
            }
        }
        gridButton(Grid.contentOutputs(row = 0)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("15 of 16 content types enabled on this screen").assertExists()
        onAllNodesWithText("Media").assertCountEquals(2)

        runOnIdle { tick = 1 }
        waitForIdle()

        onNodeWithText("tick 1").assertExists("the surrounding content must have re-rendered")
        onNodeWithText("Content Outputs — Screen 1").assertExists("the dialog must still be up")
        onNodeWithText("15 of 16 content types enabled on this screen").assertExists()
        onAllNodesWithText("Media").assertCountEquals(2)
        // The Bible chip is a bare label with one translation configured; it appears alongside the
        // dialog's own checkbox label, so both are still present.
        onAllNodesWithText("Bible").assertCountEquals(2)
    }

    /**
     * The same dialog when one toggle *does* change: the cell that changed re-renders, the preview
     * and header follow it, and the fifteen cells that did not change are left where they were.
     */
    @Test
    fun `changing one toggle re-renders only what depends on it`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                ProjectionSettingsTab(
                    settings = settings,
                    onSettingsChange = { transform -> settings = transform(settings) },
                    companionServer = CompanionServer(),
                    detectScreens = { twoExternalScreens() },
                    ffmpegProbe = { PINNED_FFMPEG },
                )
            }
        }
        gridButton(Grid.contentOutputs(row = 0)).performScrollTo().performClick()
        waitForIdle()

        runOnIdle {
            settings = settings.copy(
                projectionSettings = settings.projectionSettings.withAssignment(
                    0,
                    settings.projectionSettings.getAssignment(0).copy(showCanvas = false),
                ),
            )
        }
        waitForIdle()

        onNodeWithText("14 of 16 content types enabled on this screen").assertExists()
        onAllNodesWithText("Canvas").assertCountEquals(1) // dropped from the preview
        onAllNodesWithText("Media").assertCountEquals(2) // every other cell untouched
        onAllNodesWithText("Dictionary").assertCountEquals(2)
    }

    // ── Stored settings reaching the screen without interaction ─────────────────────────────────

    @Test
    fun `a stored display mode change reaches its row`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                ProjectionSettingsTab(
                    settings = settings,
                    onSettingsChange = inertCallback,
                    companionServer = CompanionServer(),
                    detectScreens = { twoExternalScreens() },
                    ffmpegProbe = { PINNED_FFMPEG },
                )
            }
        }
        gridButton(Grid.displayMode(row = 0)).assertTextEquals("Full Screen")

        runOnIdle {
            settings = settings.copy(
                projectionSettings = settings.projectionSettings.withAssignment(
                    0,
                    settings.projectionSettings.getAssignment(0)
                        .copy(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
                ),
            )
        }
        waitForIdle()

        gridButton(Grid.displayMode(row = 0)).assertTextEquals("Stage Monitor")
        gridButton(Grid.displayMode(row = 1)).assertTextEquals("Full Screen")
    }

    @Test
    fun `a stored content change reaches its summary button`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                ProjectionSettingsTab(
                    settings = settings,
                    onSettingsChange = inertCallback,
                    companionServer = CompanionServer(),
                    detectScreens = { twoExternalScreens() },
                    ffmpegProbe = { PINNED_FFMPEG },
                )
            }
        }
        gridButton(Grid.contentOutputs(row = 1)).assertTextEquals("15 of 16 enabled")

        runOnIdle {
            settings = settings.copy(
                projectionSettings = settings.projectionSettings.withAssignment(
                    1,
                    settings.projectionSettings.getAssignment(1).copy(showMedia = false, showCanvas = false),
                ),
            )
        }
        waitForIdle()

        gridButton(Grid.contentOutputs(row = 1)).assertTextEquals("13 of 16 enabled")
        gridButton(Grid.contentOutputs(row = 0)).assertTextEquals("15 of 16 enabled")
    }

    @Test
    fun `adding a browser source in settings adds its row`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                ProjectionSettingsTab(
                    settings = settings,
                    onSettingsChange = inertCallback,
                    companionServer = CompanionServer(),
                    detectScreens = { twoExternalScreens() },
                    ffmpegProbe = { PINNED_FFMPEG },
                )
            }
        }
        onAllNodesWithText("Remove").assertCountEquals(0)

        runOnIdle {
            settings = settings.copy(
                projectionSettings = settings.projectionSettings.copy(
                    browserSourceOutputs = listOf(ScreenAssignment()),
                ),
            )
        }
        waitForIdle()

        onNodeWithText("Browser Source 1").assertExists()
        onAllNodesWithText("Remove").assertCountEquals(1)
        // The screen grid above is untouched by a browser source appearing.
        gridButton(Grid.displayMode(row = 0)).assertTextEquals("Full Screen")
    }
}

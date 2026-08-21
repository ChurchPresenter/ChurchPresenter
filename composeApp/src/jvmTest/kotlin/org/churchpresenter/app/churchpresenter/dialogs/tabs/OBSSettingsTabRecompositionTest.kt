@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OBSSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the tab from its **input** rather than from its controls: the settings object is replaced
 * from outside and the rendered tab must follow.
 *
 * This is the direction the behaviour tests cannot cover. The host, port and password boxes each keep
 * a `remember(...)`-keyed copy of their setting, so a box showing what was typed proves only that it
 * echoed a keystroke — it says nothing about the box re-seeding when the settings change underneath,
 * which is what a settings import does.
 */
class OBSSettingsTabRecompositionTest {

    private fun rerenderable(
        initial: AppSettings = AppSettings(),
        block: ComposeUiTest.(set: (OBSSettings.() -> OBSSettings) -> Unit) -> Unit,
    ) = runComposeUiTest {
        val manager = OBSWebSocketManager()
        var state by mutableStateOf(initial)
        setContent {
            MaterialTheme {
                OBSSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state) },
                    obsManager = manager,
                )
            }
        }
        block { change ->
            state = state.copy(obsSettings = state.obsSettings.change())
            waitForIdle()
        }
    }

    @Test
    fun `the tab survives a recomposition that changes none of its inputs`() {
        rerenderable(initial = obsEnabled()) { set ->
            onNodeWithText(ObsLabel.SECTION_CONNECTION).assertExists()
            obsFields().assertCountEquals(4 + obsSceneModes.size)

            set { this }

            onNodeWithText(ObsLabel.SECTION_CONNECTION).assertExists()
            onNodeWithText(ObsLabel.SECTION_MAPPINGS).assertExists()
            obsFields().assertCountEquals(4 + obsSceneModes.size)
        }
    }

    @Test
    fun `switching OBS on in settings reveals both cards`() = rerenderable { set ->
        onNodeWithText(ObsLabel.SECTION_MAPPINGS).assertDoesNotExist()
        onNode(isToggleable()).assertIsOff()

        set { copy(enabled = true) }

        onNode(isToggleable()).assertIsOn()
        onNodeWithText(ObsLabel.SECTION_MAPPINGS).assertExists()
        onNodeWithText(ObsLabel.CONNECT).assertExists()
    }

    @Test
    fun `switching OBS off in settings takes both cards away`() {
        rerenderable(initial = obsEnabled()) { set ->
            onNodeWithText(ObsLabel.SECTION_MAPPINGS).assertExists()

            set { copy(enabled = false) }

            onNodeWithText(ObsLabel.SECTION_MAPPINGS).assertDoesNotExist()
            onNodeWithText(ObsLabel.CONNECT).assertDoesNotExist()
            onNode(isToggleable()).assertIsOff()
        }
    }

    @Test
    fun `a stored host and port reach their boxes without any interaction`() {
        rerenderable(initial = obsEnabled()) { set ->
            assertObsFieldShows("localhost", "the host box out of the box")
            assertObsFieldShows("4455", "the port box out of the box")

            set { copy(host = "imported.local", port = 9999) }

            assertObsFieldShows("imported.local", "the host box after the settings changed")
            assertObsFieldShows("9999", "the port box after the settings changed")
        }
    }

    @Test
    fun `a stored password reaches its box masked`() {
        rerenderable(initial = obsEnabled()) { set ->
            set { copy(password = "imported") }
            assertObsFieldShows("•".repeat("imported".length), "the password box after the settings changed")
        }
    }

    @Test
    fun `stored scene mappings reach their boxes without any interaction`() {
        rerenderable(initial = obsEnabled()) { set ->
            set {
                copy(
                    defaultScene = "Wide",
                    sceneMappings = mapOf(
                        Presenting.BIBLE.name to "Bible Scene",
                        Presenting.QA.name to "QA Scene",
                    ),
                )
            }

            assertObsFieldShows("Wide", "the default scene")
            assertObsFieldShows("Bible Scene", "the Bible box")
            assertObsFieldShows("QA Scene", "the Q&A box")
        }
    }

    /**
     * The parent hands the tab a new `onSettingsChange` on each recomposition, as `OptionsDialog`
     * does. A tab that kept the stale one would write into a callback the parent has replaced, and
     * the write would appear to succeed while reaching nothing.
     */
    @Test
    fun `a click reaches the newest callback when the parent keeps replacing it`() = runComposeUiTest {
        val manager = OBSWebSocketManager()
        var settings by mutableStateOf(AppSettings())
        var generation by mutableStateOf(0)
        var calledGeneration = -1

        setContent {
            MaterialTheme {
                val thisGeneration = generation
                OBSSettingsTab(
                    settings = settings,
                    onSettingsChange = { transform ->
                        calledGeneration = thisGeneration
                        settings = transform(settings)
                    },
                    obsManager = manager,
                )
            }
        }

        onNode(isToggleable()).performClick()
        waitForIdle()
        assertEquals(0, calledGeneration, "the first callback must be the one invoked")
        assertEquals(true, settings.obsSettings.enabled, "and its write must land")

        generation = 1
        waitForIdle()

        onNode(isToggleable()).performClick()
        waitForIdle()
        assertEquals(1, calledGeneration, "the replacement callback must be invoked, not the stale one")
        assertEquals(false, settings.obsSettings.enabled, "and its write must land too")
    }

    /** The shape `OptionsDialog` uses: a parent that forwards its own parameters straight through. */
    @Test
    fun `the tab tracks its inputs when reached through a parent that forwards them`() = runComposeUiTest {
        val manager = OBSWebSocketManager()
        var settings by mutableStateOf(obsEnabled())
        setContent {
            MaterialTheme {
                ObsHost(
                    settings = settings,
                    onSettingsChange = { transform -> settings = transform(settings) },
                    obsManager = manager,
                )
            }
        }

        assertObsFieldShows("localhost", "the host out of the box")
        settings = settings.copy(obsSettings = settings.obsSettings.copy(host = "forwarded.local"))
        waitForIdle()
        assertObsFieldShows("forwarded.local", "the host after the parent changed it")

        setScene(Presenting.MEDIA, "Media Scene")
        assertEquals(
            mapOf(Presenting.MEDIA.name to "Media Scene"),
            settings.obsSettings.sceneMappings,
            "a typed scene must reach the parent's callback",
        )
    }
}

@Composable
private fun ObsHost(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    obsManager: OBSWebSocketManager,
) {
    OBSSettingsTab(settings = settings, onSettingsChange = onSettingsChange, obsManager = obsManager)
}

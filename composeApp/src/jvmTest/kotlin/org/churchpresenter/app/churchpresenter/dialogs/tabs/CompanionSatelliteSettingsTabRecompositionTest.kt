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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the tab from its **input** rather than from its controls: the settings are replaced from
 * outside and the rendered cards must follow.
 *
 * Every box on a card keeps a `remember(...)`-keyed copy of its field, so a box showing what was
 * typed proves only that it echoed a keystroke. Here nothing is typed — which is what says those
 * keys re-seed when a settings import replaces the connection under them.
 *
 * It also exercises the three composables' recomposition paths: the tab, each `CompanionConnectionCard`
 * and each `CompanionPlacementBlock` are called again with parameters that may or may not have
 * changed, and the cards must neither lose their state nor pick up a neighbour's.
 */
class CompanionSatelliteSettingsTabRecompositionTest {

    private fun rerenderable(
        initial: AppSettings,
        viewModel: CompanionSatelliteViewModel? = null,
        block: ComposeUiTest.(set: (AppSettings) -> Unit, get: () -> AppSettings) -> Unit,
    ) = runComposeUiTest {
        var state by mutableStateOf(initial)
        setContent {
            MaterialTheme {
                CompanionSatelliteSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state) },
                    viewModel = viewModel,
                )
            }
        }
        block({ next -> state = next; waitForIdle() }, { state })
    }

    @Test
    fun `the tab survives a recomposition that changes nothing`() {
        val fixture = satelliteSettings(connection { copy(name = "Booth", showInTab = true) })
        rerenderable(fixture) { set, _ ->
            assertSatelliteBoxShows("Booth", "the name box")
            satelliteToggles().assertCountEquals(Placement.entries.size + 1)

            set(fixture.copy())

            assertSatelliteBoxShows("Booth", "the name box after a no-op re-render")
            satelliteToggles().assertCountEquals(Placement.entries.size + 1)
            placementCheckbox(Placement.TAB).assertIsOn()
        }
    }

    @Test
    fun `a stored connection change reaches its boxes without any interaction`() {
        rerenderable(satelliteSettings(connection { copy(name = "Before", host = "10.0.0.1") })) { set, get ->
            assertSatelliteBoxShows("Before", "the name box out of the box")

            set(satelliteSettings(get().onlyConnection().copy(name = "After", host = "10.0.0.9")))

            assertSatelliteBoxShows("After", "the name box after the settings changed")
            assertSatelliteBoxShows("10.0.0.9", "the host box after the settings changed")
        }
    }

    @Test
    fun `a stored placement change reveals its boxes without any interaction`() {
        rerenderable(satelliteSettings(connection())) { set, get ->
            placementCheckbox(Placement.LEFT).assertIsOff()

            set(satelliteSettings(get().onlyConnection().copy(showInLeftSidebar = true, leftSidebarRows = 6)))

            placementCheckbox(Placement.LEFT).assertIsOn()
            assertSatelliteBoxShows("6", "the left sidebar rows box")
        }
    }

    @Test
    fun `adding a connection in settings adds its card`() {
        rerenderable(satelliteSettings(connection { copy(name = "First") })) { set, get ->
            removeButtons().assertCountEquals(0)

            set(satelliteSettings(get().onlyConnection(), connection { copy(name = "Second") }))

            removeButtons().assertCountEquals(2)
            assertSatelliteBoxShows("First", "the first card")
            assertSatelliteBoxShows("Second", "the second card")
        }
    }

    @Test
    fun `removing a connection in settings removes its card`() {
        val fixture = satelliteSettings(
            connection { copy(name = "First") },
            connection { copy(name = "Second") },
        )
        rerenderable(fixture) { set, get ->
            removeButtons().assertCountEquals(2)

            set(satelliteSettings(get().companionSatelliteConnections.last()))

            removeButtons().assertCountEquals(0)
            assertSatelliteBoxShows("Second", "the surviving card")
        }
    }

    /**
     * Two cards on screen, one of them edited from outside. The other must not follow it — each card
     * is called again with its own connection, and a card that read the wrong one would show it.
     */
    @Test
    fun `changing one connection in settings leaves the other card alone`() {
        val fixture = satelliteSettings(
            connection { copy(name = "First", host = "10.0.0.1") },
            connection { copy(name = "Second", host = "10.0.0.2") },
        )
        rerenderable(fixture) { set, get ->
            val (first, second) = get().companionSatelliteConnections
            set(satelliteSettings(first.copy(name = "First Renamed"), second))

            assertSatelliteBoxShows("First Renamed", "the edited card")
            assertSatelliteBoxShows("Second", "the untouched card")
            assertSatelliteBoxShows("10.0.0.2", "which keeps its own host")
        }
    }

    /**
     * The parent hands the tab a new `onSettingsChange` on each recomposition, as `OptionsDialog`
     * does. A card that kept the stale one would write into a callback the parent has replaced.
     */
    @Test
    fun `a click reaches the newest callback when the parent keeps replacing it`() = runComposeUiTest {
        var settings by mutableStateOf(satelliteSettings(connection()))
        var generation by mutableStateOf(0)
        var calledGeneration = -1

        setContent {
            MaterialTheme {
                val thisGeneration = generation
                CompanionSatelliteSettingsTab(
                    settings = settings,
                    onSettingsChange = { transform ->
                        calledGeneration = thisGeneration
                        settings = transform(settings)
                    },
                    viewModel = null,
                )
            }
        }

        placementCheckbox(Placement.TAB).performScrollTo().performClick()
        waitForIdle()
        assertEquals(0, calledGeneration, "the first callback must be the one invoked")
        assertEquals(true, settings.onlyConnection().showInTab, "and its write must land")

        generation = 1
        waitForIdle()

        autoConnectSwitch().performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, calledGeneration, "the replacement callback must be invoked, not the stale one")
        assertEquals(true, settings.onlyConnection().autoConnect, "and its write must land too")
    }

    /** The shape `OptionsDialog` uses: a parent forwarding its own parameters straight through. */
    @Test
    fun `the tab tracks its inputs when reached through a parent that forwards them`() = runComposeUiTest {
        var settings by mutableStateOf(satelliteSettings(connection { copy(name = "Forwarded") }))
        setContent {
            MaterialTheme {
                SatelliteHost(
                    settings = settings,
                    onSettingsChange = { transform -> settings = transform(settings) },
                )
            }
        }

        assertSatelliteBoxShows("Forwarded", "the name out of the box")
        settings = satelliteSettings(settings.onlyConnection().copy(name = "Forwarded Again"))
        waitForIdle()
        assertSatelliteBoxShows("Forwarded Again", "the name after the parent changed it")

        onNodeWithText(SatLabel.ADD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(2, settings.companionSatelliteConnections.size, "a click must reach the parent's callback")
    }
}

@Composable
private fun SatelliteHost(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    CompanionSatelliteSettingsTab(
        settings = settings,
        onSettingsChange = onSettingsChange,
        viewModel = null,
    )
}

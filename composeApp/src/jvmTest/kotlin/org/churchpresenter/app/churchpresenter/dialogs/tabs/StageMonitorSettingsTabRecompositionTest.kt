@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.MetronomePosition
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorContentType
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorSettings
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorStyleZone
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the tab from its **input** rather than from its controls: the settings object is replaced
 * from outside and the rendered tab must follow.
 *
 * This is the direction the behaviour tests cannot cover. They change a setting by clicking, and the
 * routing dropdowns in particular display their own local `currentValue` — so a dropdown that had
 * stopped reading the settings entirely would still look right to them. Here nothing is clicked;
 * every assertion is about a value the tab was handed, which is what says the tab is a function of
 * its settings.
 *
 * It also covers the path a settings import or an Instance Link update takes: those replace the
 * whole object under a tab that is already on screen.
 */
class StageMonitorSettingsTabRecompositionTest {

    /** Renders the tab over a settings object [block] can swap out, then re-asserts. */
    private fun rerenderable(
        initial: AppSettings = AppSettings(),
        block: ComposeUiTest.(set: (StageMonitorSettings.() -> StageMonitorSettings) -> Unit) -> Unit,
    ) = runComposeUiTest {
        var state by mutableStateOf(initial)
        setContent {
            MaterialTheme {
                StageMonitorSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state) },
                )
            }
        }
        block { change ->
            state = state.copy(stageMonitorSettings = state.stageMonitorSettings.change())
            waitForIdle()
        }
    }

    @Test
    fun `the tab survives a recomposition that changes none of its inputs`() = rerenderable { set ->
        onAllNodesWithText("Screen Content").assertCountEquals(1)
        colorFields().assertCountEquals(ZoneOrdinal.COUNT * 3 + CHORD_COLOUR_ZONES)

        set { this }

        onAllNodesWithText("Screen Content").assertCountEquals(1)
        colorFields().assertCountEquals(ZoneOrdinal.COUNT * 3 + CHORD_COLOUR_ZONES)
        numberFields().assertCountEquals(ZoneOrdinal.COUNT * 3)
    }

    /**
     * The assertion the routing tests cannot make. `DropdownSettingsField` shows its own
     * `currentValue`, so this is the only place the dropdown is proven to render the value it was
     * *given* rather than the one it last remembered being clicked.
     */
    @Test
    fun `a stored routing change reaches its dropdown without any interaction`() = rerenderable { set ->
        assertRoutingShows(ContentLabel.of(StageMonitorContentType.MEDIA), ZoneLabel.FULL_SCREEN)

        set { copy(contentZones = contentZones + (StageMonitorContentType.MEDIA to StageMonitorZone.B)) }

        assertRoutingShows(ContentLabel.of(StageMonitorContentType.MEDIA), ZoneLabel.ZONE_2)
        assertRoutingShows(ContentLabel.of(StageMonitorContentType.WEB), ZoneLabel.FULL_SCREEN)
    }

    @Test
    fun `a stored routing change reaches the preview without any interaction`() = rerenderable { set ->
        onAllNodesWithText("Bible, Songs").assertCountEquals(1)

        set { copy(contentZones = contentZones + (StageMonitorContentType.SONGS to StageMonitorZone.E)) }

        onAllNodesWithText("Bible, Songs").assertCountEquals(0)
        onAllNodesWithText("Bible").assertCountEquals(1)
        onAllNodesWithText("Songs").assertCountEquals(1)
    }

    @Test
    fun `a stored metronome change reaches both its dropdown and the preview`() = rerenderable { set ->
        set { copy(metronomePosition = MetronomePosition.BOTTOM_CENTER) }

        // The anchor's name is positional and shares nothing with the zone names any more.
        assertRoutingShows(ContentLabel.METRONOME, MetronomeLabel.BOTTOM_CENTER)
        onAllNodesWithText(MetronomeLabel.BOTTOM_CENTER).assertCountEquals(1)
    }

    @Test
    fun `a stored zone style change reaches its editor without any interaction`() = rerenderable { set ->
        assertNumberFieldShows(35, "Top-Left's font size out of the box")

        set {
            copy(zoneStyles = zoneStyles + (StageMonitorStyleZone.A to
                styleFor(StageMonitorStyleZone.A).copy(fontSize = 177, color = "#ABCDEF")))
        }

        assertNumberFieldShows(177, "Top-Left's font size after the settings changed")
        assertColorFieldShows("#ABCDEF", "Top-Left's colour after the settings changed")
    }

    @Test
    fun `a stored font family change reaches its dropdown without any interaction`() = rerenderable { set ->
        assertFontFieldShows("Arial", "the zone fonts out of the box")

        set {
            copy(zoneStyles = zoneStyles + (StageMonitorStyleZone.FULL_SCREEN to
                styleFor(StageMonitorStyleZone.FULL_SCREEN).copy(fontType = SENTINEL_FONT)))
        }

        assertFontFieldShows(SENTINEL_FONT, "Full Screen's font after the settings changed")
    }

    /**
     * A whole-object replacement, as a settings import performs — routing, metronome and every zone
     * style change at once, and each must pick up its own value rather than a neighbour's.
     */
    @Test
    fun `replacing every setting at once repaints the whole tab correctly`() = rerenderable { set ->
        set {
            copy(
                contentZones = contentZones +
                    (StageMonitorContentType.BIBLE to StageMonitorZone.E) +
                    (StageMonitorContentType.CLOCK to StageMonitorZone.NONE),
                metronomePosition = MetronomePosition.TOP_CENTER,
                zoneStyles = ZoneOrdinal.inOrder.withIndex().associate { (index, zone) ->
                    zone to styleFor(zone).copy(fontSize = 120 + index)
                },
            )
        }

        assertRoutingShows(ContentLabel.of(StageMonitorContentType.BIBLE), ZoneLabel.ZONE_5)
        assertRoutingShows(ContentLabel.of(StageMonitorContentType.CLOCK), ZoneLabel.NONE)
        assertRoutingShows(ContentLabel.METRONOME, MetronomeLabel.TOP_CENTER)
        for (index in ZoneOrdinal.inOrder.indices) {
            assertNumberFieldShows(120 + index, "the font size of zone $index")
        }
        onAllNodesWithText("Songs").assertCountEquals(1) // Top-Left is left with Songs alone
        onAllNodesWithText("Clock").assertCountEquals(1) // now in the None row
    }

    /**
     * The parent hands the tab a **new `onSettingsChange` instance** on each recomposition, as
     * `OptionsDialog` does. The tab must use the callback it was last given: one that skipped the
     * update would keep writing into a callback the parent has already replaced, and the write would
     * appear to succeed while reaching nothing.
     */
    @Test
    fun `a click reaches the newest callback when the parent keeps replacing it`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        var generation by mutableStateOf(0)
        var calledGeneration = -1

        setContent {
            MaterialTheme {
                val thisGeneration = generation
                StageMonitorSettingsTab(
                    settings = settings,
                    onSettingsChange = { transform ->
                        calledGeneration = thisGeneration
                        settings = transform(settings)
                    },
                )
            }
        }

        styleButton(ZoneOrdinal.of(StageMonitorStyleZone.FULL_SCREEN), "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(0, calledGeneration, "the first callback must be the one invoked")
        assertEquals(true, settings.styleOf(StageMonitorStyleZone.FULL_SCREEN).bold, "and its write must land")

        generation = 1
        waitForIdle()

        styleButton(ZoneOrdinal.of(StageMonitorStyleZone.FULL_SCREEN), "I").performClick()
        waitForIdle()
        assertEquals(1, calledGeneration, "the replacement callback must be the one invoked, not the stale one")
        assertEquals(true, settings.styleOf(StageMonitorStyleZone.FULL_SCREEN).italic, "and its write must land too")
    }

    /**
     * The same round trip reached through [StageMonitorHost] — the shape `OptionsDialog` uses, where
     * the parent forwards its own parameters, so the tab's skip decision comes from the caller's
     * change flags rather than from comparing values itself.
     */
    @Test
    fun `the tab tracks its inputs when reached through a parent that forwards them`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                StageMonitorHost(settings = settings) { transform -> settings = transform(settings) }
            }
        }

        assertRoutingShows(ContentLabel.of(StageMonitorContentType.PICTURES), ZoneLabel.FULL_SCREEN)
        settings = settings.copy(
            stageMonitorSettings = settings.stageMonitorSettings.copy(
                contentZones = settings.stageMonitorSettings.contentZones +
                    (StageMonitorContentType.PICTURES to StageMonitorZone.C),
            ),
        )
        waitForIdle()
        assertRoutingShows(ContentLabel.of(StageMonitorContentType.PICTURES), ZoneLabel.ZONE_3)

        styleButton(ZoneOrdinal.of(StageMonitorStyleZone.E), "U").performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            true,
            settings.styleOf(StageMonitorStyleZone.E).underline,
            "a click must reach the parent's callback",
        )
    }
}

/**
 * Stands in for `OptionsDialog`: a composable that takes the settings and the callback as its own
 * parameters and forwards them straight through, so the compiler propagates its caller's change
 * flags into the tab.
 */
@Composable
private fun StageMonitorHost(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    StageMonitorSettingsTab(settings = settings, onSettingsChange = onSettingsChange)
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.data.settings.MetronomePosition
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorContentType
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the routing section: which zone each content type is sent to, and where the metronome dot
 * is anchored.
 *
 * **Every assertion here goes through the settings object, never through the dropdown's own text.**
 * `DropdownSettingsField` holds a local `currentValue` and displays whatever was last clicked, so
 * `assertRoutingShows` immediately after a pick would hold even if the choice were dropped on the
 * floor — that exact assertion is what made seven Song-tab tests vacuous. The dropdown's display is
 * still checked, but from a **fresh render of the saved settings**, which is the only way to prove
 * what it shows came back out of storage.
 */
class StageMonitorSettingsTabRoutingTest {

    // ── One test per content type ───────────────────────────────────────────────────────────────

    /**
     * Each content type in turn is routed somewhere it is not already, and the stored map must show
     * exactly that one change. Routing is one `Map<ContentType, Zone>` updated by
     * `contentZones + (type to zone)`, so the failure this guards against is a type writing under a
     * neighbour's key — which would leave the map the right size and pass any count-based check.
     */
    @Test
    fun `every content type routes to the zone it is given, and only it`() {
        for (type in StageMonitorContentType.entries) {
            val target = if (type == StageMonitorContentType.CLOCK) {
                StageMonitorZone.TOP_RIGHT
            } else {
                StageMonitorZone.BOTTOM_RIGHT
            }
            stageMonitorTab { get ->
                val before = get().stageMonitorSettings.contentZones

                chooseRouting(ContentLabel.of(type), ZoneLabel.of(target))

                val after = get().stageMonitorSettings.contentZones
                assertEquals(target, after.getValue(type), "$type must be routed to $target")
                assertEquals(
                    before.keys,
                    after.keys,
                    "$type must not add or drop a key",
                )
                for (other in StageMonitorContentType.entries.filter { it != type }) {
                    assertEquals(
                        before.getValue(other),
                        after.getValue(other),
                        "routing $type must leave $other alone",
                    )
                }
            }
        }
    }

    /**
     * The round trip the display half of the tab depends on. A picked zone is only proven stored
     * when a *fresh* tab, given the saved settings, shows it — see this class's note on the local
     * `currentValue`.
     */
    @Test
    fun `a routed zone is what a fresh render of the saved settings shows`() {
        var saved = StageMonitorZone.NONE
        stageMonitorTab { get ->
            chooseRouting(ContentLabel.of(StageMonitorContentType.MEDIA), ZoneLabel.BOTTOM_LEFT)
            saved = get().stageMonitorSettings.zoneFor(StageMonitorContentType.MEDIA)
        }
        assertEquals(StageMonitorZone.BOTTOM_LEFT, saved, "the pick must have been stored to be re-rendered")

        stageMonitorTab(
            initial = stageSettings { copy(contentZones = contentZones + (StageMonitorContentType.MEDIA to saved)) },
        ) { _ ->
            assertRoutingShows(ContentLabel.of(StageMonitorContentType.MEDIA), ZoneLabel.BOTTOM_LEFT)
        }
    }

    @Test
    fun `routing a content type to None stores None`() = stageMonitorTab { get ->
        chooseRouting(ContentLabel.of(StageMonitorContentType.WEB), ZoneLabel.NONE)
        assertEquals(
            StageMonitorZone.NONE,
            get().stageMonitorSettings.zoneFor(StageMonitorContentType.WEB),
            "None must be storable — it is how a type is switched off",
        )
    }

    // ── The zone menus each type offers ─────────────────────────────────────────────────────────

    /**
     * Bible, Songs and Next are meant to share the screen, so their menus leave Full Screen out.
     * Everything else offers all seven zones. The menu is checked by opening it and counting the
     * clickable items, since the field behind it carries its caption as well and is told apart by it.
     */
    @Test
    fun `only Bible Songs and Next are denied the full-screen zone`() {
        val denied = setOf(
            StageMonitorContentType.BIBLE,
            StageMonitorContentType.SONGS,
            StageMonitorContentType.NEXT,
        )
        for (type in StageMonitorContentType.entries) {
            stageMonitorTab { _ ->
                routingDropdown(ContentLabel.of(type)).performScrollTo().performClick()
                waitForIdle()
                val expected = if (type in denied) 0 else 1
                onAllNodes(hasTextExactly(ZoneLabel.FULL_SCREEN) and hasClickAction())
                    .assertCountEquals(expected)
            }
        }
    }

    @Test
    fun `a full-screen-denied type still offers every other zone`() = stageMonitorTab { _ ->
        routingDropdown(ContentLabel.of(StageMonitorContentType.BIBLE)).performScrollTo().performClick()
        waitForIdle()
        for (option in ZoneLabel.all.filter { it != ZoneLabel.FULL_SCREEN }) {
            onAllNodes(hasTextExactly(option) and hasClickAction())
                .assertCountEquals(1)
        }
    }

    // ── Metronome ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every metronome position can be chosen and is stored`() {
        for (position in MetronomePosition.entries.filter { it != MetronomePosition.NONE }) {
            stageMonitorTab { get ->
                chooseRouting(ContentLabel.METRONOME, MetronomeLabel.of(position))
                assertEquals(
                    position,
                    get().stageMonitorSettings.metronomePosition,
                    "${MetronomeLabel.of(position)} must store $position",
                )
            }
        }
    }

    @Test
    fun `the metronome menu offers all ten anchors`() = stageMonitorTab { _ ->
        routingDropdown(ContentLabel.METRONOME).performScrollTo().performClick()
        waitForIdle()
        for (option in MetronomeLabel.all.distinct()) {
            onAllNodes(hasTextExactly(option) and hasClickAction())
                .assertCountEquals(1)
        }
        assertEquals(10, MetronomePosition.entries.size, "a new anchor needs a label here")
    }

    @Test
    fun `a chosen metronome anchor is what a fresh render of the saved settings shows`() {
        var saved = MetronomePosition.NONE
        stageMonitorTab { get ->
            chooseRouting(ContentLabel.METRONOME, MetronomeLabel.CENTER)
            saved = get().stageMonitorSettings.metronomePosition
        }
        assertEquals(MetronomePosition.CENTER, saved, "the pick must have been stored to be re-rendered")

        stageMonitorTab(initial = stageSettings { copy(metronomePosition = saved) }) { _ ->
            assertRoutingShows(ContentLabel.METRONOME, MetronomeLabel.CENTER)
        }
    }

    @Test
    fun `choosing a metronome anchor leaves the content routing alone`() = stageMonitorTab { get ->
        val before = get().stageMonitorSettings.contentZones
        chooseRouting(ContentLabel.METRONOME, MetronomeLabel.MIDDLE_RIGHT)
        assertEquals(
            MetronomePosition.MIDDLE_RIGHT,
            get().stageMonitorSettings.metronomePosition,
            "the anchor must be stored",
        )
        assertEquals(before, get().stageMonitorSettings.contentZones, "and the routing map untouched")
    }

    /**
     * The metronome shares five of its ten labels with the zone menus ("Top-Left", "None", …), so
     * this checks the two dropdowns really are independent rather than accidentally writing through
     * the same label lookup.
     */
    @Test
    fun `a shared label picked in one dropdown does not move the other`() = stageMonitorTab { get ->
        chooseRouting(ContentLabel.METRONOME, ZoneLabel.TOP_LEFT)
        assertEquals(MetronomePosition.TOP_LEFT, get().stageMonitorSettings.metronomePosition)
        assertEquals(
            StageMonitorZone.FULL_SCREEN,
            get().stageMonitorSettings.zoneFor(StageMonitorContentType.WEB),
            "the Web routing must be untouched by a metronome pick",
        )

        chooseRouting(ContentLabel.of(StageMonitorContentType.WEB), ZoneLabel.TOP_LEFT)
        assertEquals(
            MetronomePosition.TOP_LEFT,
            get().stageMonitorSettings.metronomePosition,
            "and the metronome untouched by a routing pick",
        )
    }
}

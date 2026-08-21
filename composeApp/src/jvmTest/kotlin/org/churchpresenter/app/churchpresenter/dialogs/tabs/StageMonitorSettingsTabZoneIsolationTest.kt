@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.StageMonitorZoneStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sweeps **all six** zone editors, checking each writes only into its own entry.
 *
 * `StageMonitorSettingsTabZoneStyleTest` drives every control of one zone; this drives one control of
 * every zone. Together they cover the two ways the shared editor can be mis-wired: a control writing
 * the wrong *field*, and a zone writing the wrong *key*.
 *
 * The second is the likelier and the harder to see. All six editors are the same composable closing
 * over a different zone, and each callback rebuilds the map with
 * `zoneStyles + (zone to styleFor(zone).copy(...))`. A zone captured wrongly — or a map replaced
 * rather than added to — leaves a tab that looks entirely correct until two zones are configured
 * differently, which is exactly what these fixtures do.
 */
class StageMonitorSettingsTabZoneIsolationTest {

    private val defaults = StageMonitorSettings.defaultZoneStyles()

    private fun assertOnly(
        changed: StageMonitorStyleZone,
        get: () -> AppSettings,
        what: String,
        check: (StageMonitorZoneStyle) -> Unit,
    ) {
        check(get().styleOf(changed))
        for (other in StageMonitorStyleZone.entries.filter { it != changed }) {
            assertEquals(defaults.getValue(other), get().styleOf(other), "$what: $other must be untouched")
        }
        assertEquals(
            StageMonitorStyleZone.entries.size,
            get().stageMonitorSettings.zoneStyles.size,
            "$what: every zone must still have an entry",
        )
    }

    @Test
    fun `each zone's font size writes only its own entry`() {
        for (zone in ZoneOrdinal.inOrder) {
            // The marker is unique on the tab, so the field found by it can only be this zone's.
            stageMonitorTab(initial = zoneStyled(zone) { copy(fontSize = 199) }) { get ->
                retypeNumberField(showing = 199, to = 88)
                assertOnly(zone, get, "font size of $zone") { assertEquals(88, it.fontSize) }
            }
        }
    }

    @Test
    fun `each zone's text colour writes only its own entry`() {
        for (zone in ZoneOrdinal.inOrder) {
            stageMonitorTab(initial = zoneStyled(zone) { copy(color = "#ABABAB") }) { get ->
                recolor(fromHex = "#ABABAB", toHex = "#CDCDCD")
                assertOnly(zone, get, "colour of $zone") {
                    assertTrue(it.color.equals("#CDCDCD", ignoreCase = true), "was ${it.color}")
                }
            }
        }
    }

    /**
     * Asserted as a *flip* rather than as "becomes bold": Top-Right ships bold, so clicking its B
     * clears the flag. Expecting `true` everywhere would fail on that one zone for the right reason
     * and hide whether the other five worked.
     */
    @Test
    fun `each zone's bold button writes only its own entry`() {
        for (zone in ZoneOrdinal.inOrder) {
            val wasBold = defaults.getValue(zone).bold
            stageMonitorTab { get ->
                styleButton(ZoneOrdinal.of(zone), "B").performScrollTo().performClick()
                waitForIdle()
                assertOnly(zone, get, "bold of $zone") { assertEquals(!wasBold, it.bold) }
            }
        }
    }

    @Test
    fun `each zone's vertical alignment writes only its own entry`() {
        for (zone in ZoneOrdinal.inOrder) {
            stageMonitorTab { get ->
                verticalAlignButton(ZoneOrdinal.of(zone), VAlign.BOTTOM).performScrollTo().performClick()
                waitForIdle()
                assertOnly(zone, get, "vertical alignment of $zone") {
                    assertEquals(Constants.BOTTOM, it.verticalAlignment)
                }
            }
        }
    }

    @Test
    fun `each zone's horizontal alignment writes only its own entry`() {
        for (zone in ZoneOrdinal.inOrder) {
            stageMonitorTab { get ->
                horizontalAlignButton(ZoneOrdinal.of(zone), HAlign.RIGHT).performScrollTo().performClick()
                waitForIdle()
                assertOnly(zone, get, "horizontal alignment of $zone") {
                    assertEquals(Constants.RIGHT, it.horizontalAlignment)
                }
            }
        }
    }

    /**
     * Two zones configured in one session. Each write rebuilds the whole map from the previous one,
     * so an edit that dropped its predecessor would only show up once a second zone is touched.
     */
    @Test
    fun `configuring one zone after another keeps both`() = stageMonitorTab { get ->
        styleButton(ZoneOrdinal.of(StageMonitorStyleZone.B), "U").performScrollTo().performClick()
        waitForIdle()
        styleButton(ZoneOrdinal.of(StageMonitorStyleZone.C), "B").performScrollTo().performClick()
        waitForIdle()

        assertEquals(true, get().styleOf(StageMonitorStyleZone.B).underline, "the first edit must survive")
        assertEquals(true, get().styleOf(StageMonitorStyleZone.C).bold, "and the second must land")
        assertEquals(
            defaults.getValue(StageMonitorStyleZone.B).bold,
            get().styleOf(StageMonitorStyleZone.B).bold,
            "without the second edit leaking into the first zone",
        )
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.composables.TextBackdropMode
import org.churchpresenter.app.churchpresenter.composables.mode
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Stage Monitor tab's transition switches, and the three zone-style controls
 * [StageMonitorSettingsTabZoneStyleTest] does not reach: the glyph outline, the text backing and the
 * chord colour.
 *
 * The zone controls assert the same two halves that suite does -- the zone under test took the
 * value, and every other zone kept its own -- because all six editors come from one composable and
 * the failure that invites is a callback writing into the wrong zone.
 */
class StageMonitorSettingsTabMotionTest {

    private val zone = StageMonitorStyleZone.A
    private val ordinal = ZoneOrdinal.of(zone)

    private fun assertOtherZonesUntouched(get: () -> AppSettings) {
        val defaults = StageMonitorSettings.defaultZoneStyles()
        for (other in StageMonitorStyleZone.entries.filter { it != zone }) {
            assertEquals(defaults.getValue(other), get().styleOf(other), "$other must be untouched")
        }
    }

    // ── Transitions ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the fade and crossfade switches each store their own flag`() = stageMonitorTab { get ->
        val before = get().stageMonitorSettings
        assertTrue(before.fadeIn && before.fadeOut && !before.crossfade, "fades on, crossfade off by default")

        toggleCheckbox("Fade In")
        assertFalse(get().stageMonitorSettings.fadeIn)
        assertTrue(get().stageMonitorSettings.fadeOut, "Fade Out is its own switch")

        toggleCheckbox("Fade Out")
        assertFalse(get().stageMonitorSettings.fadeOut)

        toggleCheckbox("Crossfade")
        assertTrue(get().stageMonitorSettings.crossfade)
        assertEquals(before.transitionDuration, get().stageMonitorSettings.transitionDuration)
    }

    // ── Outline, backing, chord colour ──────────────────────────────────────────────────────────

    @Test
    fun `the outline button switches the zone's glyph outline on and off`() = stageMonitorTab { get ->
        assertFalse(get().styleOf(zone).outline.enabled, "no outline out of the box")

        styleButton(ordinal, "O").performScrollTo().performClick()
        waitForIdle()
        assertTrue(get().styleOf(zone).outline.enabled)
        assertOtherZonesUntouched(get)

        styleButton(ordinal, "O").performClick()
        waitForIdle()
        assertFalse(get().styleOf(zone).outline.enabled, "a second press takes it off again")
    }

    @Test
    fun `the backing button puts a backdrop behind the zone's text and takes it away`() = stageMonitorTab { get ->
        assertEquals(TextBackdropMode.OFF, get().styleOf(zone).backdrop.mode, "no backing out of the box")

        styleButton(ordinal, "A").performScrollTo().performClick()
        waitForIdle()
        assertNotEquals(TextBackdropMode.OFF, get().styleOf(zone).backdrop.mode)
        assertOtherZonesUntouched(get)

        styleButton(ordinal, "A").performClick()
        waitForIdle()
        assertEquals(TextBackdropMode.OFF, get().styleOf(zone).backdrop.mode)
    }

    @Test
    fun `a song zone's chord colour stores the confirmed hex`() {
        stageMonitorTab(initial = zoneStyled(zone) { copy(chordColor = "#1A2B3C") }) { get ->
            recolor(fromHex = "#1A2B3C", toHex = "#C3B2A1")

            assertTrue(get().styleOf(zone).chordColor.equals("#C3B2A1", ignoreCase = true))
            assertEquals("#FFFFFF", get().styleOf(zone).color, "the text colour must be untouched")
            assertOtherZonesUntouched(get)
        }
    }
}

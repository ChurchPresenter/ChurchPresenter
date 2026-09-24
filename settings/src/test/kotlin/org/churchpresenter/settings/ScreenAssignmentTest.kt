package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What one physical output's own identity is: its key channel and its Browser Source/NDI naming.
 *
 * Everything about *what* an output shows or *how* it looks -- content, display mode -- now lives
 * on the [OutputProfile] it follows (see `OutputProfileTest.kt`), never on the assignment itself.
 */
class ScreenAssignmentTest {

    // ── Fill and key ────────────────────────────────────────────────────────────

    @Test
    fun `an output has no key channel by default`() {
        val output = ScreenAssignment()

        assertFalse(output.hasKeyOutput)
        assertEquals(
            Constants.OUTPUT_ROLE_NORMAL,
            output.primaryOutputRole,
            "without a key channel the output is just a picture, not the fill half of a pair",
        )
    }

    @Test
    fun `configuring a key target makes this output the fill`() {
        val keyed = ScreenAssignment(keyTargetDisplay = 1)

        assertTrue(keyed.hasKeyOutput)
        assertEquals(
            Constants.OUTPUT_ROLE_FILL,
            keyed.primaryOutputRole,
            "a hardware keyer needs to be told which signal is fill and which is key",
        )
    }

    @Test
    fun `the first display counts as a key target`() {
        // Display 0 is a real device; only the negative sentinels mean "none".
        val keyed = ScreenAssignment(keyTargetDisplay = 0)

        assertTrue(keyed.hasKeyOutput, "display 0 is a device, not an absence")
        assertEquals(Constants.OUTPUT_ROLE_FILL, keyed.primaryOutputRole)
    }

    @Test
    fun `the none sentinel is not a key target`() {
        val output = ScreenAssignment(keyTargetDisplay = Constants.KEY_TARGET_NONE)

        assertFalse(output.hasKeyOutput)
        assertEquals(Constants.OUTPUT_ROLE_NORMAL, output.primaryOutputRole)
    }

    @Test
    fun `the auto sentinel is not a key target either`() {
        // -1 means "resolve at runtime" for the main target; as a key target it is still not a device.
        assertFalse(ScreenAssignment(keyTargetDisplay = -1).hasKeyOutput)
    }
}

/**
 * Mapping a stage-monitor content zone to the style that draws it.
 *
 * Content can be placed in seven zones but only six are drawn — `NONE` means "do not show this at
 * all" and therefore has no style. Every other zone must map, because the caller uses the result to
 * look a style up; a zone that mapped to null by mistake would silently stop drawing content the
 * operator had placed.
 */
class StageMonitorZoneMappingTest {

    @Test
    fun `every drawn zone maps to its own style zone`() {
        val mapped = StageMonitorZone.entries
            .filter { it != StageMonitorZone.NONE }
            .associateWith { it.toStyleZone() }

        assertTrue(
            mapped.values.none { it == null },
            "a zone with no style stops being drawn: ${mapped.filterValues { it == null }.keys}",
        )
        assertEquals(
            mapped.size,
            mapped.values.toSet().size,
            "two zones sharing a style would be restyled together",
        )
    }

    @Test
    fun `each zone maps to the style of the same name`() {
        assertEquals(StageMonitorStyleZone.A, StageMonitorZone.A.toStyleZone())
        assertEquals(StageMonitorStyleZone.B, StageMonitorZone.B.toStyleZone())
        assertEquals(StageMonitorStyleZone.C, StageMonitorZone.C.toStyleZone())
        assertEquals(StageMonitorStyleZone.D, StageMonitorZone.D.toStyleZone())
        assertEquals(StageMonitorStyleZone.E, StageMonitorZone.E.toStyleZone())
        assertEquals(StageMonitorStyleZone.FULL_SCREEN, StageMonitorZone.FULL_SCREEN.toStyleZone())
    }

    @Test
    fun `hidden content has no style zone`() {
        assertNull(
            StageMonitorZone.NONE.toStyleZone(),
            "NONE means the content is not placed anywhere, so there is nothing to style",
        )
    }

    @Test
    fun `there is a style zone for every drawn content zone and no more`() {
        val fromZones = StageMonitorZone.entries.mapNotNull { it.toStyleZone() }.toSet()

        assertEquals(
            StageMonitorStyleZone.entries.toSet(),
            fromZones,
            "a style zone nothing maps to would be configurable but never used",
        )
    }

    // ── What a Browser Source output is called ──────────────────────────────────

    @Test
    fun `an unnamed browser source falls back to the numbered label`() {
        assertEquals("Browser Source 2", ScreenAssignment().browserSourceLabelOr("Browser Source 2"))
    }

    @Test
    fun `a named browser source is called what the operator named it`() {
        val output = ScreenAssignment(browserSourceName = "Choir")

        assertEquals("Choir", output.browserSourceLabelOr("Browser Source 2"))
    }

    @Test
    fun `a name of nothing but spaces is no name at all`() {
        val output = ScreenAssignment(browserSourceName = "   ")

        assertEquals(
            "Browser Source 2",
            output.browserSourceLabelOr("Browser Source 2"),
            "a blank name would label every screen with an empty string",
        )
    }

    @Test
    fun `surrounding space is trimmed off a real name`() {
        val output = ScreenAssignment(browserSourceName = "  Stage  ")

        assertEquals("Stage", output.browserSourceLabelOr("Browser Source 1"))
    }

    // ── NDI output naming and defaults ──────────────────────────────────────────

    @Test
    fun `an unnamed ndi output falls back to the numbered label`() {
        assertEquals("NDI Output 2", ScreenAssignment().ndiLabelOr("NDI Output 2"))
    }

    @Test
    fun `a named ndi output keeps its name`() {
        assertEquals("Lyrics", ScreenAssignment(ndiName = "Lyrics").ndiLabelOr("NDI Output 1"))
    }

    @Test
    fun `an ndi name of nothing but spaces is no name at all`() {
        // A source advertised as whitespace is one an OBS operator cannot pick out of a list.
        assertEquals("NDI Output 1", ScreenAssignment(ndiName = "   ").ndiLabelOr("NDI Output 1"))
    }

    @Test
    fun `an ndi name is trimmed of surrounding space`() {
        assertEquals("Lyrics", ScreenAssignment(ndiName = "  Lyrics  ").ndiLabelOr("NDI Output 1"))
    }

    @Test
    fun `a new ndi output defaults to enabled 1080p30 in alpha mode`() {
        val output = ScreenAssignment()

        assertTrue(output.ndiEnabled)
        assertEquals(1920, output.ndiWidth)
        assertEquals(1080, output.ndiHeight)
        assertEquals(30, output.ndiFps)
        // Alpha is the mode SDI cannot do, and the one that makes a lower third arrive keyed.
        assertEquals(Constants.NDI_MODE_ALPHA, output.ndiMode)
    }
}

package org.churchpresenter.app.churchpresenter.data.settings

import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What one physical output is asked to show.
 *
 * Every projector, capture card and browser source gets one of these, and six derived properties on
 * it decide what actually renders there: whether Bible and song text appear at all, whether the
 * output is a full screen or a lower-third band, and whether it is half of a fill+key pair driving
 * a hardware keyer.
 *
 * They are one-liners, which is exactly why they are worth pinning — they are read at render time
 * rather than stored, so a change to what counts as "off" or as "keyed" silently changes what an
 * output does, and the only symptom is a screen showing the wrong thing during a service.
 */
class ScreenAssignmentTest {

    // ── Whether text appears on this output ─────────────────────────────────────

    @Test
    fun `an output shows bible and songs by default`() {
        val output = ScreenAssignment()

        assertTrue(output.showBible, "a newly detected screen shows the service, not nothing")
        assertTrue(output.showSongs)
    }

    @Test
    fun `switching a kind of content off hides it`() {
        val output = ScreenAssignment(
            bibleMode = Constants.SONG_LANG_OFF,
            songMode = Constants.SONG_LANG_OFF,
        )

        assertFalse(output.showBible)
        assertFalse(output.showSongs)
    }

    @Test
    fun `every language mode other than off still shows`() {
        // "off" is the only mode that hides; the rest choose which language to render.
        listOf(Constants.SONG_LANG_BOTH, Constants.SONG_LANG_PRIMARY, "secondary").forEach { mode ->
            assertTrue(ScreenAssignment(bibleMode = mode).showBible, "bibleMode=$mode should still show")
            assertTrue(ScreenAssignment(songMode = mode).showSongs, "songMode=$mode should still show")
        }
    }

    @Test
    fun `the two kinds of content are switched independently`() {
        val bibleOnly = ScreenAssignment(songMode = Constants.SONG_LANG_OFF)

        assertTrue(bibleOnly.showBible, "an overflow screen can carry the reading without the lyrics")
        assertFalse(bibleOnly.showSongs)
    }

    // ── Full screen or a band across the bottom ─────────────────────────────────

    @Test
    fun `an output is full screen by default`() {
        val output = ScreenAssignment()

        assertFalse(output.isLowerThird)
        assertFalse(output.isLowerThirdVertical)
    }

    @Test
    fun `both band orientations count as a lower third`() {
        val horizontal = ScreenAssignment(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)
        val vertical = ScreenAssignment(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL)

        assertTrue(horizontal.isLowerThird)
        assertTrue(vertical.isLowerThird, "a vertical band is still a band; layout branches on this")
    }

    @Test
    fun `only the vertical band reports as vertical`() {
        assertFalse(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL).isLowerThirdVertical)
        assertTrue(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL).isLowerThirdVertical)
    }

    @Test
    fun `a stage monitor is not a lower third`() {
        val stage = ScreenAssignment(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)

        assertFalse(stage.isLowerThird, "the stage monitor has its own layout entirely")
        assertFalse(stage.isLowerThirdVertical)
    }

    @Test
    fun `an unrecognised display mode falls back to full screen`() {
        // A settings file from a newer build could name a mode this one has never heard of.
        val unknown = ScreenAssignment(displayMode = "some_future_mode")

        assertFalse(unknown.isLowerThird, "an unknown mode must render something rather than nothing")
    }

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

    @Test
    fun `a key channel is independent of what the output shows`() {
        val keyedBandWithoutSongs = ScreenAssignment(
            keyTargetDisplay = 2,
            displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            songMode = Constants.SONG_LANG_OFF,
        )

        assertTrue(keyedBandWithoutSongs.hasKeyOutput)
        assertTrue(keyedBandWithoutSongs.isLowerThird)
        assertFalse(keyedBandWithoutSongs.showSongs)
        assertTrue(keyedBandWithoutSongs.showBible, "these four decisions are made separately")
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
        assertEquals(StageMonitorStyleZone.TOP_LEFT, StageMonitorZone.TOP_LEFT.toStyleZone())
        assertEquals(StageMonitorStyleZone.TOP_RIGHT, StageMonitorZone.TOP_RIGHT.toStyleZone())
        assertEquals(StageMonitorStyleZone.BOTTOM_LEFT, StageMonitorZone.BOTTOM_LEFT.toStyleZone())
        assertEquals(StageMonitorStyleZone.BOTTOM_MIDDLE, StageMonitorZone.BOTTOM_MIDDLE.toStyleZone())
        assertEquals(StageMonitorStyleZone.BOTTOM_RIGHT, StageMonitorZone.BOTTOM_RIGHT.toStyleZone())
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
}

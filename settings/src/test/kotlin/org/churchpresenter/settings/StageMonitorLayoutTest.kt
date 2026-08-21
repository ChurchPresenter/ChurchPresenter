package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The layout catalog and the routing that hangs off it.
 *
 * Zones are slots owned by a layout rather than fixed positions, so the invariants worth pinning are
 * the ones the rest of the app assumes: that a layout's slots are the first N in order, that
 * FULL_SCREEN is never one of them, and that changing layout cannot leave a content type pointing
 * somewhere nothing draws it.
 */
class StageMonitorLayoutTest {

    @Test
    fun `every layout fills the first N slots in order`() {
        for (layout in StageMonitorLayout.entries) {
            val expected = StageMonitorStyleZone.entries.take(layout.slots.size)
            assertEquals(expected, layout.slots, "$layout must use A, B, C… with no gaps")
        }
    }

    /** FULL_SCREEN replaces the grid rather than sitting in it, so no layout may claim it. */
    @Test
    fun `no layout draws the full-screen zone as one of its cells`() {
        for (layout in StageMonitorLayout.entries) {
            assertFalse(
                StageMonitorStyleZone.FULL_SCREEN in layout.slots,
                "$layout must not use FULL_SCREEN as a slot",
            )
        }
    }

    @Test
    fun `the catalog offers two arrangements at every zone count from two to five`() {
        assertEquals(listOf(2, 3, 4, 5), StageMonitorLayout.zoneCounts())
        for (count in StageMonitorLayout.zoneCounts()) {
            val layouts = StageMonitorLayout.withZoneCount(count)
            assertEquals(2, layouts.size, "$count zones must offer two arrangements")
            assertTrue(layouts.all { it.slots.size == count })
        }
    }

    @Test
    fun `every row and cell carries a positive weight`() {
        for (layout in StageMonitorLayout.entries) {
            for (row in layout.rows) {
                assertTrue(row.weight > 0f, "$layout has a row with no height")
                assertTrue(row.cells.isNotEmpty(), "$layout has an empty row")
                assertTrue(row.cells.all { it.weight > 0f }, "$layout has a cell with no width")
            }
        }
    }

    /** The arrangement the monitor has always drawn, kept as the default so saved screens open unchanged. */
    @Test
    fun `classic is the default and is two zones over three`() {
        assertEquals(StageMonitorLayout.CLASSIC, StageMonitorSettings().layout)
        val rows = StageMonitorLayout.CLASSIC.rows
        assertEquals(2, rows.size)
        assertEquals(2, rows[0].cells.size)
        assertEquals(3, rows[1].cells.size)
        assertEquals(
            0.8f,
            rows[1].cells[1].weight,
            "the middle of the bottom three has always been the narrow one",
        )
    }

    @Test
    fun `draws covers the full screen always and none never`() {
        val twoZones = StageMonitorLayout.TOP_BOTTOM
        assertTrue(twoZones.draws(StageMonitorZone.FULL_SCREEN))
        assertFalse(twoZones.draws(StageMonitorZone.NONE))
        assertTrue(twoZones.draws(StageMonitorZone.A))
        assertTrue(twoZones.draws(StageMonitorZone.B))
        assertFalse(twoZones.draws(StageMonitorZone.C), "a two-zone layout has no third zone")
    }

    // ── Routing across a layout change ──────────────────────────────────────────

    @Test
    fun `switching to a smaller layout sends what it cannot draw to none`() {
        val classic = StageMonitorSettings()
        assertEquals(StageMonitorZone.C, classic.zoneFor(StageMonitorContentType.ANNOUNCEMENT_TEXT))

        val narrowed = classic.withLayout(StageMonitorLayout.LEFT_RIGHT)

        assertEquals(StageMonitorLayout.LEFT_RIGHT, narrowed.layout)
        assertEquals(
            StageMonitorZone.NONE,
            narrowed.zoneFor(StageMonitorContentType.ANNOUNCEMENT_TEXT),
            "Zone 3 is gone, so what was in it is shown nowhere and must say so",
        )
        assertEquals(
            StageMonitorZone.A,
            narrowed.zoneFor(StageMonitorContentType.BIBLE),
            "a zone the new layout still draws keeps its routing",
        )
        assertEquals(
            StageMonitorZone.FULL_SCREEN,
            narrowed.zoneFor(StageMonitorContentType.MEDIA),
            "the full screen survives every layout",
        )
    }

    @Test
    fun `switching layouts leaves nothing stranded`() {
        for (layout in StageMonitorLayout.entries) {
            val moved = StageMonitorSettings().withLayout(layout)
            assertEquals(
                emptyList(),
                moved.strandedTypes(),
                "$layout must not leave a content type pointing at a zone it does not draw",
            )
        }
    }

    /** Going back does not undo it — the routing was cleared, and only the styles survive. */
    @Test
    fun `a round trip through a smaller layout does not restore the routing`() {
        val there = StageMonitorSettings().withLayout(StageMonitorLayout.LEFT_RIGHT)
        val back = there.withLayout(StageMonitorLayout.CLASSIC)

        assertEquals(StageMonitorLayout.CLASSIC, back.layout)
        assertEquals(StageMonitorZone.NONE, back.zoneFor(StageMonitorContentType.ANNOUNCEMENT_TEXT))
        assertEquals(
            StageMonitorSettings().styleFor(StageMonitorStyleZone.E),
            back.styleFor(StageMonitorStyleZone.E),
            "zone styles are not routing and must come back untouched",
        )
    }

    /**
     * A hand-edited or imported document can still strand a type; the UI reads this to say so.
     *
     * Built by going through [StageMonitorSettings.withLayout] first — which clears the defaults
     * this layout cannot draw — and then putting one type back where nothing draws it, so the one
     * stranding under test is the only one there is.
     */
    @Test
    fun `stranded types are reported for a document the UI did not produce`() {
        val clean = StageMonitorSettings().withLayout(StageMonitorLayout.LEFT_RIGHT)
        val hand = clean.copy(
            contentZones = clean.contentZones + (StageMonitorContentType.BIBLE to StageMonitorZone.E),
        )
        assertEquals(listOf(StageMonitorContentType.BIBLE), hand.strandedTypes())
    }

    @Test
    fun `a type switched off is not stranded, it is off`() {
        val clean = StageMonitorSettings().withLayout(StageMonitorLayout.LEFT_RIGHT)
        val off = clean.copy(
            contentZones = clean.contentZones + (StageMonitorContentType.BIBLE to StageMonitorZone.NONE),
        )
        assertEquals(emptyList(), off.strandedTypes())
    }

    // ── Lookups ─────────────────────────────────────────────────────────────────

    @Test
    fun `typesIn reads the routing map from the zone's side`() {
        val defaults = StageMonitorSettings()
        assertEquals(
            listOf(StageMonitorContentType.BIBLE, StageMonitorContentType.SONGS),
            defaults.typesIn(StageMonitorZone.A),
        )
        assertEquals(emptyList(), defaults.typesIn(StageMonitorZone.E))
    }

    @Test
    fun `a content type missing from saved settings falls back to its default zone`() {
        val partial = StageMonitorSettings(contentZones = emptyMap())
        assertEquals(StageMonitorZone.A, partial.zoneFor(StageMonitorContentType.BIBLE))
        assertEquals(StageMonitorZone.D, partial.zoneFor(StageMonitorContentType.CLOCK))
    }

    @Test
    fun `a zone missing from saved settings falls back to its default style`() {
        val partial = StageMonitorSettings(zoneStyles = emptyMap())
        assertEquals(
            StageMonitorSettings.defaultZoneStyles().getValue(StageMonitorStyleZone.B),
            partial.styleFor(StageMonitorStyleZone.B),
        )
    }

    @Test
    fun `the two zone enums convert both ways`() {
        for (slot in StageMonitorStyleZone.entries) {
            assertEquals(slot, slot.toZone().toStyleZone(), "$slot must survive the round trip")
        }
        assertEquals(null, StageMonitorZone.NONE.toStyleZone(), "None is not drawn, so it has no style")
    }
}

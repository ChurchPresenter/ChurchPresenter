package org.churchpresenter.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The schedule card density ladder: Compact / Normal / Detailed, plus one rung smaller and one
 * larger than those. Pins the rung resolution (so a value saved under the old continuous zoom still
 * lands on a sensible rung), the clamped step, and which rungs show the detail line vs the kind
 * chip.
 *
 * The three original rungs keep their percents and their bands, so nothing anyone has already saved
 * moves when the ladder grows at the ends. The outer two are only reachable by stepping past what
 * used to be the end.
 */
class ScheduleZoomTest {

    // ── The enum itself ────────────────────────────────────────────────────────

    @Test fun `the ladder runs smallest to largest, with the original three unmoved`() {
        assertEquals(
            listOf(
                ScheduleDensity.EXTRA_COMPACT, ScheduleDensity.COMPACT, ScheduleDensity.NORMAL,
                ScheduleDensity.DETAILED, ScheduleDensity.EXTRA_DETAILED,
            ),
            ScheduleDensity.entries,
            "scheduleZoomIn/Out walk the ladder by ordinal, so the declared order IS the step order",
        )
        assertEquals(55, ScheduleDensity.EXTRA_COMPACT.percent)
        assertEquals(70, ScheduleDensity.COMPACT.percent, "unchanged, so a saved 70 still means Compact")
        assertEquals(100, ScheduleDensity.NORMAL.percent)
        assertEquals(150, ScheduleDensity.DETAILED.percent)
        assertEquals(200, ScheduleDensity.EXTRA_DETAILED.percent)
    }

    // ── scheduleDensityFor ────────────────────────────────────────────────────

    @Test fun `a legacy low percent resolves to Compact`() {
        assertEquals(ScheduleDensity.COMPACT, scheduleDensityFor(70))
        assertEquals(ScheduleDensity.COMPACT, scheduleDensityFor(90))
    }

    @Test fun `a legacy mid percent resolves to Normal`() {
        assertEquals(ScheduleDensity.NORMAL, scheduleDensityFor(100))
        assertEquals(ScheduleDensity.NORMAL, scheduleDensityFor(91))
        assertEquals(ScheduleDensity.NORMAL, scheduleDensityFor(119))
    }

    @Test fun `a legacy high percent resolves to Detailed`() {
        // 150 was the top of the old scheme, so everything a user could have saved lands here
        // rather than on the new rung above it.
        assertEquals(ScheduleDensity.DETAILED, scheduleDensityFor(120))
        assertEquals(ScheduleDensity.DETAILED, scheduleDensityFor(150))
        assertEquals(ScheduleDensity.DETAILED, scheduleDensityFor(175))
    }

    @Test fun `the outer rungs resolve past the old scheme's ends`() {
        assertEquals(ScheduleDensity.EXTRA_DETAILED, scheduleDensityFor(200))
        assertEquals(ScheduleDensity.EXTRA_DETAILED, scheduleDensityFor(500))
        assertEquals(ScheduleDensity.EXTRA_COMPACT, scheduleDensityFor(55))
        assertEquals(ScheduleDensity.EXTRA_COMPACT, scheduleDensityFor(60))
    }

    @Test fun `percents at or below zero still resolve to the smallest rung rather than throwing`() {
        assertEquals(ScheduleDensity.EXTRA_COMPACT, scheduleDensityFor(0))
        assertEquals(ScheduleDensity.EXTRA_COMPACT, scheduleDensityFor(-100))
    }

    // ── scheduleZoomIn / scheduleZoomOut ─────────────────────────────────────

    @Test fun `zooming in steps to the next rung up, from every starting rung`() {
        assertEquals(70, scheduleZoomIn(55), "the smallest rung -> Compact")
        assertEquals(100, scheduleZoomIn(70), "Compact -> Normal")
        assertEquals(150, scheduleZoomIn(100), "Normal -> Detailed")
        assertEquals(200, scheduleZoomIn(150), "Detailed -> the largest rung")
        assertEquals(200, scheduleZoomIn(200), "the largest rung is the top, so it stays put")
    }

    @Test fun `zooming out steps to the next rung down, from every starting rung`() {
        assertEquals(55, scheduleZoomOut(55), "the smallest rung is the bottom, so it stays put")
        assertEquals(55, scheduleZoomOut(70), "Compact -> the smallest rung")
        assertEquals(70, scheduleZoomOut(100), "Normal -> Compact")
        assertEquals(100, scheduleZoomOut(150), "Detailed -> Normal")
        assertEquals(150, scheduleZoomOut(200), "the largest rung -> Detailed")
    }

    @Test fun `zooming steps by rung, not by the raw legacy percent`() {
        // 80 and 140 are legacy values that resolve to Compact and Detailed respectively, but
        // neither is one of the ladder's own three percents — the step must still land exactly on
        // the neighbouring rung's percent, not drift from the raw input.
        assertEquals(100, scheduleZoomIn(80), "a legacy Compact-range percent still steps to Normal's 100")
        assertEquals(100, scheduleZoomOut(140), "a legacy Detailed-range percent still steps to Normal's 100")
    }

    @Test fun `zooming in then out from the middle rung returns to where it started`() {
        assertEquals(100, scheduleZoomOut(scheduleZoomIn(100)))
        assertEquals(100, scheduleZoomIn(scheduleZoomOut(100)))
    }

    // ── scheduleCanZoomIn / scheduleCanZoomOut ───────────────────────────────

    @Test fun `can-zoom flags respect the ladder ends`() {
        // These are what grey out the -/+ controls, so they have to move with the ladder's ends
        // rather than stay pinned to Compact and Detailed.
        assertFalse(scheduleCanZoomOut(55), "already at the smallest rung")
        assertTrue(scheduleCanZoomIn(55))
        assertFalse(scheduleCanZoomIn(200), "already at the largest rung")
        assertTrue(scheduleCanZoomOut(200))
        assertTrue(scheduleCanZoomOut(70), "Compact is no longer the bottom")
        assertTrue(scheduleCanZoomIn(150), "Detailed is no longer the top")
    }

    @Test fun `both can-zoom flags are true in the middle rung`() {
        assertTrue(scheduleCanZoomIn(100), "Normal can still step up to Detailed")
        assertTrue(scheduleCanZoomOut(100), "Normal can still step down to Compact")
    }

    // ── Display flags ─────────────────────────────────────────────────────────

    @Test fun `the two smallest rungs hide the detail line, the rest show it`() {
        assertFalse(scheduleShowDetailLine(55))
        assertFalse(scheduleShowDetailLine(70))
        assertTrue(scheduleShowDetailLine(100))
        assertTrue(scheduleShowDetailLine(150))
        assertTrue(scheduleShowDetailLine(200))
    }

    @Test fun `only the two largest rungs show the kind chip`() {
        assertFalse(scheduleShowKindDetails(55))
        assertFalse(scheduleShowKindDetails(70))
        assertFalse(scheduleShowKindDetails(100))
        assertTrue(scheduleShowKindDetails(150))
        assertTrue(scheduleShowKindDetails(200))
    }

    @Test fun `zooming past either end of the ladder stays on the end rung`() {
        val largest = ScheduleDensity.entries.last().percent
        val smallest = ScheduleDensity.entries.first().percent
        assertEquals(largest, scheduleZoomIn(largest))
        assertEquals(smallest, scheduleZoomOut(smallest))
    }
}

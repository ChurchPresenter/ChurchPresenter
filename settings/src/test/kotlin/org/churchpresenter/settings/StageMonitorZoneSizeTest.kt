package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * How big each zone is, and what moving one does to the others.
 *
 * The invariant under everything here is that a row's widths and the screen's row heights each sum
 * to exactly 100 in whole numbers — the diagram in the settings tab says so out loud, so a rounding
 * that leaves 101 is a visible defect rather than a detail.
 */
class StageMonitorZoneSizeTest {

    private val classic = StageMonitorSettings()

    private fun StageMonitorZoneSizes.assertSumsTo100(what: String) {
        assertEquals(STAGE_ZONE_FULL_PERCENT, rowHeights.sum(), "$what: the rows must fill the screen")
        rowCellWidths.forEachIndexed { index, widths ->
            assertEquals(STAGE_ZONE_FULL_PERCENT, widths.sum(), "$what: row $index must fill its width")
        }
        assertTrue(rowHeights.all { it == it.toInt().toFloat() }, "$what: row heights must be whole")
        assertTrue(
            rowCellWidths.flatten().all { it == it.toInt().toFloat() },
            "$what: widths must be whole",
        )
    }

    // ── The defaults ────────────────────────────────────────────────────────────

    @Test
    fun `every layout starts at whole percentages that add up to 100`() {
        for (layout in StageMonitorLayout.entries) {
            StageMonitorZoneSizes.of(layout).assertSumsTo100("$layout")
        }
    }

    /** The catalog weights carried over as percentages: two thirds over one third, and 0.8 of a cell. */
    @Test
    fun `classic keeps the proportions the catalog gives it`() {
        val sizes = StageMonitorZoneSizes.of(StageMonitorLayout.CLASSIC)
        assertEquals(listOf(67f, 33f), sizes.rowHeights)
        assertEquals(listOf(50f, 50f), sizes.rowCellWidths[0])
        assertEquals(listOf(36f, 28f, 36f), sizes.rowCellWidths[1], "the middle cell is the narrow one")
    }

    @Test
    fun `an unresized layout reports the catalog sizes and no customization`() {
        assertEquals(StageMonitorZoneSizes.of(StageMonitorLayout.CLASSIC), classic.layoutSizes())
        assertFalse(classic.hasCustomZoneSizes())
    }

    @Test
    fun `sizing saved for a layout with a different grid is ignored`() {
        val wrong = classic.copy(
            zoneSizes = mapOf(StageMonitorLayout.CLASSIC to StageMonitorZoneSizes(listOf(50f), listOf(listOf(100f)))),
        )
        assertEquals(StageMonitorZoneSizes.of(StageMonitorLayout.CLASSIC), wrong.layoutSizes())
        assertFalse(StageMonitorZoneSizes(listOf(50f), listOf(listOf(100f))).fits(StageMonitorLayout.CLASSIC))
    }

    // ── Trading between neighbours ──────────────────────────────────────────────

    /** The whole point of a divider: it belongs to two zones, so it moves two and no more. */
    @Test
    fun `widening a zone takes the width from its one neighbour, not from the whole row`() {
        val wider = classic.withZoneWidth(StageMonitorStyleZone.C, 50f)

        assertEquals(50f, wider.zoneWidthPercent(StageMonitorStyleZone.C))
        assertEquals(14f, wider.zoneWidthPercent(StageMonitorStyleZone.D), "the neighbour absorbs it")
        assertEquals(36f, wider.zoneWidthPercent(StageMonitorStyleZone.E), "the far cell must not move")
        wider.layoutSizes().assertSumsTo100("after widening C")
    }

    @Test
    fun `the last zone in a row trades with the one before it`() {
        val wider = classic.withZoneWidth(StageMonitorStyleZone.E, 50f)

        assertEquals(50f, wider.zoneWidthPercent(StageMonitorStyleZone.E))
        assertEquals(14f, wider.zoneWidthPercent(StageMonitorStyleZone.D))
        assertEquals(36f, wider.zoneWidthPercent(StageMonitorStyleZone.C))
    }

    @Test
    fun `a taller row takes the height from the row below and leaves the widths alone`() {
        val taller = classic.withZoneHeight(StageMonitorStyleZone.A, 80f)

        assertEquals(80f, taller.zoneHeightPercent(StageMonitorStyleZone.A))
        assertEquals(80f, taller.zoneHeightPercent(StageMonitorStyleZone.B), "a row has one height")
        assertEquals(20f, taller.zoneHeightPercent(StageMonitorStyleZone.C))
        assertEquals(classic.layoutSizes().rowCellWidths, taller.layoutSizes().rowCellWidths)
    }

    @Test
    fun `widening a zone on one row leaves the other row untouched`() {
        val wider = classic.withZoneWidth(StageMonitorStyleZone.A, 70f)

        assertEquals(listOf(36f, 28f, 36f), wider.layoutSizes().rowCellWidths[1])
        assertEquals(listOf(67f, 33f), wider.layoutSizes().rowHeights)
    }

    @Test
    fun `a width is clamped so its neighbour keeps the minimum`() {
        val squeezed = classic.withZoneWidth(StageMonitorStyleZone.A, 99f)

        assertEquals(
            STAGE_ZONE_FULL_PERCENT - STAGE_ZONE_MIN_WIDTH_PERCENT,
            squeezed.zoneWidthPercent(StageMonitorStyleZone.A),
        )
        assertEquals(STAGE_ZONE_MIN_WIDTH_PERCENT, squeezed.zoneWidthPercent(StageMonitorStyleZone.B))
    }

    @Test
    fun `a zone cannot be shrunk away`() {
        val tiny = classic.withZoneWidth(StageMonitorStyleZone.A, 0f)
        assertEquals(STAGE_ZONE_MIN_WIDTH_PERCENT, tiny.zoneWidthPercent(StageMonitorStyleZone.A))

        val short = classic.withZoneHeight(StageMonitorStyleZone.A, 1f)
        assertEquals(STAGE_ZONE_MIN_HEIGHT_PERCENT, short.zoneHeightPercent(StageMonitorStyleZone.A))
    }

    /** Height has the higher floor: a zone is unreadable when it is short before it is narrow. */
    @Test
    fun `height keeps more room than width does`() {
        assertTrue(STAGE_ZONE_MIN_HEIGHT_PERCENT > STAGE_ZONE_MIN_WIDTH_PERCENT)
    }

    @Test
    fun `a fractional percentage is stored as a whole one`() {
        val nudged = classic.withZoneWidth(StageMonitorStyleZone.A, 42.6f)

        assertEquals(43f, nudged.zoneWidthPercent(StageMonitorStyleZone.A))
        nudged.layoutSizes().assertSumsTo100("after a fractional drag")
    }

    /** A long drag is one move per frame, so the rounding must not compound. */
    @Test
    fun `repeated small changes still add up to 100`() {
        var settings = classic
        for (percent in 50 downTo 20) {
            settings = settings.withZoneWidth(StageMonitorStyleZone.A, percent.toFloat())
            settings.layoutSizes().assertSumsTo100("at $percent%")
        }
        assertEquals(20f, settings.zoneWidthPercent(StageMonitorStyleZone.A))
        assertEquals(80f, settings.zoneWidthPercent(StageMonitorStyleZone.B))
    }

    @Test
    fun `a zone the layout does not draw changes nothing`() {
        val single = StageMonitorSettings().withLayout(StageMonitorLayout.LEFT_RIGHT)
        assertEquals(single, single.withZoneWidth(StageMonitorStyleZone.E, 50f))
        assertEquals(single, single.withZoneHeight(StageMonitorStyleZone.E, 50f))
    }

    /** One row, or one cell in a row, owns the whole of it and has nothing to trade with. */
    @Test
    fun `a lone row or cell stays at 100`() {
        val leftRight = StageMonitorSettings().withLayout(StageMonitorLayout.LEFT_RIGHT)
        val tried = leftRight.withZoneHeight(StageMonitorStyleZone.A, 40f)
        assertEquals(STAGE_ZONE_FULL_PERCENT, tried.zoneHeightPercent(StageMonitorStyleZone.A))

        val topBottom = StageMonitorSettings().withLayout(StageMonitorLayout.TOP_BOTTOM)
        val narrowed = topBottom.withZoneWidth(StageMonitorStyleZone.A, 40f)
        assertEquals(STAGE_ZONE_FULL_PERCENT, narrowed.zoneWidthPercent(StageMonitorStyleZone.A))
    }

    // ── Evening out, and going back ─────────────────────────────────────────────

    @Test
    fun `evening a row splits it equally and leaves every other row alone`() {
        val even = classic.withZoneWidth(StageMonitorStyleZone.A, 70f).withEvenRowWidths(StageMonitorStyleZone.C)

        assertEquals(listOf(33f, 33f, 34f), even.layoutSizes().rowCellWidths[1], "the remainder goes to the last")
        assertEquals(70f, even.zoneWidthPercent(StageMonitorStyleZone.A), "the other row is not its business")
    }

    @Test
    fun `evening everything squares up every row and every cell`() {
        val even = classic.withZoneHeight(StageMonitorStyleZone.A, 80f).withEvenZoneSizes()

        assertEquals(listOf(50f, 50f), even.layoutSizes().rowHeights)
        assertEquals(listOf(50f, 50f), even.layoutSizes().rowCellWidths[0])
        assertEquals(listOf(33f, 33f, 34f), even.layoutSizes().rowCellWidths[1])
        even.layoutSizes().assertSumsTo100("after evening out")
    }

    @Test
    fun `reset brings the layout back to the catalog proportions`() {
        val resized = classic.withZoneWidth(StageMonitorStyleZone.A, 70f)
        assertTrue(resized.hasCustomZoneSizes())

        val back = resized.withDefaultZoneSizes()
        assertEquals(StageMonitorZoneSizes.of(StageMonitorLayout.CLASSIC), back.layoutSizes())
        assertFalse(back.hasCustomZoneSizes())
    }

    // ── One layout's sizing is not another's ────────────────────────────────────

    @Test
    fun `each layout keeps its own sizing across a switch`() {
        val resized = classic.withZoneWidth(StageMonitorStyleZone.A, 70f)
        val elsewhere = resized.withLayout(StageMonitorLayout.QUAD)

        assertFalse(elsewhere.hasCustomZoneSizes(), "the new layout starts at its own catalog shape")
        assertEquals(70f, elsewhere.withLayout(StageMonitorLayout.CLASSIC).zoneWidthPercent(StageMonitorStyleZone.A))
    }

    @Test
    fun `resizing one layout does not touch another`() {
        val resized = classic
            .withZoneWidth(StageMonitorStyleZone.A, 70f)
            .withLayout(StageMonitorLayout.QUAD)
            .withZoneWidth(StageMonitorStyleZone.A, 25f)

        assertEquals(25f, resized.zoneWidthPercent(StageMonitorStyleZone.A))
        assertEquals(70f, resized.withLayout(StageMonitorLayout.CLASSIC).zoneWidthPercent(StageMonitorStyleZone.A))
    }

    @Test
    fun `zone sizes survive a save and a load`() {
        val resized = classic.withZoneWidth(StageMonitorStyleZone.C, 50f).withZoneHeight(StageMonitorStyleZone.A, 75f)
        val json = Json { ignoreUnknownKeys = true }

        val loaded = json.decodeFromString(
            StageMonitorSettings.serializer(),
            json.encodeToString(StageMonitorSettings.serializer(), resized),
        )

        assertEquals(resized, loaded)
        assertEquals(50f, loaded.zoneWidthPercent(StageMonitorStyleZone.C))
        assertEquals(75f, loaded.zoneHeightPercent(StageMonitorStyleZone.A))
    }

    @Test
    fun `settings written before zone sizing existed open at the catalog shape`() {
        val old = Json { ignoreUnknownKeys = true }
            .decodeFromString(StageMonitorSettings.serializer(), """{"layout":"CLASSIC"}""")

        assertEquals(StageMonitorZoneSizes.of(StageMonitorLayout.CLASSIC), old.layoutSizes())
        assertFalse(old.hasCustomZoneSizes())
    }

    // ── The arithmetic on its own ───────────────────────────────────────────────

    @Test
    fun `trading keeps the pair's total and moves nobody else`() {
        assertEquals(
            listOf(20f, 40f, 40f),
            tradeShares(listOf(30f, 30f, 40f), 0, 20f, STAGE_ZONE_MIN_WIDTH_PERCENT),
        )
    }

    @Test
    fun `trading clamps at both ends of the pair`() {
        val values = listOf(30f, 30f, 40f)
        assertEquals(listOf(50f, 10f, 40f), tradeShares(values, 0, 90f, STAGE_ZONE_MIN_WIDTH_PERCENT))
        assertEquals(listOf(10f, 50f, 40f), tradeShares(values, 0, 5f, STAGE_ZONE_MIN_WIDTH_PERCENT))
    }

    @Test
    fun `a single share has nobody to trade with`() {
        assertEquals(listOf(100f), tradeShares(listOf(100f), 0, 40f, STAGE_ZONE_MIN_WIDTH_PERCENT))
    }

    @Test
    fun `an even split hands the remainder to the last share`() {
        assertEquals(listOf(100f), evenShares(1))
        assertEquals(listOf(50f, 50f), evenShares(2))
        assertEquals(listOf(33f, 33f, 34f), evenShares(3))
        for (count in 1..5) {
            assertEquals(STAGE_ZONE_FULL_PERCENT, evenShares(count).sum(), "$count shares must fill the row")
        }
    }

    @Test
    fun `an equal split of a layout is not the same as its catalog shape`() {
        assertNotEquals(
            StageMonitorZoneSizes.of(StageMonitorLayout.CLASSIC),
            StageMonitorZoneSizes.evenOver(StageMonitorLayout.CLASSIC),
        )
        StageMonitorZoneSizes.evenOver(StageMonitorLayout.CLASSIC).assertSumsTo100("evened classic")
    }

    @Test
    fun `cellOf finds a zone in the grid and nowhere else`() {
        assertEquals(0 to 0, StageMonitorLayout.CLASSIC.cellOf(StageMonitorStyleZone.A))
        assertEquals(1 to 2, StageMonitorLayout.CLASSIC.cellOf(StageMonitorStyleZone.E))
        assertEquals(null, StageMonitorLayout.LEFT_RIGHT.cellOf(StageMonitorStyleZone.E))
        assertEquals(null, StageMonitorLayout.CLASSIC.cellOf(StageMonitorStyleZone.FULL_SCREEN))
    }
}

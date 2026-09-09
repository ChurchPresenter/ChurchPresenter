package org.churchpresenter.settings

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/** The whole of a row's height, or of a row's width — every share below is a percentage of it. */
const val STAGE_ZONE_FULL_PERCENT = 100f

/**
 * The smallest share a zone can be squeezed to, wide and tall.
 *
 * A zone at 0% is not a smaller zone, it is a missing one, and the layout catalog is where zones
 * are added and removed. Height has the higher floor because a zone that is short is unreadable
 * before it is narrow: a line of text needs its own height whatever the column is doing.
 */
const val STAGE_ZONE_MIN_WIDTH_PERCENT = 10f
const val STAGE_ZONE_MIN_HEIGHT_PERCENT = 15f

/**
 * Percentage sizing for one layout's grid: the height of each row down the screen, and the width
 * of each cell across each row. Every list is whole numbers summing to exactly 100.
 *
 * Kept per layout rather than per zone because a zone's size is only meaningful inside the grid it
 * belongs to — zone B is beside A in one layout and under it in another — and because switching
 * layouts to look at it should not throw away the sizing of the one in use.
 */
@Serializable
data class StageMonitorZoneSizes(
    val rowHeights: List<Float> = emptyList(),
    val rowCellWidths: List<List<Float>> = emptyList()
) {
    /** True when this sizing describes [layout]'s actual grid — same rows, same cells in each. */
    fun fits(layout: StageMonitorLayout): Boolean =
        rowHeights.size == layout.rows.size &&
            rowCellWidths.size == layout.rows.size &&
            layout.rows.indices.all { rowCellWidths[it].size == layout.rows[it].cells.size }

    companion object {
        /** [layout]'s own weights, expressed as percentages. */
        fun of(layout: StageMonitorLayout): StageMonitorZoneSizes = StageMonitorZoneSizes(
            rowHeights = layout.rows.map { it.weight }.asPercentages(),
            rowCellWidths = layout.rows.map { r -> r.cells.map { it.weight }.asPercentages() }
        )

        /** Every row and every cell of [layout] given an equal share. */
        fun evenOver(layout: StageMonitorLayout): StageMonitorZoneSizes = StageMonitorZoneSizes(
            rowHeights = evenShares(layout.rows.size),
            rowCellWidths = layout.rows.map { evenShares(it.cells.size) }
        )
    }
}

/**
 * [count] whole percentages summing to 100, the last one carrying whatever does not divide.
 *
 * Three zones are 33/33/34 rather than 33.33 each, because these numbers are read off a diagram
 * that claims to add up to 100 and a column of 33s does not.
 */
fun evenShares(count: Int): List<Float> {
    if (count <= 0) return emptyList()
    val each = floor(STAGE_ZONE_FULL_PERCENT / count)
    return List(count) { i -> if (i == count - 1) STAGE_ZONE_FULL_PERCENT - each * (count - 1) else each }
}

/**
 * These weights as whole shares of 100, whatever they add up to now.
 *
 * Whole numbers throughout, by largest remainder, so what a zone reports is what it is: rounding
 * only for display would show a row of 36 + 29 + 36 beside the claim that a row shares 100%.
 */
private fun List<Float>.asPercentages(): List<Float> {
    if (isEmpty()) return this
    val total = sum()
    if (total <= 0f) return evenShares(size)
    val exact = map { it / total * STAGE_ZONE_FULL_PERCENT }
    val shares = exact.map { floor(it) }.toMutableList()
    var over = (STAGE_ZONE_FULL_PERCENT - shares.sum()).roundToInt()
    for (i in exact.indices.sortedByDescending { exact[it] - shares[it] }) {
        if (over <= 0) break
        shares[i] += 1f
        over--
    }
    return shares
}

/**
 * [values] with the entry at [index] set to [percent], the difference taken from — or handed to —
 * the single neighbour it shares an edge with: the next entry, or the previous one when it is last.
 *
 * This is the divider written as arithmetic. A divider belongs to exactly two zones, so moving it
 * moves exactly those two: their own total is what stays fixed, which is what keeps the whole list
 * at 100 without disturbing anything further along the row. Neither of the pair drops below
 * [minimum], and [percent] is clamped to whatever that leaves.
 */
fun tradeShares(values: List<Float>, index: Int, percent: Float, minimum: Float): List<Float> {
    if (values.size < 2 || index !in values.indices) return values
    val partner = if (index < values.lastIndex) index + 1 else index - 1
    val total = values[index] + values[partner]
    val mine = percent.roundToInt().toFloat().coerceIn(minimum, total - minimum)
    return values.mapIndexed { i, value ->
        when (i) {
            index -> mine
            partner -> total - mine
            else -> value
        }
    }
}

/** Where [zone] sits in this layout's grid, as row index to cell index, or null if it is not drawn. */
fun StageMonitorLayout.cellOf(zone: StageMonitorStyleZone): Pair<Int, Int>? {
    rows.forEachIndexed { rowIndex, row ->
        val cellIndex = row.cells.indexOfFirst { it.slot == zone }
        if (cellIndex >= 0) return rowIndex to cellIndex
    }
    return null
}

/**
 * Where the metronome flash dot is anchored on the stage monitor screen — a free 3x3 grid,
 * independent of the content zones above (no full-screen option since it's a small overlay).
 */
@Serializable
enum class MetronomePosition {
    NONE,
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

fun StageMonitorZone.toStyleZone(): StageMonitorStyleZone? = when (this) {
    StageMonitorZone.A -> StageMonitorStyleZone.A
    StageMonitorZone.B -> StageMonitorStyleZone.B
    StageMonitorZone.C -> StageMonitorStyleZone.C
    StageMonitorZone.D -> StageMonitorStyleZone.D
    StageMonitorZone.E -> StageMonitorStyleZone.E
    StageMonitorZone.FULL_SCREEN -> StageMonitorStyleZone.FULL_SCREEN
    StageMonitorZone.NONE -> null
}

/** The routing target that draws this style zone. */
fun StageMonitorStyleZone.toZone(): StageMonitorZone = when (this) {
    StageMonitorStyleZone.A -> StageMonitorZone.A
    StageMonitorStyleZone.B -> StageMonitorZone.B
    StageMonitorStyleZone.C -> StageMonitorZone.C
    StageMonitorStyleZone.D -> StageMonitorZone.D
    StageMonitorStyleZone.E -> StageMonitorZone.E
    StageMonitorStyleZone.FULL_SCREEN -> StageMonitorZone.FULL_SCREEN
}

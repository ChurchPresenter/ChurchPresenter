package org.churchpresenter.settings

/**
 * What resizing a stage monitor's zones does to a settings document.
 *
 * Separate from [StageMonitorZoneSizes], which is what a size *is*: this file is the operations —
 * every one of them taking the document and returning a new one, with the current layout's entry
 * replaced. Extensions rather than members so [StageMonitorSettings] stays the persisted shape and
 * nothing here has to be a field.
 */

/**
 * The sizing in force for the current layout.
 *
 * A stored entry that no longer describes the layout's grid — a saved file from a build whose
 * catalog had a different shape — is dropped rather than stretched onto it, so the layout is
 * drawn as its author meant it instead of half-resized.
 */
fun StageMonitorSettings.layoutSizes(): StageMonitorZoneSizes =
    zoneSizes[layout]?.takeIf { it.fits(layout) } ?: StageMonitorZoneSizes.of(layout)

/** The height of the row [zone] sits in, as a percentage of the screen. */
fun StageMonitorSettings.zoneHeightPercent(zone: StageMonitorStyleZone): Float {
    val (rowIndex, _) = layout.cellOf(zone) ?: return STAGE_ZONE_FULL_PERCENT
    return layoutSizes().rowHeights[rowIndex]
}

/** The width of [zone], as a percentage of the row it sits in. */
fun StageMonitorSettings.zoneWidthPercent(zone: StageMonitorStyleZone): Float {
    val (rowIndex, cellIndex) = layout.cellOf(zone) ?: return STAGE_ZONE_FULL_PERCENT
    return layoutSizes().rowCellWidths[rowIndex][cellIndex]
}

/**
 * These settings with [zone] taking [percent] of its row's width, the neighbour on the other
 * side of the divider between them giving up or taking back the difference.
 *
 * Only that pair moves. A row's width is its own 100%, so widening a zone on the top row never
 * disturbs the bottom one, and a third zone further along the row keeps the width it had.
 */
fun StageMonitorSettings.withZoneWidth(zone: StageMonitorStyleZone, percent: Float): StageMonitorSettings {
    val (rowIndex, cellIndex) = layout.cellOf(zone) ?: return this
    val sizes = layoutSizes()
    val widths = sizes.rowCellWidths.toMutableList()
    widths[rowIndex] = tradeShares(widths[rowIndex], cellIndex, percent, STAGE_ZONE_MIN_WIDTH_PERCENT)
    return withSizes(sizes.copy(rowCellWidths = widths))
}

/**
 * These settings with [zone]'s row taking [percent] of the screen's height, the row on the
 * other side of the divider between them giving up or taking back the difference.
 *
 * Height belongs to the row, so zones sharing a row are resized together — a cell taller than
 * the cell beside it is not a grid, and there is no way to ask for one.
 */
fun StageMonitorSettings.withZoneHeight(zone: StageMonitorStyleZone, percent: Float): StageMonitorSettings {
    val (rowIndex, _) = layout.cellOf(zone) ?: return this
    val sizes = layoutSizes()
    return withSizes(
        sizes.copy(rowHeights = tradeShares(sizes.rowHeights, rowIndex, percent, STAGE_ZONE_MIN_HEIGHT_PERCENT))
    )
}

/** These settings with the zones sharing [zone]'s row given an equal width each. */
fun StageMonitorSettings.withEvenRowWidths(zone: StageMonitorStyleZone): StageMonitorSettings {
    val (rowIndex, _) = layout.cellOf(zone) ?: return this
    val sizes = layoutSizes()
    val widths = sizes.rowCellWidths.toMutableList()
    widths[rowIndex] = evenShares(widths[rowIndex].size)
    return withSizes(sizes.copy(rowCellWidths = widths))
}

/** These settings with every row and every zone of the current layout given an equal share. */
fun StageMonitorSettings.withEvenZoneSizes(): StageMonitorSettings = withSizes(StageMonitorZoneSizes.evenOver(layout))

/** These settings with the current layout back at the proportions the catalog gives it. */
fun StageMonitorSettings.withDefaultZoneSizes(): StageMonitorSettings = copy(zoneSizes = zoneSizes - layout)

/** True when the current layout has been resized away from its catalog proportions. */
fun StageMonitorSettings.hasCustomZoneSizes(): Boolean = layoutSizes() != StageMonitorZoneSizes.of(layout)

private fun StageMonitorSettings.withSizes(sizes: StageMonitorZoneSizes): StageMonitorSettings =
    copy(zoneSizes = zoneSizes + (layout to sizes))

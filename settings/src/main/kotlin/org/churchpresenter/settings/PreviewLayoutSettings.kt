package org.churchpresenter.settings

/** A layout with a fresh [PreviewLayout.id] that none of [existing] uses, one empty area to start from. */
fun newPreviewLayout(existing: List<PreviewLayout>, name: String): PreviewLayout {
    val taken = existing.map { it.id }.toSet()
    var n = existing.size + 1
    while ("layout$n" in taken) n++
    return PreviewLayout(id = "layout$n", name = name)
}

/** The layout the panel draws: the chosen one, or the first where none is chosen; null while there are none. */
fun ProjectionSettings.activeLayout(): PreviewLayout? =
    previewLayouts.firstOrNull { it.id == activePreviewLayout } ?: previewLayouts.firstOrNull()

/** [this] with the layout [id] replaced by [transform] of it. */
fun ProjectionSettings.updateLayout(id: String, transform: (PreviewLayout) -> PreviewLayout): ProjectionSettings =
    copy(previewLayouts = previewLayouts.map { if (it.id == id) transform(it) else it })

/**
 * The layout today's preview groups draw, for settings written before layouts: the shown groups
 * one under another, each a column of its grid's rows, each row its outputs side by side -- a short
 * last row keeping empty areas so its previews stay the width of the rows above. Groups that are all
 * hidden drew nothing, and become one empty area. Null when there are no groups at all, which is the
 * panel listing every output one per row, as it always did.
 */
fun layoutFromGroups(groups: List<PreviewGroup>, name: String): PreviewLayout? {
    if (groups.isEmpty()) return null
    val shown = groups.filterNot { it.hidden }
    if (shown.isEmpty()) return PreviewLayout(id = "layout1", name = name)
    val blocks = shown.map { group ->
        val columns = group.shape.columns.coerceAtLeast(1)
        val rows = group.visibleMembers().chunked(columns).map { row ->
            splitArea(SPLIT_ACROSS, List(columns) { i -> PreviewArea(output = row.getOrElse(i) { "" }) })
        }
        if (rows.size == 1) rows.first() else splitArea(SPLIT_DOWN, rows)
    }
    val root = if (blocks.size == 1) blocks.first() else splitArea(SPLIT_DOWN, blocks)
    return PreviewLayout(id = "layout1", name = name, root = root)
}

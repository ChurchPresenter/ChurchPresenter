package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * How many previews a [PreviewGroup] lays side by side and stacked.
 *
 * Presets rather than free numbers, the way Stage Monitor offers vertical, horizontal and quadrant
 * layouts: a handful of shapes covers a booth, and each one is a name the operator can read.
 */
@Serializable
enum class PreviewGroupShape(val columns: Int, val rows: Int) {
    ONE_BY_ONE(columns = 1, rows = 1),
    ONE_BY_TWO(columns = 1, rows = 2),
    TWO_BY_ONE(columns = 2, rows = 1),
    ONE_BY_THREE(columns = 1, rows = 3),
    THREE_BY_ONE(columns = 3, rows = 1),
    TWO_BY_TWO(columns = 2, rows = 2),
    ONE_BY_FOUR(columns = 1, rows = 4),
    FOUR_BY_ONE(columns = 4, rows = 1);

    /** How many outputs the grid holds. */
    val capacity: Int get() = columns * rows
}

/**
 * A block of the preview panel: some outputs arranged in a grid.
 *
 * Grouping only changes which previews the panel shows and how it lays them out. It never reorders the output lists
 * themselves, so nothing keyed by an output's position -- locks, tab preview picks -- is touched.
 *
 * [members] are output keys from `Constants.previewOutputKey`, in cell order (row by row). Members
 * past [PreviewGroupShape.capacity] stay stored but are not shown until the shape grows or they move.
 */
@Serializable
data class PreviewGroup(
    val id: String = "",
    val name: String = "",
    val shape: PreviewGroupShape = PreviewGroupShape.TWO_BY_TWO,
    val members: List<String> = emptyList(),
    /** A hidden group keeps its outputs but draws nothing, and its outputs stay out of the panel. */
    val hidden: Boolean = false,
)

/** The outputs that fit this group's grid, in cell order. */
fun PreviewGroup.visibleMembers(): List<String> = members.take(shape.capacity)

/** A group with a fresh [id] that no group in [existing] already uses. */
fun newPreviewGroup(
    existing: List<PreviewGroup>,
    shape: PreviewGroupShape = PreviewGroupShape.TWO_BY_TWO,
): PreviewGroup {
    val taken = existing.map { it.id }.toSet()
    var n = existing.size + 1
    while ("group$n" in taken) n++
    return PreviewGroup(id = "group$n", shape = shape)
}

/** [group] added at the end. */
fun ProjectionSettings.addPreviewGroup(group: PreviewGroup): ProjectionSettings =
    copy(previewGroups = previewGroups + group)

/** The group with [id] removed; its outputs fall back to the ungrouped list. */
fun ProjectionSettings.removePreviewGroup(id: String): ProjectionSettings =
    copy(previewGroups = previewGroups.filterNot { it.id == id })

/** The group with [id] replaced by [transform] of it. */
fun ProjectionSettings.updatePreviewGroup(id: String, transform: (PreviewGroup) -> PreviewGroup): ProjectionSettings =
    copy(previewGroups = previewGroups.map { if (it.id == id) transform(it) else it })

/**
 * [key] placed at the end of the group with [id]. An output lives in one group at most, so it is
 * taken out of any other first.
 */
fun ProjectionSettings.addPreviewMember(id: String, key: String): ProjectionSettings =
    copy(
        previewGroups = previewGroups.map { group ->
            when {
                group.id == id -> group.copy(members = group.members.filterNot { it == key } + key)
                else -> group.copy(members = group.members.filterNot { it == key })
            }
        }
    )

/** [key] taken out of the group with [id]. */
fun ProjectionSettings.removePreviewMember(id: String, key: String): ProjectionSettings =
    updatePreviewGroup(id) { it.copy(members = it.members.filterNot { member -> member == key }) }

/** The member at [index] of the group with [id] moved by [offset] cells, clamped to the ends. */
fun ProjectionSettings.movePreviewMember(id: String, index: Int, offset: Int): ProjectionSettings =
    updatePreviewGroup(id) { group ->
        val target = (index + offset).coerceIn(0, group.members.lastIndex.coerceAtLeast(0))
        if (index !in group.members.indices || target == index) {
            group
        } else {
            val moved = group.members.toMutableList()
            moved.add(target, moved.removeAt(index))
            group.copy(members = moved)
        }
    }

/**
 * Group members after the output at [removedIndex] of [kind] is deleted: its key goes, and the
 * outputs of that kind after it are renumbered down by one, which is what removing from the list
 * does to their positions.
 */
fun ProjectionSettings.shiftPreviewMembers(kind: String, removedIndex: Int): ProjectionSettings =
    copy(
        previewGroups = previewGroups.map { group ->
            group.copy(
                members = group.members.mapNotNull { key ->
                    val parsed = parsePreviewOutputKey(key)
                    when {
                        parsed == null || parsed.first != kind -> key
                        parsed.second == removedIndex -> null
                        parsed.second > removedIndex -> Constants.previewOutputKey(kind, parsed.second - 1)
                        else -> key
                    }
                }
            )
        }
    )

/** The kind and index a key from `Constants.previewOutputKey` was built from, or null if it is not one. */
fun parsePreviewOutputKey(key: String): Pair<String, Int>? {
    val at = key.lastIndexOf(':')
    val index = key.substring(at + 1).toIntOrNull()
    return if (at <= 0 || index == null) null else key.substring(0, at) to index
}

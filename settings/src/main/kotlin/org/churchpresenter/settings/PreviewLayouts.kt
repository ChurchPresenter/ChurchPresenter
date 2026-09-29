package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * How one area of a [PreviewLayout] is laid out: a single output's preview, or split across
 * ([SPLIT_ACROSS], side by side) or down ([SPLIT_DOWN], one above the other) into [children].
 */
const val SPLIT_NONE = ""
const val SPLIT_ACROSS = "ACROSS"
const val SPLIT_DOWN = "DOWN"

/**
 * One area of the preview panel, as its layout divides it.
 *
 * A split area has [children] and a [ratios] share for each, which always add up to one; a leaf
 * has none, and shows the output whose key is [output] -- `Constants.previewOutputKey` -- or
 * nothing while [output] is empty. [place] is where a leaf's preview sits in its area when the area
 * is taller than the preview: [Constants.TOP], [Constants.MIDDLE] or [Constants.BOTTOM].
 */
@Serializable
data class PreviewArea(
    val split: String = SPLIT_NONE,
    val children: List<PreviewArea> = emptyList(),
    val ratios: List<Float> = emptyList(),
    val output: String = "",
    val place: String = Constants.MIDDLE,
) {
    /** Whether this area is one output's preview rather than a split. */
    val isLeaf: Boolean get() = split == SPLIT_NONE || children.isEmpty()

    /** Every output key a leaf under this area shows, in reading order. */
    fun outputs(): List<String> =
        if (isLeaf) listOfNotNull(output.takeIf { it.isNotEmpty() }) else children.flatMap { it.outputs() }
}

/** A named arrangement of the preview panel the operator can switch to. */
@Serializable
data class PreviewLayout(
    val id: String = "",
    val name: String = "",
    val root: PreviewArea = PreviewArea(),
)

/** An area split [split] into [children], sharing its room equally. */
fun splitArea(split: String, children: List<PreviewArea>): PreviewArea =
    PreviewArea(split = split, children = children, ratios = List(children.size) { 1f / children.size })

/**
 * The area reached by following [path] -- each entry the index of a child -- from [this], or null
 * where the path leads nowhere.
 */
fun PreviewArea.at(path: List<Int>): PreviewArea? =
    if (path.isEmpty()) this else children.getOrNull(path.first())?.at(path.drop(1))

/** [this] with the area at [path] replaced by [transform] of it; unchanged where the path leads nowhere. */
fun PreviewArea.updatedAt(path: List<Int>, transform: (PreviewArea) -> PreviewArea): PreviewArea {
    if (path.isEmpty()) return transform(this)
    val index = path.first()
    val child = children.getOrNull(index) ?: return this
    return copy(children = children.toMutableList().also { it[index] = child.updatedAt(path.drop(1), transform) })
}

/**
 * The leaf at [path] split [split] into two: itself, keeping its output and place, and a new empty
 * area beside or under it, sharing the room equally.
 */
fun PreviewArea.splitAt(path: List<Int>, split: String): PreviewArea = updatedAt(path) { leaf ->
    splitArea(split, listOf(leaf, PreviewArea(place = leaf.place)))
}

/**
 * [this] with the area at [path] taken out, the room it had given to the areas beside it. A split
 * left with one child becomes that child. The root itself cannot be removed; it is emptied instead.
 */
fun PreviewArea.removedAt(path: List<Int>): PreviewArea {
    if (path.isEmpty()) return PreviewArea(place = place)
    val parentPath = path.dropLast(1)
    val index = path.last()
    return updatedAt(parentPath) { parent ->
        val kept = parent.children.filterIndexed { i, _ -> i != index }
        val shares = parent.ratios.filterIndexed { i, _ -> i != index }
        val total = shares.sum().takeIf { it > 0f } ?: 1f
        when (kept.size) {
            0 -> PreviewArea(place = parent.place)
            1 -> kept.first()
            else -> parent.copy(children = kept, ratios = shares.map { it / total })
        }
    }
}

/**
 * The split at [path] with the divider after child [index] moved so that child's share is [share]
 * of the room the two either side of it have together -- the other takes the rest. Neither may go
 * below [MIN_AREA_SHARE] of the split.
 */
fun PreviewArea.withDividerAt(path: List<Int>, index: Int, share: Float): PreviewArea = updatedAt(path) { split ->
    if (index !in 0 until split.ratios.lastIndex) return@updatedAt split
    val pair = split.ratios[index] + split.ratios[index + 1]
    val first = (share * pair).coerceIn(MIN_AREA_SHARE, pair - MIN_AREA_SHARE)
    split.copy(ratios = split.ratios.toMutableList().also {
        it[index] = first
        it[index + 1] = pair - first
    })
}

/** [this] with every leaf showing [output] emptied: an output is shown in one area at most. */
fun PreviewArea.without(output: String): PreviewArea = if (isLeaf) {
    if (this.output == output) copy(output = "") else this
} else {
    copy(children = children.map { it.without(output) })
}

/** The smallest share of its split an area may be dragged to. */
const val MIN_AREA_SHARE = 0.08f

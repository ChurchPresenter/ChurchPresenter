package org.churchpresenter.settings

/**
 * The geometry of merged real displays -- a video wall of two TVs side by side driven as one
 * 3840x1080 picture. See `OutputMerge.kt`, which judges a merge of real displays by [spanProblem].
 */

/** A monitor's rectangle, in the window system's logical coordinates. */
data class DisplayRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    val key: String get() = screenKey(x, y, width, height)

    fun overlaps(other: DisplayRect): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom
}

/** Why a set of monitors cannot be merged into one output. */
enum class SpanProblem {
    /** Fewer than two monitors: there is nothing to merge. */
    TOO_FEW,

    /** They do not tile one rectangle -- a gap, an overlap, or an L shape. */
    NOT_A_RECTANGLE,
}

private val SCREEN_KEY = Regex("""^(\d+)x(\d+)@(-?\d+),(-?\d+)$""")

/** The rectangle a [screenKey] names, or null when it is not one. */
fun parseScreenKey(key: String): DisplayRect? {
    val match = SCREEN_KEY.matchEntire(key) ?: return null
    val n = match.groupValues.drop(1).map { it.toInt() }
    // The key is written width x height @ x,y -- see screenKey.
    return DisplayRect(x = n[2], y = n[3], width = n[0], height = n[1]).takeIf { it.width > 0 && it.height > 0 }
}

/** The smallest rectangle holding every one of [rects]. */
fun unionOf(rects: List<DisplayRect>): DisplayRect {
    require(rects.isNotEmpty()) { "a union of nothing" }
    val x = rects.minOf { it.x }
    val y = rects.minOf { it.y }
    return DisplayRect(x, y, rects.maxOf { it.right } - x, rects.maxOf { it.bottom } - y)
}

/**
 * Why [rects] cannot become one output, or null when they can: at least two, none overlapping,
 * and together filling their union exactly -- so side by side, stacked, or a 2x2 wall, but not an
 * L, and not two monitors with a gap between them that the picture would be drawn across.
 */
fun spanProblem(rects: List<DisplayRect>): SpanProblem? {
    if (rects.size < 2) return SpanProblem.TOO_FEW
    val overlapping = rects.withIndex().any { (i, a) -> rects.drop(i + 1).any { a.overlaps(it) } }
    if (overlapping) return SpanProblem.NOT_A_RECTANGLE
    val union = unionOf(rects)
    val area = rects.sumOf { it.width.toLong() * it.height }
    return if (area == union.width.toLong() * union.height) null else SpanProblem.NOT_A_RECTANGLE
}

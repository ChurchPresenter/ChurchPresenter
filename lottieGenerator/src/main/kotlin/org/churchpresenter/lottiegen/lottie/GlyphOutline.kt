package org.churchpresenter.lottiegen.lottie

import java.awt.geom.PathIterator

/**
 * A quadratic segment becomes a cubic by pulling each control point two thirds of the way from its
 * endpoint towards the quadratic's single control point. Exact, not an approximation.
 */
private const val QUAD_TO_CUBIC = 2.0 / 3.0

/** Offsets into PathIterator's cubic `coords`: two control points, then the endpoint. */
private const val CUBIC_CTRL2_X = 2
private const val CUBIC_CTRL2_Y = 3
private const val CUBIC_END_X = 4
private const val CUBIC_END_Y = 5

/** Two points closer than this are the same point: fonts curve back to the start before closing. */
private const val CLOSE_EPSILON = 0.01

/** One closed outline of a glyph: its vertices and the in/out tangents at each. */
internal class Contour {
    val v = ArrayList<DoubleArray>()
    val inTan = ArrayList<DoubleArray>()
    val outTan = ArrayList<DoubleArray>()
}

internal fun outlineToContours(path: PathIterator): List<Contour> {
    val contours = ArrayList<Contour>()
    var current: Contour? = null
    val coords = DoubleArray(6)
    while (!path.isDone) {
        when (path.currentSegment(coords)) {
            PathIterator.SEG_MOVETO -> {
                current = Contour().also { contours.add(it) }
                addVertex(current, coords[0], coords[1])
            }
            PathIterator.SEG_LINETO -> current?.let { addVertex(it, coords[0], coords[1]) }
            PathIterator.SEG_QUADTO -> current?.addQuad(coords)
            PathIterator.SEG_CUBICTO -> current?.addCubic(coords)
            PathIterator.SEG_CLOSE -> {
                current?.mergeClosingVertex()
                current = null
            }
        }
        path.next()
    }
    return contours.filter { it.v.isNotEmpty() }
}

/** Promotes a quadratic segment to a cubic: c = p + 2/3 (q - p) at both ends. */
private fun Contour.addQuad(coords: DoubleArray) {
    val last = v.last()
    val qx = coords[0]; val qy = coords[1]
    val px = coords[2]; val py = coords[3]
    outTan[outTan.size - 1] = doubleArrayOf(
        (qx - last[0]) * QUAD_TO_CUBIC, (qy - last[1]) * QUAD_TO_CUBIC
    )
    addVertex(this, px, py)
    inTan[inTan.size - 1] = doubleArrayOf(
        (qx - px) * QUAD_TO_CUBIC, (qy - py) * QUAD_TO_CUBIC
    )
}

private fun Contour.addCubic(coords: DoubleArray) {
    val last = v.last()
    outTan[outTan.size - 1] = doubleArrayOf(coords[0] - last[0], coords[1] - last[1])
    addVertex(this, coords[CUBIC_END_X], coords[CUBIC_END_Y])
    inTan[inTan.size - 1] = doubleArrayOf(
        coords[CUBIC_CTRL2_X] - coords[CUBIC_END_X],
        coords[CUBIC_CTRL2_Y] - coords[CUBIC_END_Y],
    )
}

/**
 * Fonts usually curve back to the start point before closing, leaving a duplicate vertex.
 * Merging it keeps the closing edge's tangents.
 */
private fun Contour.mergeClosingVertex() {
    if (v.size <= 1) return
    val first = v.first()
    val last = v.last()
    val closedBackOnStart = kotlin.math.abs(first[0] - last[0]) < CLOSE_EPSILON &&
        kotlin.math.abs(first[1] - last[1]) < CLOSE_EPSILON
    if (!closedBackOnStart) return
    inTan[0] = inTan.last()
    v.removeAt(v.size - 1)
    inTan.removeAt(inTan.size - 1)
    outTan.removeAt(outTan.size - 1)
}

private fun addVertex(c: Contour, x: Double, y: Double) {
    c.v.add(doubleArrayOf(x, y))
    c.inTan.add(doubleArrayOf(0.0, 0.0))
    c.outTan.add(doubleArrayOf(0.0, 0.0))
}


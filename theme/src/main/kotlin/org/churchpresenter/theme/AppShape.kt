package org.churchpresenter.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * How rounded the app's corners are, as a fraction of the radius each call site is written with.
 *
 * Every [AppShape] taking a [Dp] is scaled by this, so the whole app's roundness is this one number.
 * Percent corners are not: they are pills and circles, and stay fully round.
 */
const val CORNER_SCALE = 0.6f

/** How far each corner's curve eases into its straight edges -- 0 is a plain circular arc. */
private const val CORNER_SMOOTHING = 0.6f

/** The app's corner: [size] × [CORNER_SCALE], drawn as a squircle. Use it wherever a rounded corner is wanted. */
@Suppress("FunctionNaming") // Stands in for a constructor, as Compose's own RoundedCornerShape(...) does.
fun AppShape(size: Dp): SquircleShape = AppShape(CornerSize(size * CORNER_SCALE))

/** [AppShape] with a radius per corner, each × [CORNER_SCALE]. Omitted corners are square. */
@Suppress("FunctionNaming") // Stands in for a constructor, as Compose's own RoundedCornerShape(...) does.
fun AppShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): SquircleShape = SquircleShape(
    topStart = CornerSize(topStart * CORNER_SCALE),
    topEnd = CornerSize(topEnd * CORNER_SCALE),
    bottomEnd = CornerSize(bottomEnd * CORNER_SCALE),
    bottomStart = CornerSize(bottomStart * CORNER_SCALE),
)

/** [AppShape] with every corner [percent] of the shorter side -- not scaled, so 50 is still a pill. */
@Suppress("FunctionNaming") // Stands in for a constructor, as Compose's own RoundedCornerShape(...) does.
fun AppShape(percent: Int): SquircleShape = AppShape(CornerSize(percent))

/** [AppShape] with every corner [corner], used as given. */
@Suppress("FunctionNaming") // Stands in for a constructor, as Compose's own RoundedCornerShape(...) does.
fun AppShape(corner: CornerSize): SquircleShape = SquircleShape(corner, corner, corner, corner)

/**
 * A rectangle whose corners are squircles: each curve starts earlier along its edge and eases in, so
 * there is no visible kink where a straight edge meets the arc, as there is with a circular corner.
 *
 * The corner is the smoothed rounded rectangle Figma and iOS draw: two cubic Béziers either side of
 * a shortened circular arc. A corner too large for its side gives up its smoothing first, then its
 * radius, so a 50% corner comes out a true pill.
 */
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): SquircleShape = SquircleShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val ltr = layoutDirection == LayoutDirection.Ltr
        val topLeft = if (ltr) topStart else topEnd
        val topRight = if (ltr) topEnd else topStart
        val bottomRight = if (ltr) bottomEnd else bottomStart
        val bottomLeft = if (ltr) bottomStart else bottomEnd
        val budget = min(size.width, size.height) / 2f
        val w = size.width
        val h = size.height
        val path = Path()
        // Clockwise from the top edge; each corner is entered along one edge and left along the next.
        val start = SquircleCorner.of(topLeft, budget).extent
        path.moveTo(start, 0f)
        SquircleCorner.of(topRight, budget).drawInto(path, Offset(w, 0f), Offset(1f, 0f), Offset(0f, 1f))
        SquircleCorner.of(bottomRight, budget).drawInto(path, Offset(w, h), Offset(0f, 1f), Offset(-1f, 0f))
        SquircleCorner.of(bottomLeft, budget).drawInto(path, Offset(0f, h), Offset(-1f, 0f), Offset(0f, -1f))
        SquircleCorner.of(topLeft, budget).drawInto(path, Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f))
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is SquircleShape &&
                topStart == other.topStart && topEnd == other.topEnd &&
                bottomEnd == other.bottomEnd && bottomStart == other.bottomStart
            )

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = HASH_PRIME * result + topEnd.hashCode()
        result = HASH_PRIME * result + bottomEnd.hashCode()
        result = HASH_PRIME * result + bottomStart.hashCode()
        return result
    }

    override fun toString(): String =
        "SquircleShape(topStart = $topStart, topEnd = $topEnd, bottomEnd = $bottomEnd, bottomStart = $bottomStart)"

    private companion object {
        const val HASH_PRIME = 31
    }
}

private fun Size.toRect() = Rect(Offset.Zero, this)

/**
 * One corner's measurements, all as distances from the corner point along its two edges.
 *
 * [extent] is how far along each edge the curve begins; [b] places the Bézier handles, [c]
 * and [d] where the arc starts, and [arcSweep] is the arc's angle in radians.
 */
private class SquircleCorner(
    val radius: Float,
    val extent: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val arcSweep: Float,
) {
    /** The first handle's distance -- always twice [b], which is what makes the curvature continuous. */
    val a: Float get() = 2f * b

    /**
     * Continues [path] through the corner at [corner], arriving along [inDir] and leaving along
     * [outDir] (both unit vectors), ending on the next edge [extent] past the corner.
     */
    fun drawInto(path: Path, corner: Offset, inDir: Offset, outDir: Offset) {
        fun at(back: Float, across: Float) = corner - inDir * back + outDir * across
        if (radius <= 0f) {
            path.lineTo(corner.x, corner.y)
            return
        }
        val edgeIn = at(extent, 0f)
        path.lineTo(edgeIn.x, edgeIn.y)
        val h1 = at(extent - a, 0f)
        val h2 = at(extent - a - b, 0f)
        val arcStart = at(extent - a - b - c, d)
        path.cubicTo(h1.x, h1.y, h2.x, h2.y, arcStart.x, arcStart.y)
        val arcEnd = at(d, extent - a - b - c)
        val h3 = at(0f, extent - a - b)
        if (arcSweep > 0f) {
            // The short arc as one cubic, its handles along the tangents the Béziers either side end on.
            val k = FOUR_THIRDS * tan(arcSweep / 4f) * radius
            val t0 = (arcStart - h2).unit()
            val t1 = (h3 - arcEnd).unit()
            val c1 = arcStart + t0 * k
            val c2 = arcEnd - t1 * k
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, arcEnd.x, arcEnd.y)
        }
        val h4 = at(0f, extent - a)
        val edgeOut = at(0f, extent)
        path.cubicTo(h3.x, h3.y, h4.x, h4.y, edgeOut.x, edgeOut.y)
    }

    companion object {
        private const val FOUR_THIRDS = 4f / 3f
        private const val RIGHT_ANGLE = PI.toFloat() / 2f

        /** The measurements for a corner of [requested] radius on a side whose half is [budget]. */
        fun of(requested: Float, budget: Float): SquircleCorner {
            val radius = min(requested, budget)
            if (radius <= 0f) return SquircleCorner(0f, 0f, 0f, 0f, 0f, 0f)
            val smoothing = min(CORNER_SMOOTHING, max(0f, budget / radius - 1f))
            val extent = min((1f + smoothing) * radius, budget)
            val arcSweep = RIGHT_ANGLE * (1f - smoothing)
            val arcChord = sin(arcSweep / 2f) * radius * sqrt(2f)
            val alpha = (RIGHT_ANGLE - arcSweep) / 2f
            val p3ToP4 = radius * tan(alpha / 2f)
            val beta = RIGHT_ANGLE / 2f * smoothing
            val c = p3ToP4 * cos(beta)
            val d = c * tan(beta)
            val b = (extent - arcChord - c - d) / 3f
            return SquircleCorner(radius, extent, b, c, d, arcSweep)
        }
    }
}

private fun Offset.unit(): Offset {
    val length = getDistance()
    return if (length == 0f) this else this / length
}

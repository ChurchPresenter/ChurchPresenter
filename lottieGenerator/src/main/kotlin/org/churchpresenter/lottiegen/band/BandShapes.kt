package org.churchpresenter.lottiegen.band

import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.band.BandGeometry.ARCH_H
import org.churchpresenter.lottiegen.band.BandGeometry.ARCH_W
import org.churchpresenter.lottiegen.band.BandGeometry.SLANT
import org.churchpresenter.lottiegen.band.BandGeometry.WAVE_AMPLITUDE
import org.churchpresenter.lottiegen.band.BandGeometry.WAVE_TOP
import org.churchpresenter.lottiegen.lottie.makeCurvedPath
import org.churchpresenter.lottiegen.lottie.makeEllipse
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeRect

/** One thing the band paints: a shape in a colour role, or a group already carrying its own paint. */
internal sealed interface BandPiece {
    class Shaped(val role: BandColorRole, val shape: JsonObject) : BandPiece
    class Painted(val group: JsonObject) : BandPiece
}

/** The primitive shapes the styles are built from, each placed on the band rectangle [b]. */
internal open class BandShapes(protected val b: SlotBox) {
    protected val x = b.x
    protected val y = b.y
    protected val w = b.w
    protected val h = b.h
    protected val right = b.right
    protected val bottom = b.bottom

    protected fun rect(role: BandColorRole, x: Double, y: Double, w: Double, h: Double): BandPiece =
        BandPiece.Shaped(role, makeRect(w, h, 0.0, listOf(x + w / 2, y + h / 2)))

    protected fun poly(role: BandColorRole, vararg points: Pair<Double, Double>): BandPiece =
        polyOf(role, points.toList())

    protected fun polyOf(role: BandColorRole, points: List<Pair<Double, Double>>): BandPiece =
        BandPiece.Shaped(role, makePath(points.map { listOf(it.first, it.second) }))

    protected fun accent(x: Double, y: Double, w: Double, h: Double) = rect(BandColorRole.ACCENT, x, y, w, h)
    protected fun second(x: Double, y: Double, w: Double, h: Double) = rect(BandColorRole.SECOND, x, y, w, h)
    protected fun tertiary(x: Double, y: Double, w: Double, h: Double) = rect(BandColorRole.TERTIARY, x, y, w, h)

    /** Where the band's width fraction [f] lands, in canvas pixels. */
    protected fun fx(f: Double): Double = x + w * f

    /** A slanted stripe [from]..[to] of the band's width at the top, leaning back by [SLANT] at the bottom. */
    protected fun slant(role: BandColorRole, from: Double, to: Double): BandPiece =
        poly(role, fx(from) to y, fx(to) to y, fx(to - SLANT) to bottom, fx(from - SLANT) to bottom)

    /**
     * A wave across the band, its crest [lift] of the height above the base line, filled down to
     * the bottom edge. [flip] mirrors the wave so two of them cross rather than stack.
     */
    protected fun wave(role: BandColorRole, lift: Double, flip: Boolean = false): BandPiece {
        val baseY = bottom - h * (WAVE_TOP + lift)
        val amp = h * WAVE_AMPLITUDE * (if (flip) -1.0 else 1.0)
        val quarter = w / 4
        val handle = listOf(quarter / 2, 0.0)
        val negHandle = listOf(-quarter / 2, 0.0)
        val none = listOf(0.0, 0.0)
        val vertices = listOf(
            listOf(x, baseY),
            listOf(x + quarter, baseY - amp),
            listOf(x + 2 * quarter, baseY),
            listOf(x + 3 * quarter, baseY + amp),
            listOf(right, baseY),
            listOf(right, bottom),
            listOf(x, bottom),
        )
        val inTangents = listOf(none, negHandle, negHandle, negHandle, negHandle, none, none)
        val outTangents = listOf(handle, handle, handle, handle, none, none, none)
        return BandPiece.Shaped(role, makeCurvedPath(vertices, inTangents, outTangents, closed = true))
    }

    /** An ellipse centred on the bottom edge, so its upper half stands as an arch; [grow] widens it for a ring. */
    protected fun arch(role: BandColorRole, grow: Double): BandPiece = BandPiece.Shaped(
        role, makeEllipse(w * (ARCH_W + grow * 2), h * (ARCH_H + grow) * 2, listOf(b.centerX, bottom)),
    )
}

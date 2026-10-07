package org.churchpresenter.presentationengine.timeline

import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.model.PropertyCurve
import org.churchpresenter.presentationengine.model.RevealClip

/** The pure arithmetic [TimelineEvaluator] samples effects with: clips, directions and curves. */
internal object RevealGeometry {

    /** A split opens from the middle of the layer, in its own normalized space. */
    private const val CENTER = 0.5

    fun opposite(direction: Direction): Direction = when (direction) {
        Direction.UP -> Direction.DOWN
        Direction.DOWN -> Direction.UP
        Direction.LEFT -> Direction.RIGHT
        Direction.RIGHT -> Direction.LEFT
        Direction.IN -> Direction.OUT
        Direction.OUT -> Direction.IN
    }

    fun wipeClip(direction: Direction, role: EffectSpec.Role, p: Double): RevealClip {
        val shown = if (role == EffectSpec.Role.EXIT) 1.0 - p else p
        return when (direction) {
            Direction.DOWN -> RevealClip(0.0, 0.0, 1.0, shown)
            Direction.UP -> RevealClip(0.0, 1.0 - shown, 1.0, 1.0)
            Direction.RIGHT -> RevealClip(0.0, 0.0, shown, 1.0)
            Direction.LEFT -> RevealClip(1.0 - shown, 0.0, 1.0, 1.0)
            Direction.IN, Direction.OUT -> RevealClip(0.0, 0.0, 1.0, shown)
        }
    }

    fun splitClip(horizontal: Boolean, role: EffectSpec.Role, p: Double): RevealClip {
        val shown = if (role == EffectSpec.Role.EXIT) 1.0 - p else p
        val half = shown / 2.0
        return if (horizontal) {
            RevealClip(0.0, CENTER - half, 1.0, CENTER + half)
        } else {
            RevealClip(CENTER - half, 0.0, CENTER + half, 1.0)
        }
    }

    /**
     * [curve]'s value at progress [p]: held at its first and last keyframes beyond them, and
     * interpolated linearly within the first segment that contains [p]. Null for an empty curve.
     */
    fun interpolate(curve: PropertyCurve, p: Double): Double? {
        val frames = curve.keyframes
        if (frames.isEmpty()) return null
        val first = frames.first()
        val last = frames.last()
        return when {
            p <= first.first -> first.second
            p >= last.first -> last.second
            else -> frames.zipWithNext()
                .firstOrNull { (from, to) -> p in from.first..to.first }
                ?.let { (from, to) ->
                    val (t0, v0) = from
                    val (t1, v1) = to
                    val f = if (t1 > t0) (p - t0) / (t1 - t0) else 1.0
                    v0 + (v1 - v0) * f
                }
                ?: last.second
        }
    }
}

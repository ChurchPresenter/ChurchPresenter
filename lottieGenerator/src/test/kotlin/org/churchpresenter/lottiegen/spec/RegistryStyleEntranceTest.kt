package org.churchpresenter.lottiegen.spec

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every registered style must animate on from nothing and — because the exit is the
 * automatic mirror of the animate-in — back off into nothing.
 *
 * The failure this guards against is subtle: an element given only a POSITION_OFFSET or
 * ROTATION track is painted at full opacity on frame 0, merely displaced. On a transparent
 * lower-third canvas that reads as the decoration snapping into existence and snapping back
 * out, instead of resolving. Nothing in the generator warns about it and the composition
 * still has all its layers, so only an explicit invariant catches it.
 *
 * An element may start from nothing by any of these mechanisms:
 *  - opacity rising from 0
 *  - scale or rect-size growing from a zero dimension
 *  - a trim path whose drawn end starts at 0
 *  - text that starts slid completely out from under its own reveal mask
 */
class RegistryStyleEntranceTest {

    /** A mask-slide must clear the text plus the mask's padding to actually hide it. */
    private val minMaskClearance = 1.15

    private fun ElementSpec.startsFromNothing(): Boolean {
        for (t in tracks) {
            val first = t.keyframes.minByOrNull { it.pct } ?: continue
            val v = first.values
            when (t.property) {
                AnimProperty.OPACITY -> if (v.firstOrNull() == 0.0) return true
                AnimProperty.SCALE, AnimProperty.RECT_SIZE -> if (v.any { it == 0.0 }) return true
                AnimProperty.TRIM -> if (v.getOrNull(1) == 0.0) return true
                else -> Unit
            }
        }
        // NOTE: a per-character animator is deliberately NOT accepted here. It hides the
        // characters via a text range selector, which leaves the layer itself at full
        // opacity — a renderer that does not implement range selectors paints the entire
        // string on frame 0. Styles using an animator must still carry their own ramp.

        // A text element hidden by its own reveal mask: slid at least fully out of it.
        if (this is TextElement && maskReveal != null) {
            val slide = tracks.firstOrNull {
                it.property == AnimProperty.POSITION_OFFSET && it.offsetUnit == OffsetUnit.ELEMENT_WIDTH
            }
            val dx = slide?.keyframes?.minByOrNull { it.pct }?.values?.firstOrNull()
            if (dx != null && kotlin.math.abs(dx) >= minMaskClearance) return true
        }
        return false
    }

    @Test
    fun everyElementOfEveryRegisteredStyleStartsFromNothing() {
        val offenders = mutableListOf<String>()

        for (entry in StyleRegistry.load().entries) {
            val spec = SpecJson.decode(
                javaClass.getResourceAsStream(entry.resource)!!
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            )
            for (element in spec.elements) {
                if (!element.startsFromNothing()) {
                    offenders += "style ${spec.id} (${spec.name}) → '${element.name}' [${element.id}]"
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "these elements are fully painted at frame 0, so they pop in and pop out " +
                "instead of resolving:\n  " + offenders.joinToString("\n  ")
        )
    }
}

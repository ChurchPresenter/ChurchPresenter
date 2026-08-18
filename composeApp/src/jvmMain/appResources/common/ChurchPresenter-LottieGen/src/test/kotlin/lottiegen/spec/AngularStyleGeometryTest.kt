package lottiegen.spec

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Geometry invariants for the angular styles (25-36).
 *
 * `fitWidthTo` normalises each shape's OWN natural width onto the measured text width.
 * Two shapes drawn in the same coordinate system but with different spans therefore
 * receive DIFFERENT scale factors and fly apart — a span-0.2 accent beside a span-2.0
 * body renders 10x too large and nowhere near where it was authored. Nothing warns:
 * the layer is present, the composition is valid, it is simply in the wrong place.
 *
 * So every fitWidthTo shape within one style must share one span.
 */
class AngularStyleGeometryTest {

    private val angularIds = (25..36).map { it.toString() }.toSet()

    @Test
    fun everyFittedShapeInAStyleSharesOneSpan() {
        val offenders = mutableListOf<String>()

        for (entry in StyleRegistry.load().entries.filter { it.id in angularIds }) {
            val spec = SpecJson.decode(
                javaClass.getResourceAsStream(entry.resource)!!
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            )

            val spans = spec.elements.mapNotNull { el ->
                val xs = when {
                    el is PolygonElement && el.fitWidthTo != null -> el.verticesEm.map { it[0] }
                    el is PathElement && el.fitWidthTo != null -> el.verticesEm.map { it.x }
                    else -> null
                } ?: return@mapNotNull null
                Triple(el.name, xs.min(), xs.max())
            }
            if (spans.size < 2) continue

            val (refName, refMin, refMax) = spans.first()
            for ((name, mn, mx) in spans.drop(1)) {
                if (abs(mn - refMin) > 1e-6 || abs(mx - refMax) > 1e-6) {
                    val ratio = (refMax - refMin) / (mx - mn)
                    offenders += "style ${spec.id} (${spec.name}): '$name' spans " +
                        "%.3f..%.3f but '$refName' spans %.3f..%.3f — fitWidthTo will scale it %.2fx differently"
                            .format(mn, mx, refMin, refMax, ratio)
                }
            }
        }

        assertTrue(offenders.isEmpty(), "mismatched fitWidthTo spans:\n  " + offenders.joinToString("\n  "))
    }

    /**
     * Each angular style is a body plus one accent — the two-shape brief. Guards against a
     * third shape creeping in, and against a style losing its accent.
     */
    @Test
    fun everyAngularStyleIsExactlyTwoShapes() {
        for (entry in StyleRegistry.load().entries.filter { it.id in angularIds }) {
            val spec = SpecJson.decode(
                javaClass.getResourceAsStream(entry.resource)!!
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            )
            val shapes = spec.elements.filter {
                it is PolygonElement || it is PathElement || it is RectElement ||
                    it is EllipseElement || it is BackgroundElement
            }
            assertTrue(
                shapes.size == 2,
                "style ${spec.id} (${spec.name}) has ${shapes.size} shapes " +
                    "(${shapes.joinToString { it.name }}), expected exactly 2"
            )
            assertTrue(
                shapes.all { it is PolygonElement },
                "style ${spec.id} (${spec.name}) must be built from polygons only — " +
                    "a rect or background element reintroduces the rectangular panel"
            )
        }
    }
}

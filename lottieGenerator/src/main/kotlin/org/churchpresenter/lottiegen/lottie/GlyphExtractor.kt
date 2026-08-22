package org.churchpresenter.lottiegen.lottie

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.PathIterator
import kotlin.math.roundToInt

/** Lottie glyph outlines are authored on a 100-unit em box, so every coordinate is a percentage. */
private const val GLYPH_EM_SIZE = 100

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

/** Outline coordinates are emitted to two decimal places; more only inflates the JSON. */
private const val ROUND_2DP = 100.0


/**
 * Embeds vector glyph outlines ("chars") for the characters the animation's text layers
 * actually use, so exported files render crisp text in any lottie player with no font
 * installed. Renderers prefer a resolved font when one is available (ChurchPresenter
 * supplies the real fonts at runtime), so embedded glyphs are a portable fallback,
 * not an override.
 *
 * Glyph outlines follow the lottie convention: extracted at font size 100 with the
 * baseline at y = 0 (ascenders at negative y); "w" is the advance width at size 100.
 * Only characters present in the exported text are embedded — a few KB per file.
 */
object GlyphExtractor {

    private const val GLYPH_SIZE = 100f

    /**
     * Builds the "chars" array for the given layers, or null when no text layer uses a
     * declared font. [fonts] is the same list assembled into the document's fonts.list.
     */
    fun buildCharsArray(layers: List<JsonObject>, fonts: List<JsonObject>): JsonArray? {
        val familyStyleByName = fonts.mapNotNull { f ->
            val name = (f["fName"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val family = (f["fFamily"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val style = (f["fStyle"] as? JsonPrimitive)?.content ?: "Regular"
            name to (family to style)
        }.toMap()
        if (familyStyleByName.isEmpty()) return null

        // (family, style) -> characters used, in first-seen order
        val used = LinkedHashMap<Pair<String, String>, LinkedHashSet<Char>>()
        for (layer in layers) {
            if ((layer["ty"] as? JsonPrimitive)?.content != "5") continue
            val doc = layer["t"] as? JsonObject ?: continue
            val keyframes = (doc["d"] as? JsonObject)?.get("k") as? JsonArray ?: continue
            for (kf in keyframes) {
                val style = ((kf as? JsonObject)?.get("s") as? JsonObject) ?: continue
                val fName = (style["f"] as? JsonPrimitive)?.content ?: continue
                val text = (style["t"] as? JsonPrimitive)?.content ?: continue
                val familyStyle = familyStyleByName[fName] ?: continue
                val chars = used.getOrPut(familyStyle) { LinkedHashSet() }
                for (ch in text) if (!ch.isISOControl()) chars.add(ch)
            }
        }
        if (used.isEmpty()) return null

        return buildJsonArray {
            for ((familyStyle, chars) in used) {
                val (family, style) = familyStyle
                for (ch in chars) add(charEntry(family, style, ch))
            }
        }
    }

    private fun charEntry(family: String, style: String, ch: Char): JsonObject {
        val awtStyle = if (style == "Bold") Font.BOLD else Font.PLAIN
        val font = FontRegistry.getFont(family, awtStyle, GLYPH_SIZE)
        val frc = FontRenderContext(null, true, true)
        val glyphVector = font.createGlyphVector(frc, ch.toString())
        val advance = glyphVector.getGlyphPosition(glyphVector.numGlyphs).x
        val contours = outlineToContours(glyphVector.outline.getPathIterator(null))
        return buildJsonObject {
            put("ch", JsonPrimitive(ch.toString()))
            put("fFamily", JsonPrimitive(family))
            put("size", JsonPrimitive(GLYPH_EM_SIZE))
            put("style", JsonPrimitive(style))
            put("w", JsonPrimitive(round2(advance)))
            put("data", buildJsonObject {
                put("shapes", charShapes(contours, ch))
            })
        }
    }

    /** One glyph contour as lottie bezier data: vertices plus relative in/out tangents. */
    private class Contour {
        val v = ArrayList<DoubleArray>()
        val inTan = ArrayList<DoubleArray>()
        val outTan = ArrayList<DoubleArray>()
    }

    private fun outlineToContours(path: PathIterator): List<Contour> {
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
                PathIterator.SEG_QUADTO -> current?.let {
                    // Promote the quadratic to a cubic: c = p + 2/3 (q − p) at both ends
                    val last = it.v.last()
                    val qx = coords[0]; val qy = coords[1]
                    val px = coords[2]; val py = coords[3]
                    it.outTan[it.outTan.size - 1] = doubleArrayOf(
                        (qx - last[0]) * QUAD_TO_CUBIC, (qy - last[1]) * QUAD_TO_CUBIC
                    )
                    addVertex(it, px, py)
                    it.inTan[it.inTan.size - 1] = doubleArrayOf(
                        (qx - px) * QUAD_TO_CUBIC, (qy - py) * QUAD_TO_CUBIC
                    )
                }
                PathIterator.SEG_CUBICTO -> current?.let {
                    val last = it.v.last()
                    it.outTan[it.outTan.size - 1] = doubleArrayOf(
                        coords[0] - last[0], coords[1] - last[1]
                    )
                    addVertex(it, coords[CUBIC_END_X], coords[CUBIC_END_Y])
                    it.inTan[it.inTan.size - 1] = doubleArrayOf(
                        coords[CUBIC_CTRL2_X] - coords[CUBIC_END_X], coords[CUBIC_CTRL2_Y] - coords[CUBIC_END_Y]
                    )
                }
                PathIterator.SEG_CLOSE -> current?.let {
                    // Fonts usually curve back to the start point before closing — merge the
                    // duplicated vertex so the closing edge keeps its tangents.
                    if (it.v.size > 1) {
                        val first = it.v.first()
                        val last = it.v.last()
                        val closedBackOnStart = kotlin.math.abs(first[0] - last[0]) < CLOSE_EPSILON &&
                            kotlin.math.abs(first[1] - last[1]) < CLOSE_EPSILON
                        if (closedBackOnStart) {
                            it.inTan[0] = it.inTan.last()
                            it.v.removeAt(it.v.size - 1)
                            it.inTan.removeAt(it.inTan.size - 1)
                            it.outTan.removeAt(it.outTan.size - 1)
                        }
                    }
                    current = null
                }
            }
            path.next()
        }
        return contours.filter { it.v.isNotEmpty() }
    }

    private fun addVertex(c: Contour, x: Double, y: Double) {
        c.v.add(doubleArrayOf(x, y))
        c.inTan.add(doubleArrayOf(0.0, 0.0))
        c.outTan.add(doubleArrayOf(0.0, 0.0))
    }

    /** Mirrors bodymovin's char data: one group of "sh" paths closed by a merge-paths. */
    private fun charShapes(contours: List<Contour>, ch: Char): JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("ty", JsonPrimitive("gr"))
            put("it", buildJsonArray {
                contours.forEachIndexed { index, contour ->
                    add(buildJsonObject {
                        put("ind", JsonPrimitive(index))
                        put("ty", JsonPrimitive("sh"))
                        put("ix", JsonPrimitive(index + 1))
                        put("ks", buildJsonObject {
                            put("a", JsonPrimitive(0))
                            put("k", buildJsonObject {
                                put("i", pointArray(contour.inTan))
                                put("o", pointArray(contour.outTan))
                                put("v", pointArray(contour.v))
                                put("c", JsonPrimitive(true))
                            })
                        })
                        put("nm", JsonPrimitive(ch.toString()))
                        put("hd", JsonPrimitive(false))
                    })
                }
                if (contours.size > 1) {
                    add(buildJsonObject {
                        put("ty", JsonPrimitive("mm"))
                        put("mm", JsonPrimitive(1))
                        put("nm", JsonPrimitive("Merge Paths 1"))
                        put("hd", JsonPrimitive(false))
                    })
                }
            })
            put("nm", JsonPrimitive(ch.toString()))
            put("np", JsonPrimitive(contours.size + 1))
            put("ix", JsonPrimitive(1))
            put("hd", JsonPrimitive(false))
        })
    }

    private fun pointArray(points: List<DoubleArray>): JsonArray = buildJsonArray {
        for (p in points) {
            add(buildJsonArray {
                add(JsonPrimitive(round2(p[0])))
                add(JsonPrimitive(round2(p[1])))
            })
        }
    }

    private fun round2(x: Double): Double = (x * ROUND_2DP).roundToInt() / ROUND_2DP
}

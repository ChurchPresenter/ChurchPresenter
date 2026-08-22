package org.churchpresenter.lottiegen.lottie

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.awt.Font
import java.awt.font.FontRenderContext
import kotlin.math.roundToInt

/** Lottie's layer-type discriminator for a text layer. */
private const val TEXT_LAYER_TYPE = "5"

/** Lottie glyph outlines are authored on a 100-unit em box, so every coordinate is a percentage. */
private const val GLYPH_EM_SIZE = 100

/**
 * A quadratic segment becomes a cubic by pulling each control point two thirds of the way from its
 * endpoint towards the quadratic's single control point. Exact, not an approximation.
 */

/** Offsets into PathIterator's cubic `coords`: two control points, then the endpoint. */

/** Two points closer than this are the same point: fonts curve back to the start before closing. */

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
        for ((fName, text) in layers.textRuns()) {
            val familyStyle = familyStyleByName[fName] ?: continue
            val chars = used.getOrPut(familyStyle) { LinkedHashSet() }
            for (ch in text) if (!ch.isISOControl()) chars.add(ch)
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

    /**
     * Every (font name, text) pair the text layers carry, skipping anything that is not a text
     * layer or does not hold a document keyframe. Each `?: return@…` here was a `continue` in one
     * of two nested loops, which said nothing about which shape was being skipped or why.
     */
    private fun List<JsonObject>.textRuns(): List<Pair<String, String>> =
        asSequence()
            .filter { (it["ty"] as? JsonPrimitive)?.content == TEXT_LAYER_TYPE }
            .mapNotNull { it["t"] as? JsonObject }
            .mapNotNull { (it["d"] as? JsonObject)?.get("k") as? JsonArray }
            .flatten()
            .mapNotNull { (it as? JsonObject)?.get("s") as? JsonObject }
            .mapNotNull { style ->
                val fName = (style["f"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val text = (style["t"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                fName to text
            }
            .toList()

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

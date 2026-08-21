package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the design contract of the banner styles (37-60), which the generic
 * [RegistryStyleSmokeTest] cannot express: these are full-bleed banners, not
 * text-width panels, and they were commissioned with three promises.
 *
 *  1. every layer starts and ends on nothing — no element is ever on screen at the
 *     first or last frame of the composition;
 *  2. the banner really spans the canvas and rests on the operator's bottom margin;
 *  3. the text sits well inside the banner mass, never crowding an edge.
 *
 * Each is checked against generated Lottie output rather than the spec source, so a
 * layout change that quietly breaks the promise fails here.
 */
class BannerStyleTest {

    private companion object {
        const val FIRST_BANNER_ID = 37
        const val LAST_BANNER_ID = 60

        /** cfg.baseSize — one em in canvas pixels at the default 1080p canvas. */
        const val EM = 24.0

        /** cfg.marginV (2 rem) resolved to pixels. */
        const val MARGIN_V = 2 * EM

        /** Clearance the text must keep from the banner's top and bottom edges. */
        const val MIN_TEXT_CLEARANCE = EM

        /** How far above the bottom margin a banner may rest before it reads as floating. */
        const val MAX_MARGIN_SLACK = 3 * EM

        const val NAME_SIZE_PX = 1.2 * EM
        const val INFO_SIZE_PX = 0.9 * EM

        /** Descender allowance below the info line's position anchor. */
        const val INFO_DESCENT_PX = 0.25 * INFO_SIZE_PX
    }

    private val banners = StyleRegistry.load().entries
        .filter { it.id.toInt() >= FIRST_BANNER_ID }
        .sortedBy { it.id.toInt() }

    // ------------------------------------------------------------------ helpers

    private fun specOf(entry: RegistryEntry): StyleSpec = SpecJson.decode(
        javaClass.getResourceAsStream(entry.resource)!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    )

    private fun render(entry: RegistryEntry, align: String = "left"): JsonObject =
        LottieGenerator.generate(
            LottieGenConfig(style = entry.id, align = align),
            SpecStyleGenerator(specOf(entry))
        )

    private fun values(element: JsonElement): List<Double> = when (element) {
        is JsonArray -> element.map { it.jsonPrimitive.double }
        else -> listOf(element.jsonPrimitive.double)
    }

    /** Samples a Lottie property at [frame], holding the last keyframe reached. */
    private fun sample(prop: JsonObject, frame: Double): List<Double> {
        if (prop["a"]!!.jsonPrimitive.int == 0) return values(prop["k"]!!)
        var current = prop["k"]!!.jsonArray.first().jsonObject["s"]!!
        for (kf in prop["k"]!!.jsonArray) {
            if (kf.jsonObject["t"]!!.jsonPrimitive.double <= frame) current = kf.jsonObject["s"]!!
        }
        return values(current)
    }

    private fun transform(layer: JsonObject, prop: String): JsonObject =
        layer["ks"]!!.jsonObject[prop]!!.jsonObject

    private fun layers(json: JsonObject): List<JsonObject> =
        json["layers"]!!.jsonArray.map { it.jsonObject }

    /** The frame at which every element is at rest (out mirrors in, so this is mid-hold). */
    private fun restFrame(json: JsonObject): Double = json["op"]!!.jsonPrimitive.double / 2

    /** Vertical span of every full-canvas-width rect at rest, as (top, bottom) pixel pairs. */
    private fun bandSpans(json: JsonObject): List<Pair<Double, Double>> {
        val rest = restFrame(json)
        val canvasW = json["w"]!!.jsonPrimitive.double
        val spans = mutableListOf<Pair<Double, Double>>()
        for (layer in layers(json)) {
            if (layer["ty"]!!.jsonPrimitive.int != 4) continue
            val centerY = sample(transform(layer, "p"), rest)[1]
            val scaleY = sample(transform(layer, "s"), rest)[1] / 100.0
            for (group in layer["shapes"]!!.jsonArray) {
                for (item in group.jsonObject["it"]?.jsonArray.orEmpty()) {
                    val shape = item.jsonObject
                    if (shape["ty"]?.jsonPrimitive?.content != "rc") continue
                    val size = sample(shape["s"]!!.jsonObject, rest)
                    if (size[0] < canvasW) continue
                    val half = size[1] * scaleY / 2
                    spans.add(centerY - half to centerY + half)
                }
            }
        }
        return spans
    }

    /**
     * The banner mass around [y]: the union of full-width bands that overlap each other
     * and cover that point. Styles that layer a translucent copy over the main band
     * (Ghost Layer) or hang a second tier off it (Deck Banner) form one visual mass, and
     * the text has to be judged against that, not against a single rect.
     */
    private fun bannerMass(spans: List<Pair<Double, Double>>, y: Double): Pair<Double, Double>? {
        var mass = spans.firstOrNull { y >= it.first && y <= it.second } ?: return null
        var grew = true
        while (grew) {
            grew = false
            for (span in spans) {
                if (span.first <= mass.second && span.second >= mass.first) {
                    val merged = minOf(mass.first, span.first) to maxOf(mass.second, span.second)
                    if (merged != mass) {
                        mass = merged
                        grew = true
                    }
                }
            }
        }
        return mass
    }

    private fun textAnchorY(json: JsonObject, name: String): Double? {
        val layer = layers(json).firstOrNull {
            it["ty"]!!.jsonPrimitive.int == 5 && it["nm"]!!.jsonPrimitive.content == name
        } ?: return null
        return sample(transform(layer, "p"), restFrame(json))[1]
    }

    // -------------------------------------------------------------------- tests

    @Test
    fun theRegistryShipsEveryBannerStyle() {
        assertEquals(
            (FIRST_BANNER_ID..LAST_BANNER_ID).map { it.toString() },
            banners.map { it.id },
            "banner styles $FIRST_BANNER_ID-$LAST_BANNER_ID must all be registered"
        )
    }

    /**
     * The commissioned split: twelve banners built from a single shape, twelve from two.
     * Text and logo do not count — they are content, not the design.
     */
    @Test
    fun eachBannerIsBuiltFromOneOrTwoShapes() {
        val counts = banners.associate { entry ->
            entry.id to specOf(entry).elements.count {
                it !is TextElement && it !is LogoElement
            }
        }
        for ((id, count) in counts) {
            val expected = if (id.toInt() <= 48) 1 else 2
            assertEquals(expected, count, "style $id should be a $expected-shape design")
        }
        assertEquals(12, counts.count { it.value == 1 }, "expected 12 one-shape banners")
        assertEquals(12, counts.count { it.value == 2 }, "expected 12 two-shape banners")
    }

    /**
     * Nothing may be on screen when the animation has not started or has finished —
     * including the logo, which is an image layer and easy to forget. The shared
     * keyframe builder mirrors the in-phase into the out-phase, so a track whose first
     * keyframe is transparent also leaves nothing behind on the way out.
     */
    @Test
    fun everyLayerStartsAndEndsFullyTransparent() {
        for (entry in banners) {
            val json = render(entry)
            val last = json["op"]!!.jsonPrimitive.double
            for (layer in layers(json)) {
                val where = "style ${entry.id} layer '${layer["nm"]!!.jsonPrimitive.content}'"
                val opacity = transform(layer, "o")
                assertEquals(
                    1, opacity["a"]!!.jsonPrimitive.int,
                    "$where has a static opacity, so it is visible before the animation starts"
                )
                assertEquals(0.0, sample(opacity, 0.0).first(), "$where is visible at frame 0")
                assertEquals(0.0, sample(opacity, last).first(), "$where is still visible at frame $last")
            }
        }
    }

    /** A banner spans the screen: at least one band is exactly as wide as the canvas. */
    @Test
    fun everyBannerSpansTheFullCanvasWidth() {
        for (entry in banners) {
            for (align in listOf("left", "center", "right")) {
                val json = render(entry, align)
                assertTrue(
                    bandSpans(json).isNotEmpty(),
                    "style ${entry.id} [$align] has no full-canvas-width band — it is not a banner"
                )
            }
        }
    }

    /**
     * The banner hangs off the operator's bottom margin: never past it (which would
     * eat the safe area) and never far above it (which would leave it floating).
     */
    @Test
    fun everyBannerRestsOnTheBottomMargin() {
        for (entry in banners) {
            val json = render(entry)
            val margin = json["h"]!!.jsonPrimitive.double - MARGIN_V
            val bottom = bandSpans(json).maxOf { it.second }
            assertTrue(
                bottom <= margin + 0.5,
                "style ${entry.id}: banner bottom $bottom overruns the ${margin}px margin"
            )
            assertTrue(
                bottom >= margin - MAX_MARGIN_SLACK,
                "style ${entry.id}: banner bottom $bottom floats ${margin - bottom}px above the margin"
            )
        }
    }

    /**
     * The text must never look like it runs off its background: both lines keep at least
     * one em of the banner above the name's cap height and below the info's descender,
     * at every alignment.
     */
    @Test
    fun textKeepsClearInsideTheBannerAtEveryAlignment() {
        for (entry in banners) {
            for (align in listOf("left", "center", "right")) {
                val json = render(entry, align)
                val nameY = textAnchorY(json, "Name")!!
                val infoY = textAnchorY(json, "Info")!!
                val inkTop = nameY - NAME_SIZE_PX
                val inkBottom = infoY + INFO_DESCENT_PX
                val where = "style ${entry.id} [$align]"

                val mass = assertNotNull(
                    bannerMass(bandSpans(json), (inkTop + inkBottom) / 2),
                    "$where: the text does not sit on any banner band"
                )
                assertTrue(
                    inkTop - mass.first >= MIN_TEXT_CLEARANCE,
                    "$where: only ${inkTop - mass.first}px above the name, need $MIN_TEXT_CLEARANCE"
                )
                assertTrue(
                    mass.second - inkBottom >= MIN_TEXT_CLEARANCE,
                    "$where: only ${mass.second - inkBottom}px below the info line, " +
                        "need $MIN_TEXT_CLEARANCE"
                )
            }
        }
    }

    /**
     * Hiding a line must not push the surviving one out of the banner — the banner
     * styles recentre a lone line, which is exactly when a clearance bug would show.
     */
    @Test
    fun aLoneTextLineStaysCentredInTheBanner() {
        for (entry in banners) {
            for ((hideName, hideInfo) in listOf(true to false, false to true)) {
                val json = LottieGenerator.generate(
                    LottieGenConfig(style = entry.id, hideName = hideName, hideInfo = hideInfo),
                    SpecStyleGenerator(specOf(entry))
                )
                val line = if (hideName) "Info" else "Name"
                val sizePx = if (hideName) INFO_SIZE_PX else NAME_SIZE_PX
                val anchorY = textAnchorY(json, line)!!
                val where = "style ${entry.id} [only $line]"
                val mass = assertNotNull(
                    bannerMass(bandSpans(json), anchorY),
                    "$where: the line does not sit on any banner band"
                )
                assertTrue(
                    anchorY - sizePx - mass.first >= MIN_TEXT_CLEARANCE,
                    "$where: only ${anchorY - sizePx - mass.first}px above the line"
                )
                assertTrue(
                    mass.second - anchorY >= MIN_TEXT_CLEARANCE,
                    "$where: only ${mass.second - anchorY}px below the line"
                )
            }
        }
    }
}

package org.churchpresenter.lottiegen.band

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.band.BandJson.layer
import org.churchpresenter.lottiegen.band.BandJson.layers
import org.churchpresenter.lottiegen.band.BandJson.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the generator writes for the band's transparency: the opacity of the words, of the border,
 * and the gradient's per-stop alpha and where its blend finishes.
 *
 * These read the produced document rather than the UI, so they survive the controls being moved
 * about — which they have been, twice.
 */
class BandTransparencyTest {

    private fun generate(cfg: BibleLottieGenConfig): JsonObject = BibleLottieGenerator.generate(cfg)

    private fun cfg(
        style: BandStyle = BandStyle.GRADIENT_BAR,
        textAlpha: Int = FULL,
        referenceAlpha: Int = FULL,
        bgAlpha: Int = FULL,
        secondAlpha: Int = FULL,
        gradientPosition: Int = FULL,
        borderAlpha: Int = FULL,
        borderThickness: Int = 0,
    ) = BibleLottieGenConfig(
        canvasW = CANVAS_W, canvasH = CANVAS_H, bandStyle = style,
        textAlpha = textAlpha, referenceAlpha = referenceAlpha,
        bgAlpha = bgAlpha, secondAlpha = secondAlpha, gradientPosition = gradientPosition,
        borderAlpha = borderAlpha, borderThickness = borderThickness,
    )

    /** The opacity keyframe values of a layer's transform, in order. */
    private fun opacityKeyframes(layer: JsonObject): List<Double> =
        layer["ks"]!!.jsonObject["o"]!!.jsonObject["k"]!!.jsonArray.map {
            it.jsonObject["s"]!!.jsonArray.first().jsonPrimitive.double()
        }

    /** The gradient's stop array: colour stops first, then alpha stops. */
    private fun gradientStops(doc: JsonObject): List<Double> {
        val band = layers(doc).first { it.name.startsWith(BibleLottieTemplateLayers.BAND_PREFIX) }
        val gradient = band["shapes"]!!.jsonArray
            .flatMap { it.jsonObject["it"]!!.jsonArray }
            .map { it.jsonObject }
            .first { it["ty"]!!.jsonPrimitive.content == "gf" }
        return gradient["g"]!!.jsonObject["k"]!!.jsonObject["k"]!!.jsonArray.map { it.jsonPrimitive.double() }
    }

    @Test
    fun `the words fade up to their configured opacity, not to fully opaque`() {
        val doc = generate(cfg(textAlpha = HALF, referenceAlpha = QUARTER))

        val text = opacityKeyframes(layer(doc, "Text1"))
        val reference = opacityKeyframes(layer(doc, "Reference1"))

        assertEquals(0.0, text.first(), "the words start invisible")
        assertEquals(HALF.toDouble(), text.max(), "the words never pass their own opacity")
        assertEquals(QUARTER.toDouble(), reference.max(), "the reference has its own opacity")
    }

    @Test
    fun `a slot at full opacity is unchanged`() {
        val doc = generate(cfg())
        assertEquals(FULL.toDouble(), opacityKeyframes(layer(doc, "Text1")).max())
    }

    @Test
    fun `each gradient stop carries its own role's alpha`() {
        val doc = generate(cfg(bgAlpha = FULL, secondAlpha = 0))
        val stops = gradientStops(doc)

        // Two colours: four numbers per colour stop, then two per alpha stop.
        val alphas = stops.takeLast(TWO_STOPS * 2)
        assertEquals(1.0, alphas[1], "the first stop keeps the background's opacity")
        assertEquals(0.0, alphas[3], "the last stop fades out entirely")
    }

    @Test
    fun `the gradient position moves where the blend finishes`() {
        val full = gradientStops(generate(cfg(gradientPosition = FULL)))
        val half = gradientStops(generate(cfg(gradientPosition = HALF)))

        // The last colour stop's position is the fourth-from-last number of the colour section.
        assertEquals(1.0, full[COLOUR_STOP_STRIDE], "by default the blend runs the whole band")
        assertEquals(HALF / FULL.toDouble(), half[COLOUR_STOP_STRIDE], "and lower finishes it sooner")
    }

    @Test
    fun `a three colour gradient keeps its stops evenly spaced inside the ramp`() {
        val stops = gradientStops(generate(cfg(style = BandStyle.GRADIENT_TRIO, gradientPosition = FULL)))
        assertEquals(0.0, stops[0])
        assertEquals(0.5, stops[COLOUR_STOP_STRIDE], "the middle colour sits half way")
        assertEquals(1.0, stops[COLOUR_STOP_STRIDE * 2], "and the last at the end")
    }

    @Test
    fun `the border's opacity reaches its stroke`() {
        val doc = generate(cfg(borderThickness = BORDER_PX, borderAlpha = QUARTER))
        val strokes = layers(doc).flatMap { l ->
            (l["shapes"]?.jsonArray ?: return@flatMap emptyList())
                .flatMap { it.jsonObject["it"]!!.jsonArray }
                .map { it.jsonObject }
                .filter { it["ty"]!!.jsonPrimitive.content == "st" }
        }
        assertTrue(strokes.isNotEmpty(), "a band with a border thickness draws a stroke")
        assertTrue(
            strokes.any { it["o"]!!.jsonObject["k"]!!.jsonPrimitive.double() == QUARTER.toDouble() },
            "the stroke takes the configured border opacity",
        )
    }

    private fun kotlinx.serialization.json.JsonPrimitive.double(): Double = doubleOrNull ?: content.toDouble()

    private companion object {
        const val CANVAS_W = 960
        const val CANVAS_H = 180
        const val FULL = 100
        const val HALF = 50
        const val QUARTER = 25
        const val BORDER_PX = 4
        const val TWO_STOPS = 2

        /** A colour stop is a position plus red, green and blue. */
        const val COLOUR_STOP_STRIDE = 4
    }
}

/** The layer-name prefix the band's shape layers share, mirrored here so the test reads the output. */
private object BibleLottieTemplateLayers {
    const val BAND_PREFIX = "Band"
}

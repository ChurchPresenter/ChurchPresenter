package org.churchpresenter.lottiegen.band

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.band.BandJson.intField
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.TextRun
import org.churchpresenter.lottiegen.lottie.TextWrapBox
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeGradientFillStops
import org.churchpresenter.lottiegen.lottie.makeTextData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The engine additions the band generator needed: offset keyframes, markers, wrap boxes, multi-stop gradients. */
class EngineAdditionsTest {

    private fun run() = TextRun("x", "Arial", 10.0, 400, listOf(1.0, 1.0, 1.0), "none")

    /** The first document of a text layer's data, where the wrap box lands. */
    private fun JsonObject.document(): JsonObject =
        this["d"]!!.jsonObject["k"]!!.jsonArray[0].jsonObject["s"]!!.jsonObject

    @Test
    fun `a start frame shifts the whole in-hold-out sequence`() {
        val points = listOf(KeyframeInput(0.0, jsonArrayOf(0.0)), KeyframeInput(100.0, jsonArrayOf(1.0)))
        val plain = buildKeyframes(points, 10, 20, 10)
        val shifted = buildKeyframes(points, 10, 20, 10, startFrame = 5)
        val times = { arr: JsonArray -> arr.map { it.jsonObject["t"]!!.jsonPrimitive.content.toInt() } }
        assertEquals(listOf(0, 10, 30, 40), times(plain))
        assertEquals(listOf(5, 15, 35, 45), times(shifted))
    }

    @Test
    fun `markers, matte parents and hidden flags are written only when asked`() {
        val builder = LottieBuilder(10, 10)
        builder.setDuration(1, 1, 1)
        val matte = builder.addShapeLayer("M", buildJsonArray { }, LottieBuilder.defaultTransform(), td = 1)
        builder.addShapeLayer(
            "S", buildJsonArray { }, LottieBuilder.defaultTransform(), tt = 1, tp = matte, hidden = true,
        )
        builder.addImageLayer("I", "a", LottieBuilder.defaultTransform(), tt = 1, tp = matte)
        builder.addTextLayer("T", makeTextData(run()), LottieBuilder.defaultTransform())
        val plainDoc = builder.toJson()
        assertNull(plainDoc["markers"], "no markers unless one was added")
        val layers = plainDoc["layers"]!!.jsonArray.map { it.jsonObject }
        assertEquals(0, layers[1].intField("tp"))
        assertEquals(true, layers[1]["hd"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(0, layers[2].intField("tp"))
        assertNull(layers[3]["hd"])
        builder.addMarker("hold", 3, 4)
        assertEquals(1, builder.toJson()["markers"]!!.jsonArray.size)
    }

    @Test
    fun `a wrap box lands in the text document as sz and ps`() {
        val doc = makeTextData(run(), TextWrapBox(1.0, 2.0, 30.0, 40.0))
        val s = doc.document()
        assertEquals(listOf(30.0, 40.0), s["sz"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() })
        assertEquals(listOf(1.0, 2.0), s["ps"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() })
        assertNull(makeTextData(run()).document()["sz"])
    }

    @Test
    fun `a multi-stop gradient spreads its colours evenly and refuses fewer than two`() {
        val colors = listOf(listOf(0.0, 0.0, 0.0), listOf(0.5, 0.5, 0.5), listOf(1.0, 1.0, 1.0))
        val fill = makeGradientFillStops(colors, 100.0, listOf(0.0, 0.0), listOf(1.0, 0.0))
        val stops = fill["g"]!!.jsonObject["k"]!!.jsonObject["k"]!!.jsonArray
            .map { it.jsonPrimitive.content.toDouble() }
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.5, 0.5, 0.5, 0.5, 1.0, 1.0, 1.0, 1.0), stops.take(12))
        assertEquals(3, fill["g"]!!.jsonObject["p"]!!.jsonPrimitive.content.toInt())
        assertFailsWith<IllegalArgumentException> {
            makeGradientFillStops(listOf(listOf(0.0, 0.0, 0.0)), 100.0, listOf(0.0, 0.0), listOf(1.0, 0.0))
        }
    }
}

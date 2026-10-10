package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpecGeneratorEdgeTest {

    private fun render(vararg elements: ElementSpec, cfg: LottieGenConfig = LottieGenConfig()): String =
        LottieGenerator.generate(
            cfg, SpecStyleGenerator(StyleSpec(id = "90", name = "Edge", elements = elements.toList())),
        )
            .toString()

    @Test
    fun `an empty path still draws, and repeats with a fitted, leftward step`() {
        val json = render(
            PathElement("p", repeat = RepeatSpec(copies = 3, offsetXEm = -1.0, fitWidthTo = WidthBasis.NAME)),
            RectElement("r", repeat = RepeatSpec(copies = 1, fitWidthTo = WidthBasis.INFO)),
        )
        assertTrue(json.contains("\"ty\":\"rp\""), "a repeater was emitted")
    }

    @Test
    fun `a rect growing from its edge keeps that edge still, even with a value-less keyframe`() {
        val grow = AnimTrack(
            AnimProperty.RECT_SIZE,
            listOf(SpecKeyframe(0.0, emptyList()), SpecKeyframe(100.0, listOf(1.0, 1.0))),
        )
        val rect = RectElement("r", tracks = listOf(grow), growFrom = GrowOrigin.ALIGN_EDGE)
        for (align in listOf("left", "right", "center")) {
            assertTrue(render(rect, cfg = LottieGenConfig(align = align)).contains("\"nm\""))
        }
    }

    @Test
    fun `a mask moved by a short-valued position track still slides, and a missing bundled spec fails loudly`() {
        val slide = AnimTrack(
            AnimProperty.POSITION_OFFSET,
            listOf(SpecKeyframe(0.0, emptyList()), SpecKeyframe(100.0, listOf(1.0))),
        )
        val text = TextElement(
            "t", "Name", field = TextFieldRef.NAME, maskReveal = MaskRevealSpec(tracks = listOf(slide)),
        )
        val still = text.copy(maskReveal = MaskRevealSpec())
        assertTrue(render(text) != render(still))
        assertTrue(kotlin.runCatching { SpecStyleGenerator.fromResource("/styles/none.json") }.isFailure)
    }

    @Test
    fun `a text layer carries the matte, parent and hidden flags it is given`() {
        val builder = LottieBuilder(w = 100, h = 100)
        builder.addTextLayer("plain", buildJsonObject {}, buildJsonObject {})
        builder.addTextLayer(
            "all", buildJsonObject {}, buildJsonObject {}, td = 1, tt = 1, parent = 1, tp = 1, hidden = true,
        )
        val layers = builder.toJson()["layers"] as JsonArray
        val all = layers.map { it as JsonObject }.first { (it["nm"] as JsonPrimitive).content == "all" }
        val flags = listOf("td", "tt", "parent", "tp", "hd").map { (all[it] as JsonPrimitive).content }
        assertEquals(listOf("1", "1", "1", "1", "true"), flags)
    }
}

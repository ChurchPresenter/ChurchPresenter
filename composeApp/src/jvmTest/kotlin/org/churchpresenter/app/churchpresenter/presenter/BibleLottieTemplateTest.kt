package org.churchpresenter.app.churchpresenter.presenter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.band.BandTextAlign as GenAlign
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.band.TextAnimation
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleLottieTemplateTest {

    private val cfg = BibleLottieGenConfig(
        canvasW = 960, canvasH = 180, layout = SlotLayout.SIDE_BY_SIDE,
        bgInSeconds = 0.5f, textInSeconds = 0.5f, holdSeconds = 1f, textOutSeconds = 0.25f, bgOutSeconds = 0.5f,
        textAnimation = TextAnimation.TYPEWRITER, textAlign = GenAlign.LEFT, tickerPxPerSecond = 200,
    )
    private val template = assertNotNull(parseBibleLottieTemplate(LottieBandTestSupport.templateJson(cfg)))

    @Test
    fun `a generated file parses with its size, markers, slots and metadata`() {
        assertEquals(960f, template.width)
        assertEquals(180f, template.height)
        assertEquals(60f, template.frameRate)
        assertEquals(165f, template.totalFrames)
        assertEquals(LottieSegment(0f, 30f), template.segment(BibleLottieTemplate.SEGMENT_BG_IN))
        assertEquals(LottieSegment(60f, 60f), template.segment(BibleLottieTemplate.SEGMENT_HOLD))
        assertEquals(LottieSegment(135f, 30f), template.segment(BibleLottieTemplate.SEGMENT_BG_OUT))
        assertEquals(setOf("Text1", "Reference1", "Text2", "Reference2"), template.slots.keys)
        assertTrue(template.hasLayer("Text2") && template.hasLayer("Text1Shadow") && !template.hasLayer("Nope"))
        assertEquals(BandTextMotion.TYPEWRITER, template.meta.textMotion)
        assertEquals(200f, template.meta.tickerPxPerSecond)
        assertEquals(BandTextAlign.LEFT, template.meta.textAlign)
        assertNull(template.meta.referenceAlign, "follow-settings is no pin")
    }

    @Test
    fun `the clock maps onto frames phase by phase`() {
        assertEquals(0f, template.frameAt(BibleBandClock(BibleBandPhase.IDLE, 1f)))
        assertEquals(30f, template.frameAt(BibleBandClock(BibleBandPhase.ENTER, 0.5f)), "half way to the hold")
        val heldFrame = template.frameAt(BibleBandClock(BibleBandPhase.HOLD, 0.3f))
        assertEquals(60f, heldFrame, "pinned on the hold's first frame")
        assertEquals(127.5f, template.frameAt(BibleBandClock(BibleBandPhase.TEXT_OUT, 0.5f)))
        assertEquals(45f, template.frameAt(BibleBandClock(BibleBandPhase.TEXT_IN, 0.5f)))
        assertEquals(165f, template.frameAt(BibleBandClock(BibleBandPhase.EXIT, 1f)))
        assertEquals(60f / 165f, template.progressAt(BibleBandClock()))
        assertEquals(1f, template.progressAt(BibleBandClock(BibleBandPhase.EXIT, 2f)), "progress is clamped")
        assertEquals(750L, template.segmentMs(BibleLottieTemplate.SEGMENT_TEXT_OUT, BibleLottieTemplate.SEGMENT_BG_OUT))
        assertEquals(0.5f, template.progressWithin(BibleLottieTemplate.SEGMENT_TEXT_IN, 45f))
        assertEquals(1f, template.progressWithin(BibleLottieTemplate.SEGMENT_TEXT_IN, 500f))
    }

    @Test
    fun `a plain Lottie with no markers is played in fifths and its documents give the boxes`() {
        val plain = """{"fr":30,"ip":0,"op":100,"w":200,"h":50,"layers":[
            {"ty":5,"nm":"Text1","t":{"d":{"k":[{"s":{"sz":[100,20],"ps":[5,7]}}]}}},
            {"ty":4,"nm":"Band"}]}"""
        val t = assertNotNull(parseBibleLottieTemplate(plain))
        assertEquals(LottieSegment(40f, 20f), t.segment(BibleLottieTemplate.SEGMENT_HOLD))
        assertEquals(LottieSlotBox(5f, 7f, 100f, 20f), t.slots["Text1"])
        assertEquals(BandTextMotion.NONE, t.meta.textMotion)
    }

    @Test
    fun `an older generated file without declared slots takes its boxes from the mattes`() {
        val old = LottieBandTestSupport.templateJson(cfg.copy(textAnimation = TextAnimation.WIPE))
        val obj = Json.parseToJsonElement(old).jsonObject
        val meta = obj["cp"]!!.jsonObject
        val stripped = Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            JsonObject(obj + ("cp" to JsonObject(meta - "slots"))),
        )
        val t = assertNotNull(parseBibleLottieTemplate(stripped))
        val declared = assertNotNull(parseBibleLottieTemplate(old)).slots.getValue("Text1")
        val recovered = t.slots.getValue("Text1")
        assertEquals(declared.y, recovered.y, 0.01f, "the matte is the slot box")
        assertEquals(declared.h, recovered.h, 0.01f)
    }

    @Test
    fun `files that are not templates are null`() {
        assertNull(parseBibleLottieTemplate("not json"))
        assertNull(parseBibleLottieTemplate("""{"fr":30,"ip":0,"op":0,"w":1,"h":1}"""), "no frames")
        assertNull(parseBibleLottieTemplate("""{"fr":30,"ip":0,"op":10,"w":0,"h":1}"""), "no canvas")
        assertNull(loadBibleLottieTemplate(""))
        assertNull(loadBibleLottieTemplate("/nowhere/band.json"))
        val dir = Files.createTempDirectory("template").toFile()
        try {
            assertNotNull(loadBibleLottieTemplate(LottieBandTestSupport.writeTemplate(dir).absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `rewriting the fonts sets each layer's face, one font entry per face, and drops the glyph outlines`() {
        val faces = mapOf(
            "Text1" to BandFontKey("Georgia", bold = true, italic = false),
            "Reference1" to BandFontKey("Georgia", bold = true, italic = false),
            "Text2" to BandFontKey("Verdana", bold = false, italic = true),
        )
        val rewritten = Json.parseToJsonElement(rewriteTemplateFonts(template.json, faces)).jsonObject
        assertNull(rewritten["chars"], "outlines only cover the sample's characters")
        val list = rewritten["fonts"]!!.jsonObject["list"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("Georgia-Bold", "Verdana-Italic"), list.map { it["fName"]!!.jsonPrimitive.content })
        assertEquals("Bold", list[0]["fStyle"]!!.jsonPrimitive.content)
        assertEquals("Italic", list[1]["fStyle"]!!.jsonPrimitive.content)
        fun face(name: String): String {
            val layer = rewritten["layers"]!!.jsonArray.map { it.jsonObject }
                .first { it["nm"]!!.jsonPrimitive.content == name }
            val document = layer["t"]!!.jsonObject["d"]!!.jsonObject["k"]!!.jsonArray[0].jsonObject["s"]!!.jsonObject
            return document["f"]!!.jsonPrimitive.content
        }
        assertEquals("Georgia-Bold", face("Text1"))
        assertEquals("Verdana-Italic", face("Text2"))
        assertEquals("Arial-Regular", face("Text1Shadow"), "a layer not in the map keeps the file's face")
        assertEquals("Bold Italic", BandFontKey("A", bold = true, italic = true).styleName)
        assertEquals("A-BoldItalic", BandFontKey("A", bold = true, italic = true).lottieName)
        assertEquals("Regular", BandFontKey("A", bold = false, italic = false).styleName)
    }
}

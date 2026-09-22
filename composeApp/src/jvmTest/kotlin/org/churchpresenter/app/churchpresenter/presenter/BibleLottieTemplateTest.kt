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
        val swap = BibleBandClock(BibleBandPhase.TEXT_SWAP, 0.5f)
        assertEquals(45f, template.frameAt(swap), "the incoming text half-way through text_in")
        assertEquals(127.5f, template.outgoingFrameAt(swap), "the outgoing text half-way through text_out")
        assertEquals(127.5f / 165f, template.outgoingProgressAt(swap))
        assertEquals(500L, template.swapMs(), "the longer of text_out and text_in")
        assertEquals(165f, template.frameAt(BibleBandClock(BibleBandPhase.EXIT, 1f)))
        assertEquals(60f / 165f, template.progressAt(BibleBandClock()))
        assertEquals(1f, template.progressAt(BibleBandClock(BibleBandPhase.EXIT, 2f)), "progress is clamped")
        assertEquals(750L, template.segmentMs(BibleLottieTemplate.SEGMENT_TEXT_OUT, BibleLottieTemplate.SEGMENT_BG_OUT))
        assertEquals(0.5f, template.progressWithin(BibleLottieTemplate.SEGMENT_TEXT_IN, 45f))
        assertEquals(1f, template.progressWithin(BibleLottieTemplate.SEGMENT_TEXT_IN, 500f))
    }

    @Test
    fun `a crossfade the file names is used, and a file without one is swapped in its text segments' time`() {
        val named =
            assertNotNull(parseBibleLottieTemplate(LottieBandTestSupport.templateJson(cfg.copy(swapSeconds = 1.2f))))
        assertEquals(1200L, named.meta.swapMs)
        assertEquals(1200L, named.swapMs())
        val stripped = Json.parseToJsonElement(LottieBandTestSupport.templateJson(cfg)).jsonObject.let { doc ->
            val meta = doc["cp"]!!.jsonObject.filterKeys { it != "swapMs" }
            JsonObject(doc.toMutableMap().apply { put("cp", JsonObject(meta)) })
        }
        val older = assertNotNull(parseBibleLottieTemplate(stripped.toString()))
        assertNull(older.meta.swapMs)
        assertEquals(500L, older.swapMs(), "the longer of text_out and text_in, as before")
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

    // ── textSlotCount, past the original two ────────────────────────────────────────────────────

    @Test
    fun `a two-slot template reports two, counting up from Text1`() {
        assertEquals(2, template.textSlotCount)
    }

    @Test
    fun `a 2x2 grid template reports all four slots`() {
        val json = LottieBandTestSupport.templateJson(cfg.copy(layout = SlotLayout.GRID_2X2))
        val grid = assertNotNull(parseBibleLottieTemplate(json))
        assertEquals(4, grid.textSlotCount)
        assertEquals(
            setOf("Text1", "Reference1", "Text2", "Reference2", "Text3", "Reference3", "Text4", "Reference4"),
            grid.slots.keys,
        )
        assertTrue(grid.hasLayer("Text3") && grid.hasLayer("Text4"))
    }

    @Test
    fun `a single layout template reports one slot`() {
        val json = LottieBandTestSupport.templateJson(cfg.copy(layout = SlotLayout.SINGLE))
        val single = assertNotNull(parseBibleLottieTemplate(json))
        assertEquals(1, single.textSlotCount)
        assertTrue(!single.hasLayer("Text2"))
    }

    @Test
    fun `a gap in the layers is not reached past`() {
        // Not something the generator can produce, but a hand-edited file could: Text1/Text2/Text4
        // with no Text3 must count 2, the run from Text1, rather than 4.
        val json = LottieBandTestSupport.templateJson(cfg.copy(layout = SlotLayout.GRID_2X2))
        val obj = Json.parseToJsonElement(json).jsonObject
        val layers = obj["layers"]!!.jsonArray.filterNot { it.jsonObject["nm"]?.jsonPrimitive?.content == "Text3" }
        val newLayers = kotlinx.serialization.json.JsonArray(layers)
        val gapped = JsonObject(obj.toMutableMap().apply { put("layers", newLayers) })
        val t = assertNotNull(parseBibleLottieTemplate(gapped.toString()))
        assertEquals(2, t.textSlotCount)
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

    @Test
    fun `a file with no fonts block of its own is given one`() {
        val bare = """{"layers":[{"nm":"Text1","t":{"d":{"k":[{"s":{"f":"Arial-Regular"}}]}}}]}"""

        val rewritten = Json.parseToJsonElement(
            rewriteTemplateFonts(bare, mapOf("Text1" to BandFontKey("Georgia", bold = false, italic = false))),
        ).jsonObject

        val list = rewritten["fonts"]!!.jsonObject["list"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("Georgia-Regular"), list.map { it["fName"]!!.jsonPrimitive.content })
    }

    @Test
    fun `a layer the rewrite cannot reach into is left exactly as it was`() {
        // Each of these is missing one step of the path to a text document: no `t`, no `d`, no `k`,
        // a keyframe that is not an object, and a keyframe with no `s`. None may be rewritten, and
        // none may be dropped either -- a band draws whatever layers the file has.
        val layers = listOf(
            """{"nm":"Text1"}""",
            """{"nm":"Text1","t":{}}""",
            """{"nm":"Text1","t":{"d":{}}}""",
            """{"nm":"Text1","t":{"d":{"k":["not an object"]}}}""",
            """{"nm":"Text1","t":{"d":{"k":[{"noS":1}]}}}""",
            """"a layer that is not an object at all"""",
            """{"noName":1}""",
        )
        val faces = mapOf("Text1" to BandFontKey("Georgia", bold = false, italic = false))

        layers.forEach { layer ->
            val json = """{"layers":[$layer]}"""
            val rewritten = Json.parseToJsonElement(rewriteTemplateFonts(json, faces)).jsonObject
            assertEquals(
                Json.parseToJsonElement(json).jsonObject["layers"],
                rewritten["layers"],
                "an unreachable layer is carried through untouched",
            )
        }
    }

    // ── What a file leaves out, or gets wrong ───────────────────────────────────

    /** The smallest playable Lottie, with [extra] spliced into its top-level object. */
    private fun minimal(extra: String = "") = """{"fr":30,"op":90,"w":1920,"h":1080$extra}"""

    @Test
    fun `a file missing its frame rate, end frame or canvas is not a template`() {
        assertNull(parseBibleLottieTemplate("""{"op":90,"w":1920,"h":1080}"""), "no frame rate")
        assertNull(parseBibleLottieTemplate("""{"fr":"fast","op":90,"w":1920,"h":1080}"""), "a frame rate in words")
        assertNull(parseBibleLottieTemplate("""{"fr":30,"w":1920,"h":1080}"""), "no end frame")
        assertNull(parseBibleLottieTemplate("""{"fr":30,"op":90,"h":1080}"""), "no width")
        assertNull(parseBibleLottieTemplate("""{"fr":30,"op":90,"w":1920}"""), "no height")
        assertNull(parseBibleLottieTemplate("""[1, 2, 3]"""), "not an object at all")
        assertNull(parseBibleLottieTemplate("""{"fr":30,"op":"""), "cut off mid-file")
    }

    @Test
    fun `a file with no in point starts at frame zero`() {
        assertEquals(90f, assertNotNull(parseBibleLottieTemplate(minimal())).totalFrames)
        assertEquals(60f, assertNotNull(parseBibleLottieTemplate(minimal(""","ip":30"""))).totalFrames)
    }

    @Test
    fun `markers missing a name or a time are passed over, and one missing a duration lasts no time`() {
        val markers = ""","markers":[
            1,
            {"tm":0,"dr":10},
            {"cm":"bg_in"},
            {"cm":"bg_in","tm":0,"dr":18},
            {"cm":"text_in","tm":18,"dr":18},
            {"cm":"hold","tm":36},
            {"cm":"text_out","tm":54,"dr":18},
            {"cm":"bg_out","tm":72,"dr":18}
        ]"""
        val t = assertNotNull(parseBibleLottieTemplate(minimal(markers)))

        assertEquals(LottieSegment(0f, 18f), t.segments[BibleLottieTemplate.SEGMENT_BG_IN])
        assertEquals(LottieSegment(36f, 0f), t.segments[BibleLottieTemplate.SEGMENT_HOLD])
        assertEquals(5, t.segments.size, "the nameless and timeless markers add nothing")
    }

    @Test
    fun `an incomplete set of markers falls back to fifths rather than playing half a band`() {
        val t = assertNotNull(parseBibleLottieTemplate(minimal(""","markers":[{"cm":"bg_in","tm":0,"dr":5}]""")))

        assertEquals(LottieSegment(0f, 18f), t.segments[BibleLottieTemplate.SEGMENT_BG_IN])
        assertEquals(LottieSegment(72f, 18f), t.segments[BibleLottieTemplate.SEGMENT_BG_OUT])
    }

    @Test
    fun `every alignment and text motion the generator writes is read back`() {
        fun meta(json: String) = assertNotNull(parseBibleLottieTemplate(minimal(""","cp":$json"""))).meta

        assertEquals(BandTextAlign.CENTER, meta("""{"textAlign":"CENTER"}""").textAlign)
        assertEquals(BandTextAlign.RIGHT, meta("""{"referenceAlign":"RIGHT"}""").referenceAlign)
        assertNull(meta("""{"textAlign":"JUSTIFY"}""").textAlign, "an alignment the player has not got is left to it")
        assertEquals(BandTextMotion.TYPEWRITER_WORDS, meta("""{"textAnimation":"TYPEWRITER_WORDS"}""").textMotion)
        assertEquals(BandTextMotion.TICKER, meta("""{"textAnimation":"TICKER","tickerPxPerSecond":240}""").textMotion)
        assertEquals(240f, meta("""{"textAnimation":"TICKER","tickerPxPerSecond":240}""").tickerPxPerSecond)
        assertEquals(BandTextMotion.NONE, meta("""{"textAnimation":"SPIN"}""").textMotion)
        assertEquals(750L, meta("""{"swapMs":750}""").swapMs)
    }
}

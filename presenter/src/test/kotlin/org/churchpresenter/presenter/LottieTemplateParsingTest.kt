package org.churchpresenter.presenter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LottieTemplateParsingTest {

    private fun template(layers: String = "[]", extra: String = "") =
        """{"fr":30,"ip":0,"op":90,"w":1920,"h":200,"layers":$layers$extra}"""

    private fun matte(name: String, td: Int? = 1, shapes: String = RECT) =
        """{"nm":"$name"${td?.let { ",\"td\":$it" } ?: ""},"shapes":[{"it":$shapes}]}"""

    @Test
    fun `text that is not a Lottie is no template`() {
        assertNull(parseBibleLottieTemplate("not json"))
        assertNull(parseBibleLottieTemplate("""{"ip":0,"op":90,"w":1,"h":1}"""), "no frame rate")
        assertNull(parseBibleLottieTemplate("""{"fr":30,"op":0,"w":1920,"h":200}"""), "nothing to play")
    }

    @Test
    fun `a file with no layers still plays, with no slots of its own`() {
        val parsed = assertNotNull(parseBibleLottieTemplate("""{"fr":30,"ip":0,"op":90,"w":1920,"h":200}"""))
        assertTrue(parsed.layerNames.isEmpty())
        assertTrue(parsed.slots.isEmpty())
    }

    @Test
    fun `a text slot's box is read off its matte`() {
        val parsed = assertNotNull(parseBibleLottieTemplate(template("[${matte("Text1Matte")},${docLayer("Text1")}]")))
        assertEquals(LottieSlotBox(92f, 42f, 416f, 116f), parsed.slots["Text1"])
    }

    @Test
    fun `a matte that is not a track matte, not a text slot's, or has no rectangle gives no box`() {
        val layers = listOf(
            matte("Text1Matte", td = null),
            matte("Text1Matte", td = 0),
            matte("LogoMatte"),
            matte("Reference1Matte", shapes = """[{"ty":"el"}]"""),
            matte("Text2Matte", shapes = """[{"ty":"rc","s":{"a":0,"k":[100]},"p":{"a":0,"k":[1,2]}}]"""),
            """{"td":1}""",
            """{"nm":"Text1Matte","td":"x"}""",
            """{"nm":"Text2Matte","td":1,"shapes":[{}]}""",
            docLayer("Text1"),
            docLayer("Text2"),
            docLayer("Reference1"),
        ).joinToString(",", "[", "]")
        val parsed = assertNotNull(parseBibleLottieTemplate(template(layers)))
        val fromDocuments = LottieSlotBox(0f, 0f, 10f, 10f)
        assertEquals(
            mapOf("Text1" to fromDocuments, "Text2" to fromDocuments, "Reference1" to fromDocuments),
            parsed.slots,
        )
    }

    @Test
    fun `a text layer's ascent comes from the font it names`() {
        val fonts = JsonObject(
            Json.parseToJsonElement(
                """{"list":[{"fName":"Inter","ascent":75},{"fName":"NoAscent"},{"ascent":10}]}""",
            ).jsonObject,
        )
        val layers = listOf(
            textLayer("Text1", "Inter"),
            textLayer("Text2", "NoAscent"),
            textLayer("Reference1", "Missing"),
            """{"ty":4,"nm":"Shape"}""",
            """{"ty":5}""",
            """{"ty":5,"nm":"Bare"}""",
        ).map { Json.parseToJsonElement(it).jsonObject }

        assertEquals(mapOf("Text1" to 0.75f), readTextAscents(fonts, layers))
        assertEquals(emptyMap(), readTextAscents(null, layers))
    }

    @Test
    fun `nothing to fit keeps the size it asked for`() {
        val box = LottieSlotBox(0f, 0f, 100f, 50f)
        assertEquals(24f, fitLottieSlot(SlotFitRequest("", box, 24f, 0f, singleLine = false)) { 10f }.fontSize)
        assertEquals(1f, fitLottieSlot(SlotFitRequest("Hi", box, 0f, 0f, singleLine = false)) { 10f }.fontSize)
    }

    @Test
    fun `a single line too wide for its box is shrunk until it fits across`() {
        val box = LottieSlotBox(0f, 0f, 100f, 50f)
        val fitted = fitLottieSlot(SlotFitRequest("John 3:16 KJV", box, 40f, 0f, singleLine = true)) { 20f }
        assertTrue(fitted.fontSize < 40f, "fitted at ${fitted.fontSize}")
        assertEquals(1, fitted.lines.size)
    }

    private fun docLayer(name: String) =
        """{"ty":5,"nm":"$name","t":{"d":{"k":[{"s":{"sz":[10,10],"ps":[0,0]}}]}}}"""

    private fun textLayer(name: String, font: String) =
        """{"ty":5,"nm":"$name","t":{"d":{"k":[{"s":{"f":"$font","t":"x"}}]}}}"""

    private companion object {
        const val RECT = """[{"ty":"rc","s":{"a":0,"k":[420,120]},"p":{"a":0,"k":[300,100]}}]"""
    }

    @Test
    fun `a single line that wraps or stands too tall for its box is shrunk`() {
        val short = LottieSlotBox(0f, 0f, 1_000f, 20f)
        val tall = fitLottieSlot(SlotFitRequest("Reference", short, 40f, 0f, singleLine = true)) { 10f }
        assertTrue(tall.fontSize < 40f, "fitted at ${tall.fontSize}")
        val twoLines = fitLottieSlot(SlotFitRequest("John 3:16\nKJV", short, 40f, 0f, singleLine = true)) { 10f }
        assertTrue(twoLines.fontSize < 40f, "fitted at ${twoLines.fontSize}")
    }

    @Test
    fun `a blank written line keeps its place when a lyric wraps`() {
        val box = LottieSlotBox(0f, 0f, 200f, 400f)
        val fitted = fitLottieSlot(SlotFitRequest("first\n\nthird", box, 20f, 1f, singleLine = false)) { 8f }
        assertEquals(listOf("first", "", "third"), fitted.lines)
    }
}

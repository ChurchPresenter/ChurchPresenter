package org.churchpresenter.lottiegen.lottie

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MalformedLottieInputTest {

    private fun font(name: String?, family: String?, style: String? = null) = buildJsonObject {
        name?.let { put("fName", it) }
        family?.let { put("fFamily", it) }
        style?.let { put("fStyle", it) }
    }

    private fun textLayer(fName: String?, text: String?, type: String = "5") = buildJsonObject {
        put("ty", type)
        put("t", buildJsonObject {
            put("d", buildJsonObject {
                put("k", buildJsonArray {
                    add(buildJsonObject {
                        put("s", buildJsonObject {
                            fName?.let { put("f", it) }
                            text?.let { put("t", it) }
                        })
                    })
                    add(JsonPrimitive("not a keyframe"))
                })
            })
        })
    }

    @Test
    fun `fonts without a name or family declare nothing`() {
        val layers = listOf(textLayer("Sans", "Hi"))
        assertNull(GlyphExtractor.buildCharsArray(layers, listOf(font(null, "Verdana"), font("Sans", null))))
    }

    @Test
    fun `only text layers with a declared font and a string are extracted`() {
        val layers = listOf(
            textLayer("Sans", "Ab\n"),
            textLayer("Other", "zz"),
            textLayer(null, "no font"),
            textLayer("Sans", null),
            textLayer("Sans", "skipped", type = "4"),
            buildJsonObject { put("ty", "5") },
            buildJsonObject { put("ty", "5"); put("t", buildJsonObject { put("d", JsonPrimitive(1)) }) },
        )
        val chars = assertNotNull(GlyphExtractor.buildCharsArray(layers, listOf(font("Sans", "Verdana"))))
        assertEquals(listOf("A", "b"), chars.map { ((it as JsonObject)["ch"] as JsonPrimitive).content })
        assertEquals("Regular", ((chars.first() as JsonObject)["style"] as JsonPrimitive).content)
    }

    @Test
    fun `a font no text layer uses yields no chars`() {
        assertNull(
            GlyphExtractor.buildCharsArray(listOf(textLayer("Other", "x")),
            listOf(font("Sans", "Verdana", "Bold"))),
        )
    }

    @Test
    fun `static texts skip whatever is not a text document, in layers and assets alike`() {
        val lottie = buildJsonObject {
            put("layers", JsonArray(listOf(textLayer("Sans", "top"), JsonPrimitive(3), textLayer("Sans", null))))
            put("assets", buildJsonArray {
                add(buildJsonObject { put("layers", JsonArray(listOf(textLayer("Sans", "inner")))) })
                add(buildJsonObject { put("id", "image") })
                add(JsonPrimitive("junk"))
            })
        }
        assertEquals(listOf("top", "inner"), LottieTextShaping.staticTexts(lottie))
    }

    @Test
    fun `json that does not parse to an object reads as auto`() {
        assertEquals(TextShaping.AUTO, LottieTextShaping.modeOf("[1, 2]"))
        assertEquals(TextShaping.AUTO, LottieTextShaping.modeOf("{ not json"))
        assertEquals(TextShaping.AUTO, LottieTextShaping.modeOf("\"\\u12\""))
    }
}

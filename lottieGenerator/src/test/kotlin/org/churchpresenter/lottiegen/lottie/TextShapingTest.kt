package org.churchpresenter.lottiegen.lottie

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TAMIL = "கர்த்தர் நல்லவர் என்பதை ருசித்துப்பாருங்கள்"

class TextShapingTest {

    private fun toJson(lottie: JsonObject) = Json.encodeToString(JsonObject.serializer(), lottie)

    @Test
    fun `scripts whose letters change shape need shaping, Latin Greek and Cyrillic do not`() {
        assertTrue(LottieTextShaping.needsShaping(TAMIL))
        assertTrue(LottieTextShaping.needsShaping("नमस्ते"), "Devanagari")
        assertTrue(LottieTextShaping.needsShaping("سلام"), "Arabic")
        assertTrue(LottieTextShaping.needsShaping("สวัสดี"), "Thai")
        assertFalse(LottieTextShaping.needsShaping("Amazing grace, how sweet the sound! 3:16"))
        assertFalse(LottieTextShaping.needsShaping("Ἐν ἀρχῇ ἦν ὁ λόγος"), "Greek")
        assertFalse(LottieTextShaping.needsShaping("В начале было Слово"), "Cyrillic")
        assertFalse(LottieTextShaping.needsShaping("é"), "a combining accent on Latin is inherited")
        assertFalse(LottieTextShaping.needsShaping(""))
    }

    @Test
    fun `auto groups only when some text needs it, the other two settle it outright`() {
        assertTrue(LottieTextShaping.groupsText(TextShaping.AUTO, listOf("Grace", TAMIL)))
        assertFalse(LottieTextShaping.groupsText(TextShaping.AUTO, listOf("Grace", "Peace")))
        assertFalse(LottieTextShaping.groupsText(TextShaping.AUTO, emptyList()))
        assertTrue(LottieTextShaping.groupsText(TextShaping.WHOLE_LINES, listOf("Grace")))
        assertFalse(LottieTextShaping.groupsText(TextShaping.PER_LETTER, listOf(TAMIL)))
    }

    @Test
    fun `a key reads back as its mode, and anything else as auto`() {
        TextShaping.entries.forEach { assertEquals(it, TextShaping.fromKey(it.key)) }
        assertEquals(TextShaping.AUTO, TextShaping.fromKey(null))
        assertEquals(TextShaping.AUTO, TextShaping.fromKey("sideways"))
    }

    @Test
    fun `the mode is written under cp beside what is already there, and read back`() {
        val lottie = buildJsonObject {
            put("v", JsonPrimitive("5.7.4"))
            put(LottieTextShaping.METADATA_KEY, buildJsonObject { put("kind", JsonPrimitive("bible-band")) })
        }
        val written = LottieTextShaping.withMode(lottie, TextShaping.PER_LETTER)
        val cp = written.getValue(LottieTextShaping.METADATA_KEY).jsonObject
        assertEquals("letters", cp.getValue(LottieTextShaping.TEXT_SHAPING).jsonPrimitive.content)
        assertEquals("bible-band", cp.getValue("kind").jsonPrimitive.content, "the band's own metadata is kept")
        assertEquals("5.7.4", written.getValue("v").jsonPrimitive.content)
        assertEquals(TextShaping.PER_LETTER, LottieTextShaping.modeOf(written))
        assertEquals(TextShaping.PER_LETTER, LottieTextShaping.modeOf(toJson(written)))
        assertEquals(TextShaping.AUTO, LottieTextShaping.modeOf(lottie), "a file from before the setting is auto")
        assertEquals(TextShaping.AUTO, LottieTextShaping.modeOf("not json"))
    }

    @Test
    fun `the text a file draws is read from every text layer, precompositions included`() {
        fun textLayer(text: String) = buildJsonObject {
            put("ty", JsonPrimitive(5))
            put("t", buildJsonObject {
                put("d", buildJsonObject {
                    put("k", buildJsonArray {
                        add(buildJsonObject { put("s", buildJsonObject { put("t", JsonPrimitive(text)) }) })
                    })
                })
            })
        }
        val lottie = buildJsonObject {
            put("layers", buildJsonArray {
                add(textLayer("Name"))
                add(buildJsonObject { put("ty", JsonPrimitive(4)) })
            })
            put("assets", buildJsonArray {
                add(buildJsonObject { put("layers", buildJsonArray { add(textLayer(TAMIL)) }) })
            })
        }
        assertEquals(listOf("Name", TAMIL), LottieTextShaping.staticTexts(lottie))
        assertTrue(LottieTextShaping.groupsText(toJson(lottie)), "auto sees the Tamil inside the precomposition")
        assertTrue(
            LottieTextShaping.groupsText(toJson(buildJsonObject {}), listOf(TAMIL)),
            "live text the player puts in counts too",
        )
        assertFalse(LottieTextShaping.groupsText("{"), "a file that does not parse draws as Compottie always has")
    }

    @Test
    fun `the lower-third generator writes its setting into the file`() {
        val tamil = LottieGenConfig(nameText = TAMIL, textShaping = "auto")
        val generated = LottieGenerator.generate(tamil)
        assertEquals(TextShaping.AUTO, LottieTextShaping.modeOf(generated))
        assertTrue(LottieTextShaping.groupsText(toJson(generated)))
        val letters = LottieGenerator.generate(tamil.copy(textShaping = "letters"))
        assertFalse(LottieTextShaping.groupsText(toJson(letters)), "letter by letter is kept even for Tamil")
        assertFalse(LottieTextShaping.groupsText(toJson(LottieGenerator.generate(LottieGenConfig()))))
    }

    @Test
    fun `the band generator writes its setting beside the band's own metadata`() {
        val band = BibleLottieGenerator.generate(BibleLottieGenConfig(textShaping = TextShaping.WHOLE_LINES))
        val cp = band.getValue(LottieTextShaping.METADATA_KEY).jsonObject
        assertEquals("lines", cp.getValue(LottieTextShaping.TEXT_SHAPING).jsonPrimitive.content)
        val kind = cp.getValue(BibleLottieGenerator.METADATA_KIND).jsonPrimitive.content
        assertEquals(BibleLottieGenerator.METADATA_KIND_BAND, kind)
    }

    @Test
    fun `a typewriter reveals whole characters, never a vowel sign without its letter`() {
        val word = "கர்த்தர்"
        val shown = (0..10).map { LottieTextShaping.typedText(word, it / 10f) }
        assertEquals("", shown.first())
        assertEquals(word, shown.last())
        // Every prefix ends on a cluster boundary: nothing is left that is only a mark
        shown.filter { it.isNotEmpty() }.forEach { prefix ->
            assertFalse(Character.getType(prefix.last()) == Character.NON_SPACING_MARK.toInt() && prefix.length == 1)
            assertTrue(word.startsWith(prefix))
        }
        assertEquals("Gr", LottieTextShaping.typedText("Grace", 0.4f))
        assertEquals("Grace", LottieTextShaping.typedText("Grace", 2f), "a fraction past the end shows it all")
    }

    @Test
    fun `a bundled family names its bold file when it has one and its regular cut otherwise`() {
        assertEquals("/fonts/NotoSansTamil-Bold.ttf", FontRegistry.bundledFile("Noto Sans Tamil", bold = true))
        assertEquals("/fonts/NotoSansTamil-Regular.ttf", FontRegistry.bundledFile("Noto Sans Tamil", bold = false))
        assertEquals("/fonts/PatuaOne-Regular.ttf", FontRegistry.bundledFile("Patua One", bold = true))
        assertNull(FontRegistry.bundledFile("Not A Font", bold = false))
    }
}

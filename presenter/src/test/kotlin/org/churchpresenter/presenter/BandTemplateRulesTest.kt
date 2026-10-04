package org.churchpresenter.presenter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BandTemplateRulesTest {

    @Test
    fun `a file with no frames, rate or canvas cannot be played`() {
        val good = LottieTemplateSize(frameRate = 30f, width = 1920f, height = 200f, totalFrames = 90f)
        assertTrue(good.isPlayable)
        assertFalse(good.copy(frameRate = 0f).isPlayable)
        assertFalse(good.copy(totalFrames = 0f).isPlayable)
        assertFalse(good.copy(width = 0f).isPlayable)
        assertFalse(good.copy(height = 0f).isPlayable)
    }

    @Test
    fun `only a Lottie background with a file is a band`() {
        assertTrue(usesBibleLottieBand(BackgroundConfig(Constants.BACKGROUND_LOTTIE, backgroundLottie = "band.json")))
        assertFalse(usesBibleLottieBand(BackgroundConfig(Constants.BACKGROUND_LOTTIE, backgroundLottie = " ")))
        assertFalse(usesBibleLottieBand(BackgroundConfig(Constants.BACKGROUND_COLOR, backgroundLottie = "band.json")))
    }

    @Test
    fun `the number shares the title's row only where both sit in the same place`() {
        val together = SongSettings(
            songNumberPosition = Constants.ABOVE_VERSE, titlePosition = Constants.ABOVE_VERSE,
            songNumberLowerThirdPosition = Constants.ABOVE_VERSE, titleLowerThirdPosition = Constants.ABOVE_VERSE,
            songNumberHorizontalAlignment = Constants.CENTER,
            songNumberLowerThirdHorizontalAlignment = Constants.CENTER,
            songNumberCorner = Constants.NONE, songNumberLowerThirdCorner = Constants.NONE,
        )
        assertTrue(together.numberSharesTitlePosition(SongStyleTarget.FULL_SCREEN))
        assertTrue(together.numberSharesTitlePosition(SongStyleTarget.LOWER_THIRD))
        val apart = together.copy(
            songNumberPosition = Constants.BELOW_VERSE,
            songNumberLowerThirdHorizontalAlignment = Constants.RIGHT,
        )
        assertFalse(apart.numberSharesTitlePosition(SongStyleTarget.FULL_SCREEN))
        assertFalse(apart.numberSharesTitlePosition(SongStyleTarget.LOWER_THIRD))
        val cornered = together.withNumberCorner(lowerThird = false, value = Constants.TOP_LEFT)
        assertFalse(cornered.numberSharesTitlePosition(SongStyleTarget.FULL_SCREEN))
    }

    private fun verse(text: String, abbreviation: String) = SelectedVerse(
        translationFileName = "${abbreviation.lowercase()}.spb",
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    @Test
    fun `a one-slot band stacks every verse and puts their references on one line`() {
        val slots = bibleBandSlots(
            listOf(verse("For God so loved", "KJV"), verse("Ибо так возлюбил", "RST")),
            translations = listOf(BibleTranslationSettings()),
            isKey = false,
            availableSlots = 1,
        )
        assertEquals("For God so loved\nИбо так возлюбил", slots.getValue(BibleLottieTemplate.LAYER_TEXT_1).text)
        assertTrue(slots.getValue(BibleLottieTemplate.LAYER_REFERENCE_1).text.contains("  ·  "))
    }

    @Test
    fun `slots past the verses shown are left empty`() {
        val slots = bibleBandSlots(
            listOf(verse("For God so loved", "KJV"), verse("Ибо так возлюбил", "RST")),
            translations = emptyList(),
            isKey = true,
            availableSlots = 4,
        )
        assertEquals("Ибо так возлюбил", slots.getValue(BibleLottieTemplate.LAYER_TEXT_2).text)
        assertEquals("", slots.getValue(BibleLottieTemplate.LAYER_TEXT_3).text)
        assertEquals("", slots.getValue(BibleLottieTemplate.LAYER_REFERENCE_4).text)
    }

    @Test
    fun `a template's text layers are pointed at the faces asked for, and nothing else is touched`() {
        val json = """{"fr":30,"chars":[{"ch":"A"}],"layers":[
            {"nm":"Text1","t":{"d":{"k":[{"s":{"f":"Old","t":"x"}},"odd"]}}},
            {"nm":"Reference1"},
            {"nm":"Text2","t":{}},
            {"nm":"Logo"},
            "not a layer",
            {"t":{"d":{"k":[]}}}
        ]}"""
        val face = BandFontKey("Inter", bold = true, italic = false)
        val out = Json.parseToJsonElement(
            rewriteTemplateFonts(json, mapOf("Text1" to face, "Reference1" to face, "Text2" to face)),
        ).jsonObject

        assertFalse("chars" in out, "the generator's glyph outlines are dropped")
        val list = out.getValue("fonts").jsonObject.getValue("list").jsonArray
        assertEquals(listOf("Inter-Bold"), list.map { it.jsonObject.getValue("fName").jsonPrimitive.content })
        val text1 = out.getValue("layers").jsonArray[0].jsonObject
        val doc = text1.getValue("t").jsonObject.getValue("d").jsonObject.getValue("k").jsonArray[0]
            .jsonObject.getValue("s").jsonObject
        assertEquals("Inter-Bold", doc.getValue("f").jsonPrimitive.content)
    }
}

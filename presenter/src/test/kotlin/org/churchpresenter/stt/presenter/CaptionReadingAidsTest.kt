package org.churchpresenter.stt.presenter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.churchpresenter.settings.CaptionReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Bionic reading's bold word starts and RSVP's cut down to the flash on screen. */
class CaptionReadingAidsTest {

    /** The parts of [text] bold, by the last weight a span gives each character. */
    private fun boldParts(text: AnnotatedString): String = text.text.indices.joinToString("") { i ->
        val weight = text.spanStyles.filter { i >= it.start && i < it.end && it.item.fontWeight != null }
            .lastOrNull()?.item?.fontWeight
        if (weight == FontWeight.Bold) text.text[i].toString() else if (text.text[i] == ' ') " " else "."
    }

    @Test
    fun `bionic reading bolds the first half of a word and the first letter of a short one`() {
        val ranges = bionicPrefixes("Blessed are the peacemakers")
        val bold = ranges.map { "Blessed are the peacemakers".substring(it.first, it.last + 1) }
        assertEquals(listOf("Bles", "a", "t", "peacem"), bold)
    }

    @Test
    fun `an apostrophe keeps a word whole, and punctuation is never bold`() {
        val text = "don't stop, God."
        assertEquals(listOf("do", "st", "G"), bionicPrefixes(text).map { text.substring(it.first, it.last + 1) })
    }

    @Test
    fun `the rest of each word is regular, even on bold captions, and colours stay`() {
        val coloured = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Blessed ") }
            withStyle(SpanStyle(color = Color.Red)) { append("are") }
        }
        val emphasised = bionicEmphasis(coloured)
        assertEquals("Bles... a..", boldParts(emphasised))
        assertEquals(Color.Red, emphasised.spanStyles.first { it.item.color == Color.Red }.item.color)
    }

    @Test
    fun `an RSVP flash is the newest words on one line, highlights kept`() {
        val text = buildAnnotatedString {
            append("Blessed are\nthe ")
            withStyle(SpanStyle(color = Color.Red)) { append("peacemakers") }
        }
        val flash = rsvpFlash(text, 2)
        assertEquals("the peacemakers", flash.text)
        assertEquals(4 until 15, flash.spanStyles.single().let { it.start until it.end })
        assertEquals("are the peacemakers", rsvpFlash(text, 3).text, "a line break between words becomes a space")
    }

    @Test
    fun `reading aids leave a caption alone when neither is on`() {
        val text = AnnotatedString("Blessed are the peacemakers")
        assertSame(text, withReadingAids(text, flashWords = 0, CaptionReading()))
        assertEquals("peacemakers", withReadingAids(text, flashWords = 1, CaptionReading()).text)
        assertEquals("peacem.....", boldParts(withReadingAids(text, 1, CaptionReading(bionicReading = true))))
    }
}

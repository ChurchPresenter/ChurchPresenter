package org.churchpresenter.sharedui.utils

import androidx.compose.ui.graphics.Color
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.songchords.SecondVoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A song's second-voice markup on its way to the screen: taken out, and its words coloured. */
class SecondVoiceTextTest {

    private val gold = Color(0xFFFFD43B)
    private val inks = VoiceColors(gold, Color(0xFF69DB7C))

    // ── Directives and presentable lines ────────────────────────────────────────

    @Test
    fun `background and voice directives are directives, lyrics and headers are not`() {
        assertTrue(isLyricDirective("[voice: 2]"))
        assertTrue(isLyricDirective("[background: #000000]"))
        assertFalse(isLyricDirective("[Verse 1]"))
        assertFalse(isLyricDirective("2>That saved"))
    }

    @Test
    fun `a voice block becomes voice-2 lines and the directives go`() {
        val lines = listOf("[Verse 1]", "one", "[voice: 2]", "two", "", "three", "[voice: 1]", "four")

        assertEquals(listOf("[Verse 1]", "one", "2>two", "", "2>three", "four"), presentableSongLines(lines))
    }

    @Test
    fun `a voice block ends at the next header`() {
        val lines = listOf("[voice: 2]", "two", "[Chorus]", "main", "[background: #000000]")

        assertEquals(listOf("2>two", "[Chorus]", "main"), presentableSongLines(lines))
    }

    @Test
    fun `a plain lyric line has no markup`() {
        assertEquals("Was blind, but now I see", plainLyricLine("Was blind, but 2>(now I see)"))
    }

    // ── Voiced lines ────────────────────────────────────────────────────────────

    @Test
    fun `a voiced line gives the ranges the second voice sings`() {
        val voiced = voicedLine("Was blind, but 2>(now I see)", Constants.TEXT_TRANSFORM_UPPERCASE)

        assertEquals("WAS BLIND, BUT NOW I SEE", voiced.text)
        assertEquals(listOf(15..23), voiced.voiceRanges.map { it.range })
        assertEquals("NOW I SEE", voiced.text.substring(15..23))
    }

    @Test
    fun `a whole voice-2 line is one range`() {
        assertEquals(
            listOf(0..9),
            voicedLine("2>That saved", Constants.TEXT_TRANSFORM_NONE).voiceRanges.map { it.range },
        )
        assertEquals(emptyList(), voicedLine("plain", Constants.TEXT_TRANSFORM_NONE).voiceRanges.map { it.range })
    }

    @Test
    fun `casing that changes the length draws the line in one colour`() {
        val voiced = voicedLine("Straße 2>(da)", Constants.TEXT_TRANSFORM_UPPERCASE)

        assertEquals("STRASSE DA", voiced.text)
        assertEquals(emptyList(), voiced.voiceRanges.map { it.range })
    }

    // ── Display text ────────────────────────────────────────────────────────────

    @Test
    fun `the display text colours only the second voice's words`() {
        val text = voicedDisplayText("Was blind, but 2>(now I see)", Constants.TEXT_TRANSFORM_NONE, 0f, 0f, inks)

        assertEquals("Was blind, but now I see", text.text)
        val spans = text.spanStyles.filter { it.item.color == gold }
        assertEquals(listOf(15 to 24), spans.map { it.start to it.end })
    }

    @Test
    fun `no colour draws the words without the markup and without a span`() {
        val text = voicedDisplayText("2>That saved", Constants.TEXT_TRANSFORM_UPPERCASE, 0f, 0f, null)

        assertEquals("THAT SAVED", text.text)
        assertTrue(text.spanStyles.none { it.item.color == gold })
    }

    @Test
    fun `a line with no markup is styled as before`() {
        val plain = voicedDisplayText("Amazing grace", Constants.TEXT_TRANSFORM_UPPERCASE, 0f, 0f, inks)

        assertEquals(styledDisplayText("Amazing grace", Constants.TEXT_TRANSFORM_UPPERCASE, 0f, 0f), plain)
    }

    @Test
    fun `a block reads each line's markup on its own`() {
        val text = voicedDisplayText(
            listOf("Amazing grace", "2>That saved"),
            Constants.TEXT_TRANSFORM_NONE,
            0f,
            0f,
            inks,
        )

        assertEquals("Amazing grace\nThat saved", text.text)
        assertEquals(listOf(14 to 24), text.spanStyles.filter { it.item.color == gold }.map { it.start to it.end })
    }

    @Test
    fun `each voice's ranges carry its voice`() {
        val voiced = voicedLine("Lord 2>(Christ) 3>(Lord)", Constants.TEXT_TRANSFORM_NONE)

        assertEquals("Lord Christ Lord", voiced.text)
        assertEquals(
            listOf(VoicedRange(5..10, SecondVoice.SECOND), VoicedRange(12..15, SecondVoice.THIRD)),
            voiced.voiceRanges,
        )
    }

    @Test
    fun `the third voice's words are drawn in its own colour`() {
        val text = voicedDisplayText("2>(yes) 3>(amen)", Constants.TEXT_TRANSFORM_NONE, 0f, 0f, inks)

        assertEquals("yes amen", text.text)
        val colours = text.spanStyles.associate { text.text.substring(it.start, it.end) to it.item.color }
        assertEquals(mapOf("yes" to inks.second, "amen" to inks.third), colours)
    }

    @Test
    fun `VoiceColors has no colour for the main voice`() {
        assertEquals(null, inks.of(SecondVoice.MAIN))
        assertEquals(inks.third, inks.of(SecondVoice.THIRD))
    }

    @Test
    fun `a voice-3 block's lines are written as voice-3 lines until the next header`() {
        assertEquals(
            listOf("[Verse 1]", "3>Amen", "3>Amen", "[Verse 2]", "plain"),
            presentableSongLines(listOf("[Verse 1]", "[voice: 3]", "Amen", "Amen", "[Verse 2]", "plain")),
        )
    }
}

package org.churchpresenter.stt

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the STT tab colours the words the STT server asked it to highlight.
 *
 * `applyHighlighting` builds a per-character colour array and then collapses it into contiguous
 * spans, so what matters is which characters end up which colour and that neighbouring runs of the
 * same colour are not split. Assertions read the colour back per character rather than counting
 * spans, which keeps them independent of how the runs happen to be merged.
 */
class STTHighlightingTest {

    private val base = Color.White
    private val red = Color(0xFFFF0000)
    private val green = Color(0xFF00FF00)

    /** The colour each character was given, so a test can assert on coverage rather than on spans. */
    private fun AnnotatedString.colours(): List<Color> = text.indices.map { index ->
        spanStyles.last { index >= it.start && index < it.end }.item.color
    }

    private fun highlight(
        text: String,
        vararg words: HighlightedWord,
        enabled: Boolean = true,
    ): AnnotatedString = applyHighlighting(text, words.toList(), enabled, base)

    private fun word(
        word: String,
        color: String = "#FF0000",
        caseSensitive: Boolean = false,
        isRegex: Boolean = false,
    ) = HighlightedWord(word = word, color = color, caseSensitive = caseSensitive, isRegex = isRegex)

    @Test
    fun `with highlighting off the whole caption keeps the base colour`() {
        val result = highlight("praise the Lord", word("Lord"), enabled = false)

        assertEquals("praise the Lord", result.text)
        assertTrue(result.colours().all { it == base })
    }

    @Test
    fun `with no words to highlight the whole caption keeps the base colour`() {
        val result = highlight("praise the Lord")

        assertTrue(result.colours().all { it == base })
    }

    @Test
    fun `a matched word is coloured and the rest is not`() {
        val result = highlight("praise the Lord", word("Lord"))

        val colours = result.colours()
        assertEquals("praise the Lord", result.text)
        assertTrue(colours.take(11).all { it == base }, "only the word should change colour")
        assertTrue(colours.drop(11).all { it == red })
    }

    @Test
    fun `matching is case-insensitive by default`() {
        val result = highlight("praise the LORD", word("lord"))

        assertTrue(result.colours().drop(11).all { it == red })
    }

    @Test
    fun `a case-sensitive word does not match a different casing`() {
        val result = highlight("praise the LORD", word("lord", caseSensitive = true))

        assertTrue(result.colours().all { it == base })
    }

    @Test
    fun `only whole words match`() {
        // "or" appears inside "Lord" but is not a word there.
        val result = highlight("Lord", word("or"))

        assertTrue(result.colours().all { it == base })
    }

    @Test
    fun `every occurrence of a word is coloured`() {
        val result = highlight("holy holy holy", word("holy"))

        val colours = result.colours()
        // Every "holy" is coloured; the separating spaces are not part of any match.
        assertTrue("holy holy holy".indices.filter { it % 5 != 4 }.all { colours[it] == red })
        assertTrue("holy holy holy".indices.filter { it % 5 == 4 }.all { colours[it] == base })
    }

    @Test
    fun `two words can take two different colours`() {
        val result = highlight(
            "faith and hope",
            word("faith", color = "#FF0000"),
            word("hope", color = "#00FF00"),
        )

        val colours = result.colours()
        assertEquals(red, colours[0])
        assertEquals(base, colours[6])
        assertEquals(green, colours.last())
    }

    @Test
    fun `a later word wins where two highlights overlap`() {
        val result = highlight(
            "grace",
            word("grace", color = "#FF0000"),
            word("grace", color = "#00FF00"),
        )

        assertTrue(result.colours().all { it == green })
    }

    @Test
    fun `a regex word matches as a pattern`() {
        val result = highlight("Psalm 23", word("\\d+", isRegex = true))

        val colours = result.colours()
        assertTrue(colours.take(6).all { it == base })
        assertTrue(colours.drop(6).all { it == red }, "the number should be the coloured part")
    }

    @Test
    fun `a non-regex word with regex characters is matched literally`() {
        val result = highlight("cost is 5 (approx)", word("(approx)"))

        // Matched as the literal text, not as a group — and nothing blows up.
        assertTrue(result.colours().drop(10).all { it == red })
    }

    @Test
    fun `a blank word is ignored`() {
        val result = highlight("praise", word("  "))

        assertTrue(result.colours().all { it == base })
    }

    @Test
    fun `an invalid regex is skipped without losing the caption`() {
        val result = highlight("praise the Lord", word("[unclosed", isRegex = true))

        assertEquals("praise the Lord", result.text)
        assertTrue(result.colours().all { it == base })
    }

    @Test
    fun `an unparseable colour does not drop the caption`() {
        val result = highlight("praise", word("praise", color = "not a colour"))

        assertEquals("praise", result.text)
    }

    @Test
    fun `an empty caption produces an empty result`() {
        assertEquals("", highlight("", word("Lord")).text)
    }

    @Test
    fun `a word matches next to punctuation`() {
        val result = highlight("Lord, hear us", word("Lord"))

        assertTrue(result.colours().take(4).all { it == red })
        assertEquals(base, result.colours()[4], "the comma is not part of the word")
    }

    @Test
    fun `accented letters count as part of a word`() {
        // UNICODE_CHARACTER_CLASS means "é" is a letter, so "Hosanna" inside "Hosanné" is not a match.
        val result = highlight("Hosanné", word("Hosanna"))

        assertTrue(result.colours().all { it == base })
    }
}

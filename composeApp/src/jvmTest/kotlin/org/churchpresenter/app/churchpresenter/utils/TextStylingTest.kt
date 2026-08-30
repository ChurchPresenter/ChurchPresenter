package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The three per-element settings that reach the text rather than the `TextStyle`. */
class TextStylingTest {

    private val verse = "For God so loved the world"

    @Test
    fun `uppercase raises the whole verse`() {
        assertEquals(
            "FOR GOD SO LOVED THE WORLD",
            applyTextTransform(verse, Constants.TEXT_TRANSFORM_UPPERCASE),
        )
    }

    @Test
    fun `lowercase drops the whole verse`() {
        assertEquals(
            "for god so loved the world",
            applyTextTransform(verse, Constants.TEXT_TRANSFORM_LOWERCASE),
        )
    }

    @Test
    fun `capitalize raises every word`() {
        assertEquals(
            "For God So Loved The World",
            applyTextTransform(verse, Constants.TEXT_TRANSFORM_CAPITALIZE),
        )
    }

    @Test
    fun `capitalize leaves the rest of a word as the translation wrote it`() {
        // Many translations set the divine name that way deliberately; lowercasing the tail would
        // turn it into "Lord".
        assertEquals(
            "The LORD Is My Shepherd",
            applyTextTransform("the LORD is my shepherd", Constants.TEXT_TRANSFORM_CAPITALIZE),
        )
    }

    @Test
    fun `capitalize does not break a word at an apostrophe`() {
        assertEquals("God's Own Son", applyTextTransform("god's own son", Constants.TEXT_TRANSFORM_CAPITALIZE))
    }

    @Test
    fun `none leaves the verse alone`() {
        assertEquals(verse, applyTextTransform(verse, Constants.TEXT_TRANSFORM_NONE))
    }

    @Test
    fun `an unknown transform leaves the verse alone`() {
        // What an older settings file stores, or a newer one this build does not know about.
        assertEquals(verse, applyTextTransform(verse, "something-else"))
    }

    @Test
    fun `underline and strikethrough combine rather than replacing each other`() {
        val combined = combinedTextDecoration(underline = true, strikethrough = true)

        assertTrue(combined.contains(TextDecoration.Underline))
        assertTrue(combined.contains(TextDecoration.LineThrough))
    }

    @Test
    fun `each decoration stands alone`() {
        assertEquals(TextDecoration.Underline, combinedTextDecoration(underline = true, strikethrough = false))
        assertEquals(TextDecoration.LineThrough, combinedTextDecoration(underline = false, strikethrough = true))
        assertEquals(TextDecoration.None, combinedTextDecoration(underline = false, strikethrough = false))
    }

    @Test
    fun `spacing is expressed as a fraction of the em`() {
        // 7 points of tracking at a 70pt face is a tenth of an em, and stays a tenth of an em
        // however far the presenter scales the type.
        assertEquals(0.1f, spacingEm(7, 70))
    }

    @Test
    fun `spacing against a zero font size is no spacing rather than a division by zero`() {
        assertEquals(0f, spacingEm(7, 0))
    }

    @Test
    fun `no word spacing leaves the string unannotated`() {
        val text = styledDisplayText(verse, Constants.TEXT_TRANSFORM_NONE, letterSpacingEm = 0.1f, wordSpacingEm = 0f)

        assertEquals(verse, text.text)
        assertTrue(text.spanStyles.isEmpty(), "nothing to annotate when no word spacing is asked for")
    }

    @Test
    fun `word spacing widens every space and nothing else`() {
        val text = styledDisplayText("a b c", Constants.TEXT_TRANSFORM_NONE, letterSpacingEm = 0f, wordSpacingEm = 0.5f)

        assertEquals(listOf(1 to 2, 3 to 4), text.spanStyles.map { it.start to it.end })
    }

    @Test
    fun `a widened space carries the paragraph tracking as well as the extra`() {
        // A span's letterSpacing replaces the style's rather than adding to it, so the paragraph's
        // own tracking has to be folded in or the spaces come out narrower than the letters.
        val text = styledDisplayText("a b", Constants.TEXT_TRANSFORM_NONE, letterSpacingEm = 0.2f, wordSpacingEm = 0.5f)

        assertEquals(SpanStyle(letterSpacing = 0.7f.em), text.spanStyles.single().item)
    }

    @Test
    fun `the transform is applied before the spaces are found`() {
        val text = styledDisplayText(
            "a b",
            Constants.TEXT_TRANSFORM_UPPERCASE,
            letterSpacingEm = 0f,
            wordSpacingEm = 1f,
        )

        assertEquals("A B", text.text)
        assertEquals(listOf(1 to 2), text.spanStyles.map { it.start to it.end })
    }
}

package org.churchpresenter.songs

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class InsertSnippetTest {

    private fun at(text: String, caret: Int) = TextFieldValue(text, TextRange(caret))

    @Test
    fun `a chord goes in at the caret and leaves it after`() {
        val result = insertSnippet(at("Amazing grace", 7), "[G]", ownLine = false)

        assertEquals("Amazing[G] grace", result.text)
        assertEquals(10, result.selection.start, "the caret follows the chord in")
    }

    @Test
    fun `inserting over a selection replaces it`() {
        val selected = TextFieldValue("Amazing grace", TextRange(0, 7))
        assertEquals("[G] grace", insertSnippet(selected, "[G]", ownLine = false).text)
    }

    @Test
    fun `a section marker is given a line of its own`() {
        val result = insertSnippet(at("first line", 10), "[Verse 2]", ownLine = true)
        assertEquals("first line\n\n[Verse 2]\n", result.text)
    }

    @Test
    fun `a marker at the very start does not open with blank lines`() {
        assertEquals("[Verse 1]\n", insertSnippet(at("", 0), "[Verse 1]", ownLine = true).text)
    }

    @Test
    fun `a marker does not stack blank lines that are already there`() {
        val result = insertSnippet(at("first line\n\n", 12), "[Verse 2]", ownLine = true)
        assertEquals("first line\n\n[Verse 2]\n", result.text)
    }

    @Test
    fun `a marker inserted before existing text keeps that text on its own line`() {
        val result = insertSnippet(at("\nsecond", 0), "[Verse 1]", ownLine = true)
        assertEquals("[Verse 1]\nsecond", result.text)
    }
}

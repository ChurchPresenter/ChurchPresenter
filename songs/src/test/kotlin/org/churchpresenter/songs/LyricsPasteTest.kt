package org.churchpresenter.songs

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsPasteTest {

    @Test
    fun `plain text replaces the selection and leaves the cursor after it`() {
        val pasted = pastedInto(TextFieldValue("one two three", TextRange(4, 7)), "2")

        assertEquals("one 2 three", pasted.text)
        assertEquals(TextRange(5), pasted.selection)
    }

    @Test
    fun `a reversed selection is replaced the same way`() {
        assertEquals("aXd", pastedInto(TextFieldValue("abcd", TextRange(3, 1)), "X").text)
    }

    @Test
    fun `plain text goes into the field and a chord sheet goes to the converter`() {
        var value: TextFieldValue? = null
        var sheet: String? = null
        val chords = "G       C\nAmazing grace\nD       G\nhow sweet the sound"

        pasteLyrics("hello", TextFieldValue("", TextRange(0)), { value = it }, { sheet = it })
        assertEquals("hello", value?.text)
        assertNull(sheet)

        value = null
        pasteLyrics(chords, TextFieldValue(""), { value = it }, { sheet = it })
        assertEquals(chords, sheet)
        assertNull(value)
    }

    @Test
    fun `an empty or unreadable clipboard changes nothing`() {
        var calls = 0
        pasteLyrics(null, TextFieldValue("x"), { calls++ }, { calls++ })
        pasteLyrics("", TextFieldValue("x"), { calls++ }, { calls++ })

        assertEquals(0, calls)
    }
}

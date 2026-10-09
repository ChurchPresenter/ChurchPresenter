package org.churchpresenter.helper.ui

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class WithBoldTest {

    @Test
    fun `the words between the markers are bold and the markers are dropped`() {
        val text = withBold("I'm **not** a chatbot")
        assertEquals("I'm not a chatbot", text.text)
        val bold = text.spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals("not", text.text.substring(bold.start, bold.end))
    }

    @Test
    fun `text with no markers is unchanged`() {
        val text = withBold("Hi, I'm Wick")
        assertEquals("Hi, I'm Wick", text.text)
        assertEquals(0, text.spanStyles.size)
    }
}

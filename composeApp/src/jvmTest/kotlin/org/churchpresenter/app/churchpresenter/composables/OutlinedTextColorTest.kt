package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The outline pass over text built from coloured runs -- captions, a highlighted word (#607).
 *
 * A run's own colour beats the colour handed to `Text`, so the stroke copy was drawn in the fill's
 * colours and the outline colour did nothing. The stroke copy is recoloured run by run instead.
 */
class OutlinedTextColorTest {

    private val captions = buildAnnotatedString {
        withStyle(SpanStyle(color = Color.White)) { append("Grace and ") }
        withStyle(SpanStyle(color = Color.Yellow, fontWeight = FontWeight.Bold)) { append("peace") }
    }

    @Test
    fun `every coloured run is drawn in the outline colour`() {
        val stroke = captions.inColor(Color.Red)
        assertEquals(listOf(Color.Red, Color.Red), stroke.spanStyles.map { it.item.color })
    }

    @Test
    fun `everything else a run sets is kept, so the stroke lines up with the fill`() {
        val stroke = captions.inColor(Color.Red)
        assertEquals(captions.text, stroke.text)
        assertEquals(FontWeight.Bold, stroke.spanStyles[1].item.fontWeight)
        assertEquals(captions.spanStyles.map { it.start to it.end }, stroke.spanStyles.map { it.start to it.end })
    }

    @Test
    fun `text with no coloured runs is handed back as it is`() {
        val plain = buildAnnotatedString { append("Grace and peace") }
        assertSame(plain, plain.inColor(Color.Red))
    }
}

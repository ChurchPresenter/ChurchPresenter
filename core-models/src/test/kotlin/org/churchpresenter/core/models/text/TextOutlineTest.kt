package org.churchpresenter.core.models.text

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextOutlineTest {

    @Test
    fun `an untouched outline draws nothing`() {
        assertFalse(TextOutline().isVisible)
        assertFalse(TextOutline().enabled)
    }

    @Test
    fun `enabling it at a real width makes it visible`() {
        assertTrue(TextOutline(enabled = true).isVisible)
    }

    @Test
    fun `a zero width is off however the flag reads`() {
        assertFalse(
            TextOutline(enabled = true, width = 0).isVisible,
            "a stroke with no width is nothing to draw, and the renderers skip the second pass on it",
        )
    }

    @Test
    fun `the width the field offers is a width that draws`() {
        assertTrue(TextOutline.WIDTH_RANGE.first >= 1, "the range cannot offer an invisible stroke")
        assertTrue(TextOutline.DEFAULT_WIDTH in TextOutline.WIDTH_RANGE)
    }

    @Test
    fun `it round-trips, and a document written before it existed reads back off`() {
        val styled = TextOutline(enabled = true, color = "#FF8800", width = 9)
        assertEquals(styled, Json.decodeFromString<TextOutline>(Json.encodeToString(styled)))
        assertEquals(
            TextOutline(),
            Json.decodeFromString<TextOutline>("{}"),
            "every field defaults, so a settings file from before this shipped draws exactly what it drew",
        )
    }
}

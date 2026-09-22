package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class TabLabelStyleTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a settings file written before the style existed reads as text only`() {
        assertEquals(TabLabelStyle.TEXT, json.decodeFromString<AppSettings>("{}").tabLabelStyle)
    }

    @Test
    fun `each style survives a round-trip under its own name`() {
        for (style in TabLabelStyle.entries) {
            val encoded = json.encodeToString(AppSettings(tabLabelStyle = style))
            assertEquals(style, json.decodeFromString<AppSettings>(encoded).tabLabelStyle)
        }
    }

    @Test
    fun `the style is stored by name so the file stays readable across versions`() {
        val encoded = json.encodeToString(AppSettings(tabLabelStyle = TabLabelStyle.ICONS_AND_TEXT))
        assertEquals(TabLabelStyle.ICONS_AND_TEXT, json.decodeFromString<AppSettings>(encoded).tabLabelStyle)
        assert(encoded.contains("\"tabLabelStyle\":\"ICONS_AND_TEXT\""))
    }

    @Test
    fun `one press steps through every style and then wraps round`() {
        assertEquals(TabLabelStyle.ICONS_AND_TEXT, TabLabelStyle.TEXT.next())
        assertEquals(TabLabelStyle.ICONS, TabLabelStyle.ICONS_AND_TEXT.next())
        assertEquals(TabLabelStyle.TEXT, TabLabelStyle.ICONS.next())
    }
}

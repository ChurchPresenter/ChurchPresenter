package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** The `{tokens}` a saved message asks for, and filling them in. */
class MessageTemplateTest {

    @Test
    fun `each token is asked for once, in the order it first appears`() {
        assertEquals(
            listOf("number", "room"),
            messageTokens("Child #{number} to {room}; #{number} again, { room }"),
        )
    }

    @Test
    fun `text with no tokens, or only empty braces, asks for nothing`() {
        assertEquals(emptyList(), messageTokens("Service starts at 10"))
        assertEquals(emptyList(), messageTokens("Empty {} and { }"))
    }

    @Test
    fun `filling replaces every occurrence and leaves a token with no value as written`() {
        assertEquals(
            "Child #42 to {room}, #42",
            fillMessage("Child #{number} to {room}, #{ number }", mapOf("number" to "42")),
        )
    }

    @Test
    fun `saved messages survive the settings file`() {
        val json = Json { ignoreUnknownKeys = true }
        val settings = AppSettings(messageTemplates = listOf(MessageTemplate("message1", "Nursery", "#{number}", 90)))
        val written = json.encodeToString(AppSettings.serializer(), settings)
        val back = json.decodeFromString(AppSettings.serializer(), written)
        assertEquals(settings.messageTemplates, back.messageTemplates)
        assertEquals(emptyList(), json.decodeFromString(AppSettings.serializer(), "{}").messageTemplates)
    }
}

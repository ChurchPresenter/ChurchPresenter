package org.churchpresenter.server

import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** What a phone is sent in place of a broadcast — see [phoneFrame]. */
class PhoneFrameTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun frame(type: String, payload: String) =
        json.encodeToString(WebSocketMessage.serializer(), WebSocketMessage(type, payload))

    private fun decode(frame: String) = json.decodeFromString(WebSocketMessage.serializer(), frame)

    @Test
    fun `a frame within the limit reaches a phone untouched`() {
        val small = frame(Constants.WS_EVENT_SCHEDULE_UPDATED, """{"items":[],"total":0}""")

        assertSame(small, phoneFrame(small, json))
    }

    @Test
    fun `an oversized frame keeps its type and loses its payload`() {
        // About the size of a large library's catalog: past iOS's 1 MB once Cyrillic is two bytes a letter.
        val catalog = frame(Constants.WS_EVENT_SONGS_UPDATED, "Ангелы в небе ".repeat(70_000))

        val sent = decode(phoneFrame(catalog, json))

        assertEquals(Constants.WS_EVENT_SONGS_UPDATED, sent.type)
        assertEquals("", sent.payload)
    }

    @Test
    fun `an oversized frame that is not a message is left as it is`() {
        val garbage = "x".repeat(600 * 1024)

        assertSame(garbage, phoneFrame(garbage, json))
    }
}

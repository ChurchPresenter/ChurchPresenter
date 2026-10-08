package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Clear groups as the settings file keeps them. */
class ClearGroupTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `clear groups survive the settings file, and a document without them has none`() {
        val settings = AppSettings(
            clearGroups = listOf(
                ClearGroup("clear1", "Clear text", listOf("SLIDE", "MESSAGES")),
                ClearGroup("clear2", "Empty"),
            ),
        )
        val written = json.encodeToString(AppSettings.serializer(), settings)
        assertEquals(settings.clearGroups, json.decodeFromString(AppSettings.serializer(), written).clearGroups)
        assertEquals(emptyList(), json.decodeFromString(AppSettings.serializer(), "{}").clearGroups)
    }
}

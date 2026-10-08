package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Props as the settings file keeps them. */
class PropDefinitionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `props survive the settings file, and a document without them has none`() {
        val settings = AppSettings(
            props = listOf(
                PropDefinition("prop1", "Logo", PropKind.IMAGE, PropCorner.BOTTOM_RIGHT, 10, imagePath = "/logo.png"),
                PropDefinition("prop2", "Start", PropKind.COUNTDOWN, countdownTo = "10:30"),
            ),
        )
        val written = json.encodeToString(AppSettings.serializer(), settings)
        assertEquals(settings.props, json.decodeFromString(AppSettings.serializer(), written).props)
        assertEquals(emptyList(), json.decodeFromString(AppSettings.serializer(), "{}").props)
    }

    @Test
    fun `a new prop is a picture in the top right at the default height`() {
        val prop = PropDefinition("p", "P")
        assertEquals(PropKind.IMAGE, prop.kind)
        assertEquals(PropCorner.TOP_RIGHT, prop.corner)
        assertEquals(DEFAULT_PROP_SIZE_PERCENT, prop.sizePercent)
    }
}

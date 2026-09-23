package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutputScaleModeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a settings file written before the scale button existed reads as fit`() {
        // Fit is what both tabs drew before there was a choice, so an upgrade must not change it.
        val settings = json.decodeFromString<AppSettings>("""{"pictureSettings":{}}""")

        assertEquals(OutputScaleMode.FIT, settings.pictureSettings.scaleMode)
        assertEquals(OutputScaleMode.FIT, settings.mediaScaleMode)
    }

    @Test
    fun `each mode survives a round-trip for pictures and for media, under its own name`() {
        for (mode in OutputScaleMode.entries) {
            val encoded = json.encodeToString(
                AppSettings(pictureSettings = PictureSettings(scaleMode = mode), mediaScaleMode = mode)
            )
            val decoded = json.decodeFromString<AppSettings>(encoded)

            assertEquals(mode, decoded.pictureSettings.scaleMode)
            assertEquals(mode, decoded.mediaScaleMode)
            if (mode != OutputScaleMode.FIT) {
                // The names a Canvas source's scale is saved under, so the two read alike.
                assertTrue(encoded.contains("\"mediaScaleMode\":\"${mode.name}\""), encoded)
            }
        }
    }

    @Test
    fun `one press steps fit, fill, stretch and then wraps back to fit`() {
        val walked = generateSequence(OutputScaleMode.FIT) { it.next() }.take(4).toList()

        assertEquals(
            listOf(OutputScaleMode.FIT, OutputScaleMode.FILL, OutputScaleMode.STRETCH, OutputScaleMode.FIT),
            walked,
        )
    }
}

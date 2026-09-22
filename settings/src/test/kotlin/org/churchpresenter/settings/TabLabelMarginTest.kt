package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabLabelMarginTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a settings file written before the spacing existed reads as normal`() {
        assertEquals(TabLabelMargin.NORMAL, json.decodeFromString<AppSettings>("{}").tabLabelMargin)
    }

    @Test
    fun `each spacing survives a round-trip under its own name`() {
        for (margin in TabLabelMargin.entries) {
            val encoded = json.encodeToString(AppSettings(tabLabelMargin = margin))
            assertEquals(margin, json.decodeFromString<AppSettings>(encoded).tabLabelMargin)
            // The default is not written at all, which is what makes an old file read as normal.
            if (margin != TabLabelMargin.NORMAL) {
                assertTrue(encoded.contains("\"tabLabelMargin\":\"${margin.name}\""), encoded)
            }
        }
    }

    @Test
    fun `one press steps from the smallest to the largest and then wraps round`() {
        val walked = generateSequence(TabLabelMargin.SMALL) { it.next() }.take(TabLabelMargin.entries.size + 1).toList()

        assertEquals(
            listOf(
                TabLabelMargin.SMALL,
                TabLabelMargin.SMALL_NORMAL,
                TabLabelMargin.NORMAL,
                TabLabelMargin.NORMAL_LARGE,
                TabLabelMargin.LARGE,
                TabLabelMargin.SMALL,
            ),
            walked,
        )
    }
}

package org.churchpresenter.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The nudge that puts a top-aligned label on the centre line of the first control beside it.
 *
 * Derived arithmetic rather than a literal, and nothing was reading it — a change to either number
 * silently moves every label that uses it.
 */
class SettingsFieldConstantsTest {

    @Test
    fun `the first-control offset centres a label against a standard field`() {
        // (field height - label height) / 2, which is the only reason the number is what it is.
        assertEquals((42.dp - 20.dp) / 2, SettingRowFirstControlOffset)
        assertEquals(11.dp, SettingRowFirstControlOffset)
    }
}

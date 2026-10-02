@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.SongField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every column the grid can show: its header and the order clicking that header sorts by. The grid
 * only draws the columns that are switched on, so the ones off by default are reached here directly.
 */
class SongTableColumnsTest {

    @Test
    fun `every column has a header of its own`() = runComposeUiTest {
        val labels = mutableMapOf<SongField, String>()
        setContent {
            Themed {
                Column {
                    SongField.entries.forEach { field ->
                        val label = columnLabel(field)
                        labels[field] = label
                        Text(label)
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(SongField.entries.toSet(), labels.keys)
        assertTrue(labels.values.all { it.isNotBlank() }, "no column is drawn without a header")
        assertEquals(labels.size, labels.values.toSet().size, "no two columns share a header")
    }

    @Test
    fun `clicking a column's header sorts by that column`() {
        SongField.entries.forEach { field ->
            assertEquals(field.name, field.sortColumn().name, "$field sorts by its own column")
        }
    }
}

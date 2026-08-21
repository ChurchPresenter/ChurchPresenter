package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.CompanionSatelliteSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The chip row used to pick among several [CompanionSatelliteSettings] connections targeting the
 * same placement. Covers rendering of every connection, the click callback, and the
 * empty-list/no-selection edge cases.
 */
@OptIn(ExperimentalTestApi::class)
class CompanionConnectionChipRowTest {

    private fun connection(id: String, name: String) = CompanionSatelliteSettings(id = id, name = name)

    @Test
    fun `every connection's name renders as a chip`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CompanionConnectionChipRow(
                    connections = listOf(connection("a", "Left Sidebar"), connection("b", "Right Sidebar")),
                    selectedId = "a",
                    onSelect = {},
                )
            }
        }
        onNodeWithText("Left Sidebar").assertExists()
        onNodeWithText("Right Sidebar").assertExists()
    }

    @Test
    fun `an empty connection list renders no chips without error`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CompanionConnectionChipRow(connections = emptyList(), selectedId = null, onSelect = {})
            }
        }
        onNodeWithText("Left Sidebar").assertDoesNotExist()
    }

    @Test
    fun `clicking a chip invokes onSelect with that connection's id`() = runComposeUiTest {
        var selected: String? = null
        setContent {
            MaterialTheme {
                CompanionConnectionChipRow(
                    connections = listOf(connection("a", "Left Sidebar"), connection("b", "Right Sidebar")),
                    selectedId = "a",
                    onSelect = { selected = it },
                )
            }
        }
        onNodeWithText("Right Sidebar").performClick()
        assertEquals("b", selected)
    }

    @Test
    fun `no selectedId still renders every chip without error`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CompanionConnectionChipRow(
                    connections = listOf(connection("a", "Left Sidebar")),
                    selectedId = null,
                    onSelect = {},
                )
            }
        }
        onNodeWithText("Left Sidebar").assertExists()
    }

    @Test
    fun `a selectedId matching no connection still renders every chip without error`() = runComposeUiTest {
        var selected: String? = null
        setContent {
            MaterialTheme {
                CompanionConnectionChipRow(
                    connections = listOf(connection("a", "Left Sidebar")),
                    selectedId = "no-such-id",
                    onSelect = { selected = it },
                )
            }
        }
        onNodeWithText("Left Sidebar").assertExists()
        onNodeWithText("Left Sidebar").performClick()
        assertEquals("a", selected)
    }
}

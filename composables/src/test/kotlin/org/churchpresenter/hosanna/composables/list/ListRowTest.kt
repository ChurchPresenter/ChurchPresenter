@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.defaults.ListDefaults
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test
import kotlin.test.assertEquals

class ListRowTest {

    private val events = mutableListOf<String>()

    @Composable
    private fun Row(name: String, selected: Boolean = false, keep: Boolean = false, enabled: Boolean = true) {
        ListRow(
            name, { events += name }, Modifier.width(420.dp),
            selected = selected, keepActions = keep, enabled = enabled,
            actions = { RowAction(testIcon(), "Delete $name", { events += "delete $name" }, destructive = true) },
        )
    }

    @Test
    fun `a click reaches the row and actions wait for hover`() = runComposeUiTest {
        setContent { HosannaSurface { Row("Amazing Grace") } }
        onNodeWithContentDescription("Delete Amazing Grace").assertDoesNotExist()
        onNodeWithText("Amazing Grace").performClick()
        onNodeWithText("Amazing Grace").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithContentDescription("Delete Amazing Grace").performClick()
        waitForIdle()
        assertEquals(listOf("Amazing Grace", "delete Amazing Grace"), events)
    }

    @Test
    fun `selected and kept rows show their actions without hover`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    Row("Picked", selected = true)
                    Row("Starred", keep = true)
                    Row("Plain")
                }
            }
        }
        onNodeWithText("Picked").assertIsSelected()
        onNodeWithContentDescription("Delete Picked").assertExists()
        onNodeWithContentDescription("Delete Starred").assertExists()
        onNodeWithContentDescription("Delete Plain").assertDoesNotExist()
    }

    @Test
    fun `a disabled row ignores clicks`() = runComposeUiTest {
        setContent { HosannaSurface { Row("Offline", enabled = false) } }
        onNodeWithText("Offline").assertIsNotEnabled().performClick()
        waitForIdle()
        assertEquals(emptyList(), events)
    }

    @Test
    fun `rows grow with their lines and show their parts`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    ListRow("One", {})
                    ListRow("Two", {}, supporting = "Hymnal", trailing = { ListMeta("4 verses") })
                    ListRow("Three", {}, supporting = "A long question", threeLine = true, liveTag = "LIVE")
                }
            }
        }
        onNodeWithText("One").assertHeightIsAtLeast(ListDefaults.OneLine)
        onNodeWithText("Two").assertHeightIsAtLeast(ListDefaults.TwoLine)
        onNodeWithText("Three").assertHeightIsAtLeast(ListDefaults.ThreeLine)
        onNodeWithText("4 verses").assertExists()
        onNodeWithText("LIVE").assertExists()
    }
}

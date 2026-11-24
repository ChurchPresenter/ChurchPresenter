@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.list

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.defaults.ListDefaults
import org.churchpresenter.hosanna.composables.model.NavigationItem
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationListTest {

    private val books = listOf("Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth")
        .map { NavigationItem(it.lowercase(), it) }

    @Test
    fun `clicking a row selects it`() = runComposeUiTest {
        var selected by mutableStateOf<String?>(null)
        setContent { HosannaSurface { NavigationList(books, selected, { selected = it }, Modifier.width(200.dp)) } }
        onNodeWithText("Exodus").performClick()
        waitForIdle()
        assertEquals("exodus", selected)
        onNodeWithText("Exodus").assertIsSelected().assertHeightIsAtLeast(ListDefaults.NavigationRow)
    }

    @Test
    fun `arrows, Home, End and page keys move the selection`() = runComposeUiTest {
        var selected by mutableStateOf<String?>("exodus")
        setContent {
            HosannaSurface {
                NavigationList(books, selected, { selected = it }, Modifier.width(200.dp).testTag("books"))
            }
        }
        val list = onNodeWithTag("books")
        list.requestFocus()
        list.performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals("leviticus", selected)
        list.performKeyInput { pressKey(Key.DirectionUp) }
        assertEquals("exodus", selected)
        list.performKeyInput { pressKey(Key.MoveEnd) }
        assertEquals("ruth", selected)
        list.performKeyInput { pressKey(Key.MoveHome) }
        assertEquals("genesis", selected)
        list.performKeyInput { pressKey(Key.PageDown) }
        assertEquals("ruth", selected)
    }

    @Test
    fun `typing jumps to the first match`() = runComposeUiTest {
        var selected by mutableStateOf<String?>("genesis")
        setContent {
            HosannaSurface {
                NavigationList(books, selected, { selected = it }, Modifier.width(200.dp).testTag("books"))
            }
        }
        val list = onNodeWithTag("books")
        list.requestFocus()
        list.performKeyInput { pressKey(Key.J) }
        assertEquals("joshua", selected)
        list.performKeyInput { pressKey(Key.U) }
        assertEquals("judges", selected)
    }

    @Test
    fun `the selected row scrolls into view`() = runComposeUiTest {
        var selected by mutableStateOf<String?>("genesis")
        setContent {
            HosannaSurface {
                NavigationList(books, selected, { selected = it }, Modifier.width(200.dp).height(100.dp))
            }
        }
        onNodeWithText("Ruth").assertDoesNotExist()
        selected = "ruth"
        waitForIdle()
        onNodeWithText("Ruth").assertIsSelected()
    }
}

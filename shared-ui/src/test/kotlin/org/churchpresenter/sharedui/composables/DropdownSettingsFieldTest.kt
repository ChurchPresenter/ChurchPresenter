package org.churchpresenter.sharedui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DropdownSettingsFieldTest {

    private val options = listOf("Top", "Middle", "Bottom")

    @Test
    fun `the field shows its value and its label in capitals`() = runComposeUiTest {
        setContent { MaterialTheme { DropdownSettingsField("Middle", options, {}, label = "Position") } }
        onNodeWithText("Middle").assertExists()
        onNodeWithText("POSITION").assertExists()
    }

    @Test
    fun `a field with no label shows only its value`() = runComposeUiTest {
        setContent { MaterialTheme { DropdownSettingsField("Middle", options, {}) } }
        onNodeWithText("Middle").assertExists()
        assertEquals(0, onAllNodesWithText("POSITION").fetchSemanticsNodes().size)
    }

    @Test
    fun `clicking the field lists every option`() = runComposeUiTest {
        setContent { MaterialTheme { DropdownSettingsField("Middle", options, {}) } }
        onNodeWithText("Middle").performClick()
        waitForIdle()
        onNodeWithText("Top").assertExists()
        onNodeWithText("Bottom").assertExists()
    }

    @Test
    fun `picking an option reports it and closes the list`() = runComposeUiTest {
        var value by mutableStateOf("Middle")
        setContent { MaterialTheme { DropdownSettingsField(value, options, { value = it }) } }
        onNodeWithText("Middle").performClick()
        waitForIdle()
        onNodeWithText("Bottom").performClick()
        waitForIdle()
        assertEquals("Bottom", value)
        assertEquals(0, onAllNodesWithText("Top").fetchSemanticsNodes().size)
    }

    @Test
    fun `a fixed width is the width the field takes`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSettingsField("Middle", options, {}, modifier = Modifier.testTag("field"), width = 220.dp)
            }
        }
        val width = onNodeWithTag("field").fetchSemanticsNode().size.width
        assertEquals((220 * density.density).toInt(), width)
    }

    @Test
    fun `a measured width never drops below its minimum`() = runComposeUiTest {
        var width = 0.dp
        setContent { MaterialTheme { width = rememberDropdownWidthFor(listOf("A"), min = 160.dp, max = 280.dp) } }
        waitForIdle()
        assertEquals(160.dp, width)
    }

    @Test
    fun `a measured width never grows past its maximum`() = runComposeUiTest {
        var width = 0.dp
        setContent {
            MaterialTheme {
                width = rememberDropdownWidthFor(listOf("An extraordinarily long option name ".repeat(5)), max = 280.dp)
            }
        }
        waitForIdle()
        assertEquals(280.dp, width)
    }

    @Test
    fun `no options measure as the minimum`() = runComposeUiTest {
        var width = 0.dp
        setContent { MaterialTheme { width = rememberDropdownWidthFor(emptyList(), min = 100.dp) } }
        waitForIdle()
        assertEquals(100.dp, width)
    }

    @Test
    fun `a width between the bounds follows the widest option`() = runComposeUiTest {
        var narrow: Dp = 0.dp
        var wide: Dp = 0.dp
        setContent {
            MaterialTheme {
                narrow = rememberDropdownWidthFor(listOf("Short option"), min = 0.dp, max = 1000.dp)
                wide = rememberDropdownWidthFor(
                    listOf("Short option", "A noticeably longer option"),
                    min = 0.dp,
                    max = 1000.dp,
                )
            }
        }
        waitForIdle()
        assertTrue(wide > narrow, "wide $wide should exceed narrow $narrow")
    }
}

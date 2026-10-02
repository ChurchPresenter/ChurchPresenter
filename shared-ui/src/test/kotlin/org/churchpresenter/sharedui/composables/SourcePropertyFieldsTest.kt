package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class SourcePropertyFieldsTest {

    private fun ComposeUiTest.input(index: Int = 0): SemanticsNodeInteraction =
        onAllNodes(hasSetTextAction())[index]

    @Test
    fun `a text field reports every edit as it is typed`() = runComposeUiTest {
        var name by mutableStateOf("Title")
        setContent { PropertyTextField("Name", name) { name = it } }
        input().performTextReplacement("Welcome")
        waitForIdle()
        assertEquals("Welcome", name)
    }

    @Test
    fun `a commit field reports nothing while typing and the trimmed text on Done`() = runComposeUiTest {
        val commits = mutableListOf<String>()
        setContent { PropertyCommitTextField("Host", "10.0.0.1") { commits += it } }
        input().performTextReplacement(" 10.0.0.2 ")
        waitForIdle()
        assertEquals(emptyList(), commits)
        input().performImeAction()
        waitForIdle()
        assertEquals(listOf("10.0.0.2"), commits)
    }

    @Test
    fun `a commit field that was not changed commits nothing on Done`() = runComposeUiTest {
        val commits = mutableListOf<String>()
        setContent { PropertyCommitTextField("Host", "10.0.0.1") { commits += it } }
        input().performImeAction()
        waitForIdle()
        assertEquals(emptyList(), commits)
    }

    @Test
    fun `a commit field commits when focus leaves it`() = runComposeUiTest {
        val commits = mutableListOf<String>()
        setContent {
            Column {
                PropertyCommitTextField("Host", "10.0.0.1") { commits += it }
                PropertyTextField("Other", "") {}
            }
        }
        input(0).requestFocus()
        input(0).performTextReplacement("server.local")
        input(1).requestFocus()
        waitForIdle()
        assertEquals(listOf("server.local"), commits)
    }

    @Test
    fun `a commit field that loses focus unchanged commits nothing`() = runComposeUiTest {
        val commits = mutableListOf<String>()
        setContent {
            Column {
                PropertyCommitTextField("Host", "10.0.0.1") { commits += it }
                PropertyTextField("Other", "") {}
            }
        }
        input(0).requestFocus()
        input(1).requestFocus()
        waitForIdle()
        assertEquals(emptyList(), commits)
    }

    @Test
    fun `a whole number field applies a typed number when focus leaves it`() = runComposeUiTest {
        var size by mutableIntStateOf(48)
        setContent {
            Column {
                PropertyIntField("Size", size, 8..500) { size = it }
                PropertyTextField("Other", "") {}
            }
        }
        input(0).requestFocus()
        input(0).performTextReplacement("72")
        input(1).requestFocus()
        waitForIdle()
        assertEquals(72, size)
    }

    @Test
    fun `a whole number field does not report a value that did not change`() = runComposeUiTest {
        var reports = 0
        setContent { PropertyIntField("Size", 48, 8..500) { reports++ } }
        input().performTextReplacement(" 48 ")
        input().performImeAction()
        waitForIdle()
        assertEquals(0, reports)
        input().assertTextContains("48")
    }

    @Test
    fun `a decimal field applies a number on Done`() = runComposeUiTest {
        var value by mutableFloatStateOf(1f)
        setContent { PropertyFloatField("Scale", value) { value = it } }
        input().performTextReplacement("2.5")
        input().performImeAction()
        waitForIdle()
        assertEquals(2.5f, value)
    }

    @Test
    fun `a decimal field puts the old value back when the text is not a number`() = runComposeUiTest {
        var reported: Float? = null
        setContent { PropertyFloatField("Scale", 1.5f) { reported = it } }
        input().performTextReplacement("wide")
        input().performImeAction()
        waitForIdle()
        assertNull(reported)
        input().assertTextContains("1.500")
    }

    @Test
    fun `a decimal field applies a number when focus leaves it`() = runComposeUiTest {
        var value by mutableFloatStateOf(1f)
        setContent {
            Column {
                PropertyFloatField("Scale", value) { value = it }
                PropertyTextField("Other", "") {}
            }
        }
        input(0).requestFocus()
        input(0).performTextReplacement("0.75")
        input(1).requestFocus()
        waitForIdle()
        assertEquals(0.75f, value)
    }

    @Test
    fun `a decimal field that loses focus holding nonsense restores its value`() = runComposeUiTest {
        var reported: Float? = null
        setContent {
            Column {
                PropertyFloatField("Scale", 2f) { reported = it }
                PropertyTextField("Other", "") {}
            }
        }
        input(0).requestFocus()
        input(0).performTextReplacement("?")
        input(1).requestFocus()
        waitForIdle()
        assertNull(reported)
        input(0).assertTextContains("2.000")
    }

    @Test
    fun `a zero-to-one slider reads as a percentage`() = runComposeUiTest {
        setContent { PropertySlider("Opacity", 0.4f, 0f, 1f) {} }
        onNodeWithText("Opacity").assertExists()
        onNodeWithText("40%").assertExists()
    }

    @Test
    fun `any other slider reads as a decimal`() = runComposeUiTest {
        setContent { PropertySlider("Rotation", 12.5f, -180f, 180f) {} }
        onNodeWithText("12.50").assertExists()
    }

    @Test
    fun `a slider with an input applies a typed value clamped into its range`() = runComposeUiTest {
        var value by mutableFloatStateOf(50f)
        setContent { PropertySliderWithInput("Size", value, 0f, 100f) { value = it } }
        input().performTextReplacement("250")
        input().performImeAction()
        waitForIdle()
        assertEquals(100f, value)
    }

    @Test
    fun `a slider with an input puts its number back when the text is not one`() = runComposeUiTest {
        var reported: Float? = null
        setContent { PropertySliderWithInput("Size", 30f, 0f, 100f) { reported = it } }
        input().performTextReplacement("lots")
        input().performImeAction()
        waitForIdle()
        assertNull(reported)
        input().assertTextContains("30")
    }

    @Test
    fun `a slider with an input applies a typed value when focus leaves it`() = runComposeUiTest {
        var value by mutableFloatStateOf(10f)
        setContent {
            Column {
                PropertySliderWithInput("Size", value, 0f, 100f) { value = it }
                PropertyTextField("Other", "") {}
            }
        }
        input(0).requestFocus()
        input(0).performTextReplacement("-5")
        input(1).requestFocus()
        waitForIdle()
        assertEquals(0f, value)
    }

    @Test
    fun `a slider with an input shows its unit beside the number`() = runComposeUiTest {
        setContent { PropertySliderWithInput("Size", 30f, 0f, 100f, suffix = "px") {} }
        onNodeWithText("px").assertExists()
    }

    @Test
    fun `a slider with an input and no unit shows only the number`() = runComposeUiTest {
        setContent { PropertySliderWithInput("Size", 30f, 0f, 100f) {} }
        assertEquals(1, onAllNodes(hasText("30")).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithText("px").fetchSemanticsNodes().size)
    }

    @Test
    fun `an opacity label names the color first`() = runComposeUiTest {
        var label = ""
        setContent { label = opacityLabel(Res.string.color) }
        waitForIdle()
        assertEquals("Color: Opacity", label)
    }
}

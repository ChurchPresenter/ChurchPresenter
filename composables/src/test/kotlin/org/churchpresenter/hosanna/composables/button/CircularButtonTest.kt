@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.settle
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CircularButtonTest {

    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    @Test
    fun `the three sizes are round and match the spec`() {
        val expected = mapOf(
            IconButtonSize.Large to IconButtonMetrics(44.dp, 20.dp, 18.dp, null),
            IconButtonSize.Medium to IconButtonMetrics(36.dp, 16.dp, 16.dp, null),
            IconButtonSize.Small to IconButtonMetrics(28.dp, 14.dp, 14.dp, null),
        )
        expected.forEach { (size, metrics) -> assertEquals(metrics, CircularButtonDefaults.metrics(size)) }
    }

    @Test
    fun `each size draws a square of its side`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    IconButtonSize.entries.forEach { size -> CircularButton(testIcon(), size.name, {}, size = size) }
                }
            }
        }
        IconButtonSize.entries.forEach { size ->
            val side = CircularButtonDefaults.metrics(size).side
            onNodeWithContentDescription(size.name).assertWidthIsEqualTo(side).assertHeightIsEqualTo(side)
        }
    }

    @Test
    fun `a click reaches onClick`() = runComposeUiTest {
        var clicks = 0
        setContent { HosannaSurface { CircularButton(testIcon(), "Play", { clicks++ }) } }
        onNodeWithContentDescription("Play").assert(button).performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `a disabled or loading circular button cannot be clicked`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var clicks = 0
        setContent {
            HosannaSurface {
                Column {
                    CircularButton(testIcon(), "Stop", { clicks++ }, enabled = false)
                    CircularButton(testIcon(), "Record", { clicks++ }, loading = true)
                }
            }
        }
        settle()
        onNodeWithContentDescription("Stop").assertIsNotEnabled().performClick()
        onNodeWithContentDescription("Record").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `a circular toggle flips its state and reports it`() = runComposeUiTest {
        var on by mutableStateOf(false)
        setContent { HosannaSurface { CircularToggleButton(testIcon(), "Loop", on, { on = it }) } }
        val node = onNodeWithContentDescription("Loop").assert(checkbox)
        node.assertIsOff().performClick()
        waitForIdle()
        assertTrue(on)
        node.assertIsOn()
    }

    @Test
    fun `a group holds its buttons and each stays reachable`() = runComposeUiTest {
        val pressed = mutableListOf<String>()
        setContent {
            HosannaSurface {
                CircularButtonGroup {
                    listOf("Back", "Play", "Forward").forEach { name ->
                        CircularButton(testIcon(), name, { pressed += name })
                    }
                }
            }
        }
        listOf("Back", "Play", "Forward").forEach { onNodeWithContentDescription(it).performClick() }
        waitForIdle()
        assertEquals(listOf("Back", "Play", "Forward"), pressed)
    }
}

@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.hosanna.composables.defaults.CheckboxDefaults
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckboxTest {

    private val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    @Test
    fun `clicking anywhere on the row toggles it`() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent {
            HosannaSurface {
                Checkbox(checked, { checked = it }, "Show chords", description = "Only on the stage display")
            }
        }
        onNodeWithText("Show chords").assert(checkbox).assertIsOff()
        onNodeWithText("Only on the stage display").performMouseInput { click(center) }
        waitForIdle()
        assertTrue(checked)
        onNodeWithText("Show chords").assertIsOn().performMouseInput { click(center) }
        waitForIdle()
        assertFalse(checked)
    }

    @Test
    fun `Space toggles a focused checkbox`() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent { HosannaSurface { Checkbox(checked, { checked = it }, "Match case", compact = true) } }
        onNodeWithText("Match case").requestFocus()
        onNodeWithText("Match case").performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()
        assertTrue(checked)
    }

    @Test
    fun `a parent checkbox reports mixed and checks everything on click`() = runComposeUiTest {
        var children by mutableStateOf(listOf(true, false))
        setContent {
            HosannaSurface {
                val state = when {
                    children.all { it } -> ToggleableState.On
                    children.none { it } -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                }
                TriStateCheckbox(state, { children = children.map { state != ToggleableState.On } }, "All screens")
            }
        }
        onNodeWithText("All screens")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate))
            .performMouseInput { click(center) }
        waitForIdle()
        assertEquals(listOf(true, true), children)
        onNodeWithText("All screens").assertIsOn()
    }

    @Test
    fun `a disabled checkbox ignores clicks`() = runComposeUiTest {
        var changes = 0
        setContent { HosannaSurface { Checkbox(true, { changes++ }, "Locked", enabled = false) } }
        onNodeWithText("Locked").assertIsNotEnabled().performMouseInput { click(center) }
        waitForIdle()
        assertEquals(0, changes)
    }

    @Test
    fun `the trailing text and description show, rows keep their height`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    Checkbox(true, {}, "Lobby screen", trailing = "6 items")
                    Checkbox(false, {}, "Compact", compact = true)
                    Checkbox(false, {}, "Described", description = "A line under it")
                }
            }
        }
        onNodeWithText("6 items").assertExists()
        onNodeWithText("A line under it").assertExists()
        onNodeWithText("Lobby screen").assertHeightIsAtLeast(CheckboxDefaults.MinHeight)
        onNodeWithText("Compact").assertHeightIsEqualTo(CheckboxDefaults.CompactHeight)
    }
}

@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.defaults.SwitchDefaults
import org.churchpresenter.hosanna.composables.model.SwitchLayout
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.settle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwitchTest {

    private val switch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    private val busy = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun `clicking anywhere on the row flips it`() = runComposeUiTest {
        var on by mutableStateOf(false)
        setContent {
            HosannaSurface { Switch(on, { on = it }, "Quick upload", description = "Send each slide to the ATEM") }
        }
        onNodeWithText("Quick upload").assert(switch).assertIsOff()
        onNodeWithText("Send each slide to the ATEM").performMouseInput { click(center) }
        waitForIdle()
        assertTrue(on)
        onNodeWithText("Quick upload").assertIsOn().performMouseInput { click(center) }
        waitForIdle()
        assertFalse(on)
    }

    @Test
    fun `Space flips a focused switch`() = runComposeUiTest {
        var on by mutableStateOf(false)
        setContent { HosannaSurface { Switch(on, { on = it }, "Loop", compact = true) } }
        onNodeWithText("Loop").requestFocus()
        onNodeWithText("Loop").performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()
        assertTrue(on)
    }

    @Test
    fun `a loading switch shows the spinner and ignores clicks`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var loading by mutableStateOf(true)
        var changes = 0
        setContent { HosannaSurface { Switch(false, { changes++ }, "Companion server", isLoading = loading) } }
        settle()
        onNodeWithText("Companion server").assertIsNotEnabled().performMouseInput { click(center) }
        onAllNodes(busy).assertCountEquals(1)
        loading = false
        settle()
        onNodeWithText("Companion server").assertIsEnabled()
        onAllNodes(busy).assertCountEquals(0)
        assertEquals(0, changes)
    }

    @Test
    fun `a disabled switch ignores clicks`() = runComposeUiTest {
        var changes = 0
        setContent { HosannaSurface { Switch(true, { changes++ }, "Upload clips", enabled = false) } }
        onNodeWithText("Upload clips").assertIsNotEnabled().performMouseInput { click(center) }
        waitForIdle()
        assertEquals(0, changes)
    }

    @Test
    fun `each layout keeps its height and shows its text`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    Switch(true, {}, "Form", description = "Under the label")
                    Switch(true, {}, "Row", Modifier.width(320.dp), layout = SwitchLayout.Row)
                    Switch(true, {}, "Inline", layout = SwitchLayout.Inline, description = "Hidden inline")
                    Switch(true, {}, "Compact", compact = true, description = "Hidden compact")
                    Switch(true, {}, "Remote", Modifier.width(320.dp), trailing = "3 phones", live = true)
                }
            }
        }
        onNodeWithText("Under the label").assertExists()
        onNodeWithText("Hidden inline").assertDoesNotExist()
        onNodeWithText("Hidden compact").assertDoesNotExist()
        onNodeWithText("3 phones").assertExists()
        onNodeWithText("Form").assertHeightIsAtLeast(SwitchDefaults.MinHeight)
        onNodeWithText("Row").assertHeightIsAtLeast(SwitchDefaults.RowHeight)
        onNodeWithText("Compact").assertHeightIsEqualTo(SwitchDefaults.CompactHeight)
    }
}

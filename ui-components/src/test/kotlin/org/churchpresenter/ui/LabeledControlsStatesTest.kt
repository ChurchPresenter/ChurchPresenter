package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three labelled controls in each of the states a settings tab actually puts them in.
 *
 * The existing suite covers the plain on/off case. What is exercised here is everything a caller
 * can vary — disabled, a supporting line, the control moved to the trailing edge — because each is
 * a separate branch inside the composable and a settings row that silently ignores `enabled` would
 * let an operator change a setting the app has decided they may not.
 */
@OptIn(ExperimentalTestApi::class)
class LabeledControlsStatesTest {

    @Test
    fun `a checked checkbox reports itself on`() = runComposeUiTest {
        setContent { MaterialTheme { LabeledCheckbox(checked = true, onCheckedChange = {}, label = "Loop") } }
        onAllNodes(hasClickAction()).onFirst().assertIsOn()
    }

    @Test
    fun `an unchecked checkbox reports itself off and toggles on`() = runComposeUiTest {
        var state = false
        setContent { MaterialTheme { LabeledCheckbox(state, { state = it }, label = "Loop") } }
        onAllNodes(hasClickAction()).onFirst().assertIsOff().performClick()
        assertTrue(state)
    }

    @Test
    fun `a disabled checkbox refuses the click`() = runComposeUiTest {
        var state = false
        setContent { MaterialTheme { LabeledCheckbox(state, { state = it }, "Loop", enabled = false) } }
        onAllNodes(hasClickAction()).onFirst().assertIsNotEnabled()
        assertFalse(state)
    }

    @Test
    fun `a supporting line is drawn under the label`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LabeledCheckbox(true, {}, "Loop", supporting = "Repeats the folder when it ends")
            }
        }
        onNodeWithText("Loop").assertIsDisplayed()
        onNodeWithText("Repeats the folder when it ends").assertIsDisplayed()
    }

    @Test
    fun `the control can sit at the trailing edge`() = runComposeUiTest {
        var state = false
        setContent {
            MaterialTheme {
                LabeledCheckbox(state, { state = it }, "Loop", controlAtEnd = true, spacing = 8.dp)
            }
        }
        onAllNodes(hasClickAction()).onFirst().performClick()
        assertTrue(state, "moving the control must not detach it from the row's click")
    }

    @Test
    fun `a radio button reports selection and reselects`() = runComposeUiTest {
        var picked = ""
        setContent {
            MaterialTheme {
                LabeledRadioButton(selected = true, onClick = { picked = "a" }, label = "Option A")
            }
        }
        onAllNodes(hasClickAction()).onFirst().performClick()
        assertEquals("a", picked, "clicking the already-selected option still reports it")
    }

    @Test
    fun `an unselected disabled radio button refuses the click`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                LabeledRadioButton(false, { clicked = true }, "Option B", enabled = false)
            }
        }
        onAllNodes(hasClickAction()).onFirst().assertIsNotEnabled()
        assertFalse(clicked)
    }

    @Test
    fun `a switch toggles both ways`() = runComposeUiTest {
        var on = true
        setContent { MaterialTheme { LabeledSwitch(on, { on = it }, "Live") } }
        onAllNodes(hasClickAction()).onFirst().assertIsOn().performClick()
        assertFalse(on)
    }

    @Test
    fun `a disabled switch reports itself disabled`() = runComposeUiTest {
        setContent { MaterialTheme { LabeledSwitch(false, {}, "Live", enabled = false) } }
        onAllNodes(hasClickAction()).onFirst().assertIsNotEnabled().assertIsOff()
    }

    @Test
    fun `a switch carries a supporting line too`() = runComposeUiTest {
        setContent {
            MaterialTheme { LabeledSwitch(true, {}, "Live", supporting = "Sends to the output") }
        }
        onNodeWithText("Sends to the output").assertIsDisplayed()
    }
}

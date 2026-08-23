@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The labelled checkbox, radio button and switch.
 *
 * Every test here clicks **the label**, never the control, because that is the whole point. The
 * pattern these replace put a bare `Text` next to a `Checkbox` in a plain `Row`: identical on screen,
 * but only the small square responded. Everyone aims at the word — it is the bigger target and the one
 * that reads as the thing being chosen — and nothing happened. 64 sites in this codebase had that
 * shape, and only two did not.
 *
 * The other half is accessibility, and it is asserted the same way: control and label are **one**
 * node with a role and a state, so a screen reader announces "Enabled, checked" rather than an
 * unlabelled checkbox followed by a stray line of text.
 */
class LabeledControlsTest {

    // ── Checkbox ────────────────────────────────────────────────────────────────

    @Test
    fun `clicking the label toggles the checkbox`() = runComposeUiTest {
        var checked = false
        setContent {
            MaterialTheme {
                LabeledCheckbox(checked = checked, onCheckedChange = { checked = it }, label = "Enabled")
            }
        }

        onNodeWithText("Enabled").performClick()

        assertTrue(checked, "the label must be part of the control, not decoration beside it")
    }

    @Test
    fun `the label and its checkbox are one node carrying the state`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LabeledCheckbox(checked = true, onCheckedChange = { }, label = "Enabled")
            }
        }

        // One node, checked, addressable by its label — which is what a screen reader reads out.
        onNodeWithText("Enabled").assertIsOn()
    }

    @Test
    fun `an unchecked box reports itself off`() = runComposeUiTest {
        setContent {
            MaterialTheme { LabeledCheckbox(checked = false, onCheckedChange = { }, label = "Enabled") }
        }

        onNodeWithText("Enabled").assertIsOff()
    }

    @Test
    fun `a disabled checkbox reports itself disabled and ignores the label`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                LabeledCheckbox(
                    checked = false,
                    onCheckedChange = { clicks++ },
                    label = "Enabled",
                    enabled = false,
                )
            }
        }

        onNodeWithText("Enabled").assertIsNotEnabled()
        onNodeWithText("Enabled").performClick()
        // Disabled has to mean both: says so, and does nothing. A control that silently swallows the
        // press looks broken rather than unavailable.
        assertEquals(0, clicks)
    }

    @Test
    fun `toggling twice returns to where it started`() = runComposeUiTest {
        val seen = mutableListOf<Boolean>()
        // Compose state, not a plain local: without recomposition the second click would still see
        // the original value and report `true` twice.
        var checked by mutableStateOf(false)
        setContent {
            MaterialTheme {
                LabeledCheckbox(
                    checked = checked,
                    onCheckedChange = { checked = it; seen += it },
                    label = "Enabled",
                )
            }
        }

        onNodeWithText("Enabled").performClick()
        waitForIdle()
        onNodeWithText("Enabled").performClick()
        waitForIdle()

        assertEquals(listOf(true, false), seen, "each click must report the value it moves to")
    }

    // ── Radio button ────────────────────────────────────────────────────────────

    @Test
    fun `clicking the label selects the radio button`() = runComposeUiTest {
        var picked = ""
        setContent {
            MaterialTheme {
                LabeledRadioButton(selected = false, onClick = { picked = "still" }, label = "Still")
            }
        }

        onNodeWithText("Still").performClick()

        assertEquals("still", picked)
    }

    @Test
    fun `a selected radio button reports itself selected`() = runComposeUiTest {
        setContent {
            MaterialTheme { LabeledRadioButton(selected = true, onClick = { }, label = "Still") }
        }

        onNodeWithText("Still").assertIsSelected()
    }

    @Test
    fun `a disabled radio button ignores the label`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                LabeledRadioButton(selected = false, onClick = { clicks++ }, label = "Still", enabled = false)
            }
        }

        onNodeWithText("Still").assertIsNotEnabled()
        onNodeWithText("Still").performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `re-selecting an already selected radio still reports the click`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                LabeledRadioButton(selected = true, onClick = { clicks++ }, label = "Still")
            }
        }

        onNodeWithText("Still").performClick()

        // Selection is the caller's to decide — swallowing the click here would stop a group from
        // being able to treat re-picking as a deliberate action.
        assertEquals(1, clicks)
    }

    // ── Switch ──────────────────────────────────────────────────────────────────

    @Test
    fun `clicking the label toggles the switch`() = runComposeUiTest {
        var on = false
        setContent {
            MaterialTheme { LabeledSwitch(checked = on, onCheckedChange = { on = it }, label = "Auto-start") }
        }

        onNodeWithText("Auto-start").performClick()

        assertTrue(on)
    }

    @Test
    fun `a switch carries its on state on the same node as its label`() = runComposeUiTest {
        setContent {
            MaterialTheme { LabeledSwitch(checked = true, onCheckedChange = { }, label = "Auto-start") }
        }

        onNodeWithText("Auto-start").assertIsOn()
    }

    @Test
    fun `a disabled switch ignores the label`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                LabeledSwitch(
                    checked = false,
                    onCheckedChange = { clicks++ },
                    label = "Auto-start",
                    enabled = false,
                )
            }
        }

        onNodeWithText("Auto-start").assertIsNotEnabled()
        onNodeWithText("Auto-start").performClick()
        assertEquals(0, clicks)
    }

    // ── Layout: the control pushed to the right edge ────────────────────────────

    @Test
    fun `a checkbox laid out at the end is still one click target on its label`() = runComposeUiTest {
        // `controlAtEnd` reverses the row so the label sits left and the box is pushed right, which
        // is the settings-row look. The label must stay part of the control through that swap.
        var checked = false
        setContent {
            MaterialTheme {
                LabeledCheckbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    label = "Enabled",
                    controlAtEnd = true,
                )
            }
        }

        onNodeWithText("Enabled").assertIsOff()
        onNodeWithText("Enabled").performClick()

        assertTrue(checked)
    }

    @Test
    fun `a radio button laid out at the end still reports the click on its label`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                LabeledRadioButton(
                    selected = false,
                    onClick = { clicks++ },
                    label = "Still",
                    controlAtEnd = true,
                )
            }
        }

        onNodeWithText("Still").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a switch laid out at the end behaves the same as the other two`() = runComposeUiTest {
        var on = false
        setContent {
            MaterialTheme {
                LabeledSwitch(
                    checked = on,
                    onCheckedChange = { on = it },
                    label = "Auto-start",
                    controlAtEnd = true,
                )
            }
        }

        onNodeWithText("Auto-start").performClick()

        assertTrue(on)
    }

    // ── The supporting second line ──────────────────────────────────────────────

    @Test
    fun `a checkbox's supporting line is shown under its label`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LabeledCheckbox(
                    checked = false,
                    onCheckedChange = { },
                    label = "Enabled",
                    supporting = "Applies the next time the app starts",
                )
            }
        }

        onNodeWithText("Enabled").assertExists()
        onNodeWithText("Applies the next time the app starts").assertExists()
    }

    @Test
    fun `the supporting line is description, not a second click target`() = runComposeUiTest {
        // The row owns the single click. If the description published its own target, a press on it
        // would toggle nothing while looking like part of the control.
        var clicks = 0
        setContent {
            MaterialTheme {
                LabeledCheckbox(
                    checked = false,
                    onCheckedChange = { clicks++ },
                    label = "Enabled",
                    supporting = "Applies at next start",
                )
            }
        }

        onNodeWithText("Enabled").performClick()

        assertEquals(1, clicks, "one press on the row must produce exactly one call")
    }

    @Test
    fun `a radio button carries a supporting line too`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LabeledRadioButton(
                    selected = true,
                    onClick = { },
                    label = "Still",
                    supporting = "One image, held until you change it",
                )
            }
        }

        onNodeWithText("Still").assertIsSelected()
        onNodeWithText("One image, held until you change it").assertExists()
    }

    @Test
    fun `a supporting line survives being pushed to the end together with the control`() =
        runComposeUiTest {
            // Both optional shapes at once — the layout settings rows actually use.
            var checked = false
            setContent {
                MaterialTheme {
                    LabeledCheckbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        label = "Enabled",
                        supporting = "Applies at next start",
                        controlAtEnd = true,
                    )
                }
            }

            onNodeWithText("Applies at next start").assertExists()
            onNodeWithText("Enabled").performClick()

            assertTrue(checked)
        }

    @Test
    fun `a disabled row with a supporting line is inert and says so`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                LabeledSwitch(
                    checked = false,
                    onCheckedChange = { clicks++ },
                    label = "Auto-start",
                    supporting = "Requires permission",
                    controlAtEnd = true,
                    enabled = false,
                )
            }
        }

        onNodeWithText("Auto-start").assertIsNotEnabled()
        onNodeWithText("Auto-start").performClick()

        assertEquals(0, clicks)
    }

    // ── The shape being replaced ────────────────────────────────────────────────

    @Test
    fun `there is exactly one click target, not two competing ones`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                LabeledCheckbox(checked = false, onCheckedChange = { clicks++ }, label = "Enabled")
            }
        }

        // The control is passed `onCheckedChange = null` precisely so it does not publish a second
        // click target nested inside the row's. One press must produce one call, not two.
        onNodeWithText("Enabled").performClick()

        assertEquals(1, clicks)
    }
}

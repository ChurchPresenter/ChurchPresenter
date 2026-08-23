package org.churchpresenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The numeric settings field used by every bounded setting in the app — font sizes, ports,
 * opacities, margins, timer durations.
 *
 * Its two arrow buttons are the only place the `range` clamp is applied on the way up: they refuse
 * a step that would leave the range, where the text field accepts any digits and simply declines to
 * report an out-of-range value. Both paths are exercised here.
 *
 * The layout is asserted as well as the behaviour. The arrow column previously measured to a
 * zero-size rect — the text column carries a `fillMaxWidth()` child and had no `weight`, so in the
 * `Row` it was measured against the full available width and consumed all of it, leaving nothing for
 * the 20dp arrow column. That made both arrows undeliverable, in a test and in the real UI alike, so
 * `both arrows are laid out at a clickable size` is a regression guard, not a formality.
 */
@OptIn(ExperimentalTestApi::class)
class NumberSettingsTextFieldTest {

    /** What the field currently shows; its contents are `EditableText`, never `Text`. */
    private fun ComposeUiTest.shownValue(): String? =
        onAllNodes(hasSetTextAction())[0].fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.EditableText)?.text

    @Test
    fun `both arrows are laid out at a clickable size`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.width(200.dp)) {
                    NumberSettingsTextField(label = "Font size", initialText = 8, range = 1..40, onValueChange = { })
                }
            }
        }

        val up = onNodeWithContentDescription("Increment").fetchSemanticsNode().size
        val down = onNodeWithContentDescription("Decrement").fetchSemanticsNode().size
        assertTrue(up.width > 0 && up.height > 0, "the up arrow must occupy space to be clickable, was $up")
        assertTrue(down.width > 0 && down.height > 0, "the down arrow must occupy space to be clickable, was $down")
    }

    @Test
    fun `the up arrow steps the value and reports it`() = runComposeUiTest {
        var reported: Int? = null
        setContent {
            MaterialTheme {
                NumberSettingsTextField(initialText = 8, range = 1..40, onValueChange = { reported = it })
            }
        }

        onNodeWithContentDescription("Increment").performClick()

        assertEquals(9, reported, "clicking up must report the stepped value to the caller")
        assertEquals("9", shownValue(), "the field must show the stepped value")
    }

    @Test
    fun `the down arrow steps the value and reports it`() = runComposeUiTest {
        var reported: Int? = null
        setContent {
            MaterialTheme {
                NumberSettingsTextField(initialText = 8, range = 1..40, onValueChange = { reported = it })
            }
        }

        onNodeWithContentDescription("Decrement").performClick()

        assertEquals(7, reported, "clicking down must report the stepped value to the caller")
        assertEquals("7", shownValue(), "the field must show the stepped value")
    }

    @Test
    fun `the up arrow refuses to step past the top of the range`() = runComposeUiTest {
        var reported: Int? = null
        setContent {
            MaterialTheme {
                NumberSettingsTextField(initialText = 40, range = 1..40, onValueChange = { reported = it })
            }
        }

        onNodeWithContentDescription("Increment").performClick()

        assertNull(reported, "a step out of the range must not reach the setting")
        assertEquals("40", shownValue(), "a refused step must leave the shown value alone")
    }

    @Test
    fun `the down arrow refuses to step below the bottom of the range`() = runComposeUiTest {
        var reported: Int? = null
        setContent {
            MaterialTheme {
                NumberSettingsTextField(initialText = 1, range = 1..40, onValueChange = { reported = it })
            }
        }

        onNodeWithContentDescription("Decrement").performClick()

        assertNull(reported, "a step out of the range must not reach the setting")
        assertEquals("1", shownValue(), "a refused step must leave the shown value alone")
    }

    @Test
    fun `repeated steps accumulate`() = runComposeUiTest {
        var reported: Int? = null
        setContent {
            MaterialTheme {
                NumberSettingsTextField(initialText = 8, range = 1..40, onValueChange = { reported = it })
            }
        }

        repeat(3) { onNodeWithContentDescription("Increment").performClick() }
        onNodeWithContentDescription("Decrement").performClick()

        assertEquals(10, reported, "each click must step from the value the previous one left behind")
        assertEquals("10", shownValue())
    }

    @Test
    fun `a typed in-range number is reported`() = runComposeUiTest {
        var reported: Int? = null
        setContent {
            MaterialTheme {
                NumberSettingsTextField(initialText = 8, range = 1..40, onValueChange = { reported = it })
            }
        }

        onAllNodes(hasSetTextAction())[0].performTextReplacement("24")

        assertEquals(24, reported, "a typed value inside the range must reach the setting")
    }

    @Test
    fun `a typed out-of-range number is shown but not reported`() = runComposeUiTest {
        var reported: Int? = null
        setContent {
            MaterialTheme {
                NumberSettingsTextField(initialText = 8, range = 1..40, onValueChange = { reported = it })
            }
        }

        onAllNodes(hasSetTextAction())[0].performTextReplacement("99")

        assertNull(reported, "a typed value outside the range must not reach the setting")
        assertEquals("99", shownValue(), "the operator has to see what they typed in order to correct it")
    }

    @Test
    fun `clearing the field falls back to zero rather than crashing`() = runComposeUiTest {
        var reported: Int? = null
        setContent {
            MaterialTheme {
                NumberSettingsTextField(initialText = 8, range = 0..40, onValueChange = { reported = it })
            }
        }

        onAllNodes(hasSetTextAction())[0].performTextReplacement("")

        // `toIntOrNull() ?: 0` — with 0 inside the range it is a legitimate value and is reported.
        assertEquals(0, reported, "an emptied field must resolve to 0, not throw")
        assertEquals("0", shownValue())
    }

    @Test
    fun `the label is shown uppercased above the value`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                NumberSettingsTextField(label = "Font size", initialText = 8, range = 1..40, onValueChange = { })
            }
        }

        onNodeWithText("FONT SIZE").assertExists("the label names which setting the number belongs to")
    }
}

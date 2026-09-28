package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A canvas layer's font-size field (found on Windows): it used to clamp on every keystroke, so
 * clearing it and typing 36 was cut to 8 at the "3" and came out as 368, and deleting down to 5
 * jumped to 8. It now takes the whole number and checks it once, on Enter or on leaving the field.
 */
@OptIn(ExperimentalTestApi::class)
class PropertyIntFieldTest {

    private fun field(start: Int, block: ComposeUiTest.(get: () -> Int) -> Unit) =
        runComposeUiTest {
            var size by mutableIntStateOf(start)
            setContent { PropertyIntField("Font size", size, 8..500) { size = it } }
            block { size }
        }

    @Test
    fun `a number typed in full is kept as typed and applied on Enter`() = field(48) { size ->
        val input = onNode(hasSetTextAction())
        input.performTextReplacement("3")
        assertEquals(48, size(), "nothing is applied halfway through typing")
        input.performTextReplacement("36")
        input.performImeAction()
        waitForIdle()
        assertEquals(36, size())
    }

    @Test
    fun `a number below the range is raised to its floor only once committed`() = field(48) { size ->
        val input = onNode(hasSetTextAction())
        input.performTextReplacement("5")
        assertEquals(48, size())
        input.performImeAction()
        waitForIdle()
        assertEquals(8, size())
    }

    @Test
    fun `a number above the range is lowered to its ceiling`() = field(48) { size ->
        onNode(hasSetTextAction()).apply {
            performTextReplacement("9000")
            performImeAction()
        }
        waitForIdle()
        assertEquals(500, size())
    }

    @Test
    fun `something that is not a number leaves the size as it was`() = field(48) { size ->
        onNode(hasSetTextAction()).apply {
            performTextReplacement("big")
            performImeAction()
        }
        waitForIdle()
        assertEquals(48, size())
    }
}

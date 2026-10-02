package org.churchpresenter.app.churchpresenter.extensions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** `conditional`, the `Modifier` extension that applies a modifier only while a condition holds. */
@OptIn(ExperimentalTestApi::class)
class ModifierTest {

    /**
     * The node's measured width. Taken as right-minus-left rather than through `DpRect.width`,
     * whose simple name collides with the layout modifier of the same name used below.
     */
    private fun SemanticsNodeInteraction.measuredWidth() = getBoundsInRoot().let { it.right - it.left }

    // ── conditional ─────────────────────────────────────────────────────────────

    @Test
    fun `a condition that holds applies the modifier`() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("box").conditional(true) { width(40.dp) }.size(100.dp))
        }

        assertEquals(40.dp, onNodeWithTag("box").measuredWidth())
    }

    @Test
    fun `a condition that does not hold leaves the modifier chain alone`() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("box").conditional(false) { width(40.dp) }.size(100.dp))
        }

        assertEquals(
            100.dp,
            onNodeWithTag("box").measuredWidth(),
            "the block must not be applied at all, not applied and then undone",
        )
    }

    @Test
    fun `the condition is re-read when it changes`() = runComposeUiTest {
        var narrow by mutableStateOf(false)
        setContent {
            Box(Modifier.testTag("box").conditional(narrow) { width(40.dp) }.size(100.dp))
        }
        assertEquals(100.dp, onNodeWithTag("box").measuredWidth())

        narrow = true
        waitForIdle()

        assertEquals(40.dp, onNodeWithTag("box").measuredWidth(), "a state-driven condition has to recompose")
    }
}

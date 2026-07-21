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
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two `Modifier` extensions the dialogs are built with.
 *
 * `errorShake` is the app's only feedback for a rejected entry — a wrong API key, an invalid host —
 * on controls that show no error text of their own: the field simply jerks side to side. Two things
 * about it are load-bearing and invisible from the call site. It has to come to rest at zero, or the
 * field stays permanently offset inside its dialog; and it has to call back exactly once when it
 * finishes, because the caller uses that to lower the `trigger` flag again — a callback that never
 * arrives leaves the flag raised and the field cannot be shaken a second time, so the next wrong
 * entry gets no feedback at all.
 *
 * These run in a real composition. The clock is driven by hand rather than by wall time, so the
 * ~500 ms of animation costs nothing and the assertions land on frames chosen deliberately instead
 * of on whatever the machine happened to render.
 */
@OptIn(ExperimentalTestApi::class)
class ModifierTest {

    /**
     * The node's measured width. Taken as right-minus-left rather than through `DpRect.width`,
     * whose simple name collides with the layout modifier of the same name used below.
     */
    private fun SemanticsNodeInteraction.measuredWidth() = getBoundsInRoot().let { it.right - it.left }

    /** How far the node has been pushed from the left edge of the root. */
    private fun SemanticsNodeInteraction.offsetFromLeft() = getBoundsInRoot().left

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

    // ── errorShake ──────────────────────────────────────────────────────────────

    @Test
    fun `a field that has not been rejected sits still`() = runComposeUiTest {
        var finished = 0
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.errorShake(trigger = false) { finished++ }) {
                Box(Modifier.testTag("field").size(100.dp))
            }
        }

        mainClock.advanceTimeBy(1_000)

        assertEquals(0.dp, onNodeWithTag("field").offsetFromLeft(), "an untriggered field must not drift")
        assertEquals(0, finished, "and must not report an animation it never ran")
    }

    @Test
    fun `a rejected field is visibly displaced`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.errorShake(trigger = true) {}) {
                Box(Modifier.testTag("field").size(100.dp))
            }
        }

        // Frame by frame through the first legs, which travel 0 → +5 → -5.
        val seen = (1..20).map {
            mainClock.advanceTimeByFrame()
            onNodeWithTag("field").offsetFromLeft()
        }

        assertTrue(
            seen.any { it > 0.dp } && seen.any { it < 0.dp },
            "the whole point is that the operator sees the field move both ways: $seen",
        )
    }

    @Test
    fun `a rejected field comes back to rest exactly where it started`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.errorShake(trigger = true) {}) {
                Box(Modifier.testTag("field").size(100.dp))
            }
        }

        mainClock.advanceTimeBy(5_000)

        assertEquals(
            0.dp,
            onNodeWithTag("field").offsetFromLeft(),
            "a field left offset stays crooked in its dialog for the rest of the session",
        )
    }

    @Test
    fun `the caller is told once when the shake is over`() = runComposeUiTest {
        var finished = 0
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.errorShake(trigger = true) { finished++ }) {
                Box(Modifier.testTag("field").size(100.dp))
            }
        }

        mainClock.advanceTimeBy(30)
        assertEquals(0, finished, "not until it has actually finished")

        mainClock.advanceTimeBy(5_000)

        assertEquals(1, finished, "the caller lowers its trigger flag on this — twice would lower it twice")
    }

    @Test
    fun `a second rejection shakes the field again`() = runComposeUiTest {
        // The real cycle: raise the flag, shake, the callback lowers it, raise it again.
        var trigger by mutableStateOf(false)
        var finished = 0
        mainClock.autoAdvance = false
        setContent {
            Box(
                Modifier.errorShake(trigger) {
                    finished++
                    trigger = false
                },
            ) {
                Box(Modifier.testTag("field").size(100.dp))
            }
        }

        trigger = true
        mainClock.advanceTimeBy(5_000)
        assertEquals(1, finished)

        trigger = true
        mainClock.advanceTimeBy(5_000)

        assertEquals(2, finished, "a second wrong key has to get the same feedback as the first")
        assertEquals(0.dp, onNodeWithTag("field").offsetFromLeft())
    }
}

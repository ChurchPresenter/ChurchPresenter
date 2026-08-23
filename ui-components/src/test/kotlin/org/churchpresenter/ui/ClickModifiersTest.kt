package org.churchpresenter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The low-level `Modifier.pointerInput`-based click handlers that stand in for
 * `Modifier.clickable`/`combinedClickable` where a specific [androidx.compose.ui.input.pointer.PointerEventPass]
 * is required: [initialPassClickable]/[initialPassCombinedClickable] fire before a child's Main-pass
 * gesture can consume the event; [finalPassClickable]/[finalPassCombinedClickable] fire only when no
 * child consumed it.
 *
 * Two things are deliberately not covered:
 * - The double-click threshold reads `System.currentTimeMillis()` directly, not an injectable or
 *   virtualized clock, so only the "fires a double-click" side (two clicks the test executes back
 *   to back, reliably well under the 300ms window) is exercised. Proving the *other* side — two
 *   clicks spaced more than 300ms apart register as two singles — would need a real 300ms+ wait,
 *   which the project's no-real-delay rule for tests rules out.
 * - `initialPassClickable` existing specifically to beat a Main-pass scroll gesture (the ARM Mac
 *   bug in its own doc comment) isn't reconstructed here. Compose's guarantee that the Initial pass
 *   reaches a node before the Main pass is a framework invariant, not this file's own logic; the
 *   part that *is* this file's logic — that the modifier fires `onClick` on a real click — is
 *   covered directly instead.
 */
@OptIn(ExperimentalTestApi::class)
class ClickModifiersTest {

    // ── initialPassClickable ───────────────────────────────────────────────────────────────────

    @Test
    fun `initialPassClickable invokes onClick when clicked`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                Box(Modifier.testTag("box").size(50.dp).initialPassClickable { clicked = true })
            }
        }
        onNodeWithTag("box").performClick()
        assertTrue(clicked, "clicking must invoke onClick")
    }

    @Test
    fun `nested initialPassClickable elements let the outer one consume first`() = runComposeUiTest {
        var outerClicked = false
        var innerClicked = false
        setContent {
            MaterialTheme {
                Box(
                    Modifier.testTag("outer").size(100.dp).initialPassClickable { outerClicked = true },
                    contentAlignment = Alignment.TopStart,
                ) {
                    Box(Modifier.testTag("inner").size(20.dp).initialPassClickable { innerClicked = true })
                }
            }
        }
        onNodeWithTag("inner").performClick()
        assertTrue(outerClicked, "the Initial pass reaches the outer element first, so it must consume the click")
        assertFalse(innerClicked, "the inner element must see the click as already consumed")
    }

    // ── finalPassClickable ─────────────────────────────────────────────────────────────────────

    @Test
    fun `finalPassClickable invokes onClick when clicking empty space in the container`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                Box(Modifier.testTag("box").size(50.dp).finalPassClickable { clicked = true })
            }
        }
        onNodeWithTag("box").performClick()
        assertTrue(clicked, "clicking the container itself must invoke its onClick")
    }

    @Test
    fun `finalPassClickable does not fire when a child consumes the click`() = runComposeUiTest {
        var outerClicked = false
        var innerClicked = false
        setContent {
            MaterialTheme {
                Box(
                    Modifier.testTag("outer").size(100.dp).finalPassClickable { outerClicked = true },
                    contentAlignment = Alignment.TopStart,
                ) {
                    Box(Modifier.testTag("inner").size(20.dp).clickable { innerClicked = true })
                }
            }
        }
        onNodeWithTag("inner").performClick()
        assertTrue(innerClicked, "the child's own click handler must still fire")
        assertFalse(outerClicked, "the container must not also fire when a child already consumed the click")
    }

    // ── initialPassCombinedClickable ───────────────────────────────────────────────────────────

    @Test
    fun `initialPassCombinedClickable invokes onClick on a single click`() = runComposeUiTest {
        var clickCount = 0
        var doubleCount = 0
        setContent {
            MaterialTheme {
                Box(
                    Modifier.testTag("box").size(50.dp).initialPassCombinedClickable(
                        onClick = { clickCount++ },
                        onDoubleClick = { doubleCount++ },
                    )
                )
            }
        }
        onNodeWithTag("box").performClick()
        assertEquals(1, clickCount, "a lone click must invoke onClick")
        assertEquals(0, doubleCount, "a lone click must not invoke onDoubleClick")
    }

    @Test
    fun `initialPassCombinedClickable invokes onDoubleClick on the second of two rapid clicks`() =
        runComposeUiTest {
            var clickCount = 0
            var doubleCount = 0
            setContent {
                MaterialTheme {
                    Box(
                        Modifier.testTag("box").size(50.dp).initialPassCombinedClickable(
                            onClick = { clickCount++ },
                            onDoubleClick = { doubleCount++ },
                        )
                    )
                }
            }
            onNodeWithTag("box").performClick()
            onNodeWithTag("box").performClick()
            assertEquals(1, clickCount, "the second, rapid click must not also count as a plain click")
            assertEquals(1, doubleCount, "two rapid clicks must register as one double-click")
        }

    @Test
    fun `initialPassCombinedClickable falls back to onClick when no onDoubleClick is given`() = runComposeUiTest {
        var clickCount = 0
        setContent {
            MaterialTheme {
                Box(
                    Modifier.testTag("box").size(50.dp).initialPassCombinedClickable(onClick = { clickCount++ })
                )
            }
        }
        onNodeWithTag("box").performClick()
        onNodeWithTag("box").performClick()
        assertEquals(2, clickCount, "without a double-click handler, every click must still invoke onClick")
    }

    @Test
    fun `nested initialPassCombinedClickable elements let the outer one consume first`() = runComposeUiTest {
        var outerClicked = false
        var innerClicked = false
        setContent {
            MaterialTheme {
                Box(
                    Modifier.testTag("outer").size(100.dp)
                        .initialPassCombinedClickable(onClick = { outerClicked = true }),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Box(
                        Modifier.testTag("inner").size(20.dp)
                            .initialPassCombinedClickable(onClick = { innerClicked = true })
                    )
                }
            }
        }
        onNodeWithTag("inner").performClick()
        assertTrue(outerClicked, "the Initial pass reaches the outer element first, so it must consume the click")
        assertFalse(innerClicked, "the inner element must see the click as already consumed")
    }

    // ── finalPassCombinedClickable ─────────────────────────────────────────────────────────────

    @Test
    fun `finalPassCombinedClickable invokes onClick when clicking empty space in the container`() =
        runComposeUiTest {
            var clicked = false
            setContent {
                MaterialTheme {
                    Box(Modifier.testTag("box").size(50.dp).finalPassCombinedClickable(onClick = { clicked = true }))
                }
            }
            onNodeWithTag("box").performClick()
            assertTrue(clicked, "clicking the container itself must invoke its onClick")
        }

    @Test
    fun `finalPassCombinedClickable does not fire when a child consumes the click`() = runComposeUiTest {
        var outerClicked = false
        var innerClicked = false
        setContent {
            MaterialTheme {
                Box(
                    Modifier.testTag("outer").size(100.dp)
                        .finalPassCombinedClickable(onClick = { outerClicked = true }),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Box(Modifier.testTag("inner").size(20.dp).clickable { innerClicked = true })
                }
            }
        }
        onNodeWithTag("inner").performClick()
        assertTrue(innerClicked, "the child's own click handler must still fire")
        assertFalse(outerClicked, "the container must not also fire when a child already consumed the click")
    }

    @Test
    fun `finalPassCombinedClickable invokes onDoubleClick on the second of two rapid clicks`() = runComposeUiTest {
        var clickCount = 0
        var doubleCount = 0
        setContent {
            MaterialTheme {
                Box(
                    Modifier.testTag("box").size(50.dp).finalPassCombinedClickable(
                        onClick = { clickCount++ },
                        onDoubleClick = { doubleCount++ },
                    )
                )
            }
        }
        onNodeWithTag("box").performClick()
        onNodeWithTag("box").performClick()
        assertEquals(1, clickCount, "the second, rapid click must not also count as a plain click")
        assertEquals(1, doubleCount, "two rapid clicks must register as one double-click")
    }

    @Test
    fun `finalPassCombinedClickable falls back to onClick when no onDoubleClick is given`() = runComposeUiTest {
        var clickCount = 0
        setContent {
            MaterialTheme {
                Box(
                    Modifier.testTag("box").size(50.dp).finalPassCombinedClickable(onClick = { clickCount++ })
                )
            }
        }
        onNodeWithTag("box").performClick()
        onNodeWithTag("box").performClick()
        assertEquals(2, clickCount, "without a double-click handler, every click must still invoke onClick")
    }
}

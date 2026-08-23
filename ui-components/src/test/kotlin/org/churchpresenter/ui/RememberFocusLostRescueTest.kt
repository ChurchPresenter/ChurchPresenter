package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The composable that hands a tab its focus-rescue state.
 *
 * A null host window is the headless case and also the real one for any caller that has not been
 * given the AWT frame — everything the state does to AWT is a no-op then, but the composable itself
 * still has to hand back a usable state rather than fall over.
 *
 * The identity assertion is the point of `remember`: a new state on every recomposition would reset
 * `tabHasFocus` continuously and leave the banner flickering.
 */
@OptIn(ExperimentalTestApi::class)
class RememberFocusLostRescueTest {

    @Test
    fun `it returns a state even with no host window`() = runComposeUiTest {
        lateinit var state: FocusLostRescueState
        setContent {
            MaterialTheme { state = rememberFocusLostRescue(null, FocusRequester()) }
        }
        waitForIdle()
        assertTrue(state.active, "active defaults to true")
    }

    @Test
    fun `the active flag is passed straight through`() = runComposeUiTest {
        lateinit var state: FocusLostRescueState
        setContent {
            MaterialTheme { state = rememberFocusLostRescue(null, FocusRequester(), active = false) }
        }
        waitForIdle()
        assertFalse(state.active)
        assertFalse(state.bannerVisible, "an inactive tab never shows the banner")
    }

    @Test
    fun `the same state survives recomposition`() = runComposeUiTest {
        val bump = mutableStateOf(0)
        val requester = FocusRequester()
        val seen = mutableListOf<FocusLostRescueState>()
        setContent {
            MaterialTheme {
                bump.value
                seen += rememberFocusLostRescue(null, requester)
            }
        }
        waitForIdle()
        bump.value = 1
        waitForIdle()
        assertTrue(seen.size >= 2, "the composable has to have run twice for this to mean anything")
        assertSame(seen.first(), seen.last(), "a new state each pass would reset the focus tracking")
    }

    @Test
    fun `a changed focus requester produces a fresh state`() = runComposeUiTest {
        val requester = mutableStateOf(FocusRequester())
        val seen = mutableListOf<FocusLostRescueState>()
        setContent { MaterialTheme { seen += rememberFocusLostRescue(null, requester.value) } }
        waitForIdle()
        requester.value = FocusRequester()
        waitForIdle()
        assertTrue(seen.first() !== seen.last(), "the state is keyed on the requester it will call")
    }

    @Test
    fun `the state tracks the window focus the composition reports`() = runComposeUiTest {
        lateinit var state: FocusLostRescueState
        setContent { MaterialTheme { state = rememberFocusLostRescue(null, FocusRequester()) } }
        waitForIdle()
        // Whatever the test window reports, the state must agree with it rather than assume.
        state.onFocusChanged(true)
        assertTrue(state.tabHasFocus)
        state.onFocusChanged(false)
        assertFalse(state.tabHasFocus)
    }

    @Test
    fun `rescue on a windowless state does not throw`() = runComposeUiTest {
        lateinit var state: FocusLostRescueState
        setContent { MaterialTheme { state = rememberFocusLostRescue(null, FocusRequester()) } }
        waitForIdle()
        state.onPointerPress()
        state.restoreAwtFocusOwner()
        waitForIdle()
        assertTrue(true, "both are no-ops without a window, and must stay no-ops")
    }
}

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.awt.Canvas
import java.awt.Container
import java.awt.Panel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [FocusLostRescueState]'s pure decision logic — [FocusLostRescueState.bannerVisible] and the two
 * event-recording methods — plus [findAwtCanvas]'s tree search. The actual AWT window-focus
 * healing ([FocusLostRescueState.resyncWedgedWindowFocus]/`restoreAwtFocusOwner`) is real,
 * timing-sensitive AWT window-activation behaviour verified by hand on real hardware (see this
 * file's own doc comments); it is not exercised here, consistent with this project's rule against
 * tests that race a real, non-injectable delay. What *is* tested from that path is the
 * `hostWindow == null` guard both methods share, which is deterministic on every machine.
 */
@OptIn(ExperimentalTestApi::class)
class FocusLostRescueTest {

    private fun state(active: Boolean = true) =
        FocusLostRescueState(
            null,
            FocusRequester(),
            CoroutineScope(Dispatchers.Unconfined)
        ).apply { this.active = active }

    // ── bannerVisible ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the banner is visible by default — inactive tab focus, window nominally focused`() {
        val s = state()
        assertTrue(s.bannerVisible, "tabHasFocus starts false, so the banner must show until something focuses the tab")
    }

    @Test
    fun `the banner hides once the tab has focus and the window is focused`() {
        val s = state()
        s.onFocusChanged(true)
        assertFalse(s.bannerVisible)
    }

    @Test
    fun `the banner shows when the window loses focus even if the tab still has it`() {
        val s = state()
        s.onFocusChanged(true)
        s.windowFocused = false
        assertTrue(s.bannerVisible, "a healthy tab under an unfocused window must still warn — keys won't arrive")
    }

    @Test
    fun `an inactive state never shows the banner, regardless of focus`() {
        val s = state(active = false)
        s.windowFocused = false
        assertFalse(s.bannerVisible, "active=false gates the banner off entirely")
    }

    // ── onFocusChanged / onPointerPress with no host window ──────────────────────────────────

    @Test
    fun `onFocusChanged records the reported focus state`() {
        val s = state()
        s.onFocusChanged(true)
        assertTrue(s.tabHasFocus)
        s.onFocusChanged(false)
        assertFalse(s.tabHasFocus)
    }

    @Test
    fun `onPointerPress with no host window is a no-op`() {
        state().onPointerPress() // hostWindow == null — must return before touching AWT
    }

    @Test
    fun `restoreAwtFocusOwner with no host window is a no-op`() {
        state().restoreAwtFocusOwner() // hostWindow == null — must return before touching AWT
    }

    // ── findAwtCanvas ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `findAwtCanvas finds a Canvas that is the container itself`() {
        val canvas = Canvas()
        assertSame(canvas, findAwtCanvas(canvas))
    }

    @Test
    fun `findAwtCanvas finds a Canvas nested inside child containers`() {
        val canvas = Canvas()
        val inner = Panel().apply { add(canvas) }
        val outer = Panel().apply { add(Panel()); add(inner) }
        assertSame(canvas, findAwtCanvas(outer))
    }

    @Test
    fun `findAwtCanvas returns null when no Canvas exists anywhere in the tree`() {
        val outer: Container = Panel().apply { add(Panel()); add(Panel()) }
        assertNull(findAwtCanvas(outer))
    }

    // ── FocusLostBanner ────────────────────────────────────────────────────────────────────────

    @Test
    fun `FocusLostBanner renders nothing while the banner is not visible`() = runComposeUiTest {
        val s = state()
        s.onFocusChanged(true) // healthy — bannerVisible false
        setContent {
            MaterialTheme {
                FocusLostBanner(state = s, text = "Click to restore keyboard control")
            }
        }
        onNodeWithText("Click to restore keyboard control").assertDoesNotExist()
    }

    @Test
    fun `FocusLostBanner renders its text while the banner is visible`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                FocusLostBanner(state = state(), text = "Click to restore keyboard control")
            }
        }
        onNodeWithText("Click to restore keyboard control").assertExists()
    }
}

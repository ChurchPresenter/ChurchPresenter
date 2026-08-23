package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.companionserver.InstanceLinkStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The window-and-panel decisions the root composable makes: which stored layout is being edited,
 * how a panel width is held inside its cap, and when a collapsing panel stops being composed.
 */
class MainDesktopLayoutStateTest {

    // ── Which layout a placement edits ──────────────────────────────────────────

    @Test
    fun `a floating window edits the windowed layout`() {
        assertFalse(isMaximizedPlacement(WindowPlacement.Floating))
    }

    @Test
    fun `a maximized window edits the maximized layout`() {
        assertTrue(isMaximizedPlacement(WindowPlacement.Maximized))
    }

    @Test
    fun `fullscreen shares the maximized layout rather than the windowed one`() {
        // Otherwise going fullscreen would silently start editing the windowed sizes.
        assertTrue(isMaximizedPlacement(WindowPlacement.Fullscreen))
    }

    @Test
    fun `an unknown placement is treated as maximized`() {
        assertTrue(isMaximizedPlacement(null))
    }

    // ── Holding a panel inside its cap ──────────────────────────────────────────

    @Test
    fun `a width beyond the cap is brought back to it`() {
        assertEquals(300f, clampPanelWidth(currentPx = 480f, capPx = 300f))
    }

    @Test
    fun `a width inside the cap is left alone`() {
        assertEquals(220f, clampPanelWidth(currentPx = 220f, capPx = 300f))
    }

    @Test
    fun `a width exactly at the cap is left alone`() {
        assertEquals(300f, clampPanelWidth(currentPx = 300f, capPx = 300f))
    }

    @Test
    fun `clamping is idempotent, so a drag never works from a stale base`() {
        val once = clampPanelWidth(480f, 300f)
        assertEquals(once, clampPanelWidth(once, 300f))
    }

    // ── When a collapsing panel is still on screen ──────────────────────────────

    @Test
    fun `an open panel is composed`() {
        assertTrue(isPanelRendered(collapsed = false, visibleFraction = 1f))
    }

    @Test
    fun `a panel still animating shut is composed`() {
        // Collapsed but not yet gone — dropping it here would make it vanish rather than slide.
        assertTrue(isPanelRendered(collapsed = true, visibleFraction = 0.4f))
    }

    @Test
    fun `a panel that has finished collapsing is not composed`() {
        assertFalse(isPanelRendered(collapsed = true, visibleFraction = 0f))
    }

    @Test
    fun `an opening panel is composed from the very start`() {
        assertTrue(isPanelRendered(collapsed = false, visibleFraction = 0f))
    }

    // ── Disconnecting a link ────────────────────────────────────────────────────

    @Test
    fun `a link in any live state can be disconnected`() {
        assertTrue(canDisconnectInstanceLink(InstanceLinkStatus.CONNECTED))
        assertTrue(canDisconnectInstanceLink(InstanceLinkStatus.CONNECTING))
        assertTrue(canDisconnectInstanceLink(InstanceLinkStatus.ERROR))
    }

    @Test
    fun `a link already detached offers nothing to disconnect`() {
        assertFalse(canDisconnectInstanceLink(InstanceLinkStatus.DISCONNECTED))
    }
}

package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The math behind the schedule and preview side panels: how wide either is allowed to grow given
 * the window size and the other panel ([computePanelCapPx]), and which of the two saved layouts —
 * windowed or maximized — a drag's final width is written back into ([withScheduleWidth],
 * [withPreviewWidth]).
 *
 * Both come with their own documented history of getting this wrong (see the sidebar-resize notes
 * referenced at their call sites in [MainDesktop]): a cap that clamps to zero before the window's
 * first layout pass has even reported a size, and a saved width landing in the wrong one of the
 * two layouts and silently overwriting a size the user never asked to change.
 */
class MainDesktopPanelResizeTest {

    // ── computePanelCapPx ────────────────────────────────────────────────────────

    @Test
    fun `before the first layout pass reports a size, the cap is unbounded rather than zero`() {
        val cap = computePanelCapPx(availablePx = 0f, otherPanelPx = 0f, reservePx = 232f, absMaxPx = 600f)
        assertEquals(Float.MAX_VALUE, cap, "an unknown window size must not clamp the panel to zero")
    }

    @Test
    fun `a negative reported size is treated the same as unknown`() {
        val cap = computePanelCapPx(availablePx = -1f, otherPanelPx = 0f, reservePx = 232f, absMaxPx = 600f)
        assertEquals(Float.MAX_VALUE, cap)
    }

    @Test
    fun `a wide window with no other panel caps at the absolute maximum`() {
        val cap = computePanelCapPx(availablePx = 3000f, otherPanelPx = 0f, reservePx = 232f, absMaxPx = 600f)
        assertEquals(600f, cap)
    }

    @Test
    fun `plenty of room but a wide other panel leaves proportionally less cap`() {
        val cap = computePanelCapPx(availablePx = 1000f, otherPanelPx = 400f, reservePx = 232f, absMaxPx = 600f)
        assertEquals(1000f - 400f - 232f, cap)
    }

    @Test
    fun `a narrow window still leaves room for both handles and content, never below zero`() {
        val cap = computePanelCapPx(availablePx = 100f, otherPanelPx = 400f, reservePx = 232f, absMaxPx = 600f)
        assertEquals(0f, cap, "the cap must floor at zero rather than go negative")
    }

    @Test
    fun `an ignored collapsed panel is passed as zero, freeing up the full cap`() {
        // Mirrors the call site: `panelCapPx(if (collapsed) 0f else otherPanelPx)`.
        val cap = computePanelCapPx(availablePx = 800f, otherPanelPx = 0f, reservePx = 232f, absMaxPx = 600f)
        assertEquals(568f, cap)
    }

    // ── withScheduleWidth / withPreviewWidth ────────────────────────────────────

    @Test
    fun `saving the schedule width while windowed updates only the windowed layout`() {
        val before = AppSettings()
        val after = withScheduleWidth(before, isMaximized = false, widthDp = 340)

        assertEquals(340, after.windowedLayout.schedulePanelWidthDp)
        assertEquals(before.maximizedLayout.schedulePanelWidthDp, after.maximizedLayout.schedulePanelWidthDp)
    }

    @Test
    fun `saving the schedule width while maximized updates only the maximized layout`() {
        val before = AppSettings()
        val after = withScheduleWidth(before, isMaximized = true, widthDp = 340)

        assertEquals(340, after.maximizedLayout.schedulePanelWidthDp)
        assertEquals(before.windowedLayout.schedulePanelWidthDp, after.windowedLayout.schedulePanelWidthDp)
    }

    @Test
    fun `saving the schedule width never touches the preview width`() {
        val before = AppSettings()
        val after = withScheduleWidth(before, isMaximized = false, widthDp = 340)

        assertEquals(before.windowedLayout.previewPanelWidthDp, after.windowedLayout.previewPanelWidthDp)
    }

    @Test
    fun `saving the preview width while windowed updates only the windowed layout`() {
        val before = AppSettings()
        val after = withPreviewWidth(before, isMaximized = false, widthDp = 420)

        assertEquals(420, after.windowedLayout.previewPanelWidthDp)
        assertEquals(before.maximizedLayout.previewPanelWidthDp, after.maximizedLayout.previewPanelWidthDp)
    }

    @Test
    fun `saving the preview width while maximized updates only the maximized layout`() {
        val before = AppSettings()
        val after = withPreviewWidth(before, isMaximized = true, widthDp = 420)

        assertEquals(420, after.maximizedLayout.previewPanelWidthDp)
        assertEquals(before.windowedLayout.previewPanelWidthDp, after.windowedLayout.previewPanelWidthDp)
    }

    @Test
    fun `saving the preview width never touches the schedule width`() {
        val before = AppSettings()
        val after = withPreviewWidth(before, isMaximized = true, widthDp = 420)

        assertEquals(before.maximizedLayout.schedulePanelWidthDp, after.maximizedLayout.schedulePanelWidthDp)
    }

    @Test
    fun `saving one panel's width leaves the other panel's collapsed flag untouched`() {
        val before = AppSettings(
            windowedLayout =
                AppSettings().windowedLayout.copy(schedulePanelCollapsed = true, previewPanelCollapsed = true),
        )
        val after = withScheduleWidth(before, isMaximized = false, widthDp = 340)

        assertTrue(after.windowedLayout.schedulePanelCollapsed, "resizing must not un-collapse the panel")
        assertTrue(after.windowedLayout.previewPanelCollapsed)
    }
}

package org.churchpresenter.canvas

import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which of the two saved layouts — windowed or maximized — a canvas panel drag's final width is
 * written back into. Same shape as `MainDesktopPanelResizeTest`'s `withScheduleWidth`/
 * `withPreviewWidth`: the divider drag itself is not driven through a Compose UI test — dragging a
 * pixel-based `draggable` handle in a headless test is unreliable — so the branch that decides which
 * layout gets the new width is pulled out and tested directly instead.
 */
class CanvasTabPanelResizeTest {

    @Test
    fun `saving the left panel width while windowed updates only the windowed layout`() {
        val before = AppSettings()
        val after = withCanvasLeftPanelWidth(before, isMaximized = false, widthDp = 260)

        assertEquals(260, after.windowedLayout.canvasLeftPanelWidthDp)
        assertEquals(before.maximizedLayout.canvasLeftPanelWidthDp, after.maximizedLayout.canvasLeftPanelWidthDp)
    }

    @Test
    fun `saving the left panel width while maximized updates only the maximized layout`() {
        val before = AppSettings()
        val after = withCanvasLeftPanelWidth(before, isMaximized = true, widthDp = 260)

        assertEquals(260, after.maximizedLayout.canvasLeftPanelWidthDp)
        assertEquals(before.windowedLayout.canvasLeftPanelWidthDp, after.windowedLayout.canvasLeftPanelWidthDp)
    }

    @Test
    fun `saving the left panel width never touches the right panel width`() {
        val before = AppSettings()
        val after = withCanvasLeftPanelWidth(before, isMaximized = false, widthDp = 260)

        assertEquals(before.windowedLayout.canvasRightPanelWidthDp, after.windowedLayout.canvasRightPanelWidthDp)
    }

    @Test
    fun `saving the right panel width while windowed updates only the windowed layout`() {
        val before = AppSettings()
        val after = withCanvasRightPanelWidth(before, isMaximized = false, widthDp = 320)

        assertEquals(320, after.windowedLayout.canvasRightPanelWidthDp)
        assertEquals(before.maximizedLayout.canvasRightPanelWidthDp, after.maximizedLayout.canvasRightPanelWidthDp)
    }

    @Test
    fun `saving the right panel width while maximized updates only the maximized layout`() {
        val before = AppSettings()
        val after = withCanvasRightPanelWidth(before, isMaximized = true, widthDp = 320)

        assertEquals(320, after.maximizedLayout.canvasRightPanelWidthDp)
        assertEquals(before.windowedLayout.canvasRightPanelWidthDp, after.windowedLayout.canvasRightPanelWidthDp)
    }

    @Test
    fun `saving the right panel width never touches the left panel width`() {
        val before = AppSettings()
        val after = withCanvasRightPanelWidth(before, isMaximized = true, widthDp = 320)

        assertEquals(before.maximizedLayout.canvasLeftPanelWidthDp, after.maximizedLayout.canvasLeftPanelWidthDp)
    }
}

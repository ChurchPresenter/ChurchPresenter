package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.input.key.Key
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutputShortcutsTest {
    @Test
    fun `output windows recognize navigation and display keys`() {
        assertEquals(OutputShortcut.NEXT, outputShortcut(Key.DirectionRight))
        assertEquals(OutputShortcut.NEXT, outputShortcut(Key.Spacebar))
        assertEquals(OutputShortcut.PREVIOUS, outputShortcut(Key.PageUp))
        assertEquals(OutputShortcut.FULLSCREEN, outputShortcut(Key.F11))
        assertEquals(OutputShortcut.HIDE, outputShortcut(Key.Escape))
        assertEquals(OutputShortcut.ON_TOP, outputShortcut(Key.T))
        assertEquals(OutputShortcut.BLACKOUT, outputShortcut(Key.B))
        assertNull(outputShortcut(Key.A))
    }

    @Test
    fun `output targets cycle through both monitors and the projection window`() {
        var settings = ProjectionSettings(windowedProjection = true)
        settings = nextProjectionTarget(settings, 2)
        assertEquals(0, settings.quickPlayDisplay)
        settings = nextProjectionTarget(settings, 2)
        assertEquals(1, settings.quickPlayDisplay)
        settings = nextProjectionTarget(settings, 2)
        assertEquals(true, settings.windowedProjection)
        assertNull(settings.quickPlayDisplay)
        assertNull(nextProjectionTarget(settings, 0).quickPlayDisplay)
    }
}

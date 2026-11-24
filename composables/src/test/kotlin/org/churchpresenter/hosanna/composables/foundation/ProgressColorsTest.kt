package org.churchpresenter.hosanna.composables.foundation

import org.churchpresenter.hosanna.composables.model.ProgressStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.hosanna.composables.testing.LightThemeColors

class ProgressColorsTest {

    private val c = LightThemeColors

    @Test
    fun `the fill follows the status`() {
        assertEquals(c.primary, c.progressColors(ProgressStatus.Running).fill)
        assertEquals(c.success, c.progressColors(ProgressStatus.Finished).fill)
        assertEquals(c.error, c.progressColors(ProgressStatus.Failed).fill)
        assertEquals(c.faint, c.progressColors(ProgressStatus.Paused).fill)
    }

    @Test
    fun `status text matches the fill, paused reads a little darker`() {
        assertEquals(c.success, c.progressColors(ProgressStatus.Finished).status)
        assertEquals(c.error, c.progressColors(ProgressStatus.Failed).status)
        assertEquals(c.onSurfaceVariant, c.progressColors(ProgressStatus.Paused).status)
        val running = c.progressColors(ProgressStatus.Running)
        assertEquals(c.outlineVariant, running.track)
        assertEquals(c.onSurface, running.label)
        assertEquals(c.onSurfaceVariant, running.detail)
    }
}

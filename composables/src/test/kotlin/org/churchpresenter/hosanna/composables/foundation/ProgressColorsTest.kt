package org.churchpresenter.hosanna.composables.foundation

import org.churchpresenter.theme.themeColorsFor
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.hosanna.composables.model.ProgressStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressColorsTest {

    private val c = themeColorsFor(ThemeMode.LIGHT)

    @Test
    fun `the fill follows the status`() {
        assertEquals(c.primary, c.progressColors(ProgressStatus.Running).fill)
        assertEquals(c.success, c.progressColors(ProgressStatus.Finished).fill)
        assertEquals(c.error, c.progressColors(ProgressStatus.Failed).fill)
        assertEquals(c.onSurfaceFaint, c.progressColors(ProgressStatus.Paused).fill)
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

package org.churchpresenter.sharedui.utils

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which output a settings preview is drawn the shape of. */
class PreviewOutputSizeTest {

    private fun settingsWith(vararg screens: ScreenAssignment) =
        AppSettings(projectionSettings = ProjectionSettings(screenAssignments = screens.toList()))

    @Test
    fun `with nothing assigned the preview is 1920x1080`() {
        assertEquals(FallbackOutputSize, previewOutputSize(AppSettings()))
    }

    @Test
    fun `the first screen that has reported its bounds sets the shape`() {
        val settings = settingsWith(
            ScreenAssignment(targetBoundsW = 0, targetBoundsH = 0),
            ScreenAssignment(targetBoundsW = 1024, targetBoundsH = 768),
            ScreenAssignment(targetBoundsW = 2560, targetBoundsH = 1080),
        )
        assertEquals(OutputSize(1024, 768), previewOutputSize(settings))
    }

    @Test
    fun `a screen with only a width has not reported, and is skipped`() {
        val settings = settingsWith(ScreenAssignment(targetBoundsW = 1920, targetBoundsH = 0))
        assertEquals(FallbackOutputSize, previewOutputSize(settings))
    }
}

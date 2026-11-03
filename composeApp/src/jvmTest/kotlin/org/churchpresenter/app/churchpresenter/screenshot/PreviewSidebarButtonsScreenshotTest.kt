@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.SidebarButtons
import org.churchpresenter.app.churchpresenter.SidebarShowControl
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.clearTextOnly
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.screenshot.captureComponent
import kotlin.test.BeforeTest
import kotlin.test.Test

class PreviewSidebarButtonsScreenshotTest {

    @BeforeTest
    fun latch() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
    }

    private fun shoot(name: String, manager: PresenterManager = PresenterManager()) =
        captureComponent(SECTION, name) {
            Box(Modifier.width(SIDEBAR_WIDTH)) {
                SidebarButtons(
                    presenterManager = manager,
                    onClearDisplay = {},
                    appSettings = AppSettings(),
                    showControl = SidebarShowControl(),
                    onSettingsChange = {},
                    onEditPreviewLayout = {},
                )
            }
        }

    @Test
    fun `the buttons over the preview`() = shoot("buttons")

    @Test
    fun `the buttons while the text is cleared`() =
        shoot("buttons_text_cleared", PresenterManager().apply { clearTextOnly() })

    private companion object {
        const val SECTION = "previewSidebarButtons"
        val SIDEBAR_WIDTH = 260.dp
    }
}

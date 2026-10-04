@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.schedule.ScheduleTab
import org.churchpresenter.schedule.ScheduleViewModel
import org.churchpresenter.schedule.seedEveryItemType
import org.churchpresenter.sharedui.screenshot.SCREENSHOT_ROOT
import org.churchpresenter.sharedui.screenshot.THEMES
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.theme.ChurchPresenterTheme
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

class AppPreviewScheduleScreenshotTest {

    /**
     * The schedule image the website's homepage uses, at 400dp — a panel width an operator really
     * uses (`library()` settles on 360), so the rows fill their width instead of stretching across
     * a full window. Written unstacked, one file per theme, like the other exports here.
     */
    @Test
    fun `the schedule panel at the width an operator uses`() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        val realHome = System.getProperty("user.home")
        val tempHome = Files.createTempDirectory("cp-schedule-preview").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        try {
            THEMES.forEach { (suffix, mode) ->
                val vm = ScheduleViewModel().apply { seedEveryItemType() }
                try {
                    runSkikoComposeUiTest(
                        size = Size(PANEL_WIDTH, PANEL_HEIGHT) * PREVIEW_DENSITY,
                        density = Density(PREVIEW_DENSITY),
                    ) {
                        setContent {
                            ChurchPresenterTheme(themeMode = mode) {
                                Box(Modifier.width(PANEL_WIDTH.dp)) {
                                    ScheduleTab(scheduleViewModel = vm, onPresenting = {})
                                }
                            }
                        }
                        waitForIdle()
                        captureTo(File("$SCREENSHOT_ROOT/previewApp/schedule_$suffix.png"))
                    }
                } finally {
                    vm.dispose()
                }
            }
        } finally {
            System.setProperty("user.home", realHome)
            tempHome.deleteRecursively()
        }
    }

    private companion object {
        const val PANEL_WIDTH = 400f
        const val PANEL_HEIGHT = 768f
    }
}

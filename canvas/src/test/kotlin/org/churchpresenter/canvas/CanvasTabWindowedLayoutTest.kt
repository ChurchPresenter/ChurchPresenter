package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.WindowLayoutSettings
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CanvasTabWindowedLayoutTest {

    private fun selectToolLeftDp(placement: WindowPlacement): Float {
        val realHome = System.getProperty("user.home")
        val tempHome: File = Files.createTempDirectory("cp-canvas-windowed").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        var left = 0f
        try {
            val vm = SceneViewModel()
            vm.addScene("Main")
            val settings = AppSettings(
                maximizedLayout = WindowLayoutSettings(canvasLeftPanelWidthDp = 160),
                windowedLayout = WindowLayoutSettings(canvasLeftPanelWidthDp = 320),
            )
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        CompositionLocalProvider(LocalMainWindowState provides WindowState(placement = placement)) {
                            Box(Modifier.width(1200.dp)) {
                                CanvasTab(
                                    appSettings = settings,
                                    onPresentScene = {},
                                    sceneViewModel = vm,
                                    onAddToSchedule = { _, _ -> },
                                    cameraHost = NO_CAMERAS,
                                )
                            }
                        }
                    }
                }
                waitForIdle()
                val node = onNodeWithText("◆").fetchSemanticsNode()
                left = node.boundsInRoot.left / density.density
            }
        } finally {
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
        }
        return left
    }

    @Test
    fun `a windowed main window lays the tab out with the windowed panel widths`() {
        val windowed = selectToolLeftDp(WindowPlacement.Floating)
        val maximized = selectToolLeftDp(WindowPlacement.Maximized)

        assertTrue(windowed > 320f, "the canvas starts past the windowed left panel, was $windowed")
        assertTrue(maximized < 320f, "and past the narrower maximized one when maximized, was $maximized")
        assertTrue(windowed - maximized > 140f, "the difference is the two stored widths, was ${windowed - maximized}")
    }
}

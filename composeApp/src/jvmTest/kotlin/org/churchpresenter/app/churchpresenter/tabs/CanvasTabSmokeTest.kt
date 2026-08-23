@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsExactly

/**
 * That `CanvasTab` composes at all on a machine with no display.
 *
 * It did not until [assignedDisplayBounds][org.churchpresenter.app.churchpresenter.utils.assignedDisplayBounds]
 * existed: the tab asked `GraphicsEnvironment` for the assigned output's bounds twice during
 * composition — once for the scene list's per-row aspect-ratio badge and once for the mismatch
 * warning under the canvas — and `screenDevices` throws `HeadlessException` when there is no screen.
 * That single call made the whole tab unreachable from a test.
 *
 * This is deliberately only a smoke test. It pins the thing that was actually broken — the tab
 * renders headlessly, with an assignment carrying stored bounds that match no real screen, which is
 * the shape that used to reach the throwing branch. Driving the compositor's controls is separate
 * work; what matters here is that it is now possible.
 */
class CanvasTabSmokeTest {

    private fun canvasTab(settings: AppSettings, assertions: ComposeUiTest.(SceneViewModel) -> Unit) {
        TestSingletons.latchToTestHome()
        val realHome = System.getProperty("user.home")
        val tempHome: File = Files.createTempDirectory("cp-canvas-tab").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        try {
            val scenes = SceneViewModel()
            // A scene named distinctly enough that finding it on screen proves the tab drew its
            // list, and one whose 4:3 shape mismatches every display the fallback can return, so
            // the aspect-ratio branch that used to throw is the one being exercised.
            scenes.addScene("Smoke Scene")
            scenes.updateCanvasSize(1024, 768)
            val presenter = PresenterManager()
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        CanvasTab(
                            appSettings = settings,
                            presenterManager = presenter,
                            sceneViewModel = scenes,
                            onAddToSchedule = { _, _ -> },
                        )
                    }
                }
                waitForIdle()
                assertions(scenes)
            }
        } finally {
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
        }
    }

    @Test
    fun `the tab composes with no display attached`() {
        canvasTab(AppSettings()) { scenes ->
            assertTrue(
                showsExactly("Smoke Scene"),
                "the scene list should have drawn; on screen was ${renderedText()}",
            )
            assertEquals(1, scenes.scenes.size)
        }
    }

    @Test
    fun `an assignment pointing at a screen that is not there still composes`() {
        // Stored bounds for a projector that was unplugged, plus an index past the end of the list:
        // both of the fallbacks the aspect-ratio check relies on, exercised through the real tab.
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(
                    ScreenAssignment(targetDisplay = 4, targetBoundsX = 3840, targetBoundsY = 0)
                )
            )
        )
        canvasTab(settings) { _ ->
            assertTrue(
                showsExactly("Smoke Scene"),
                "the tab must still draw when the assigned display cannot be found; on screen was ${renderedText()}",
            )
        }
    }
}

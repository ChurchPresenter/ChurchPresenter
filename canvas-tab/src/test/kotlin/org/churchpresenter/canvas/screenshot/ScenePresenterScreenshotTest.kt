@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.canvas.ScenePresenter
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.ui.screenshot.SCREENSHOT_ROOT
import kotlin.test.Test

/**
 * What the audience sees of a canvas scene, full screen.
 *
 * Moved here with the presenter: `PresenterFullScreenScreenshotTest` in `:composeApp` shoots only
 * the presenters that module still owns, the same way the announcements, Strong's-entry and
 * audience-question shots went to their own modules.
 *
 * One image, and deliberately so — a scene is a stack of arbitrary sources, so what is worth pinning
 * is that the compositor draws a layered scene at output size at all. Which sources exist and how
 * each is drawn is `CanvasTabScreenshotTest`'s job, at editor size where the differences are legible.
 */
class ScenePresenterScreenshotTest {

    /** A 1080p output. */
    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun shoot(name: String, content: @Composable () -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { Box(screen) { content() } } }
        waitForIdle()
        capture(name)
    }

    private fun ComposeUiTest.capture(name: String) {
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    @Test
    fun `a canvas scene`() = shoot("scene") { ScenePresenter(scene = scene()) }

    /** A backdrop with a line of text over it — two layers, which is what makes it a composition. */
    private fun scene() = Scene(
        name = "Welcome",
        sources = listOf(
            SceneSource.ColorSource(id = "c1", name = "Backdrop", color = "#1B2A5B"),
            SceneSource.TextSource(
                id = "t1",
                name = "Welcome",
                text = "Welcome to the 10:30 service",
                transform = SourceTransform(x = 0.1f, y = 0.4f, width = 0.8f, height = 0.2f),
                fontSize = 96,
            ),
        ),
    )

    private companion object {
        const val SECTION = "scenePresenter"
    }
}

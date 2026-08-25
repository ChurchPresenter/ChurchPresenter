package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ScenePresenterRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    @Test
    fun `a null scene renders nothing`() = runComposeUiTest {
        setContent {
            Box(screen) { ScenePresenter(scene = null) }
        }
    }

    @Test
    fun `a scene's sources are put on screen`() = runComposeUiTest {
        val scene = Scene(
            sources = listOf(
                SceneSource.TextSource(id = "t1", name = "Text 1", text = "Welcome Home")
            )
        )
        setContent {
            Box(screen) { ScenePresenter(scene = scene) }
        }
        onNodeWithText("Welcome Home", substring = true).assertExists()
    }
}

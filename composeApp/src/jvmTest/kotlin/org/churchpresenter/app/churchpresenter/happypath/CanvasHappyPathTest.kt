@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasHappyPathTest : HappyPathHarness() {

    /** Where `SceneViewModel` keeps the scenes: this fork's home, shared with every other suite. */
    private val scenesFile get() = File(System.getProperty("user.home"), ".churchpresenter/scenes.json")

    @BeforeTest
    fun clearScenes() {
        scenesFile.delete()
    }

    @AfterTest
    fun removeScenes() {
        scenesFile.delete()
    }

    private fun saveScenes(vararg scenes: Scene) {
        scenesFile.parentFile.mkdirs()
        scenesFile.writeText(Json.encodeToString(scenes.toList()))
    }

    private fun textScene(id: String, name: String, text: String) = Scene(
        id = id,
        name = name,
        sources = listOf(
            SceneSource.TextSource(
                id = "$id-text",
                name = "Title",
                transform = SourceTransform(x = 0.1f, y = 0.4f, width = 0.8f, height = 0.2f),
                text = text,
            ),
        ),
    )

    private fun ComposeUiTest.addSource(type: String) {
        onAllNodes(hasContentDescription("Add source"))[0].performClick()
        waitForIdle()
        onAllNodes(hasText(type))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.awaitScene(app: HappyApp, name: String) {
        await("$name is on air") {
            app.presenter.slideContent.value == Presenting.CANVAS && app.presenter.activeScene.value?.name == name
        }
    }

    @Test
    fun `a new scene with a text layer goes live`() = happyPath(settings(Tabs.CANVAS)) { app ->
        clickText("Create Scene")
        addSource("Text")
        goLive()

        await("the scene is on air with its text layer") {
            app.presenter.slideContent.value == Presenting.CANVAS &&
                app.presenter.activeScene.value?.sources?.any { it is SceneSource.TextSource } == true
        }
        awaitOnOutput(app, "Text")
    }

    @Test
    fun `image and QR code layers join the scene`() = happyPath(settings(Tabs.CANVAS)) { app ->
        clickText("Create Scene")
        addSource("Image")
        addSource("QR Code")
        goLive()

        await("the scene is on air") { app.presenter.slideContent.value == Presenting.CANVAS }
        assertEquals(
            listOf(SceneSource.ImageSource::class, SceneSource.QRCodeSource::class),
            app.presenter.activeScene.value?.sources?.map { it::class },
        )
    }

    @Test
    fun `another scene taken live replaces the one on air`() {
        saveScenes(
            textScene("welcome", "Welcome", "Welcome home"),
            textScene("notices", "Notices", "Notices for tonight"),
        )
        happyPath(settings(Tabs.CANVAS)) { app ->
            clickText("Welcome")
            goLive()
            awaitScene(app, "Welcome")
            awaitOnOutput(app, "Welcome home")

            clickText("Notices")
            goLive()

            awaitScene(app, "Notices")
            awaitOnOutput(app, "Notices for tonight")
        }
    }
}

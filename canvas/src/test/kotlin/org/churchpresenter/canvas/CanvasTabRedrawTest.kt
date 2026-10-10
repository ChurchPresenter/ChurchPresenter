package org.churchpresenter.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.settings.AppSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CanvasTabRedrawTest {

    private class Host {
        var dark by mutableStateOf(false)
        var generation by mutableStateOf(0)
        val presented = mutableListOf<Pair<Int, String>>()
        val scheduled = mutableListOf<Pair<Int, String>>()
        val presets = mutableListOf<Pair<Int, String>>()
    }

    private fun busyTab(block: ComposeUiTest.(vm: SceneViewModel, host: Host) -> Unit) {
        val realHome = System.getProperty("user.home")
        val tempHome: File = Files.createTempDirectory("cp-canvas-redraw").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        try {
            val vm = SceneViewModel()
            val scene = vm.addScene("Morning")
            vm.addScene("Evening")
            vm.selectScene(scene.id)
            vm.addSource(SceneSource.TextSource(id = "title", name = "Title", text = "Welcome"))
            vm.addSource(SceneSource.ShapeSource(id = "off", name = "Off", transform = SourceTransform(x = 1.5f)))
            vm.setDualLayout(scene.id, true)
            vm.selectSource("title")
            val host = Host()

            runComposeUiTest {
                setContent {
                    val gen = host.generation
                    MaterialTheme(colorScheme = if (host.dark) darkColorScheme() else lightColorScheme()) {
                        CanvasTab(
                            appSettings = AppSettings(),
                            onPresentScene = { host.presented += gen to it.name },
                            sceneViewModel = vm,
                            onAddToSchedule = { _, name -> host.scheduled += gen to name },
                            onSavePreset = { _, name -> host.presets += gen to name },
                            cameraHost = NO_CAMERAS,
                        )
                    }
                }
                waitForIdle()
                block(vm, host)
            }
        } finally {
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
        }
    }

    @Test
    fun `the whole tab redraws in the other theme without changing a scene`() = busyTab { vm, host ->
        val shown = renderedText()
        val before = vm.scenes.toList()

        repeat(2) {
            host.dark = !host.dark
            waitForIdle()
        }

        assertEquals(shown, renderedText(), "every panel still shows what it did")
        assertEquals(before, vm.scenes.toList(), "and a theme change edits no scene")
    }

    @Test
    fun `the scene actions call the host's latest callbacks after it hands over new ones`() = busyTab { vm, host ->
        val shown = renderedText()
        val before = vm.scenes.toList()

        host.generation++
        waitForIdle()
        assertEquals(shown, renderedText(), "new callbacks change nothing on screen")
        assertEquals(before, vm.scenes.toList())

        canvasButton(CanvasLabel.ADD_TO_SCHEDULE).performClick()
        canvasButton("Save preset").performClick()
        canvasButton(CanvasLabel.GO_LIVE).performClick()
        waitForIdle()

        assertEquals(listOf(1 to "Morning"), host.scheduled)
        assertEquals(listOf(1 to "Morning"), host.presets)
        assertEquals(listOf(1 to "Morning"), host.presented)
    }
}

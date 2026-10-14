@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.settings.AppSettings
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasTabInteractionsTest {

    private val offCanvas = SceneSource.TextSource(
        id = "src-far",
        name = "Far",
        transform = SourceTransform(x = 1.5f, y = 0.4f, width = 0.2f, height = 0.2f),
    )

    private fun ComposeUiTest.countOf(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).size

    private fun ComposeUiTest.settleHover() {
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    @Test
    fun `the canvas size button names the current size on hover`() =
        canvasTab(seed = { addScene("Scene") }) { _, _ ->
            val before = countOf("Canvas size (")

            onNodeWithTag(CANVAS_SIZE_BUTTON_TAG).performMouseInput { moveTo(center) }
            settleHover()

            assertEquals(before + 1, countOf("Canvas size ("))
        }

    @Test
    fun `a layer off the canvas says so on hover`() =
        canvasTab(seed = { addScene("Scene"); addSource(offCanvas) }) { _, _ ->
            val warning = "Outside the canvas — not visible"
            val before = countOf(warning)

            onAllNodesWithContentDescription(warning)[0].performMouseInput { moveTo(center) }
            settleHover()

            assertEquals(before + 1, countOf(warning))
        }

    @Test
    fun `renaming a layer in its properties renames it in the scene`() =
        canvasTab(seed = {
            addScene("Scene")
            addSource(SceneSource.TextSource(id = "txt", name = "Title"))
        }) { vm, _ ->
            onAllNodes(hasSetTextAction())[0].performTextReplacement("Headline")
            waitForIdle()

            assertEquals("Headline", vm.currentScene!!.sources.single().name)
        }

    @Test
    fun `tapping an unselected layer on the canvas selects it`() =
        canvasTab(seed = {
            addScene("Scene")
            addSource(SceneSource.ColorSource(id = "fill", name = "Backdrop"))
            selectSource(null)
        }) { vm, _ ->
            onNodeWithTag(SCENE_CANVAS_TAG).performMouseInput { click(center) }
            waitForIdle()

            assertEquals("fill", vm.selectedSourceId.value)
        }

    @Test
    fun `clicking a layer's name in the list selects it`() =
        canvasTab(seed = {
            addScene("Scene")
            addSource(SceneSource.ColorSource(id = "fill", name = "Backdrop"))
            selectSource(null)
        }) { vm, _ ->
            onAllNodesWithText("Backdrop")[0].performMouseInput { click(center) }
            waitForIdle()

            assertEquals("fill", vm.selectedSourceId.value)
        }

    @Test
    fun `a rectangle drawn on the canvas becomes a layer`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            onAllNodesWithText("□")[0].performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()

            onNodeWithTag(SCENE_CANVAS_TAG).performMouseInput {
                moveTo(Offset(20f, 20f))
                press()
                moveTo(Offset(26f, 26f))
                moveTo(Offset(120f, 90f))
                release()
            }
            waitForIdle()

            assertTrue(vm.currentScene!!.sources.single() is SceneSource.ShapeSource)
        }

    @Test
    fun `the tab composes with only what it requires`() {
        val realHome = System.getProperty("user.home")
        val home = Files.createTempDirectory("cp-canvas-bare").toFile()
        System.setProperty("user.home", home.absolutePath)
        try {
            val vm = SceneViewModel().apply { addScene("Main") }
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        CanvasTab(
                            appSettings = AppSettings(),
                            onPresentScene = {},
                            sceneViewModel = vm,
                            onAddToSchedule = { _, _ -> },
                            cameraHost = CameraHost(PINNED_CAMERAS, ffmpegAvailable = true),
                        )
                    }
                }
                onRoot().performMouseInput {
                    moveTo(Offset(204f, 300f))
                    press()
                    moveTo(Offset(234f, 300f))
                    moveTo(Offset(264f, 300f))
                    release()
                }
                waitForIdle()

                assertEquals("Main", vm.currentScene?.name)
            }
        } finally {
            realHome?.let { System.setProperty("user.home", it) }
            home.deleteRecursively()
        }
    }

    @Test
    fun `the size menu turns the second layout on without a handler of its own`() = runComposeUiTest {
        val sizes = mutableListOf<Pair<Int, Int>>()
        setContent {
            MaterialTheme {
                CanvasSizeMenu(
                    width = 1920,
                    height = 1080,
                    outputs = emptyList(),
                    onSetSize = { w, h -> sizes += w to h },
                )
            }
        }
        onNodeWithTag(CANVAS_SIZE_BUTTON_TAG).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag(CANVAS_DUAL_LAYOUT_TAG).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        assertTrue(onAllNodesWithText("Landscape and portrait").fetchSemanticsNodes(false).isEmpty())
        assertTrue(sizes.isEmpty())
    }

    @Test
    fun `the properties panel needs only a source and a callback`() = runComposeUiTest {
        var updated: SceneSource? = null
        setContent {
            MaterialTheme {
                SourcePropertiesPanel(
                    source = SceneSource.TextSource(id = "txt", name = "Title"),
                    onSourceUpdate = { updated = it },
                )
            }
        }
        onAllNodes(hasSetTextAction())[0].performTextReplacement("Caption")
        waitForIdle()

        assertEquals("Caption", updated?.name)
    }
}

package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds
import java.awt.Rectangle
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Setting a scene's canvas size from its row in the scene list (#608).
 *
 * The same stub and state reset as `SceneViewModelTest`: `addScene()` reads `presenterScreenBounds()`,
 * which throws headless, and scenes persist to `~/.churchpresenter/scenes.json`.
 */
class SceneViewModelCanvasSizeTest {

    private val scenesFile = File(System.getProperty("user.home"), ".churchpresenter/scenes.json")

    @BeforeTest
    fun stubScreenBoundsAndClearState() {
        mockkStatic("org.churchpresenter.app.churchpresenter.utils.ConstantsKt")
        every { presenterScreenBounds() } returns Rectangle(0, 0, 1920, 1080)
        scenesFile.delete()
    }

    @AfterTest
    fun unstub() {
        unmockkStatic("org.churchpresenter.app.churchpresenter.utils.ConstantsKt")
        scenesFile.delete()
    }

    private fun SceneViewModel.size(id: String) = scenes.first { it.id == id }.let { it.canvasWidth to it.canvasHeight }

    @Test
    fun `a named scene is resized even when another one is current`() {
        val vm = SceneViewModel()
        val first = vm.addScene("First")
        val second = vm.addScene("Second")
        assertEquals(second.id, vm.currentSceneId.value)

        vm.updateCanvasSize(1080, 1920, first.id)

        assertEquals(1080 to 1920, vm.size(first.id))
        assertEquals(1920 to 1080, vm.size(second.id), "the current scene is left alone")
    }

    @Test
    fun `with no scene named, the current one is resized`() {
        val vm = SceneViewModel()
        val scene = vm.addScene("Only")
        vm.updateCanvasSize(1440, 1080)
        assertEquals(1440 to 1080, vm.size(scene.id))
    }

    @Test
    fun `each side is kept within the canvas limits`() {
        val vm = SceneViewModel()
        val scene = vm.addScene("Clamped")
        vm.updateCanvasSize(0, 99_999, scene.id)
        assertEquals(CANVAS_SIDE_RANGE.first to CANVAS_SIDE_RANGE.last, vm.size(scene.id))
    }

    @Test
    fun `the new size is saved, and an unchanged one is not written again`() {
        val vm = SceneViewModel()
        val scene = vm.addScene("Saved")

        scenesFile.delete()
        vm.updateCanvasSize(1920, 1080, scene.id)
        assertFalse(scenesFile.exists(), "the size it already has is not a change worth a write")

        vm.updateCanvasSize(1080, 1920, scene.id)
        assertTrue(scenesFile.exists(), "a real change is persisted")
        assertEquals(1080 to 1920, SceneViewModel().size(scene.id), "and reloads as it was set")
    }

    @Test
    fun `an unknown scene id changes nothing`() {
        val vm = SceneViewModel()
        val scene = vm.addScene("Kept")
        vm.updateCanvasSize(1080, 1920, "no-such-scene")
        assertEquals(1920 to 1080, vm.size(scene.id))
    }
}

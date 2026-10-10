package org.churchpresenter.canvas

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.churchpresenter.core.models.scene.SceneAlternateLayout
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.sharedui.utils.presenterScreenBounds
import java.awt.Rectangle
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneViewModelEdgeCasesTest {

    private val scenesFile = File(System.getProperty("user.home"), ".churchpresenter/scenes.json")

    @BeforeTest
    fun stubScreenBoundsAndClearState() {
        mockkStatic("org.churchpresenter.sharedui.utils.ConstantsKt")
        every { presenterScreenBounds() } returns Rectangle(0, 0, 1920, 1080)
        scenesFile.delete()
    }

    @AfterTest
    fun unstub() {
        unmockkStatic("org.churchpresenter.sharedui.utils.ConstantsKt")
        scenesFile.delete()
    }

    private fun text(id: String) = SceneSource.TextSource(id = id, name = id, text = id)

    @Test
    fun `source edits with no scene open change nothing`() {
        val vm = SceneViewModel()

        vm.addSource(text("a"))
        vm.removeSource("a")
        vm.updateSource("a") { it }
        vm.moveSourceUp("a")
        vm.moveSourceDown("a")
        vm.updateTransform("a", SourceTransform(x = 0.5f), alternate = true)
        vm.updateCanvasSize(1280, 720)

        assertTrue(vm.scenes.isEmpty())
        assertNull(vm.currentScene)
        assertNull(vm.selectedSourceId.value, "a source added to no scene is not selected")
        assertTrue(!scenesFile.exists(), "and nothing is written")
    }

    @Test
    fun `an alternate move on a scene without a second layout is ignored`() {
        val vm = SceneViewModel()
        vm.addScene("Main")
        vm.addSource(text("a"))
        val before = vm.currentScene

        vm.updateTransform("a", SourceTransform(x = 0.5f), alternate = true)

        assertEquals(before, vm.currentScene)
    }

    @Test
    fun `resizing a scene that does not exist changes nothing`() {
        val vm = SceneViewModel()
        val scene = vm.addScene("Main")

        vm.updateCanvasSize(1280, 720, sceneId = "missing")

        assertEquals(scene.canvasWidth, vm.currentScene?.canvasWidth)
        assertEquals(scene.canvasHeight, vm.currentScene?.canvasHeight)
    }

    @Test
    fun `an edit addressed to a scene that does not exist changes nothing`() {
        val vm = SceneViewModel()
        val scene = vm.addScene("Main")

        vm.updateScene("missing") { it.copy(name = "Renamed") }

        assertEquals(listOf(scene.name), vm.scenes.map { it.name })
    }

    @Test
    fun `duplicating a scene drops alternate placements of layers it no longer has`() {
        val vm = SceneViewModel()
        val scene = vm.addScene("Main")
        vm.addSource(text("kept"))
        vm.setDualLayout(scene.id, true)
        vm.updateTransform("kept", SourceTransform(x = 0.25f), alternate = true)
        vm.updateScene(scene.id) {
            it.copy(
                alternate = SceneAlternateLayout(
                    it.alternate!!.transforms + ("gone" to SourceTransform(x = 0.75f)),
                )
            )
        }

        val copy = vm.duplicateScene(scene.id, "Copy")!!

        val keptId = copy.sources.single().id
        assertEquals(mapOf(keptId to SourceTransform(x = 0.25f)), copy.alternate?.transforms)
    }
}

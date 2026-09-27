package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds
import org.churchpresenter.core.models.scene.SceneAlternateLayout
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import java.awt.Rectangle
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A scene's second layout for screens of the other orientation (#608): turning it on and off, moving
 * a layer in it, and keeping it in step when layers or scenes are copied or removed.
 *
 * The same stub and state reset as `SceneViewModelTest`: `addScene()` reads `presenterScreenBounds()`,
 * which throws headless, and scenes persist to `~/.churchpresenter/scenes.json`.
 */
class SceneViewModelDualLayoutTest {

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

    private val moved = SourceTransform(x = 0.1f, y = 0.6f, width = 0.8f, height = 0.2f)

    private fun SceneViewModel.withTitle(): Pair<String, String> {
        val scene = addScene("Service")
        addSource(SceneSource.TextSource(id = "title", name = "Title", text = "Welcome"))
        return scene.id to "title"
    }

    private fun SceneViewModel.scene(id: String) = scenes.first { it.id == id }

    @Test
    fun `a new scene has one layout`() {
        val vm = SceneViewModel()
        assertNull(vm.addScene("One").alternate)
    }

    @Test
    fun `turning it on starts an empty second layout, and turning it off drops it`() {
        val vm = SceneViewModel()
        val (sceneId, _) = vm.withTitle()

        vm.setDualLayout(sceneId, true)
        assertEquals(SceneAlternateLayout(), vm.scene(sceneId).alternate)

        vm.setDualLayout(sceneId, false)
        assertNull(vm.scene(sceneId).alternate)
    }

    @Test
    fun `turning it on again keeps the positions already set`() {
        val vm = SceneViewModel()
        val (sceneId, titleId) = vm.withTitle()
        vm.setDualLayout(sceneId, true)
        vm.updateTransform(titleId, moved, alternate = true)

        vm.setDualLayout(sceneId, true)

        assertEquals(mapOf(titleId to moved), vm.scene(sceneId).alternate?.transforms)
    }

    @Test
    fun `a move in the second layout leaves the main one alone`() {
        val vm = SceneViewModel()
        val (sceneId, titleId) = vm.withTitle()
        vm.setDualLayout(sceneId, true)
        val before = vm.scene(sceneId).sources.single().transform

        vm.updateTransform(titleId, moved, alternate = true)

        assertEquals(before, vm.scene(sceneId).sources.single().transform)
        assertEquals(moved, vm.scene(sceneId).alternate?.transforms?.get(titleId))
    }

    @Test
    fun `a move in the main layout leaves the second one alone`() {
        val vm = SceneViewModel()
        val (sceneId, titleId) = vm.withTitle()
        vm.setDualLayout(sceneId, true)

        vm.updateTransform(titleId, moved)

        assertEquals(moved, vm.scene(sceneId).sources.single().transform)
        assertEquals(emptyMap(), vm.scene(sceneId).alternate?.transforms)
    }

    @Test
    fun `a move meant for a second layout the scene does not have is ignored`() {
        val vm = SceneViewModel()
        val (sceneId, titleId) = vm.withTitle()
        val before = vm.scene(sceneId)

        vm.updateTransform(titleId, moved, alternate = true)

        assertEquals(before, vm.scene(sceneId))
    }

    @Test
    fun `removing a layer forgets where it sat in the second layout`() {
        val vm = SceneViewModel()
        val (sceneId, titleId) = vm.withTitle()
        vm.setDualLayout(sceneId, true)
        vm.updateTransform(titleId, moved, alternate = true)

        vm.removeSource(titleId)

        assertEquals(emptyMap(), vm.scene(sceneId).alternate?.transforms)
    }

    @Test
    fun `a duplicated scene keeps its second layout under its own layer ids`() {
        val vm = SceneViewModel()
        val (sceneId, titleId) = vm.withTitle()
        vm.setDualLayout(sceneId, true)
        vm.updateTransform(titleId, moved, alternate = true)

        val copy = assertNotNull(vm.duplicateScene(sceneId, "Copy"))

        val newTitleId = copy.sources.single().id
        assertNotEquals(titleId, newTitleId)
        assertEquals(mapOf(newTitleId to moved), copy.alternate?.transforms)
    }

    @Test
    fun `the second layout is saved and reloads as it was`() {
        val vm = SceneViewModel()
        val (sceneId, titleId) = vm.withTitle()
        vm.setDualLayout(sceneId, true)
        vm.updateTransform(titleId, moved, alternate = true)

        assertEquals(mapOf(titleId to moved), SceneViewModel().scene(sceneId).alternate?.transforms)
    }
}

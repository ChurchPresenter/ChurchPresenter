package org.churchpresenter.canvas

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.sharedui.utils.presenterScreenBounds
import java.awt.Rectangle
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Editing a layer in either layout of a scene from the Properties panel (#937): which layout it edits,
 * what it shows for the second one, and putting a layer back where it starts there.
 *
 * The same stub and state reset as `SceneViewModelDualLayoutTest`.
 */
class SceneViewModelLayoutsTest {

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

    private val picture = SceneSource.ImageSource(
        id = "picture",
        name = "Picture",
        filePath = "/p.png",
        transform = SourceTransform(width = 0.5f, height = 1f),
    )

    private fun SceneViewModel.dual(): String {
        val scene = addScene("Both")
        addSource(picture)
        setDualLayout(scene.id, true)
        return scene.id
    }

    @Test
    fun `a scene with one layout has no second one to edit`() {
        val vm = SceneViewModel()
        vm.addScene("One")
        vm.addSource(picture)
        vm.editLayout(alternate = true)
        assertFalse(vm.editingAlternate.value)
        assertNull(vm.layoutsFor(picture.id))
    }

    @Test
    fun `the panel edits the layout it is told to`() {
        val vm = SceneViewModel().apply { dual() }
        vm.editLayout(alternate = true)
        assertTrue(vm.editingAlternate.value)
        vm.editLayout(alternate = false)
        assertFalse(vm.editingAlternate.value)
    }

    @Test
    fun `the second layout shows where the layer starts there until it is moved`() {
        val vm = SceneViewModel().apply { dual() }
        val layouts = assertNotNull(vm.layoutsFor(picture.id))

        assertTrue(layouts.mainIsLandscape)
        assertFalse(layouts.movedInAlternate)
        assertEquals(SourceTransform(width = 1f, height = 0.5f), layouts.alternateTransform, "the left half, on top")
    }

    @Test
    fun `an edit in the second layout moves it there alone, and Reset puts it back`() {
        val vm = SceneViewModel().apply { dual() }
        val fill = SourceTransform()

        assertNotNull(vm.layoutsFor(picture.id)).actions.onAlternateTransform(fill)

        val moved = assertNotNull(vm.layoutsFor(picture.id))
        assertTrue(moved.movedInAlternate)
        assertEquals(fill, moved.alternateTransform)
        assertEquals(picture.transform, vm.currentScene?.sources?.single()?.transform, "the main layout stays")

        moved.actions.onResetAlternate()
        val reset = assertNotNull(vm.layoutsFor(picture.id))
        assertFalse(reset.movedInAlternate)
        assertEquals(SourceTransform(width = 1f, height = 0.5f), reset.alternateTransform)
    }

    @Test
    fun `the switch's call chooses the layout`() {
        val vm = SceneViewModel().apply { dual() }
        assertNotNull(vm.layoutsFor(picture.id)).actions.onEditLayout(true)
        assertTrue(vm.editingAlternate.value)
    }

    @Test
    fun `resetting a layer never moved, or with no second layout, changes nothing`() {
        val vm = SceneViewModel().apply { dual() }
        val before = vm.currentScene
        vm.resetInAlternate(picture.id)
        assertEquals(before, vm.currentScene)

        val single = SceneViewModel()
        single.addScene("One")
        single.addSource(picture)
        val one = single.currentScene
        single.resetInAlternate(picture.id)
        assertEquals(one, single.currentScene)
    }
}

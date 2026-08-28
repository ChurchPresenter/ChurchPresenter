@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Canvas tab: the scene compositor's control surface.
 *
 * A scene is a stack of sources, and the order of that stack is what the audience sees — so what
 * these pin is the scene graph each control produces: which scene is current, what is in it, and in
 * what order. [org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel]'s own rules are
 * covered by the `SceneViewModel*` suites; nothing here re-tests those.
 *
 * See `CanvasTabTestSupport.kt` for the harness — and note the tab only became testable once
 * `assignedDisplayBounds` replaced a raw `screenDevices` call that threw headless (PR #88).
 */
class CanvasTabTest {

    // ── Scenes ──────────────────────────────────────────────────────────────────

    @Test
    fun `with no scenes the tab offers to create one`() = canvasTab { vm, _ ->
        assertTrue(vm.scenes.isEmpty())
        assertTrue(showsExactly("No scene selected"), "got ${renderedText()}")
        assertTrue(showsExactly("Create Scene"), "with the way forward on screen")
    }

    @Test
    fun `creating a scene selects it, so sources have somewhere to go`() = canvasTab { vm, _ ->
        clickCanvasLabel(CanvasLabel.NEW_SCENE)

        assertEquals(1, vm.scenes.size)
        assertEquals(vm.scenes.single().id, vm.currentSceneId.value, "the new scene is current")
        assertFalse(showsExactly("No scene selected"), "and the empty state is gone")
    }

    @Test
    fun `a second scene becomes the current one`() = canvasTab { vm, _ ->
        clickCanvasLabel(CanvasLabel.NEW_SCENE)
        val first = vm.scenes.single().id
        clickCanvasLabel(CanvasLabel.NEW_SCENE)

        assertEquals(2, vm.scenes.size)
        assertFalse(vm.currentSceneId.value == first, "the newest scene is the one being edited")
    }

    @Test
    fun `removing a scene takes it out of the list`() = canvasTab(seed = { addScene("Only scene") }) { vm, _ ->
        canvasButton(CanvasLabel.REMOVE_SCENE).performClick()
        waitForIdle()

        assertTrue(vm.scenes.isEmpty(), "got ${vm.scenes.map { it.name }}")
        assertTrue(showsExactly("No scene selected"), "and the empty state comes back")
    }

    // ── Sources ─────────────────────────────────────────────────────────────────

    @Test
    fun `a source added from the menu lands in the current scene`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            addSourceOfType(CanvasLabel.TEXT)

            assertEquals(listOf("Text"), vm.sourceNames())
        }

    @Test
    fun `an image source can be added from the menu`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            addSourceOfType(CanvasLabel.IMAGE)

            assertEquals(listOf("Image"), vm.sourceNames())
        }

    @Test
    fun `a clock source can be added from the menu`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            // One source per test on purpose — see addSourceOfType: the add button cannot be
            // driven twice in a single test.
            addSourceOfType(CanvasLabel.CLOCK)

            assertEquals(listOf("Clock & Timer"), vm.sourceNames())
        }

    @Test
    fun `selecting a source opens its properties`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            assertTrue(
                showsExactly("Select a source to edit properties"),
                "nothing selected to begin with",
            )

            addSourceOfType(CanvasLabel.TEXT)

            // Adding selects it, so the properties panel replaces the placeholder.
            assertEquals(vm.currentScene?.sources?.single()?.id, vm.selectedSourceId.value)
            assertFalse(showsExactly("Select a source to edit properties"))
            assertTrue(showsExactly("Properties"), "got ${renderedText().take(20)}")
        }

    @Test
    fun `deleting removes the selected source and leaves the rest`() =
        canvasTab(seed = { addScene("Scene"); seedSources("Keep", "Drop") }) { vm, _ ->
            vm.selectSource("src-Drop")
            waitForIdle()

            canvasButton(CanvasLabel.DELETE_SOURCE).performClick()
            waitForIdle()

            assertEquals(listOf("Keep"), vm.sourceNames())
        }

    // ── Layer order ─────────────────────────────────────────────────────────────

    @Test
    fun `moving a source changes which one is drawn on top`() =
        canvasTab(seed = { addScene("Scene"); seedSources("Lower", "Upper") }) { vm, _ ->
            vm.selectSource("src-Upper")
            waitForIdle()
            val before = vm.sourceNames()

            canvasButton(CanvasLabel.MOVE_BACKWARD).performClick()
            waitForIdle()

            // The stack order is what the audience sees, so it has to actually change.
            assertEquals(
                before.reversed(),
                vm.sourceNames(),
                "the two sources swapped places",
            )
        }

    @Test
    fun `moving back and forward returns a source to where it started`() =
        canvasTab(seed = { addScene("Scene"); seedSources("Lower", "Upper") }) { vm, _ ->
            vm.selectSource("src-Upper")
            waitForIdle()
            val before = vm.sourceNames()

            canvasButton(CanvasLabel.MOVE_BACKWARD).performClick()
            waitForIdle()
            canvasButton(CanvasLabel.MOVE_FORWARD).performClick()
            waitForIdle()

            assertEquals(before, vm.sourceNames())
        }

    // ── Handing the scene on ────────────────────────────────────────────────────

    @Test
    fun `adding to the schedule hands over the scene being edited`() =
        canvasTab(seed = { addScene("Pre-service loop") }) { vm, reports ->
            canvasButton(CanvasLabel.ADD_TO_SCHEDULE).performClick()
            waitForIdle()

            val scene = vm.scenes.single()
            assertEquals(listOf(scene.id to scene.name), reports.scheduled)
        }

    @Test
    fun `there is nothing to schedule or show without a scene`() = canvasTab { _, _ ->
        assertFalse(hasCanvasButton(CanvasLabel.ADD_TO_SCHEDULE), "got ${renderedText()}")
        assertFalse(hasCanvasButton(CanvasLabel.GO_LIVE))
    }
}

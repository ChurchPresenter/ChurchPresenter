package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.models.SourceTransform
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds
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
 * The Canvas scene compositor's model: scenes, their layered sources, and z-order.
 *
 * `addScene()` sizes the canvas from `presenterScreenBounds()`, which throws `HeadlessException`
 * in a test JVM — that top-level function is stubbed with `mockkStatic` on its file class
 * (`ConstantsKt`), which is what makes this ViewModel testable at all.
 *
 * The ViewModel also persists to `~/.churchpresenter/scenes.json` and reloads it in `init`, so
 * that file is deleted before each test to stop scenes leaking between them (and between runs —
 * `build/test-home` survives).
 */
class SceneViewModelTest {

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

    private fun text(id: String, name: String = id) =
        SceneSource.TextSource(id = id, name = name, text = name)

    private fun vmWithScene(): SceneViewModel = SceneViewModel().also { it.addScene("Scene 1") }

    private val SceneViewModel.sourceIds: List<String>
        get() = currentScene?.sources?.map { it.id }.orEmpty()

    // ── Scenes ──────────────────────────────────────────────────────────────────

    @Test
    fun `a new manager starts with no scenes`() {
        val vm = SceneViewModel()
        assertTrue(vm.scenes.isEmpty())
        assertNull(vm.currentSceneId.value)
        assertNull(vm.currentScene)
    }

    @Test
    fun `adding a scene selects it and sizes the canvas from the presenter screen`() {
        val vm = SceneViewModel()
        val scene = vm.addScene("Welcome")

        assertEquals(listOf("Welcome"), vm.scenes.map { it.name })
        assertEquals(scene.id, vm.currentSceneId.value, "a new scene becomes the current one")
        assertEquals(1920, scene.canvasWidth)
        assertEquals(1080, scene.canvasHeight)
    }

    @Test
    fun `a zero-sized presenter screen falls back to 1080p`() {
        // Guards the `if (bounds.width > 0)` fallback — a detached/!invalid display reports 0.
        every { presenterScreenBounds() } returns Rectangle(0, 0, 0, 0)
        val scene = SceneViewModel().addScene()
        assertEquals(1920, scene.canvasWidth)
        assertEquals(1080, scene.canvasHeight)
    }

    @Test
    fun `scenes get distinct ids even with the same name`() {
        val vm = SceneViewModel()
        repeat(3) { vm.addScene("Same") }
        assertEquals(3, vm.scenes.map { it.id }.toSet().size)
    }

    @Test
    fun `removing the current scene falls back to another and clears the selection`() {
        val vm = SceneViewModel()
        val first = vm.addScene("First")
        val second = vm.addScene("Second")
        vm.addSource(text("s1"))
        assertNotNull(vm.selectedSourceId.value)

        vm.removeScene(second.id)
        assertEquals(listOf("First"), vm.scenes.map { it.name })
        assertEquals(first.id, vm.currentSceneId.value, "should fall back to a remaining scene")
        assertNull(vm.selectedSourceId.value, "the removed scene's source must not stay selected")
    }

    @Test
    fun `removing the last scene leaves nothing current`() {
        val vm = SceneViewModel()
        val only = vm.addScene("Only")
        vm.removeScene(only.id)
        assertTrue(vm.scenes.isEmpty())
        assertNull(vm.currentSceneId.value)
    }

    @Test
    fun `removing a non-current scene leaves the current one alone`() {
        val vm = SceneViewModel()
        val first = vm.addScene("First")
        val second = vm.addScene("Second") // now current
        vm.removeScene(first.id)
        assertEquals(second.id, vm.currentSceneId.value)
    }

    @Test
    fun `removing an unknown scene is a no-op`() {
        val vm = vmWithScene()
        vm.removeScene("no-such-scene")
        assertEquals(1, vm.scenes.size)
    }

    @Test
    fun `selecting a scene clears the source selection`() {
        val vm = SceneViewModel()
        val a = vm.addScene("A")
        val b = vm.addScene("B")
        vm.addSource(text("s1"))
        assertNotNull(vm.selectedSourceId.value)

        vm.selectScene(a.id)
        assertEquals(a.id, vm.currentSceneId.value)
        assertNull(vm.selectedSourceId.value, "a selection from the previous scene must not persist")

        vm.selectScene(b.id)
        assertEquals(b.id, vm.currentSceneId.value)
    }

    @Test
    fun `renaming changes only the name`() {
        val vm = SceneViewModel()
        val scene = vm.addScene("Before")
        vm.renameScene(scene.id, "After")
        assertEquals("After", vm.scenes.single().name)
        assertEquals(scene.id, vm.scenes.single().id, "the id must be stable across a rename")
    }

    @Test
    fun `duplicating copies the sources under a new id and selects the copy`() {
        val vm = vmWithScene()
        vm.addSource(text("s1"))
        vm.addSource(text("s2"))
        val original = assertNotNull(vm.currentScene)

        val copy = assertNotNull(vm.duplicateScene(original.id))
        assertEquals("Scene 1 (copy)", copy.name)
        assertTrue(copy.id != original.id, "the duplicate needs its own id")
        assertEquals(listOf("s1", "s2"), copy.sources.map { it.id }, "sources should come along")
        assertEquals(copy.id, vm.currentSceneId.value, "the copy becomes current")
        assertEquals(2, vm.scenes.size)
    }

    @Test
    fun `duplicating an unknown scene returns null`() {
        assertNull(vmWithScene().duplicateScene("no-such-scene"))
    }

    @Test
    fun `canvas size can be overridden on the current scene`() {
        val vm = vmWithScene()
        vm.updateCanvasSize(1280, 720)
        assertEquals(1280, vm.currentScene?.canvasWidth)
        assertEquals(720, vm.currentScene?.canvasHeight)
    }

    // ── Sources ─────────────────────────────────────────────────────────────────

    @Test
    fun `adding a source appends it and selects it`() {
        val vm = vmWithScene()
        vm.addSource(text("a"))
        vm.addSource(text("b"))
        assertEquals(listOf("a", "b"), vm.sourceIds)
        assertEquals("b", vm.selectedSourceId.value, "the newest source becomes selected")
    }

    @Test
    fun `adding a source with no scene is a no-op`() {
        val vm = SceneViewModel() // no scene at all
        vm.addSource(text("orphan"))
        assertNull(vm.selectedSourceId.value)
        assertTrue(vm.scenes.isEmpty())
    }

    @Test
    fun `removing a source drops it and clears the selection only if it was selected`() {
        val vm = vmWithScene()
        vm.addSource(text("a"))
        vm.addSource(text("b"))

        vm.selectSource("a")
        vm.removeSource("b")
        assertEquals(listOf("a"), vm.sourceIds)
        assertEquals("a", vm.selectedSourceId.value, "removing an unselected source keeps the selection")

        vm.removeSource("a")
        assertNull(vm.selectedSourceId.value)
    }

    @Test
    fun `selectedSource resolves against the current scene`() {
        val vm = vmWithScene()
        vm.addSource(text("a", name = "Lower third"))
        assertEquals("Lower third", vm.selectedSource?.name)

        vm.selectSource(null)
        assertNull(vm.selectedSource)

        vm.selectSource("nonexistent")
        assertNull(vm.selectedSource, "a stale id must resolve to null, not throw")
    }

    // ── Z-order ─────────────────────────────────────────────────────────────────

    @Test
    fun `sources move up and down the layer stack`() {
        val vm = vmWithScene()
        listOf("a", "b", "c").forEach { vm.addSource(text(it)) }

        vm.moveSourceUp("c")
        assertEquals(listOf("a", "c", "b"), vm.sourceIds)

        vm.moveSourceDown("a")
        assertEquals(listOf("c", "a", "b"), vm.sourceIds)
    }

    @Test
    fun `moving past either end of the stack is a no-op`() {
        val vm = vmWithScene()
        listOf("a", "b").forEach { vm.addSource(text(it)) }

        vm.moveSourceUp("a")     // already first
        vm.moveSourceDown("b")   // already last
        vm.moveSourceUp("ghost") // not present
        vm.moveSourceDown("ghost")
        assertEquals(listOf("a", "b"), vm.sourceIds)
    }

    // ── Per-source flags ────────────────────────────────────────────────────────

    @Test
    fun `visibility and lock toggle independently and preserve the rest of the source`() {
        val vm = vmWithScene()
        vm.addSource(SceneSource.TextSource(id = "t", name = "Title", text = "Hello"))

        vm.toggleSourceVisibility("t")
        var source = assertNotNull(vm.currentScene?.sources?.single() as? SceneSource.TextSource)
        assertFalse(source.visible)
        assertFalse(source.locked, "toggling visibility must not touch the lock")
        assertEquals("Hello", source.text, "toggling must not lose the source's own fields")

        vm.toggleSourceLock("t")
        source = assertNotNull(vm.currentScene?.sources?.single() as? SceneSource.TextSource)
        assertTrue(source.locked)
        assertFalse(source.visible, "toggling the lock must not restore visibility")

        vm.toggleSourceVisibility("t")
        source = assertNotNull(vm.currentScene?.sources?.single() as? SceneSource.TextSource)
        assertTrue(source.visible)
        assertTrue(source.locked)
    }

    @Test
    fun `transform updates replace the whole transform`() {
        val vm = vmWithScene()
        vm.addSource(text("t"))
        val moved = SourceTransform(x = 0.25f, y = 0.5f, width = 0.5f, height = 0.25f, rotation = 90f, opacity = 0.4f)

        vm.updateTransform("t", moved)
        assertEquals(moved, vm.currentScene?.sources?.single()?.transform)
    }

    @Test
    fun `updateSource only touches the targeted source`() {
        val vm = vmWithScene()
        vm.addSource(SceneSource.TextSource(id = "a", name = "A", text = "first"))
        vm.addSource(SceneSource.TextSource(id = "b", name = "B", text = "second"))

        vm.updateSource("a") { (it as SceneSource.TextSource).copy(text = "edited") }

        val sources = assertNotNull(vm.currentScene?.sources)
        assertEquals("edited", (sources[0] as SceneSource.TextSource).text)
        assertEquals("second", (sources[1] as SceneSource.TextSource).text, "the sibling must be untouched")
    }

    // ── Persistence ─────────────────────────────────────────────────────────────

    @Test
    fun `scenes survive a reload from disk`() {
        val vm = vmWithScene()
        vm.addSource(SceneSource.TextSource(id = "t", name = "Title", text = "Welcome"))
        vm.updateCanvasSize(1280, 720)

        val reloaded = SceneViewModel() // reads scenes.json in init
        val scene = assertNotNull(reloaded.scenes.singleOrNull(), "expected exactly one persisted scene")
        assertEquals("Scene 1", scene.name)
        assertEquals(1280, scene.canvasWidth)
        assertEquals("Welcome", (scene.sources.single() as SceneSource.TextSource).text)
    }

    @Test
    fun `a corrupt scenes file degrades to no scenes instead of throwing`() {
        scenesFile.parentFile.mkdirs()
        scenesFile.writeText("{ this is not valid json")
        assertTrue(SceneViewModel().scenes.isEmpty())
    }
}

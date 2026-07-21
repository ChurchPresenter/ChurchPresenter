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
import kotlin.test.assertTrue

/**
 * The three per-source-type `when` blocks — hide, lock, and move/resize — applied to all eleven
 * kinds of source.
 *
 * Each of those functions is eleven near-identical branches of
 * `is SceneSource.X -> source.copy(field = …)`. The compiler guarantees the list is *complete*
 * (the `when` is exhaustive over a sealed class, so a twelfth source type will not compile until
 * it is added), but it cannot catch a branch that copies the wrong field — `copy(visible =
 * !source.locked)` type-checks perfectly and would make one source type's eye icon toggle its
 * padlock instead. That is what these tests pin down, type by type.
 *
 * Each case also asserts the *other* flag is untouched, since that is the shape the mistake takes.
 */
class SceneViewModelSourceTypesTest {

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

    /** One of every source type, each with a distinguishable id. */
    private fun everySourceType(): List<SceneSource> = listOf(
        SceneSource.ImageSource(id = "image", name = "Image", filePath = "/pics/logo.png"),
        SceneSource.TextSource(id = "text", name = "Text", text = "Welcome"),
        SceneSource.ColorSource(id = "color", name = "Color", color = "#112233"),
        SceneSource.VideoSource(id = "video", name = "Video", filePath = "/clips/loop.mp4"),
        SceneSource.BrowserSource(id = "browser", name = "Browser", url = "https://example.test"),
        SceneSource.ShapeSource(id = "shape", name = "Shape"),
        SceneSource.ClockSource(id = "clock", name = "Clock"),
        SceneSource.QRCodeSource(id = "qr", name = "QR"),
        SceneSource.CameraSource(id = "camera", name = "Camera"),
        SceneSource.ScreenCaptureSource(id = "screen", name = "Screen"),
        SceneSource.BibleSource(id = "bible", name = "Bible"),
    )

    /** A scene holding one of every source type. */
    private fun vmWithEverySource(): SceneViewModel = SceneViewModel().apply {
        addScene("Everything")
        everySourceType().forEach { addSource(it) }
    }

    private fun SceneViewModel.source(id: String): SceneSource =
        currentScene?.sources?.first { it.id == id } ?: error("no source $id")

    // ── Hiding ──────────────────────────────────────────────────────────────────

    @Test
    fun `every source type can be hidden and shown again`() {
        val vm = vmWithEverySource()

        everySourceType().forEach { original ->
            vm.toggleSourceVisibility(original.id)
            assertFalse(vm.source(original.id).visible, "${original.id} did not hide")

            vm.toggleSourceVisibility(original.id)
            assertTrue(vm.source(original.id).visible, "${original.id} did not come back")
        }
    }

    @Test
    fun `hiding a source of any type leaves its lock alone`() {
        val vm = vmWithEverySource()
        everySourceType().forEach { vm.toggleSourceLock(it.id) } // lock them all first

        everySourceType().forEach { original ->
            vm.toggleSourceVisibility(original.id)
            assertTrue(
                vm.source(original.id).locked,
                "${original.id}: hiding must not touch the padlock — the two flags share eleven copy-pasted branches",
            )
        }
    }

    // ── Locking ─────────────────────────────────────────────────────────────────

    @Test
    fun `every source type can be locked and unlocked`() {
        val vm = vmWithEverySource()

        everySourceType().forEach { original ->
            vm.toggleSourceLock(original.id)
            assertTrue(vm.source(original.id).locked, "${original.id} did not lock")

            vm.toggleSourceLock(original.id)
            assertFalse(vm.source(original.id).locked, "${original.id} did not unlock")
        }
    }

    @Test
    fun `locking a source of any type leaves its visibility alone`() {
        val vm = vmWithEverySource()

        everySourceType().forEach { original ->
            vm.toggleSourceLock(original.id)
            assertTrue(
                vm.source(original.id).visible,
                "${original.id}: locking a layer must not make it disappear from the screen",
            )
        }
    }

    // ── Moving and resizing ─────────────────────────────────────────────────────

    @Test
    fun `every source type can be moved and resized`() {
        val vm = vmWithEverySource()

        everySourceType().forEachIndexed { index, original ->
            val transform = SourceTransform(
                x = index * 10f,
                y = index * 20f,
                width = 640f,
                height = 360f,
                rotation = 45f,
                opacity = 0.5f,
            )

            vm.updateTransform(original.id, transform)

            assertEquals(transform, vm.source(original.id).transform, "${original.id} did not take the transform")
        }
    }

    @Test
    fun `moving a source of any type leaves its flags and identity alone`() {
        val vm = vmWithEverySource()
        everySourceType().forEach { vm.toggleSourceLock(it.id) }

        everySourceType().forEach { original ->
            vm.updateTransform(original.id, SourceTransform(x = 5f, y = 5f))

            val moved = vm.source(original.id)
            assertEquals(original.name, moved.name, "${original.id} lost its name")
            assertEquals(original::class, moved::class, "${original.id} came back as a different type")
            assertTrue(moved.locked, "${original.id} lost its lock")
            assertTrue(moved.visible, "${original.id} lost its visibility")
        }
    }

    @Test
    fun `a source's own settings survive being hidden, locked and moved`() {
        // The branches rebuild each source with `copy`, so a branch that constructed the wrong
        // subtype would drop everything specific to it.
        val vm = SceneViewModel().apply { addScene("Everything") }
        vm.addSource(SceneSource.BrowserSource(id = "browser", name = "Stream", url = "https://example.test", fps = 15))

        vm.toggleSourceVisibility("browser")
        vm.toggleSourceLock("browser")
        vm.updateTransform("browser", SourceTransform(x = 100f, y = 200f))

        val browser = vm.source("browser") as SceneSource.BrowserSource
        assertEquals("https://example.test", browser.url)
        assertEquals(15, browser.fps)
        assertEquals(100f, browser.transform.x)
        assertFalse(browser.visible)
        assertTrue(browser.locked)
    }

    // ── Unknown ids ─────────────────────────────────────────────────────────────

    @Test
    fun `toggling something that is not there changes nothing`() {
        val vm = vmWithEverySource()
        val before = vm.currentScene?.sources

        vm.toggleSourceVisibility("no-such-source")
        vm.toggleSourceLock("no-such-source")
        vm.updateTransform("no-such-source", SourceTransform(x = 99f))

        assertEquals(before, vm.currentScene?.sources)
    }
}

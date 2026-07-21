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
 * Whether a canvas survives the app closing.
 *
 * Scenes are written to `~/.churchpresenter/scenes.json` after every edit and read back in the
 * constructor, and both halves swallow their exceptions — a write that fails, or a source type
 * that cannot be serialized, is silent. So the only way a broken save surfaces is an operator
 * reopening the app to find last week's canvas, or a scene missing the source they spent an hour
 * positioning. These tests make that loud instead: each one edits, builds a *second* view model
 * against the same file, and checks what came back.
 *
 * The all-source-types round trip is the one that earns its keep: adding a new `SceneSource`
 * subtype without wiring up serialization would otherwise fail silently in exactly this way.
 *
 * [SceneViewModelTest] covers the in-memory model; this covers what reaches disk.
 */
class SceneViewModelPersistenceTest {

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

    /** A fresh view model reading whatever is on disk right now — i.e. the app restarted. */
    private fun reopened(): SceneViewModel = SceneViewModel()

    private fun vmWithScene(name: String = "Scene 1"): SceneViewModel =
        SceneViewModel().also { it.addScene(name) }

    private fun text(id: String, name: String = id) =
        SceneSource.TextSource(id = id, name = name, text = name)

    // ── Every edit reaches the disk ─────────────────────────────────────────────

    @Test
    fun `a new scene is saved as soon as it is created`() {
        vmWithScene("Welcome Loop")

        assertEquals(listOf("Welcome Loop"), reopened().scenes.map { it.name })
    }

    @Test
    fun `a removed scene is gone after a restart`() {
        val vm = vmWithScene("Keep")
        val doomed = vm.addScene("Remove Me")

        vm.removeScene(doomed.id)

        assertEquals(listOf("Keep"), reopened().scenes.map { it.name })
    }

    @Test
    fun `a rename is saved`() {
        val vm = vmWithScene("Old Name")
        vm.renameScene(vm.currentScene!!.id, "New Name")

        assertEquals(listOf("New Name"), reopened().scenes.map { it.name })
    }

    @Test
    fun `a duplicated scene is saved`() {
        val vm = vmWithScene("Original")
        vm.addSource(text("t1"))

        vm.duplicateScene(vm.scenes.first().id)

        val reloaded = reopened().scenes
        assertEquals(2, reloaded.size)
        assertTrue(reloaded.all { it.sources.map { s -> s.id } == listOf("t1") }, "the copy keeps the sources")
        assertEquals(2, reloaded.map { it.id }.toSet().size, "the copy is a separate scene on disk")
    }

    @Test
    fun `a canvas resize is saved`() {
        val vm = vmWithScene()
        vm.updateCanvasSize(1280, 720)

        val reloaded = reopened().scenes.single()
        assertEquals(1280, reloaded.canvasWidth)
        assertEquals(720, reloaded.canvasHeight)
    }

    @Test
    fun `an added source is saved`() {
        val vm = vmWithScene()
        vm.addSource(text("t1", "Lower Third"))

        val reloaded = reopened().scenes.single().sources.single()
        assertEquals("t1", reloaded.id)
        assertEquals("Lower Third", reloaded.name)
    }

    @Test
    fun `a removed source is gone after a restart`() {
        val vm = vmWithScene()
        vm.addSource(text("t1"))
        vm.addSource(text("t2"))

        vm.removeSource("t1")

        assertEquals(listOf("t2"), reopened().scenes.single().sources.map { it.id })
    }

    @Test
    fun `a layer reorder is saved`() {
        val vm = vmWithScene()
        vm.addSource(text("a"))
        vm.addSource(text("b"))

        // NB: these names are list-relative, not visual. Sources are drawn in list order, so the
        // LAST one is front-most — which is why the tab's "move forward" button calls
        // moveSourceDown and "move backward" calls moveSourceUp.
        vm.moveSourceDown("a")

        assertEquals(
            listOf("b", "a"),
            reopened().scenes.single().sources.map { it.id },
            "z-order is the whole point of a compositor — it has to survive a restart",
        )
    }

    @Test
    fun `hiding and locking a source are saved`() {
        val vm = vmWithScene()
        vm.addSource(text("t1"))

        vm.toggleSourceVisibility("t1")
        vm.toggleSourceLock("t1")

        val reloaded = reopened().scenes.single().sources.single()
        assertFalse(reloaded.visible)
        assertTrue(reloaded.locked)
    }

    @Test
    fun `a moved and resized source keeps its position`() {
        val vm = vmWithScene()
        vm.addSource(text("t1"))

        vm.updateTransform("t1", SourceTransform(x = 120f, y = 240f, width = 640f, height = 360f, rotation = 15f))

        val reloaded = reopened().scenes.single().sources.single().transform
        assertEquals(120f, reloaded.x)
        assertEquals(240f, reloaded.y)
        assertEquals(640f, reloaded.width)
        assertEquals(360f, reloaded.height)
        assertEquals(15f, reloaded.rotation, "an operator who nudged a layer into place must not lose it")
    }

    @Test
    fun `an edited source property is saved`() {
        val vm = vmWithScene()
        vm.addSource(text("t1"))

        vm.updateSource("t1") { (it as SceneSource.TextSource).copy(text = "Welcome to church", fontSize = 96) }

        val reloaded = reopened().scenes.single().sources.single() as SceneSource.TextSource
        assertEquals("Welcome to church", reloaded.text)
        assertEquals(96, reloaded.fontSize)
    }

    // ── Every source type survives ──────────────────────────────────────────────

    @Test
    fun `every kind of source survives a restart`() {
        // Guards the silent failure mode: a new SceneSource subtype that is not serializable makes
        // saveScenes() throw into its own catch, and the whole canvas quietly stops persisting.
        val vm = vmWithScene()
        val sources = listOf(
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
        sources.forEach { vm.addSource(it) }

        val reloaded = reopened().scenes.single().sources

        assertEquals(sources.map { it.id }, reloaded.map { it.id }, "a source type missing from the file is silent")
        assertEquals(
            sources.map { it::class.simpleName },
            reloaded.map { it::class.simpleName },
            "each source must come back as its own type, not a fallback",
        )
    }

    @Test
    fun `a source's own settings survive, not just its type`() {
        val vm = vmWithScene()
        vm.addSource(SceneSource.BrowserSource(id = "browser", name = "Stream", url = "https://example.test", fps = 15))
        vm.addSource(SceneSource.QRCodeSource(id = "qr", name = "Giving", content = "https://give.example.test"))

        val reloaded = reopened().scenes.single().sources
        assertEquals("https://example.test", (reloaded[0] as SceneSource.BrowserSource).url)
        assertEquals(15, (reloaded[0] as SceneSource.BrowserSource).fps)
        assertEquals("https://give.example.test", (reloaded[1] as SceneSource.QRCodeSource).content)
    }

    @Test
    fun `a transform applies to any source type`() {
        val vm = vmWithScene()
        vm.addSource(SceneSource.ClockSource(id = "clock", name = "Clock"))
        vm.addSource(SceneSource.ShapeSource(id = "shape", name = "Shape"))

        vm.updateTransform("clock", SourceTransform(x = 10f, y = 20f))
        vm.updateTransform("shape", SourceTransform(x = 30f, y = 40f))

        val reloaded = reopened().scenes.single().sources
        assertEquals(10f, reloaded.first { it.id == "clock" }.transform.x)
        assertEquals(40f, reloaded.first { it.id == "shape" }.transform.y)
    }

    // ── What is deliberately not persisted ──────────────────────────────────────

    @Test
    fun `reopening lands on the first scene with nothing selected inside it`() {
        val vm = vmWithScene("Welcome Loop")
        vm.addScene("Second Scene")
        vm.selectScene(vm.scenes.first().id)
        vm.addSource(text("t1"))
        vm.selectSource("t1")

        val reopened = reopened()

        assertEquals(listOf("Welcome Loop", "Second Scene"), reopened.scenes.map { it.name })
        assertEquals(
            "Welcome Loop",
            reopened.currentScene?.name,
            "the tab opens on a scene rather than on an empty canvas the operator has to pick from",
        )
        assertNull(
            reopened.selectedSourceId.value,
            "which layer was selected is session state — it must not come back with a handle already on it",
        )
    }

    @Test
    fun `reopening an empty file selects nothing`() {
        assertTrue(reopened().scenes.isEmpty())
        assertNull(reopened().currentSceneId.value, "there is no first scene to open")
    }

    @Test
    fun `a reopened scene can be picked up and edited again`() {
        val first = vmWithScene("Welcome Loop")
        first.addSource(text("t1"))

        val second = reopened()
        second.selectScene(second.scenes.single().id)
        second.addSource(text("t2"))

        assertEquals(listOf("t1", "t2"), reopened().scenes.single().sources.map { it.id })
    }

    // ── Bad files ───────────────────────────────────────────────────────────────

    @Test
    fun `an empty scenes file is treated as no scenes`() {
        scenesFile.parentFile.mkdirs()
        scenesFile.writeText("")

        assertTrue(reopened().scenes.isEmpty(), "an interrupted write must not stop the tab from opening")
    }

    @Test
    fun `an unknown key in the file is ignored rather than fatal`() {
        val vm = vmWithScene("Good Scene")
        scenesFile.writeText(scenesFile.readText().replace("\"name\"", "\"fieldFromANewerBuild\": 1, \"name\""))

        assertEquals(
            listOf("Good Scene"),
            reopened().scenes.map { it.name },
            "a field added by a newer build must not cost the operator their canvas",
        )
        assertNotNull(vm.currentScene)
    }

    /**
     * Documents a KNOWN LIMITATION: the file is decoded as a single list, so one scene using a
     * source type this build doesn't know — what a newer version would write — fails the whole
     * decode, and *every* scene is lost rather than just that one. Unknown *fields* are tolerated
     * (above); unknown source *types* are not.
     *
     * Downgrading after using a newer build is the way to reach this. If it is ever worth fixing,
     * the fix is per-entry decoding, and this expectation becomes "the readable scenes survive".
     */
    @Test
    fun `one scene using an unknown source type discards the whole file -- known limitation`() {
        val vm = vmWithScene("Good Scene")
        val saved = scenesFile.readText()
        val futureScene = """{"id":"future","name":"Future","sources":[{"type":"NewSourceKind"}]}"""
        scenesFile.writeText(saved.trimEnd().dropLast(1) + ",$futureScene]")

        assertTrue(reopened().scenes.isEmpty(), "one unreadable scene takes the readable ones with it")
        assertNotNull(vm.currentScene, "the still-open session keeps what it had in memory")
    }

    @Test
    fun `a canvas that cannot be saved keeps working`() {
        // Both halves of persistence swallow their exceptions, so the failure has to be provoked to
        // be tested at all: a directory standing where the file belongs makes every read and write
        // throw. The operator should still get a usable canvas for the rest of the service.
        scenesFile.delete()
        scenesFile.mkdirs()

        val vm = SceneViewModel()
        vm.addScene("Still Works")
        vm.addSource(text("t1"))

        assertEquals(listOf("Still Works"), vm.scenes.map { it.name })
        assertEquals(listOf("t1"), vm.currentScene?.sources?.map { it.id })
        assertTrue(scenesFile.isDirectory, "nothing was written, as expected")
    }

    @Test
    fun `saving over a corrupt file repairs it`() {
        scenesFile.parentFile.mkdirs()
        scenesFile.writeText("not json at all")

        val vm = reopened()
        assertTrue(vm.scenes.isEmpty())
        vm.addScene("Fresh Start")

        assertEquals(listOf("Fresh Start"), reopened().scenes.map { it.name }, "the operator can always start over")
    }
}

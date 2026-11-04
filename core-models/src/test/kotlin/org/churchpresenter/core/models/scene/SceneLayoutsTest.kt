package org.churchpresenter.core.models.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A scene with a landscape and a portrait layout (#608): which one an area draws, what the second one
 * looks like, and where a layer starts in it before anyone has moved it there.
 */
class SceneLayoutsTest {

    private val tolerance = 0.0001f

    private fun assertNear(expected: Float, actual: Float, message: String? = null) =
        assertEquals(expected, actual, tolerance, message)

    private val title = SceneSource.TextSource(
        id = "title",
        name = "Title",
        transform = SourceTransform(x = 0.1f, y = 0.85f, width = 0.8f, height = 0.1f),
    )
    private val capture = SceneSource.ScreenCaptureSource(id = "capture", name = "Capture")

    private fun landscape(alternate: SceneAlternateLayout? = SceneAlternateLayout()) = Scene(
        id = "s",
        canvasWidth = 1920,
        canvasHeight = 1080,
        sources = listOf(capture, title),
        alternate = alternate,
    )

    // ── alternateScene ──────────────────────────────────────────────────────────

    @Test
    fun `a scene with one layout is its own alternate`() {
        val scene = landscape(alternate = null)
        assertSame(scene, scene.alternateScene())
    }

    @Test
    fun `the second layout is the canvas turned sideways, with the same layers`() {
        val turned = landscape().alternateScene()

        assertEquals(1080 to 1920, turned.canvasWidth to turned.canvasHeight)
        assertEquals(listOf("capture", "title"), turned.sources.map { it.id })
        assertEquals("s", turned.id, "still the same scene")
    }

    @Test
    fun `a position set in the second layout is the one it draws`() {
        val moved = SourceTransform(x = 0.05f, y = 0.05f, width = 0.4f, height = 0.2f)
        val turned = landscape(SceneAlternateLayout(mapOf("title" to moved))).alternateScene()

        assertEquals(moved, turned.sources.first { it.id == "title" }.transform)
        assertEquals(title.text, (turned.sources.first { it.id == "title" } as SceneSource.TextSource).text)
    }

    @Test
    fun `a layer not moved in the second layout starts where the main one puts it, turned`() {
        val turned = landscape().alternateScene()

        assertEquals(
            capture.transform.turnedFor(capture, 1920, 1080, 1080, 1920),
            turned.sources.first { it.id == "capture" }.transform,
        )
    }

    // ── forArea ─────────────────────────────────────────────────────────────────

    @Test
    fun `an area the scene's own shape draws the main layout`() {
        val scene = landscape()
        assertSame(scene, scene.forArea(1920f, 1080f))
        assertSame(scene, scene.forArea(1440f, 1080f), "4:3 is still wider than tall")
    }

    @Test
    fun `an area of the other orientation draws the second layout`() {
        val scene = landscape()
        val drawn = scene.forArea(1080f, 1920f)
        assertEquals(1080 to 1920, drawn.canvasWidth to drawn.canvasHeight)
    }

    @Test
    fun `a portrait scene's second layout is the landscape one`() {
        val scene = Scene(canvasWidth = 1080, canvasHeight = 1920, alternate = SceneAlternateLayout())
        assertSame(scene, scene.forArea(1080f, 1920f))
        assertEquals(1920 to 1080, scene.forArea(1920f, 1080f).let { it.canvasWidth to it.canvasHeight })
    }

    @Test
    fun `a scene with one layout draws it on any area`() {
        val scene = landscape(alternate = null)
        assertSame(scene, scene.forArea(1080f, 1920f))
    }

    @Test
    fun `a square area or one with no size keeps the main layout`() {
        val scene = landscape()
        assertSame(scene, scene.forArea(500f, 500f))
        assertSame(scene, scene.forArea(0f, 1920f))
        assertSame(scene, scene.forArea(1080f, 0f))
    }

    @Test
    fun `a square canvas counts as landscape`() {
        assertTrue(Scene(canvasWidth = 1000, canvasHeight = 1000).isLandscape)
        assertTrue(!Scene(canvasWidth = 1000, canvasHeight = 1001).isLandscape)
    }

    // ── turnedFor ───────────────────────────────────────────────────────────────

    @Test
    fun `a full-screen capture fills the turned canvas too`() {
        // It used to keep its shape, a full-width band across the middle of a portrait screen (#937).
        val t = SourceTransform().turnedFor(capture, 1920, 1080, 1080, 1920)

        assertEquals(SourceTransform(), t)
    }

    @Test
    fun `pictures, video, captures and shapes keep their shape`() {
        val box = SourceTransform(x = 0.7f, y = 0.05f, width = 0.2f, height = 0.2f)
        val media = listOf(
            SceneSource.ImageSource(id = "1", name = "Image", filePath = "/f"),
            SceneSource.VideoSource(id = "2", name = "Video", filePath = "/f"),
            SceneSource.BrowserSource(id = "3", name = "Browser", url = "u"),
            SceneSource.ShapeSource(id = "4", name = "Circle", shapeType = "ellipse"),
            SceneSource.QRCodeSource(id = "5", name = "QR"),
            SceneSource.CameraSource(id = "6", name = "Camera"),
            SceneSource.ScreenCaptureSource(id = "7", name = "Capture"),
            SceneSource.NdiSource(id = "8", name = "NDI"),
            SceneSource.OmtSource(id = "9", name = "OMT"),
        )

        media.forEach { source ->
            val t = box.turnedFor(source, 1920, 1080, 1080, 1920)
            val before = (box.width * 1920) / (box.height * 1080)
            val after = (t.width * 1080) / (t.height * 1920)
            assertNear(before, after, "${source::class.simpleName} changed shape")
            assertNear(box.width * 1920 * 0.5625f, t.width * 1080, "${source::class.simpleName} scaled like the canvas")
        }
    }

    @Test
    fun `text keeps its size, its width up to the canvas, and room for the lines it wraps to`() {
        val t = title.transform.turnedFor(title, 1920, 1080, 1080, 1920)

        // 1536×108 px on landscape: capped at 1080 px wide, and as tall as its area needs.
        assertNear(1f, t.width)
        assertNear(1536f * 108f / 1080f / 1920f, t.height)
        assertNear(0.9f, t.y + t.height / 2f, "a lower third stays at the bottom")
    }

    @Test
    fun `a text box that already fits keeps its pixel size`() {
        val clock = SceneSource.ClockSource(
            id = "c",
            name = "Clock",
            transform = SourceTransform(x = 0.4f, y = 0.4f, width = 0.1f, height = 0.1f),
        )
        val t = clock.transform.turnedFor(clock, 1920, 1080, 1080, 1920)

        assertNear(192f, t.width * 1080)
        assertNear(108f, t.height * 1920)
    }

    @Test
    fun `Bible verses are treated as text`() {
        val verse = SceneSource.BibleSource(id = "v", name = "Verse", transform = title.transform)
        assertEquals(
            title.transform.turnedFor(title, 1920, 1080, 1080, 1920),
            verse.transform.turnedFor(verse, 1920, 1080, 1080, 1920),
        )
    }

    @Test
    fun `a colour keeps its fractions, so a background stays full screen`() {
        val colour = SceneSource.ColorSource(id = "bg", name = "Background")
        val box = SourceTransform(x = 0.1f, y = 0.2f, width = 0.3f, height = 0.4f)
        assertEquals(box, box.turnedFor(colour, 1920, 1080, 1080, 1920))
    }

    @Test
    fun `a box that was inside the canvas is kept inside`() {
        // Near the right edge, and wider than the portrait canvas once its size in pixels is kept.
        val t = title.transform.copy(x = 0.7f, width = 0.3f).turnedFor(title, 1920, 1080, 1080, 1920)

        assertTrue(t.x >= 0f && t.x + t.width <= 1f + tolerance, "x=${t.x} width=${t.width}")
        assertTrue(t.y >= 0f && t.y + t.height <= 1f + tolerance, "y=${t.y} height=${t.height}")
    }

    @Test
    fun `a box that was already past an edge is left past it`() {
        val offCanvas = SourceTransform(x = 1.2f, y = 0.1f, width = 0.2f, height = 0.2f)
        val t = offCanvas.turnedFor(capture, 1920, 1080, 1080, 1920)
        assertNear(1.3f, t.x + t.width / 2f, "moved no further than its centre")
    }

    @Test
    fun `a canvas with no size leaves the box alone`() {
        val box = SourceTransform(x = 0.1f, width = 0.5f)
        assertEquals(box, box.turnedFor(capture, 0, 1080, 1080, 1920))
        assertEquals(box, box.turnedFor(capture, 1920, 1080, 1080, 0))
    }

    @Test
    fun `rotation and opacity come across unchanged`() {
        val box = SourceTransform(width = 0.5f, height = 0.5f, rotation = 30f, opacity = 0.4f)
        val t = box.turnedFor(capture, 1920, 1080, 1080, 1920)
        assertEquals(30f, t.rotation)
        assertEquals(0.4f, t.opacity)
    }

    // ── withTransform ───────────────────────────────────────────────────────────

    @Test
    fun `every source type moves without losing anything else`() {
        val moved = SourceTransform(x = 0.3f, y = 0.4f, width = 0.2f, height = 0.1f)
        val sources: List<SceneSource> = listOf(
            SceneSource.ImageSource(id = "1", name = "Image", filePath = "/f"),
            SceneSource.TextSource(id = "2", name = "Text", text = "Hi"),
            SceneSource.ColorSource(id = "3", name = "Colour", color = "#123456"),
            SceneSource.VideoSource(id = "4", name = "Video", filePath = "/f"),
            SceneSource.BrowserSource(id = "5", name = "Browser", url = "u"),
            SceneSource.ShapeSource(id = "6", name = "Shape"),
            SceneSource.ClockSource(id = "7", name = "Clock"),
            SceneSource.QRCodeSource(id = "8", name = "QR"),
            SceneSource.CameraSource(id = "9", name = "Camera"),
            SceneSource.ScreenCaptureSource(id = "10", name = "Capture"),
            SceneSource.NdiSource(id = "11", name = "NDI"),
            SceneSource.BibleSource(id = "12", name = "Verse"),
        )

        sources.forEach { source ->
            val result = source.withTransform(moved)
            assertEquals(moved, result.transform, "${source::class.simpleName} did not move")
            assertEquals(source, result.withTransform(source.transform), "${source::class.simpleName} lost a field")
        }
    }

    // ── Turning with the canvas (#937) ──────────────────────────────────────────

    private val colour = SceneSource.ColorSource(id = "colour", name = "Colour")
    private val picture = SceneSource.ImageSource(id = "picture", name = "Picture", filePath = "/p.png")

    @Test
    fun `a layer over the whole canvas covers the whole turned one, whatever it holds`() {
        listOf(picture, title, colour, capture).forEach { source ->
            val t = SourceTransform(x = 0.004f, y = 0f, width = 0.995f, height = 1f)
                .turnedFor(source, 1920, 1080, 1080, 1920)
            assertEquals(SourceTransform(), t, "${source.name} fills the portrait canvas")
        }
    }

    @Test
    fun `halves side by side in landscape are stacked in portrait, the left one on top`() {
        val left = SourceTransform(x = 0.008f, y = 0f, width = 0.492f, height = 1f)
        val right = SourceTransform(x = 0.5f, y = 0f, width = 0.5f, height = 1f)

        val top = left.turnedFor(picture, 1920, 1080, 1080, 1920)
        val bottom = right.turnedFor(colour, 1920, 1080, 1080, 1920)

        assertEquals(SourceTransform(x = 0f, y = 0.008f, width = 1f, height = 0.492f), top)
        assertEquals(SourceTransform(x = 0f, y = 0.5f, width = 1f, height = 0.5f), bottom)
    }

    @Test
    fun `rows across a portrait canvas sit side by side in landscape`() {
        val row = SourceTransform(x = 0f, y = 0.6f, width = 1f, height = 0.4f)
        assertEquals(
            SourceTransform(x = 0.6f, y = 0f, width = 0.4f, height = 1f),
            row.turnedFor(picture, 1080, 1920, 1920, 1080),
        )
    }

    @Test
    fun `a band across a landscape canvas stays a band`() {
        val band = SourceTransform(x = 0f, y = 0.8f, width = 1f, height = 0.2f)
        val t = band.turnedFor(colour, 1920, 1080, 1080, 1920)
        assertEquals(band, t, "a colour band keeps its fractions, as before")
    }

    @Test
    fun `a column that stops short of an edge keeps its shape`() {
        val short = SourceTransform(x = 0f, y = 0.05f, width = 0.5f, height = 0.9f)
        val t = short.turnedFor(picture, 1920, 1080, 1080, 1920)
        assertTrue(t.width < 1f, "not turned into a full-width row")
    }

    @Test
    fun `a canvas that keeps its orientation turns nothing`() {
        val column = SourceTransform(x = 0f, y = 0f, width = 0.5f, height = 1f)
        assertEquals(column, column.turnedFor(colour, 1920, 1080, 1280, 720))
    }

    @Test
    fun `the crop and rotation come across when a layer turns with the canvas`() {
        val column = SourceTransform(x = 0f, y = 0f, width = 0.5f, height = 1f, rotation = 5f, cropX = 0.2f)
        val t = column.turnedFor(picture, 1920, 1080, 1080, 1920)
        assertEquals(0.2f, t.cropX)
        assertEquals(5f, t.rotation)
    }

    @Test
    fun `opacity is the layer's own in both layouts`() {
        val faded = SceneSource.ImageSource(
            id = "picture", name = "Picture", filePath = "/p.png",
            transform = SourceTransform(opacity = 0.4f),
        )
        val scene = Scene(
            id = "s", canvasWidth = 1920, canvasHeight = 1080, sources = listOf(faded),
            alternate = SceneAlternateLayout(mapOf("picture" to SourceTransform(width = 0.5f, opacity = 1f))),
        )
        assertNear(0.4f, scene.alternateScene().sources.single().transform.opacity)
    }

    @Test
    fun `a crop starts centred`() {
        assertEquals(DEFAULT_CROP, SourceTransform().cropX)
        assertEquals(DEFAULT_CROP, SourceTransform().cropY)
    }
}

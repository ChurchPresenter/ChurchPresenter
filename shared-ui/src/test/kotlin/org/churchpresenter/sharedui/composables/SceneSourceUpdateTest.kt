package org.churchpresenter.sharedui.composables

import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneSourceUpdateTest {

    private val moved =
        SourceTransform(x = 0.25f, y = 0.5f, width = 0.4f, height = 0.3f, rotation = 15f, opacity = 0.6f)

    private fun renamesAndMoves(source: SceneSource) {
        val renamed = updateName(source, "Renamed")
        assertEquals("Renamed", renamed.name)
        assertEquals(source.id, renamed.id)
        assertEquals(source::class, renamed::class)
        assertEquals(source.transform, renamed.transform)

        val transformed = updateTransform(source, moved)
        assertEquals(moved, transformed.transform)
        assertEquals(source.name, transformed.name)
        assertEquals(source::class, transformed::class)
        assertEquals(source, updateTransform(transformed, source.transform))
    }

    @Test
    fun `an image source is renamed and moved`() =
        renamesAndMoves(SceneSource.ImageSource(id = "i", name = "Image", filePath = "/a.png"))

    @Test
    fun `a text source is renamed and moved`() = renamesAndMoves(SceneSource.TextSource(id = "t", name = "Text"))

    @Test
    fun `a color source is renamed and moved`() = renamesAndMoves(SceneSource.ColorSource(id = "c", name = "Color"))

    @Test
    fun `a video source is renamed and moved`() =
        renamesAndMoves(SceneSource.VideoSource(id = "v", name = "Video", filePath = "/a.mp4"))

    @Test
    fun `a browser source is renamed and moved`() =
        renamesAndMoves(SceneSource.BrowserSource(id = "b", name = "Browser", url = "https://example.org"))

    @Test
    fun `a shape source is renamed and moved`() = renamesAndMoves(SceneSource.ShapeSource(id = "s", name = "Shape"))

    @Test
    fun `a clock source is renamed and moved`() = renamesAndMoves(SceneSource.ClockSource(id = "k", name = "Clock"))

    @Test
    fun `a QR code source is renamed and moved`() = renamesAndMoves(SceneSource.QRCodeSource(id = "q", name = "QR"))

    @Test
    fun `a camera source is renamed and moved`() =
        renamesAndMoves(SceneSource.CameraSource(id = "cam", name = "Camera"))

    @Test
    fun `a screen capture source is renamed and moved`() =
        renamesAndMoves(SceneSource.ScreenCaptureSource(id = "sc", name = "Screen"))

    @Test
    fun `an NDI source is renamed and moved`() = renamesAndMoves(SceneSource.NdiSource(id = "n", name = "NDI"))

    @Test
    fun `an OMT source is renamed and moved`() = renamesAndMoves(SceneSource.OmtSource(id = "o", name = "OMT"))

    @Test
    fun `a Bible source is renamed and moved`() = renamesAndMoves(SceneSource.BibleSource(id = "bi", name = "Bible"))

    @Test
    fun `a source keeps its own settings through a rename`() {
        val image = SceneSource.ImageSource(id = "i", name = "Image", filePath = "/photos/a.png")
        val renamed = updateName(image, "Logo") as SceneSource.ImageSource
        assertEquals("/photos/a.png", renamed.filePath)
    }
}

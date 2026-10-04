package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasNewSourceItemsTest {

    private val names = CanvasSourceNames(
        strImage = "Image", strText = "Text", strColor = "Color", strVideo = "Video", strTimer = "Timer",
        strQrCode = "QR Code", strCamera = "Camera", strScreenCapture = "Screen Capture", strNdi = "NDI",
        strOmt = "OMT", strBrowser = "Browser", strBible = "Bible",
    )

    private val items = names.newSourceItems()

    @Test
    fun `the menu offers every kind of source, in order`() {
        assertEquals(
            listOf(
                "Image", "Text", "Color", "Video", "Timer", "QR Code",
                "Camera", "Screen Capture", "NDI", "OMT", "Browser", "Bible",
            ),
            items.map { it.first },
        )
    }

    @Test
    fun `each entry adds a source of its own kind, named as the entry reads, under the id given`() {
        val made = items.map { (label, make) -> label to make("id-$label") }

        made.forEach { (label, source) ->
            assertEquals("id-$label", source.id)
            assertEquals(label, source.name)
        }
        assertEquals(12, made.map { it.second::class }.toSet().size)
    }

    @Test
    fun `a text layer starts in the lower middle, a timer and a code start small`() {
        val text = items.first { it.first == "Text" }.second("t") as SceneSource.TextSource
        val timer = items.first { it.first == "Timer" }.second("c").transform
        val qr = items.first { it.first == "QR Code" }.second("q").transform

        assertEquals(0.25f, text.transform.x)
        assertEquals(0.4f, text.transform.y)
        assertTrue(timer.width < 0.5f && qr.width == qr.height)
    }

    @Test
    fun `a browser layer starts on a page address to finish typing`() {
        val browser = items.first { it.first == "Browser" }.second("b") as SceneSource.BrowserSource

        assertEquals("http://www.", browser.url)
        assertEquals(0.8f, browser.transform.width)
    }

    @Test
    fun `a picture or video layer starts with no file chosen`() {
        val image = items.first { it.first == "Image" }.second("i") as SceneSource.ImageSource
        val video = items.first { it.first == "Video" }.second("v") as SceneSource.VideoSource

        assertEquals("", image.filePath)
        assertEquals("", video.filePath)
    }
}

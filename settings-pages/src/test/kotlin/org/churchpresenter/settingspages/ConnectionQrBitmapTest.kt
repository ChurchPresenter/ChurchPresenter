package org.churchpresenter.settingspages

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The connection QR's bitmap — the thing an operator points a phone at to join the companion server.
 *
 * `connectionQrContent` (what the code says) already has a suite of its own; this is the other half,
 * whether that content becomes a scannable image. It used to sit inline inside `ConnectionQrDialog`'s
 * `remember`, inside a `DialogWindow` that no test can open, so nothing exercised it at all.
 *
 * **The null return is the behaviour worth having, not a tidy default.** ZXing throws on content it
 * cannot represent, and this is called during composition — so an exception would take the whole
 * dialog down rather than leave it without a code. That is why the catch exists and why an empty
 * string is tested here rather than assumed impossible.
 */
class ConnectionQrBitmapTest {

    @BeforeTest
    fun loadSkiko() {
        // Decoding the PNG goes through Skia; without the native library this throws on first use.
        TestSingletons.latchSkikoNativeLibrary()
    }

    private val realContent = "churchpresenter://connect?host=192.168.1.50&port=8765"

    @Test
    fun `a connection link becomes a bitmap of the size asked for`() {
        val bitmap = assertNotNull(connectionQrBitmap(realContent, sizePx = 256))

        assertEquals(256, bitmap.width)
        assertEquals(256, bitmap.height)
    }

    @Test
    fun `the image encodes the content, rather than being the same picture every time`() {
        // The one failure a size check cannot see: a code that renders beautifully and sends every
        // phone to the same wrong host. Two different links must not produce identical pixels.
        val a = assertNotNull(connectionQrBitmap(realContent))
        val b = assertNotNull(connectionQrBitmap("churchpresenter://connect?host=10.0.0.9&port=8765"))

        assertNotEquals(a.toPixels(), b.toPixels(), "different links must not encode identically")
    }

    @Test
    fun `the same link always encodes the same way`() {
        // The dialog re-renders on every recomposition; a code that shifted between renders would
        // be a moving target for a camera.
        assertEquals(
            assertNotNull(connectionQrBitmap(realContent)).toPixels(),
            assertNotNull(connectionQrBitmap(realContent)).toPixels(),
        )
    }

    @Test
    fun `an api key changes the code`() {
        // A key in the link is what a phone needs to be let in at all, so it has to reach the image.
        val without = assertNotNull(connectionQrBitmap(realContent))
        val with = assertNotNull(connectionQrBitmap("$realContent&apikey=s3cret"))

        assertNotEquals(without.toPixels(), with.toPixels())
    }

    @Test
    fun `the code is dark on light, not inverted`() {
        // Every other assertion here survives swapping black and white — the sizes match, the codes
        // still differ from each other and still repeat — while an inverted code is one most phone
        // cameras will not read at all. The quiet zone is the tell: with MARGIN = 1 the outer edge
        // is background, so the corner pixel must be the light one.
        val pixels = assertNotNull(connectionQrBitmap(realContent)).toPixels()
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()

        assertEquals(white, pixels.first(), "the quiet zone around the code has to be the light one")
        assertTrue(pixels.any { it == black }, "and there has to be a code inside it")
    }

    @Test
    fun `content that cannot be encoded gives null instead of throwing`() {
        // ZXing rejects empty content. Reached from inside composition, a throw here would take the
        // dialog down; null leaves it able to say it has no code.
        assertNull(connectionQrBitmap(""))
    }

    /** A stable digest of the pixels, so two bitmaps can be compared by content. */
    private fun ImageBitmap.toPixels(): List<Int> {
        val buffer = IntArray(width * height)
        readPixels(buffer)
        return buffer.toList()
    }
}

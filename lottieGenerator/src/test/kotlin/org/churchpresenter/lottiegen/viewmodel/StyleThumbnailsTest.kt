package org.churchpresenter.lottiegen.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.model.StyleCatalog
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Style menu's thumbnails: which config they are drawn for, and when they are drawn again.
 *
 * The render step is a stand-in -- a real one needs a Compose scene per style -- that answers with
 * a picture whose width is the length of the name it was drawn with, so a test can tell which
 * config a picture belongs to.
 */
class StyleThumbnailsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val drawn = ConcurrentLinkedQueue<LottieGenConfig>()

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun thumbnails(
        ids: List<String> = listOf("1", "2", "3"),
        render: suspend (String, LottieGenConfig) -> ImageBitmap? = { _, config ->
            drawn += config
            ImageBitmap(config.nameText.length.coerceAtLeast(1), 1)
        },
    ) = StyleThumbnails(scope, render = render, styleIds = { ids }, debounceMs = 0)

    private fun waitFor(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `every style is drawn once with the config being edited`() {
        val thumbs = thumbnails()
        thumbs.request(LottieGenConfig(nameText = "Pastor John", style = "2"))
        waitFor("three pictures") { thumbs.thumbnails.size == 3 }

        assertEquals(setOf("1", "2", "3"), thumbs.thumbnails.keys)
        assertEquals(listOf("1", "2", "3"), drawn.map { it.style })
        assertTrue(drawn.all { it.nameText == "Pastor John" }, "the words are kept")
    }

    @Test
    fun `asking again for the same config draws nothing new`() {
        val thumbs = thumbnails()
        val config = LottieGenConfig(nameText = "Anna")
        thumbs.request(config)
        waitFor("three pictures") { thumbs.thumbnails.size == 3 }
        thumbs.request(config.copy(style = "3"))
        thumbs.request(config.copy(animDuration = 2f, holdDuration = 9f))

        assertEquals(3, drawn.size, "neither the style nor the durations change the pictures")
    }

    @Test
    fun `a change to the text draws every style again`() {
        val thumbs = thumbnails()
        thumbs.request(LottieGenConfig(nameText = "Anna"))
        waitFor("the first set") { thumbs.thumbnails.size == 3 }
        thumbs.request(LottieGenConfig(nameText = "Anna Smith"))
        waitFor("the second set") { thumbs.thumbnails.values.all { it.width == "Anna Smith".length } }

        assertEquals(6, drawn.size)
    }

    @Test
    fun `a build overtaken by a newer config never shows`() {
        val gate = CompletableDeferred<Unit>()
        val thumbs = thumbnails { _, config ->
            if (config.nameText == "old") gate.await()
            drawn += config
            ImageBitmap(config.nameText.length, 1)
        }
        thumbs.request(LottieGenConfig(nameText = "old"))
        thumbs.request(LottieGenConfig(nameText = "newer"))
        waitFor("the newer set") { thumbs.thumbnails.size == 3 }
        gate.complete(Unit)

        assertTrue(thumbs.thumbnails.values.all { it.width == "newer".length })
        assertTrue(drawn.none { it.nameText == "old" }, "the old build was cancelled before it drew")
    }

    @Test
    fun `a style that cannot be drawn is left out rather than kept from before`() {
        val thumbs = thumbnails { _, config ->
            if (config.nameText == "blank" && config.style == "2") null else ImageBitmap(1, 1)
        }
        thumbs.request(LottieGenConfig(nameText = "fine"))
        waitFor("the first set") { thumbs.thumbnails.size == 3 }
        thumbs.request(LottieGenConfig(nameText = "blank"))
        waitFor("style 2 dropped") { "2" !in thumbs.thumbnails }

        assertEquals(setOf("1", "3"), thumbs.thumbnails.keys)
    }

    @Test
    fun `a style whose still fails to render is skipped and the rest still draw`() {
        // What StillFrame.render throws when a composition never loads.
        val thumbs = thumbnails { _, config ->
            check(config.style != "2") { "Lottie composition failed to load for off-screen rendering" }
            drawn += config
            ImageBitmap(1, 1)
        }
        thumbs.request(LottieGenConfig(nameText = "Anna"))
        waitFor("the last style") { "3" in thumbs.thumbnails }

        assertEquals(setOf("1", "3"), thumbs.thumbnails.keys)
    }

    @Test
    fun `the default catalogue is every style`() {
        val thumbs = StyleThumbnails(scope)
        assertTrue(thumbs.thumbnails.isEmpty(), "nothing is drawn until asked")
        assertEquals(60, StyleCatalog.entries.size)
    }

    @Test
    fun `the key ignores the style and the durations and nothing else`() {
        val config = LottieGenConfig(nameText = "Anna", style = "7", animDuration = 1f, holdDuration = 8f)
        val key = thumbnailKey(config)

        assertEquals("", key.style)
        assertEquals(LottieGenConfig().animDuration, key.animDuration)
        assertEquals(LottieGenConfig().holdDuration, key.holdDuration)
        assertEquals("Anna", key.nameText)
        assertEquals(thumbnailKey(config.copy(style = "9")), key)
        assertFalse(thumbnailKey(config.copy(nameText = "Bob")) == key)
    }

    @Test
    fun `pictures are drawn at the middle of the hold`() {
        // 4s in, 3s hold, 4s out: the middle of the hold is 5.5 of 11.
        assertEquals(0.5f, thumbnailProgress(LottieGenConfig(animDuration = 4f, holdDuration = 3f)), 1e-6f)
        assertEquals(0.5f, thumbnailProgress(LottieGenConfig(animDuration = 0f, holdDuration = 2f)), 1e-6f)
        assertEquals(0f, thumbnailProgress(LottieGenConfig(animDuration = 0f, holdDuration = 0f)))
    }

    @Test
    fun `a still is cropped to what it draws with a small margin, clamped to the canvas`() {
        val width = 40
        val height = 20
        val pixels = IntArray(width * height)
        pixels[10 * width + 20] = OPAQUE
        pixels[12 * width + 24] = OPAQUE

        val (cropped, region) = assertNotNull(croppedThumbnail(pixels, width, height))

        assertEquals(20 - 6, region.x0)
        assertEquals(10 - 6, region.y0)
        assertEquals((24 + 6) - (20 - 6) + 1, region.width)
        assertEquals((12 + 6).coerceAtMost(height - 1) - (10 - 6) + 1, region.height)
        assertEquals(region.width * region.height, cropped.size)
        assertEquals(OPAQUE, cropped[6 * region.width + 6], "the drawn pixel lands inside the margin")
        assertEquals(0, cropped[0], "the margin stays transparent")
    }

    @Test
    fun `a blank still has no thumbnail`() {
        assertNull(croppedThumbnail(IntArray(4 * 4), 4, 4))
    }

    private companion object {
        const val OPAQUE = -0x1000000
    }
}

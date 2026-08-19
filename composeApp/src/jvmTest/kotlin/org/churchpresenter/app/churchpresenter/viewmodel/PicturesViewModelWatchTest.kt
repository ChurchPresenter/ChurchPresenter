package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PicturesViewModelWatchTest {

    private lateinit var dir: File
    private lateinit var model: PicturesViewModel

    @BeforeTest
    fun create() {
        dir = Files.createTempDirectory("cp-pictures-watch").toFile()
        model = PicturesViewModel()
    }

    @AfterTest
    fun cleanUp() {
        model.dispose()
        dir.deleteRecursively()
    }

    private val pngBytes: ByteArray by lazy {
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()
    }

    private fun image(name: String): File = File(dir, name).apply { writeBytes(pngBytes) }

    private fun loadWith(vararg names: String): List<File> {
        val files = names.map { image(it) }
        model.loadImagesFromFolder(dir)
        return files
    }

    private fun added(file: File): Boolean =
        runBlocking { with(model) { this@runBlocking.addWatchedImage(file) } }

    private fun fire(kind: WatchEvent.Kind<*>, file: File): Boolean =
        runBlocking { with(model) { this@runBlocking.applyWatchEvent(kind, file) } }

    private fun removed(file: File): Boolean =
        runBlocking { with(model) { this@runBlocking.removeWatchedImage(file) } }

    private class PathEvent(private val name: String, private val eventKind: WatchEvent.Kind<Path>) :
        WatchEvent<Path> {
        override fun kind(): WatchEvent.Kind<Path> = eventKind
        override fun count() = 1
        override fun context(): Path = Path.of(name)
    }

    /**
     * An OVERFLOW event whose context throws rather than returning the null the JDK gives back.
     *
     * The kind has to be tested *before* the context is read, and a fake returning null cannot
     * show that: the null-safe call would satisfy the assertion on its own, so the ordering guard
     * could be deleted with the test still green. Throwing here is what pins the order.
     */
    private class OverflowEvent : WatchEvent<Any> {
        override fun kind(): WatchEvent.Kind<Any> = StandardWatchEventKinds.OVERFLOW
        override fun count() = 1
        override fun context(): Any = error("the context of an OVERFLOW event must never be read")
    }

    /** A non-OVERFLOW event whose context is null, which is what the field crash carried. */
    private class NullContextEvent : WatchEvent<Any> {
        override fun kind(): WatchEvent.Kind<Any> =
            @Suppress("UNCHECKED_CAST")
            (StandardWatchEventKinds.ENTRY_CREATE as WatchEvent.Kind<Any>)
        override fun count() = 1
        override fun context(): Any? = null
    }

    @Test
    fun `a new file lands in name order rather than at the end`() {
        loadWith("a.jpg", "c.jpg")
        val b = image("b.jpg")

        assertTrue(added(b))
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), model.images.map { it.name })
    }

    @Test
    fun `a file sorting after everything is appended`() {
        loadWith("a.jpg", "b.jpg")
        val z = image("z.jpg")

        assertTrue(added(z))
        assertEquals(listOf("a.jpg", "b.jpg", "z.jpg"), model.images.map { it.name })
    }

    @Test
    fun `a file inserted before the selection keeps the same picture selected`() {
        val files = loadWith("b.jpg", "c.jpg")
        model.selectedImageIndex = 1

        added(image("a.jpg"))

        assertEquals(2, model.selectedImageIndex)
        assertEquals(files[1], model.getCurrentImageFile())
    }

    @Test
    fun `a file inserted after the selection leaves the index alone`() {
        loadWith("a.jpg", "b.jpg")
        model.selectedImageIndex = 0

        added(image("c.jpg"))

        assertEquals(0, model.selectedImageIndex)
    }

    @Test
    fun `a file already in the list is not added twice`() {
        val files = loadWith("a.jpg")

        assertFalse(added(files[0]))
        assertEquals(1, model.images.size)
    }

    @Test
    fun `a path that no longer exists is not added`() {
        loadWith("a.jpg")
        val ghost = File(dir, "gone.jpg")

        assertFalse(added(ghost))
        assertEquals(1, model.images.size)
    }

    @Test
    fun `a directory appearing in a watched folder is not treated as a picture`() {
        loadWith("a.jpg")
        val sub = File(dir, "subfolder").apply { mkdirs() }

        assertFalse(added(sub))
        assertEquals(1, model.images.size)
    }

    @Test
    fun `a deleted file leaves the list`() {
        val files = loadWith("a.jpg", "b.jpg")

        assertTrue(removed(files[0]))
        assertEquals(listOf("b.jpg"), model.images.map { it.name })
    }

    @Test
    fun `deleting a file before the selection keeps the same picture selected`() {
        val files = loadWith("a.jpg", "b.jpg", "c.jpg")
        model.selectedImageIndex = 2

        removed(files[0])

        assertEquals(1, model.selectedImageIndex)
        assertEquals(files[2], model.getCurrentImageFile())
    }

    @Test
    fun `deleting the last file moves the selection back onto the new last one`() {
        val files = loadWith("a.jpg", "b.jpg", "c.jpg")
        model.selectedImageIndex = 2

        removed(files[2])

        assertEquals(1, model.selectedImageIndex)
        assertEquals(files[1], model.getCurrentImageFile())
    }

    @Test
    fun `deleting the only file leaves nothing selected`() {
        val files = loadWith("a.jpg")

        removed(files[0])

        assertTrue(model.images.isEmpty())
        assertEquals(null, model.getCurrentImageFile())
    }

    @Test
    fun `a file that was never in the list cannot be removed`() {
        loadWith("a.jpg")

        assertFalse(removed(File(dir, "stranger.jpg")))
        assertEquals(1, model.images.size)
    }

    @Test
    fun `a create event adds and a delete event removes`() {
        loadWith("a.jpg")
        val b = image("b.jpg")

        assertTrue(fire(StandardWatchEventKinds.ENTRY_CREATE, b))
        assertEquals(2, model.images.size)

        assertTrue(fire(StandardWatchEventKinds.ENTRY_DELETE, b))
        assertEquals(1, model.images.size)
    }

    @Test
    fun `a modify event changes nothing`() {
        val files = loadWith("a.jpg")

        assertFalse(fire(StandardWatchEventKinds.ENTRY_MODIFY, files[0]))
        assertEquals(1, model.images.size)
    }

    @Test
    fun `reordering keeps the selection on the picture that was selected`() {
        val files = loadWith("a.jpg", "b.jpg", "c.jpg")
        model.selectedImageIndex = 0

        model.moveImage(from = 0, to = 2)

        assertEquals(listOf("b.jpg", "c.jpg", "a.jpg"), model.images.map { it.name })
        assertEquals(2, model.selectedImageIndex)
        assertEquals(files[0], model.getCurrentImageFile())
    }

    @Test
    fun `reordering bumps the order version so the grid redraws`() {
        loadWith("a.jpg", "b.jpg")
        val before = model.imageOrderVersion

        model.moveImage(from = 0, to = 1)

        assertEquals(before + 1, model.imageOrderVersion)
    }

    @Test
    fun `moving an image onto itself does nothing`() {
        loadWith("a.jpg", "b.jpg")
        val before = model.imageOrderVersion

        model.moveImage(from = 1, to = 1)

        assertEquals(listOf("a.jpg", "b.jpg"), model.images.map { it.name })
        assertEquals(before, model.imageOrderVersion)
    }

    @Test
    fun `moving from or to an index that does not exist does nothing`() {
        loadWith("a.jpg", "b.jpg")
        val before = model.imageOrderVersion

        model.moveImage(from = 0, to = 5)
        model.moveImage(from = -1, to = 1)

        assertEquals(listOf("a.jpg", "b.jpg"), model.images.map { it.name })
        assertEquals(before, model.imageOrderVersion)
    }

    @Test
    fun `loading the same folder twice does not duplicate its images`() {
        loadWith("a.jpg", "b.jpg")

        model.loadImagesFromFolder(dir)

        assertEquals(listOf("a.jpg", "b.jpg"), model.images.map { it.name })
    }

    @Test
    fun `loading a folder that is not there leaves the list empty`() {
        model.loadImagesFromFolder(File(dir, "nope"))

        assertTrue(model.images.isEmpty())
    }

    @Test
    fun `an overflow event is skipped without its context being read`() {
        assertNull(model.watchedImageName(OverflowEvent()))
    }

    @Test
    fun `an event with no context is skipped rather than crashing the watcher`() {
        assertNull(model.watchedImageName(NullContextEvent()))
    }

    @Test
    fun `a picture event yields its file name`() {
        assertEquals("b.jpg", model.watchedImageName(PathEvent("b.jpg", StandardWatchEventKinds.ENTRY_CREATE)))
    }

    @Test
    fun `an event for a file that is not a picture is skipped`() {
        assertNull(model.watchedImageName(PathEvent("notes.txt", StandardWatchEventKinds.ENTRY_CREATE)))
    }

    @Test
    fun `the picture extension is matched whatever case it was written in`() {
        assertEquals("B.JPG", model.watchedImageName(PathEvent("B.JPG", StandardWatchEventKinds.ENTRY_CREATE)))
    }
}

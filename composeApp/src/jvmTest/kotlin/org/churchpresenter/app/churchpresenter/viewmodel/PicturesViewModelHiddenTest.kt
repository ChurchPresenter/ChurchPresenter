package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.HiddenItemsStore
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PictureSettings
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pictures hidden from the slideshow (#676): Next, Previous and looping pass over them, a click still
 * shows one, and they are still hidden when the folder is opened again.
 */
class PicturesViewModelHiddenTest {

    private val folder: File = Files.createTempDirectory("cp-pictures-hidden").toFile().apply {
        listOf("a", "b", "c", "d", "e").forEach { name ->
            ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", File(this, "$name.png"))
        }
    }
    private val storeFile = File(folder.parentFile, "${folder.name}-hidden.json")
    private val created = mutableListOf<PicturesViewModel>()

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        folder.deleteRecursively()
        storeFile.delete()
    }

    private fun vm(looping: Boolean = true): PicturesViewModel = PicturesViewModel(
        appSettings = AppSettings(pictureSettings = PictureSettings(isLooping = looping)),
        hiddenStore = HiddenItemsStore(storeFile),
    ).also { created += it }

    private fun PicturesViewModel.opened(): PicturesViewModel = apply { selectFolder(folder) }

    private fun PicturesViewModel.hide(vararg names: String) =
        names.forEach { name -> toggleHidden(images.first { it.name == "$name.png" }) }

    private val PicturesViewModel.current: String get() = images[selectedImageIndex].nameWithoutExtension

    @Test
    fun `Next and Previous pass over hidden pictures`() {
        val vm = vm().opened()
        vm.hide("b", "c")

        vm.nextImage()
        assertEquals("d", vm.current)
        vm.previousImage()
        assertEquals("a", vm.current)
    }

    @Test
    fun `looping wraps to the first shown picture`() {
        val vm = vm(looping = true).opened()
        vm.hide("a", "e")
        vm.selectImage(3)

        vm.nextImage()

        assertEquals("b", vm.current)
    }

    @Test
    fun `Previous from the first shown picture wraps past hidden ones at the end`() {
        val vm = vm().opened()
        vm.hide("e")

        vm.previousImage()

        assertEquals("d", vm.current)
    }

    @Test
    fun `without looping, the slideshow stops when only hidden pictures are left`() {
        val vm = vm(looping = false).opened()
        vm.hide("d", "e")
        vm.selectImage(2)
        vm.isPlaying = true

        vm.nextImage()

        assertEquals("c", vm.current, "it stays where it is")
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `with every other picture hidden, Next stays put and stops`() {
        val vm = vm(looping = true).opened()
        vm.hide("a", "b", "d", "e")
        vm.selectImage(2)
        vm.isPlaying = true

        vm.nextImage()
        vm.previousImage()

        assertEquals("c", vm.current)
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `a hidden picture can still be picked on purpose`() {
        val vm = vm().opened()
        vm.hide("c")

        vm.selectImage(2)

        assertEquals("c", vm.current)
    }

    @Test
    fun `hiding is remembered for the folder and the first shown picture is selected`() {
        vm().opened().hide("a", "b")

        val reopened = vm().opened()

        assertEquals(setOf("a.png", "b.png"), reopened.hiddenImageNames)
        assertEquals("c", reopened.current)
        assertTrue(reopened.isHidden(reopened.images[0]))
    }

    @Test
    fun `showing a picture again puts it back in the run`() {
        val vm = vm().opened()
        vm.hide("b")
        vm.hide("b")

        vm.nextImage()

        assertEquals("b", vm.current)
        assertEquals(emptySet(), vm.hiddenImageNames)
    }

    @Test
    fun `a cue's playback starts at the first shown picture`() {
        val vm = vm().opened()
        vm.hide("a")

        vm.requestPlayback(plays = 1, folderPath = folder.absolutePath)

        assertEquals("b", vm.current)
        assertTrue(vm.isPlaying)
    }

    @Test
    fun `with no folder open there is nothing to hide`() {
        val vm = vm()
        vm.toggleHidden(File(folder, "a.png"))
        assertEquals(emptySet(), vm.hiddenImageNames)
        assertFalse(storeFile.exists())
    }
}

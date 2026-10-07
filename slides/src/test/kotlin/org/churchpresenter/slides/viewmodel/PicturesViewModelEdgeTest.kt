package org.churchpresenter.slides.viewmodel

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PictureSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.SlidesOutput
import org.churchpresenter.slides.solidImage
import org.churchpresenter.slides.tempDir
import java.awt.Color
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PicturesViewModelEdgeTest {

    private val dir = tempDir("cp-pictures-vm-edge")
    private val made = mutableListOf<PicturesViewModel>()

    @AfterTest
    fun cleanUp() {
        made.forEach { runCatching { it.dispose() } }
        dir.deleteRecursively()
    }

    private fun folder(name: String = "Pics", count: Int = 3): File = File(dir, name).apply {
        mkdirs()
        repeat(count) { solidImage(this, "img$it.png", Color.DARK_GRAY) }
    }

    private fun vm(looping: Boolean = false, open: File? = folder()): PicturesViewModel =
        PicturesViewModel(AppSettings(pictureSettings = PictureSettings(isLooping = looping))).also {
            made += it
            open?.let(it::selectFolder)
        }

    @Test
    fun `moving a picture onto itself changes nothing`() {
        val vm = vm()
        val before = vm.imageOrderVersion
        vm.moveImage(1, 1)
        assertEquals(before, vm.imageOrderVersion)
    }

    @Test
    fun `moving a picture outside the folder changes nothing`() {
        val vm = vm()
        val before = vm.images.toList()
        vm.moveImage(0, 9)
        vm.moveImage(-1, 1)
        assertEquals(before, vm.images)
    }

    @Test
    fun `the selected picture stays selected when it is moved`() {
        val vm = vm()
        vm.selectImage(0)
        val selected = vm.images[0]
        vm.moveImage(0, 2)
        assertEquals(selected, vm.images[vm.selectedImageIndex])
        assertEquals(2, vm.selectedImageIndex)
    }

    @Test
    fun `with no folder open there is nothing to schedule`() {
        assertNull(vm(open = null).getScheduleData())
    }

    @Test
    fun `go live with nothing open puts nothing on the output`() {
        val output = FakeSlidesOutput()
        vm(open = null).goLive(output)
        assertEquals(Presenting.NONE, output.onAir.value)
        assertNull(output.selectedImagePath.value)
    }

    @Test
    fun `go live reports the folder as a schedule row`() {
        val output = FakeSlidesOutput()
        val rows = mutableListOf<String>()
        vm().goLive(output, onInstanceLinkSendProject = { rows += "link" }, onWentLive = { rows += "live" })
        assertEquals(listOf("live", "link"), rows)
    }

    @Test
    fun `nothing is synced to an output that is showing something else`() {
        val output = FakeSlidesOutput()
        vm().syncWithPresenter(output)
        assertNull(output.selectedImagePath.value)
    }

    @Test
    fun `pictures cued on a preview are synced there as if they were on air`() {
        val fake = FakeSlidesOutput()
        val cued = object : SlidesOutput by fake {
            override val picturesCued: Boolean get() = true
        }
        val vm = vm()
        vm.selectImage(1)
        vm.syncWithPresenter(cued)
        assertEquals(vm.images[1].absolutePath, fake.selectedImagePath.value)
    }

    @Test
    fun `the last picture synced live has nothing queued after it`() {
        val output = FakeSlidesOutput().apply { setPresentingMode(Presenting.PICTURES) }
        val vm = vm()
        vm.selectImage(2)
        vm.syncWithPresenter(output)
        assertEquals(vm.images[2].absolutePath, output.selectedImagePath.value)
        assertNull(output.nextImagePath.value)
    }

    @Test
    fun `a folder that does not exist lists nothing`() {
        val vm = vm(open = null)
        vm.loadImagesFromFolder(File(dir, "nowhere"))
        assertTrue(vm.images.isEmpty())
    }

    @Test
    fun `macOS AppleDouble companions are not listed as pictures`() {
        // CHURCH-PRESENTER-DESKTOP-9V/9W: a Mac had written ._ files beside each picture.
        val pics = folder("FromAMac", 2).apply { File(this, "._img0.png").writeBytes(byteArrayOf(0, 5, 22, 7)) }
        val vm = vm(open = pics)
        assertEquals(listOf("img0.png", "img1.png"), vm.images.map { it.name })
    }

    @Test
    fun `loading a folder twice lists each picture once`() {
        val pics = folder("Twice", 2)
        val vm = vm(open = pics)
        vm.loadImagesFromFolder(pics)
        assertEquals(2, vm.images.size)
    }

    @Test
    fun `past the last picture without looping playback stops`() {
        val vm = vm(looping = false)
        vm.selectImage(2)
        vm.togglePlayPause()
        vm.nextImage()
        assertEquals(2, vm.selectedImageIndex)
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `past the last picture while looping it starts again`() {
        val vm = vm(looping = true)
        vm.selectImage(2)
        vm.nextImage()
        assertEquals(0, vm.selectedImageIndex)
    }

    @Test
    fun `back from the first picture wraps to the last`() {
        val vm = vm()
        vm.previousImage()
        assertEquals(2, vm.selectedImageIndex)
    }

    @Test
    fun `with nothing open the arrows still reach Instance Link`() {
        val vm = vm(open = null)
        var sent = 0
        vm.nextImage { sent++ }
        vm.previousImage { sent++ }
        assertEquals(2, sent)
    }

    @Test
    fun `a hidden picture is skipped and can be shown again`() {
        val vm = vm()
        val middle = vm.images[1]
        vm.toggleHidden(middle)
        assertTrue(vm.isHidden(middle))
        assertTrue(middle.name in vm.hiddenImageNames)
        vm.selectImage(0)
        vm.nextImage()
        assertEquals(2, vm.selectedImageIndex)
        vm.toggleHidden(middle)
        assertFalse(vm.isHidden(middle))
    }

    @Test
    fun `hiding needs a folder to remember it against`() {
        val vm = vm(open = null)
        vm.toggleHidden(File(dir, "loose.png"))
        assertTrue(vm.hiddenImageNames.isEmpty())
    }

    @Test
    fun `a request to play once starts from the top`() {
        val vm = vm(looping = true)
        vm.selectImage(2)
        vm.requestPlayback(1)
        assertTrue(vm.isPlaying)
        assertFalse(vm.isLooping)
        assertEquals(0, vm.selectedImageIndex)
    }

    @Test
    fun `a request for another folder waits until it is open`() {
        val vm = vm()
        vm.requestPlayback(2, folderPath = File(dir, "Elsewhere").absolutePath)
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `a picture can be selected by its path`() {
        val vm = vm()
        vm.selectImagePath(vm.images[2].absolutePath)
        assertEquals(2, vm.selectedImageIndex)
        vm.selectImagePath(File(dir, "unknown.png").absolutePath)
        assertEquals(2, vm.selectedImageIndex)
    }

    @Test
    fun `a remote folder skips a picture it could not fetch`() = runComposeUiTest {
        val source = folder("Source", 3)
        val bytes = source.listFiles()!!.sortedBy { it.name }.map { it.readBytes() }
        val vm = vm(open = null)
        vm.loadPictureFromRemote(
            folderId = "remote-1",
            folderPath = "/primary/Pictures/Source",
            imageCount = 3,
            fetchBytes = { index -> if (index == 1) null else bytes[index] },
        )
        waitUntil("the reachable pictures arrived", 10_000) { vm.images.size == 2 }
        assertEquals("/primary/Pictures/Source", vm.selectedFolderDisplayPath)
    }

    @Test
    fun `a viewmodel with no settings uses the defaults`() {
        val vm = PicturesViewModel(null).also { made += it }
        assertTrue(vm.images.isEmpty())
        assertNull(vm.selectedFolder)
    }
}

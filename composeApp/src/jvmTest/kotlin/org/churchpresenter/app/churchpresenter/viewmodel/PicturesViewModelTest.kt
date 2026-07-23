package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.PictureSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The image slideshow: which files a folder offers, where next/previous land, and what reaches the
 * screen.
 *
 * Two contracts here are easy to get subtly wrong and only show up live. Looping is asymmetric —
 * `nextImage` stops at the end when looping is off, but `previousImage` always wraps — and the
 * Instance Link callbacks fire even when this instance has no images of its own, because a
 * Controller drives the *primary's* folder rather than mirroring it.
 *
 * Real (tiny) PNGs are written to a temp folder rather than empty files, so the thumbnail decode
 * runs for real instead of throwing into a background handler.
 */
class PicturesViewModelTest {

    private lateinit var folder: File
    private val created = mutableListOf<PicturesViewModel>()

    @BeforeTest
    fun createFolder() {
        folder = Files.createTempDirectory("cp-pictures-test").toFile()
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        folder.deleteRecursively()
    }

    private fun vm(settings: AppSettings? = null): PicturesViewModel =
        PicturesViewModel(settings).also { created.add(it) }

    /** Writes a real 1x1 image so the thumbnail decoder has something valid to read. */
    private fun image(name: String, dir: File = folder): File {
        val file = File(dir, name)
        val format = when (file.extension.lowercase()) {
            "png" -> "png"
            "gif" -> "gif"
            "bmp" -> "bmp"
            else -> "jpg"
        }
        ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), format, file)
        return file
    }

    private fun other(name: String): File = File(folder, name).also { it.writeText("not an image") }

    private val PicturesViewModel.names: List<String> get() = images.map { it.name }

    private fun settings(
        directory: String = "",
        interval: Float = 5f,
        looping: Boolean = true,
        duration: Float = 500f,
        animation: String = Constants.ANIMATION_CROSSFADE,
    ) = AppSettings(
        pictureSettings = PictureSettings(
            storageDirectory = directory,
            autoScrollInterval = interval,
            isLooping = looping,
            transitionDuration = duration,
            animationType = animation,
        )
    )

    // ── Loading a folder ────────────────────────────────────────────────────────

    @Test
    fun `a fresh slideshow has no folder and no images`() {
        val vm = vm()
        assertNull(vm.selectedFolder)
        assertTrue(vm.images.isEmpty())
        assertEquals(0, vm.selectedImageIndex)
        assertFalse(vm.isPlaying)
        assertNull(vm.getCurrentImageFile())
    }

    @Test
    fun `choosing a folder loads its images in name order`() {
        image("c.jpg"); image("a.jpg"); image("b.jpg")
        val vm = vm()

        vm.selectFolder(folder)

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), vm.names)
        assertEquals(folder, vm.selectedFolder)
    }

    @Test
    fun `loading a folder twice does not duplicate images`() {
        // PicturesTab keys its LazyVerticalGrid by absolutePath; a duplicate path there is a fatal
        // IllegalArgumentException at render time. A repeated load (re-entrant calls, or the file
        // watcher racing the initial load) must stay idempotent — no path may appear twice.
        image("a.jpg"); image("b.jpg")
        val vm = vm()

        vm.selectFolder(folder)
        vm.loadImagesFromFolder(folder) // a second load without an intervening clear

        assertEquals(listOf("a.jpg", "b.jpg"), vm.names)
        val paths = vm.images.map { it.absolutePath }
        assertEquals(paths.size, paths.toSet().size, "image paths must be unique (grid key invariant)")
    }

    @Test
    fun `every supported image format is picked up, whatever the case of its extension`() {
        image("photo.jpg"); image("photo2.JPEG"); image("shot.PNG"); image("anim.gif"); image("old.bmp")
        val vm = vm()

        vm.selectFolder(folder)

        assertEquals(listOf("anim.gif", "old.bmp", "photo.jpg", "photo2.JPEG", "shot.PNG"), vm.names)
    }

    @Test
    fun `files that are not images are left out`() {
        image("photo.jpg")
        other("notes.txt"); other("song.sps"); other("README")
        val vm = vm()

        vm.selectFolder(folder)

        assertEquals(listOf("photo.jpg"), vm.names)
    }

    @Test
    fun `subfolders are not offered as images`() {
        image("photo.jpg")
        File(folder, "nested.jpg").mkdirs() // a directory that looks like an image
        val vm = vm()

        vm.selectFolder(folder)

        assertEquals(listOf("photo.jpg"), vm.names, "a folder cannot be projected")
    }

    @Test
    fun `an empty folder loads as an empty slideshow`() {
        val vm = vm()
        vm.selectFolder(folder)
        assertTrue(vm.images.isEmpty())
        assertEquals(folder, vm.selectedFolder)
    }

    @Test
    fun `a folder that is not there leaves the slideshow empty`() {
        val vm = vm()
        vm.selectFolder(File(folder, "missing"))
        assertTrue(vm.images.isEmpty(), "an unplugged drive must not throw")
    }

    @Test
    fun `choosing another folder replaces the previous images`() {
        image("first.jpg")
        val vm = vm()
        vm.selectFolder(folder)
        vm.selectImage(0)

        val second = Files.createTempDirectory("cp-pictures-other").toFile()
        try {
            image("second.jpg", dir = second)
            vm.selectFolder(second)

            assertEquals(listOf("second.jpg"), vm.names, "the previous folder's images must not linger")
            assertEquals(0, vm.selectedImageIndex, "the selection belongs to the folder it came from")
        } finally {
            second.deleteRecursively()
        }
    }

    @Test
    fun `clearing resets the position and stops playback`() {
        image("a.jpg"); image("b.jpg")
        val vm = vm()
        vm.selectFolder(folder)
        vm.selectImage(1)
        vm.isPlaying = true

        vm.clearImages()

        assertTrue(vm.images.isEmpty())
        assertEquals(0, vm.selectedImageIndex)
        assertFalse(vm.isPlaying, "a slideshow with nothing in it must not stay 'playing'")
    }

    // ── Navigating ──────────────────────────────────────────────────────────────

    private fun loaded(count: Int = 3): PicturesViewModel {
        repeat(count) { image("img${it + 1}.jpg") }
        return vm().apply { selectFolder(folder) }
    }

    @Test
    fun `next and previous walk the folder`() {
        val vm = loaded()
        vm.nextImage()
        assertEquals(1, vm.selectedImageIndex)
        vm.nextImage()
        assertEquals(2, vm.selectedImageIndex)
        vm.previousImage()
        assertEquals(1, vm.selectedImageIndex)
    }

    @Test
    fun `a looping slideshow wraps from the last image back to the first`() {
        val vm = loaded()
        vm.isLooping = true
        vm.selectImage(2)

        vm.nextImage()

        assertEquals(0, vm.selectedImageIndex)
    }

    @Test
    fun `a non-looping slideshow stops on the last image and stops playing`() {
        val vm = loaded()
        vm.isLooping = false
        vm.selectImage(2)
        vm.isPlaying = true

        vm.nextImage()

        assertEquals(2, vm.selectedImageIndex, "the last slide must stay up rather than looping back")
        assertFalse(vm.isPlaying, "auto-advance has nowhere left to go")
    }

    @Test
    fun `previous wraps to the last image even with looping off`() {
        // Deliberately asymmetric with nextImage: stepping back is always a manual action, so it
        // never has an auto-advance to stop.
        val vm = loaded()
        vm.isLooping = false
        vm.selectImage(0)

        vm.previousImage()

        assertEquals(2, vm.selectedImageIndex)
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `selecting a slide out of range is ignored`() {
        val vm = loaded()
        vm.selectImage(1)

        vm.selectImage(99)
        vm.selectImage(-1)

        assertEquals(1, vm.selectedImageIndex, "a stale thumbnail click must not blank the screen")
    }

    @Test
    fun `navigation does nothing with no images loaded`() {
        val vm = vm()
        vm.nextImage()
        vm.previousImage()
        assertEquals(0, vm.selectedImageIndex)
        assertNull(vm.getCurrentImageFile())
    }

    @Test
    fun `the current file follows the position`() {
        val vm = loaded()
        assertEquals("img1.jpg", vm.getCurrentImageFile()?.name)
        vm.nextImage()
        assertEquals("img2.jpg", vm.getCurrentImageFile()?.name)
    }

    @Test
    fun `play and pause toggle`() {
        val vm = loaded()
        assertFalse(vm.isPlaying)
        vm.togglePlayPause()
        assertTrue(vm.isPlaying)
        vm.togglePlayPause()
        assertFalse(vm.isPlaying)
    }

    // ── Instance Link controller ────────────────────────────────────────────────

    @Test
    fun `next and previous reach the primary even with no images here`() {
        // Controller mode does not mirror the primary's folder, so this instance is empty by design.
        val vm = vm()
        var nexts = 0
        var previouses = 0

        vm.nextImage { nexts++ }
        vm.previousImage { previouses++ }

        assertEquals(1, nexts, "an empty controller must still drive the primary's slideshow")
        assertEquals(1, previouses)
    }

    @Test
    fun `the primary is told once per press, alongside the local move`() {
        val vm = loaded()
        var nexts = 0

        vm.nextImage { nexts++ }

        assertEquals(1, nexts)
        assertEquals(1, vm.selectedImageIndex)
    }

    // ── Reordering ──────────────────────────────────────────────────────────────

    @Test
    fun `dragging a slide moves it and marks the order changed`() {
        val vm = loaded(4)
        val version = vm.imageOrderVersion

        vm.moveImage(0, 2)

        assertEquals(listOf("img2.jpg", "img3.jpg", "img1.jpg", "img4.jpg"), vm.names)
        assertEquals(version + 1, vm.imageOrderVersion, "the grid redraws off this counter")
    }

    @Test
    fun `the slide on screen stays on screen after a reorder`() {
        val vm = loaded(4)
        vm.selectImage(0) // img1 is live

        vm.moveImage(0, 3)

        assertEquals("img1.jpg", vm.getCurrentImageFile()?.name, "reordering must not switch what is being shown")
        assertEquals(3, vm.selectedImageIndex)
    }

    @Test
    fun `reordering around the live slide keeps it live`() {
        val vm = loaded(4)
        vm.selectImage(2) // img3 is live

        vm.moveImage(0, 3) // move img1 past it

        assertEquals("img3.jpg", vm.getCurrentImageFile()?.name)
        assertEquals(1, vm.selectedImageIndex, "its index shifted, but it is the same picture")
    }

    @Test
    fun `a move that changes nothing is ignored`() {
        val vm = loaded(3)
        val version = vm.imageOrderVersion
        val before = vm.names

        vm.moveImage(1, 1)

        assertEquals(before, vm.names)
        assertEquals(version, vm.imageOrderVersion, "a no-op must not force the grid to redraw")
    }

    @Test
    fun `a move outside the slideshow is ignored`() {
        val vm = loaded(3)
        val version = vm.imageOrderVersion
        val before = vm.names

        vm.moveImage(0, 9)
        vm.moveImage(-1, 0)
        vm.moveImage(5, 5)

        assertEquals(before, vm.names)
        assertEquals(version, vm.imageOrderVersion)
    }

    // ── Adding to the schedule ──────────────────────────────────────────────────

    @Test
    fun `a folder can be handed to the schedule with its name and count`() {
        image("a.jpg"); image("b.jpg")
        val vm = vm()
        vm.selectFolder(folder)

        val (path, name, count) = vm.getScheduleData()!!

        assertEquals(folder.absolutePath, path)
        assertEquals(folder.name, name)
        assertEquals(2, count)
    }

    @Test
    fun `there is nothing to add to the schedule without a folder`() {
        assertNull(vm().getScheduleData())
    }

    // ── Going live ──────────────────────────────────────────────────────────────

    @Test
    fun `going live puts the current image on screen`() {
        val vm = loaded()
        vm.selectImage(1)
        val pm = PresenterManager()

        vm.goLive(pm)

        assertEquals(vm.images[1].absolutePath, pm.selectedImagePath.value)
        assertEquals(Presenting.PICTURES, pm.presentingMode.value)
        assertTrue(pm.showPresenterWindow.value)
    }

    @Test
    fun `going live pre-loads the following image for the transition`() {
        val vm = loaded()
        val pm = PresenterManager()

        vm.goLive(pm)

        assertEquals(vm.images[1].absolutePath, pm.nextImagePath.value, "the crossfade needs the next slide ready")
    }

    @Test
    fun `the last image has nothing queued behind it`() {
        val vm = loaded()
        vm.selectImage(2)
        val pm = PresenterManager()

        vm.goLive(pm)

        assertNull(pm.nextImagePath.value)
    }

    @Test
    fun `going live with no images leaves the screen alone`() {
        val vm = vm()
        val pm = PresenterManager()

        vm.goLive(pm)

        assertNull(pm.selectedImagePath.value)
        assertEquals(Presenting.NONE, pm.presentingMode.value, "an empty folder must not blank what is already live")
    }

    @Test
    fun `going live as a controller sends the whole folder to the primary`() {
        image("a.jpg"); image("b.jpg")
        val vm = vm()
        vm.selectFolder(folder)
        val sent = mutableListOf<ScheduleItem>()

        vm.goLive(PresenterManager()) { sent.add(it) }

        val item = sent.single() as ScheduleItem.PictureItem
        assertEquals(folder.absolutePath, item.folderPath)
        assertEquals(folder.name, item.folderName)
        assertEquals(2, item.imageCount, "the primary re-adds the folder to its own schedule to present it")
    }

    // ── Keeping the screen in step ──────────────────────────────────────────────

    @Test
    fun `moving on while pictures are live updates the screen`() {
        val vm = loaded()
        val pm = PresenterManager()
        vm.goLive(pm)

        vm.nextImage()
        vm.syncWithPresenter(pm)

        assertEquals(vm.images[1].absolutePath, pm.selectedImagePath.value)
        assertEquals(vm.images[2].absolutePath, pm.nextImagePath.value)
    }

    @Test
    fun `browsing while something else is live does not touch the screen`() {
        val vm = loaded()
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.LYRICS)

        vm.selectImage(2)
        vm.syncWithPresenter(pm)

        assertNull(pm.selectedImagePath.value, "the operator is lining up the next folder, not changing the screen")
    }

    @Test
    fun `an output locked to pictures is kept in step even while another tab is live`() {
        val vm = loaded()
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setScreenLock(1, Presenting.PICTURES)

        vm.selectImage(1)
        vm.syncWithPresenter(pm)

        assertEquals(vm.images[1].absolutePath, pm.selectedImagePath.value, "the locked screen is still showing pictures")
    }

    @Test
    fun `syncing with no images does nothing`() {
        val vm = vm()
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.PICTURES)

        vm.syncWithPresenter(pm)

        assertNull(pm.selectedImagePath.value)
    }

    // ── Settings ────────────────────────────────────────────────────────────────

    @Test
    fun `a slideshow with no saved settings uses sensible defaults`() {
        val vm = vm()
        assertEquals(5f, vm.autoScrollInterval)
        assertTrue(vm.isLooping)
        assertEquals(500f, vm.transitionDuration)
        assertEquals(AnimationType.CROSSFADE, vm.animationType)
    }

    @Test
    fun `saved playback settings are applied`() {
        val vm = vm(settings(interval = 12f, looping = false, duration = 250f))
        assertEquals(12f, vm.autoScrollInterval)
        assertFalse(vm.isLooping)
        assertEquals(250f, vm.transitionDuration)
    }

    @Test
    fun `each saved transition maps to its animation`() {
        assertEquals(AnimationType.FADE, vm(settings(animation = Constants.ANIMATION_FADE)).animationType)
        assertEquals(AnimationType.SLIDE_LEFT, vm(settings(animation = Constants.ANIMATION_SLIDE_LEFT)).animationType)
        assertEquals(AnimationType.SLIDE_RIGHT, vm(settings(animation = Constants.ANIMATION_SLIDE_RIGHT)).animationType)
        assertEquals(AnimationType.NONE, vm(settings(animation = Constants.ANIMATION_NONE)).animationType)
        assertEquals(AnimationType.CROSSFADE, vm(settings(animation = Constants.ANIMATION_CROSSFADE)).animationType)
    }

    @Test
    fun `an unrecognised saved transition falls back to the default`() {
        assertEquals(
            AnimationType.CROSSFADE,
            vm(settings(animation = "SOME_OLD_VALUE")).animationType,
            "a settings file from a newer build must not leave the slideshow with no transition"
        )
    }

    @Test
    fun `the last used folder is reopened on startup`() {
        image("a.jpg"); image("b.jpg")

        val vm = vm(settings(directory = folder.absolutePath))

        assertEquals(folder, vm.selectedFolder)
        assertEquals(listOf("a.jpg", "b.jpg"), vm.names, "the operator's folder should be there when the app comes back")
    }

    @Test
    fun `a saved folder that is gone is ignored`() {
        val vm = vm(settings(directory = File(folder, "removed").absolutePath))
        assertNull(vm.selectedFolder, "a missing drive must not stop the tab from opening")
        assertTrue(vm.images.isEmpty())
    }

    @Test
    fun `a saved path pointing at a file is ignored`() {
        val file = image("a.jpg")
        val vm = vm(settings(directory = file.absolutePath))
        assertNull(vm.selectedFolder)
    }
}

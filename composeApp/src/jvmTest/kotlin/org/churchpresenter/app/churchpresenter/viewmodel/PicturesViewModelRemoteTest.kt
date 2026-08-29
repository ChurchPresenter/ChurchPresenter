package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
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
 * A picture folder mirrored from an Instance Link primary, plus the thumbnail downscale that every
 * real photograph goes through.
 *
 * `loadPictureFromRemote` is the path taken when a mirrored schedule item names a folder that does
 * not resolve here — a network share mounted somewhere else, or not at all. It has to end with the
 * same public state a local folder produces, because everything downstream (thumbnails, navigation,
 * `syncWithPresenter`) is written against that contract and nothing else distinguishes the two.
 *
 * Three things here only show up live. Images arrive **one at a time**, unlike the synchronous local
 * load, so the presenter has to be re-synced after each one or an already-live slideshow shows
 * nothing until the operator touches something. An image the primary cannot supply must be skipped
 * rather than left as a gap or a broken file. And a folder already downloaded must not be fetched
 * again — that is the whole point of the on-disk cache.
 *
 * `user.home` is swapped per test, which both isolates the cache directory and makes it genuinely
 * empty: a cache file left by an earlier run is indistinguishable from a successful fetch, so the
 * reuse test would pass having never downloaded anything.
 */
class PicturesViewModelRemoteTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private val created = mutableListOf<PicturesViewModel>()

    @BeforeTest
    fun isolateHome() {
        // Pin both JVM-wide user.home consumers to the real test home before swapping it below: the
        // Instance Link log path, and skiko's native-library unpack directory — the thumbnail decode
        // here is the first thing in this class to touch skia.
        TestSingletons.latchToTestHome()
        TestSingletons.latchSkikoNativeLibrary()

        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-pictures-remote-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun vm(): PicturesViewModel = PicturesViewModel().also { created.add(it) }

    /** Encoded bytes of a real image, so the thumbnail decode runs rather than throwing. */
    private fun pngBytes(width: Int = 1, height: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private val PicturesViewModel.names: List<String> get() = images.map { it.name }

    private fun cacheDir(folderId: String) =
        File(tempHome, ".churchpresenter/instance-link/cache/picture-folders/$folderId")

    // ── Downloading a mirrored folder ───────────────────────────────────────────

    @Test
    fun `a mirrored folder is downloaded into the cache and shown in order`() {
        val vm = vm()
        val asked = mutableListOf<Int>()

        vm.loadPictureFromRemote(
            folderId = "folder-a",
            folderPath = "/Volumes/primary-only/Sunday",
            imageCount = 3,
        ) { index -> asked.add(index); pngBytes() }

        awaitUntil("three mirrored images") { vm.images.size == 3 }
        assertEquals(listOf("image_0000.jpg", "image_0001.jpg", "image_0002.jpg"), vm.names)
        assertEquals(listOf(0, 1, 2), asked)
        assertEquals(
            "/Volumes/primary-only/Sunday",
            vm.selectedFolder?.path,
            "the folder shown is the primary's path, even though the bytes live in the local cache"
        )
        assertTrue(
            vm.images.all { it.parentFile == cacheDir("folder-a") },
            "downloaded images must land in the cache directory keyed by folder id"
        )
        awaitUntil("thumbnails for every mirrored image") { vm.thumbnails.size == 3 }
    }

    @Test
    fun `re-selecting the same mirrored folder cannot list an image twice`() {
        // PicturesTab restarts the load whenever the selected item or its version changes, so the
        // same folder is loaded again while the first download may still be running. Both loops
        // walked the same cache directory and appended the same files, and a duplicate path is
        // fatal in the grid, which keys on absolutePath: "Key ... was already used".
        //
        // The first fetch is held open so the overlap is arranged rather than raced for: the second
        // load is started while the first is provably still in flight, which is the interleaving
        // that used to duplicate. Both waits end on a signal, never on the deadline.
        val vm = vm()
        val firstFetchStarted = java.util.concurrent.CountDownLatch(1)
        val releaseFirstFetch = java.util.concurrent.CountDownLatch(1)

        vm.loadPictureFromRemote(
            folderId = "folder-twice",
            folderPath = "/Volumes/primary-only/Twice",
            imageCount = 3,
        ) {
            firstFetchStarted.countDown()
            releaseFirstFetch.await()
            pngBytes()
        }
        assertTrue(
            firstFetchStarted.await(5, java.util.concurrent.TimeUnit.SECONDS),
            "the first load has to be in flight for this to be the reported race",
        )

        vm.loadPictureFromRemote(
            folderId = "folder-twice",
            folderPath = "/Volumes/primary-only/Twice",
            imageCount = 3,
        ) { pngBytes() }
        releaseFirstFetch.countDown()

        awaitUntil("three mirrored images") { vm.images.size == 3 }
        assertEquals(
            vm.images.map { it.absolutePath }.distinct().size,
            vm.images.size,
            "a path listed twice is what crashes the grid",
        )
        assertEquals(listOf("image_0000.jpg", "image_0001.jpg", "image_0002.jpg"), vm.names)
    }

    @Test
    fun `an image the primary cannot supply is skipped rather than left broken`() {
        val vm = vm()

        vm.loadPictureFromRemote(
            folderId = "folder-gap",
            folderPath = "/Volumes/primary-only/Gappy",
            imageCount = 3,
        ) { index -> if (index == 1) null else pngBytes() }

        awaitUntil("the two images that could be fetched") { vm.images.size == 2 }
        assertEquals(listOf("image_0000.jpg", "image_0002.jpg"), vm.names)
        assertFalse(
            File(cacheDir("folder-gap"), "image_0001.jpg").exists(),
            "a failed fetch must not leave a file behind for the next run to trust"
        )
        assertTrue(
            cacheDir("folder-gap").listFiles().orEmpty().none { it.name.endsWith(".tmp") },
            "the download temp file must not survive"
        )
    }

    @Test
    fun `a folder already in the cache is not downloaded again`() {
        val first = vm()
        var fetches = 0

        first.loadPictureFromRemote(
            folderId = "folder-cached",
            folderPath = "/Volumes/primary-only/Cached",
            imageCount = 2,
        ) { fetches++; pngBytes() }
        awaitUntil("the first download") { first.images.size == 2 }
        assertEquals(2, fetches)

        val second = vm()
        second.loadPictureFromRemote(
            folderId = "folder-cached",
            folderPath = "/Volumes/primary-only/Cached",
            imageCount = 2,
        ) { fetches++; pngBytes() }
        awaitUntil("the cached folder to reappear") { second.images.size == 2 }

        assertEquals(2, fetches, "a folder mirrored once must come off disk the next time")
        assertEquals(listOf("image_0000.jpg", "image_0001.jpg"), second.names)
    }

    @Test
    fun `mirroring a folder replaces whatever was loaded before`() {
        val local = Files.createTempDirectory("cp-pictures-remote-local").toFile()
        try {
            ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpg", File(local, "local.jpg"))
            val vm = vm()
            vm.selectFolder(local)
            vm.isPlaying = true
            assertEquals(listOf("local.jpg"), vm.names)

            vm.loadPictureFromRemote(
                folderId = "folder-replace",
                folderPath = "/Volumes/primary-only/Replacement",
                imageCount = 1,
            ) { pngBytes() }

            awaitUntil("the mirrored image") { vm.images.size == 1 }
            assertEquals(listOf("image_0000.jpg"), vm.names, "the previous folder's images must not linger")
            assertEquals(0, vm.selectedImageIndex)
            assertFalse(vm.isPlaying, "a new folder starts stopped")
        } finally {
            local.deleteRecursively()
        }
    }

    @Test
    fun `each downloaded image is pushed to a screen already showing pictures`() {
        // Images arrive one at a time here, and PicturesTab's own reactive sync only reruns on an
        // index or mode change — so without the explicit per-image re-sync a slideshow that was
        // already live would sit on nothing until the operator pressed something.
        val vm = vm()
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.PICTURES)

        vm.loadPictureFromRemote(
            folderId = "folder-live",
            folderPath = "/Volumes/primary-only/Live",
            imageCount = 2,
            presenterManager = pm,
        ) { pngBytes() }

        awaitUntil("both mirrored images") { vm.images.size == 2 }
        awaitUntil("the second image to be queued behind the first") { pm.nextImagePath.value != null }
        assertEquals(vm.images[0].absolutePath, pm.selectedImagePath.value)
        assertEquals(vm.images[1].absolutePath, pm.nextImagePath.value, "the crossfade needs the next slide ready")
    }

    @Test
    fun `mirroring with nothing else live leaves the screen alone`() {
        val vm = vm()
        val pm = PresenterManager()

        vm.loadPictureFromRemote(
            folderId = "folder-idle",
            folderPath = "/Volumes/primary-only/Idle",
            imageCount = 1,
            presenterManager = pm,
        ) { pngBytes() }

        awaitUntil("the mirrored image") { vm.images.size == 1 }
        assertNull(
            pm.selectedImagePath.value,
            "downloading a folder in the background must not take over what is on screen"
        )
    }

    @Test
    fun `a folder the primary reports as empty downloads nothing`() {
        val vm = vm()
        var fetches = 0

        vm.loadPictureFromRemote(
            folderId = "folder-empty",
            folderPath = "/Volumes/primary-only/Empty",
            imageCount = 0,
        ) { fetches++; pngBytes() }

        awaitUntil("the cache directory to be created") { cacheDir("folder-empty").isDirectory }
        assertEquals(0, fetches)
        assertTrue(vm.images.isEmpty())
        assertEquals("/Volumes/primary-only/Empty", vm.selectedFolder?.path)
    }

    // ── Thumbnails ──────────────────────────────────────────────────────────────

    @Test
    fun `a full-size photograph is downscaled for the thumbnail grid`() {
        // Grid thumbnails are capped at 400px on the long edge; a folder of untouched camera JPEGs
        // held at full size is what makes the tab run the machine out of memory.
        val vm = vm()

        vm.loadPictureFromRemote(
            folderId = "folder-big",
            folderPath = "/Volumes/primary-only/Big",
            imageCount = 1,
        ) { pngBytes(width = 800, height = 600) }

        awaitUntil("the thumbnail to decode") { vm.thumbnails.size == 1 }
        val thumbnail = vm.thumbnails.values.single()
        assertEquals(400, maxOf(thumbnail.width, thumbnail.height), "the long edge is capped at 400px")
        assertEquals(300, minOf(thumbnail.width, thumbnail.height), "the aspect ratio is preserved")
    }

    @Test
    fun `an image already smaller than a thumbnail is left at its own size`() {
        val vm = vm()

        vm.loadPictureFromRemote(
            folderId = "folder-small",
            folderPath = "/Volumes/primary-only/Small",
            imageCount = 1,
        ) { pngBytes(width = 120, height = 90) }

        awaitUntil("the thumbnail to decode") { vm.thumbnails.size == 1 }
        val thumbnail = vm.thumbnails.values.single()
        assertEquals(120, thumbnail.width, "upscaling a small image would only blur it")
        assertEquals(90, thumbnail.height)
    }
}

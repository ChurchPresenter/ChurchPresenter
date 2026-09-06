package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two watchers mutating one image list at the same time.
 *
 * `selectFolder` cancels the previous folder's watch job and repopulates `_images` immediately, but
 * coroutine cancellation is cooperative: the outgoing watcher can still be inside `pollEvents()`,
 * working through a batch of events against a list that has already been emptied and refilled for
 * another folder. `removeWatchedImage` read an index and then removed it in a separate step, so the
 * index went stale between the two and `removeAt` threw `IndexOutOfBoundsException` from a
 * coroutine where nothing catches it.
 *
 * That took CI red intermittently — run 31793721082 on `main`, and again on PR #298, both as
 * `index: 5, size: 5` — surfacing inside whichever `PicturesTabScreenshotTest` case happened to be
 * running when an earlier test's leaked watcher woke up. It reads as a flaky screenshot test and is
 * nothing of the kind.
 *
 * These are stress cases, so they cannot prove the interleaving happened on any given run — but
 * they can only ever *fail* on the unfixed code, they end when the threads finish rather than on a
 * timeout, and they cost a few hundred milliseconds.
 */
class PicturesViewModelWatchRaceTest {

    private lateinit var folder: File
    private val created = mutableListOf<PicturesViewModel>()

    @BeforeTest
    fun createFolder() {
        folder = Files.createTempDirectory("cp-pictures-race").toFile()
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        folder.deleteRecursively()
    }

    private fun vm(): PicturesViewModel = PicturesViewModel().also { created.add(it) }

    /** A real 1x1 PNG, so the thumbnail decode the add path launches has something valid to read. */
    private fun image(name: String): File = File(folder, name).also {
        ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", it)
    }

    /** Runs [work] on two threads released together, and returns anything either of them threw. */
    private fun raceInPairs(work: (lane: Int) -> Unit): List<Throwable> {
        val thrown = Collections.synchronizedList(mutableListOf<Throwable>())
        val start = CountDownLatch(1)
        val lanes = (0..1).map { lane ->
            thread {
                start.await()
                runCatching { work(lane) }.onFailure { thrown.add(it) }
            }
        }
        start.countDown()
        lanes.forEach { it.join() }
        return thrown
    }

    @Test
    fun `two watchers removing the same files never throw`() {
        val viewModel = vm()
        val files = (0 until 12).map { image("photo%02d.png".format(it)) }

        repeat(12) { round ->
            viewModel.clearImages()
            viewModel.loadImagesFromFolder(folder)
            assertEquals(files.size, viewModel.images.size, "round $round starts from a full folder")

            // Both lanes are a watcher draining ENTRY_DELETE events for every file — the outgoing
            // one and its replacement, which is exactly the overlap selectFolder leaves behind.
            val thrown = raceInPairs {
                runBlocking { with(viewModel) { files.forEach { removeWatchedImage(it) } } }
            }

            assertTrue(thrown.isEmpty(), "round $round threw: ${thrown.firstOrNull()}")
            assertTrue(viewModel.images.isEmpty(), "round $round removed every file exactly once")
        }
    }

    @Test
    fun `a watcher removing while the folder is reloaded never throws`() {
        val viewModel = vm()
        val files = (0 until 12).map { image("photo%02d.png".format(it)) }
        viewModel.loadImagesFromFolder(folder)

        // One lane removes, the other empties and refills underneath it — clearImages() from the
        // caller's thread against a watcher that has not noticed its cancellation yet.
        val thrown = raceInPairs { lane ->
            repeat(12) {
                if (lane == 0) {
                    runBlocking { with(viewModel) { files.forEach { file -> removeWatchedImage(file) } } }
                } else {
                    viewModel.clearImages()
                    viewModel.loadImagesFromFolder(folder)
                }
            }
        }

        assertTrue(thrown.isEmpty(), "a reload racing a removal threw: ${thrown.firstOrNull()}")
        // Whichever lane finished last decides the contents; the invariant is that the list is
        // internally consistent, with no duplicates and no file that is not on disk.
        assertEquals(viewModel.images.distinct().size, viewModel.images.size, "no duplicate entries")
        assertTrue(viewModel.images.all { it in files }, "no entry outside the fixture folder")
    }

    @Test
    fun `a cancelled watcher stops removing`() {
        val viewModel = vm()
        val file = image("photo00.png")
        viewModel.loadImagesFromFolder(folder)

        // A watcher whose job is already cancelled must leave the list alone: by the time it wakes,
        // clearImages() has repopulated it for a different folder and the removal would be wrong.
        val removed = runBlocking {
            val cancelled = CoroutineScope(Job())
            cancelled.cancel()
            with(viewModel) { with(cancelled) { removeWatchedImage(file) } }
        }

        assertEquals(false, removed, "a cancelled watcher reports no change")
        assertTrue(file in viewModel.images, "and leaves the file in the list")
    }

    /**
     * A whole-list read is a copy, taken under the same lock every write takes.
     *
     * Sentry CHURCH-PRESENTER-DESKTOP-67: `MainDesktop` walked `images` directly to report the
     * folder to the companion server. Every *write* took the lock, that read did not, and taking a
     * `SnapshotStateList`'s iterator while another thread mutates it throws
     * `ConcurrentModificationException` — on the event thread, where it is fatal. A single-index
     * read cannot fail that way, which is why only the whole-list read moved behind
     * [PicturesViewModel.imagesSnapshot].
     *
     * **The interleaving itself is not covered.** The two tests above can be driven into their race
     * from the public API; this one cannot — several hundred rounds of reading against a lane doing
     * nothing but clearing and refilling never reproduced it, so a stress test here would pass just
     * as happily on the unlocked version and would be worse than no test at all. What is pinned is
     * the property that makes the fix work: the result is a detached copy, so nothing the caller
     * does with it can be walking live state.
     */
    @Test
    fun `a whole-list read hands back a detached copy`() {
        val viewModel = vm()
        val files = (0 until 4).map { image("photo%02d.png".format(it)) }
        viewModel.loadImagesFromFolder(folder)

        val snapshot = viewModel.imagesSnapshot()
        assertEquals(files.size, snapshot.size, "the copy is what the list held when it was taken")

        viewModel.clearImages()
        assertTrue(viewModel.images.isEmpty(), "the list itself emptied")
        assertEquals(files.size, snapshot.size, "and the copy did not follow it")
    }
}

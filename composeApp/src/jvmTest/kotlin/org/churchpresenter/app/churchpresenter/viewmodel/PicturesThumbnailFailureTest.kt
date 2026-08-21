package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What happens to a picture the app cannot decode.
 *
 * The grid draws "Loading…" for any file with no thumbnail, and the decode used to swallow its
 * exception — so a corrupt or truncated image sat on "Loading…" for the rest of the session, with no
 * error shown, logged or recorded anywhere. It also made every wait for "no thumbnail still loading"
 * unsatisfiable, which is what took `main` red on CI run 31232339922: thirty seconds spent on five
 * 480x300 gradients, a timeout that could never have been fixed by making it longer.
 *
 * The contract now is that **every file ends up in exactly one of `thumbnails` or
 * `thumbnailFailures`** — never neither, which is what "still loading, for ever" was.
 *
 * **Not covered here: the folder watcher's retry.** A file copied into a watched folder is seen the
 * instant it is created and usually before it is fully written, so the watcher re-reads it a few
 * times before giving up — without that, marking it unreadable on the first attempt would turn the
 * ordinary "drop some photos in" flow into a grid of permanent errors, which is worse than the bug
 * being fixed. Proving it would mean winning a race against a real write, and a test that has to be
 * timed to land inside a 240ms window is exactly the flake AGENT.md forbids. The retry is a bounded
 * loop around [PicturesViewModel]'s decode, which the cases below do cover.
 */
class PicturesThumbnailFailureTest {

    private lateinit var folder: File
    private val created = mutableListOf<PicturesViewModel>()

    @BeforeTest
    fun createFolder() {
        folder = Files.createTempDirectory("cp-thumb-failure").toFile()
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        folder.deleteRecursively()
    }

    private fun viewModel() = PicturesViewModel().also { created.add(it) }

    private fun writeReadable(name: String) {
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", File(folder, name))
    }

    /** A file with a picture extension that is not a picture — what a truncated copy looks like. */
    private fun writeUndecodable(name: String) {
        File(folder, name).writeText("this is not a PNG")
    }

    /**
     * Polls until every image has resolved. Ends on the positive signal itself, so a pass costs
     * milliseconds; the deadline only exists so a regression fails instead of hanging.
     */
    private fun PicturesViewModel.awaitResolved(timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (images.isNotEmpty() && images.all { it in thumbnails || it in thumbnailFailures }) return
            Thread.sleep(10)
        }
        throw AssertionError(
            "thumbnails never resolved: ${images.size} images, ${thumbnails.size} decoded, " +
                "${thumbnailFailures.size} failed — a file in none of them is the 'Loading… for ever' bug"
        )
    }

    @Test
    fun `a picture that cannot be decoded is recorded as failed rather than left loading`() {
        writeUndecodable("broken.png")
        val vm = viewModel()
        vm.selectFolder(folder)
        vm.awaitResolved()

        val broken = File(folder, "broken.png")
        assertFalse(broken in vm.thumbnails, "it did not decode, so there is no bitmap for it")
        assertContains(vm.thumbnailFailures, broken, "and the failure must be recorded, not swallowed")
        assertTrue(
            vm.thumbnailFailures.getValue(broken).isNotEmpty(),
            "with a reason, so the tile and the log can say what went wrong"
        )
    }

    @Test
    fun `an unreadable picture is reported with what is wrong with it, and never with its name`() {
        // The report used to carry Skia's `Failed to Image::makeFromEncoded` and the file name and
        // nothing else — the same sentence for an empty placeholder, a truncated copy and a file
        // that is not a picture at all, titled with a name belonging to whoever reported it.
        writeUndecodable("holiday snap.png")
        val vm = viewModel()

        val reported = runBlocking { vm.decodeThumbnail(File(folder, "holiday snap.png")) }

        assertNotNull(reported, "an unreadable picture is worth a report")
        assertContains(reported, "ext=png")
        assertContains(
            reported,
            "imageio=none",
            message = "no reader claimed it — that is what makes it broken"
        )
        assertFalse("holiday" in reported, "picture names stay on the machine: $reported")
    }

    @Test
    fun `a file with no bytes in it yet fails its tile but is not reported`() {
        // A cloud placeholder or a copy that has not started is not something the app got wrong,
        // and reporting one buries the files that genuinely could not be read.
        File(folder, "still-syncing.png").createNewFile()
        val vm = viewModel()
        val empty = File(folder, "still-syncing.png")

        val reported = runBlocking { vm.decodeThumbnail(empty) }

        assertNull(reported, "nothing to report: $reported")
        assertContains(
            vm.thumbnailFailures,
            empty,
            "the tile still has to say the picture is not there"
        )
    }

    @Test
    fun `a readable picture decodes and is not marked as failed`() {
        writeReadable("fine.png")
        val vm = viewModel()
        vm.selectFolder(folder)
        vm.awaitResolved()

        assertContains(vm.thumbnails, File(folder, "fine.png"))
        assertTrue(vm.thumbnailFailures.isEmpty(), "nothing failed: ${vm.thumbnailFailures}")
    }

    @Test
    fun `one unreadable picture does not stop the others decoding`() {
        // The decode loop is sequential, so an exception escaping it would abandon every later file
        // and leave the rest of the grid on "Loading…" too.
        writeUndecodable("2 broken.png")
        writeReadable("1 first.png")
        writeReadable("3 last.png")
        val vm = viewModel()
        vm.selectFolder(folder)
        vm.awaitResolved()

        assertEquals(3, vm.images.size)
        assertContains(vm.thumbnails, File(folder, "1 first.png"))
        assertContains(vm.thumbnails, File(folder, "3 last.png"), "the file after the broken one still loads")
        assertEquals(setOf(File(folder, "2 broken.png")), vm.thumbnailFailures.keys)
    }

    @Test
    fun `every picture resolves one way or the other`() {
        // The invariant PicturesTabScreenshotTest's wait now depends on. Without it that wait is on
        // the absence of a label, which an unreadable file satisfies never rather than late.
        listOf("a.png", "c.png").forEach { writeReadable(it) }
        listOf("b.png", "d.png").forEach { writeUndecodable(it) }
        val vm = viewModel()
        vm.selectFolder(folder)
        vm.awaitResolved()

        assertEquals(4, vm.images.size)
        vm.images.forEach { file ->
            val decoded = file in vm.thumbnails
            val failed = file in vm.thumbnailFailures
            assertTrue(decoded != failed, "$file must be in exactly one of the two, not $decoded/$failed")
        }
    }

    @Test
    fun `clearing the folder forgets the failures too`() {
        writeUndecodable("broken.png")
        val vm = viewModel()
        vm.selectFolder(folder)
        vm.awaitResolved()
        assertTrue(vm.thumbnailFailures.isNotEmpty(), "precondition: something failed")

        vm.clearImages()

        assertTrue(
            vm.thumbnailFailures.isEmpty(),
            "a stale failure would mark the next folder's file unreadable: ${vm.thumbnailFailures}"
        )
    }
}

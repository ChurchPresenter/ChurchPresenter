package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dropping files onto the schedule.
 *
 * This is how most content gets into a service order in the first place — an operator drags a
 * folder of slides, a video and a deck onto the panel and expects three usable items. What decides
 * the type is `classifyDroppedFile` (covered by `DroppedFileClassifierTest`); what is pinned here
 * is the step after it, which is where the surprises live: an image is added as *its whole folder*
 * rather than as one picture, a folder with no images is ignored rather than added empty, and a
 * file of an unknown type is skipped rather than added as something wrong.
 *
 * `handleDroppedFiles` is called directly because the drop itself arrives through an AWT
 * `DropTarget` installed on the real window, which a headless test cannot deliver. Everything below
 * it — the classification, the view-model calls, the resulting schedule — is the real thing.
 *
 * `user.home` is isolated because the view model resolves its autosave path at construction.
 */
class ScheduleTabDroppedFilesTest {

    private lateinit var tempHome: File
    private lateinit var dropDir: File
    private var realHome: String? = null
    private lateinit var vm: ScheduleViewModel

    @BeforeTest
    fun isolateHome() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-drop-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        dropDir = Files.createTempDirectory("cp-schedule-drop").toFile()
        vm = ScheduleViewModel()
    }

    @AfterTest
    fun restoreHome() {
        runCatching { vm.dispose() }
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
        dropDir.deleteRecursively()
    }

    private fun file(name: String): File =
        File(dropDir, name).apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }

    private fun folder(name: String, vararg children: String): File =
        File(dropDir, name).apply {
            mkdirs()
            children.forEach { File(this, it).writeBytes(byteArrayOf(1)) }
        }

    private fun drop(vararg files: File) = handleDroppedFiles(files.toList(), vm)

    @Test
    fun `a deck is added as a presentation, keeping its type`() {
        drop(file("sermon.pptx"))

        val item = vm.scheduleItems.single() as ScheduleItem.PresentationItem
        assertEquals("sermon", item.fileName, "named without the extension")
        assertEquals("pptx", item.fileType, "the type is kept so the right renderer is used")
        assertTrue(item.filePath.endsWith("sermon.pptx"))
    }

    @Test
    fun `a video is added as local media`() {
        drop(file("bumper.mp4"))

        val item = vm.scheduleItems.single() as ScheduleItem.MediaItem
        assertEquals("bumper", item.mediaTitle)
        assertEquals("local", item.mediaType, "a dropped file is on disk, not a stream")
    }

    @Test
    fun `dropping one image adds the folder it lives in, opening on that image`() {
        // An operator dropping a photo means "show these photos" — a one-image slideshow would be
        // useless, so the whole folder becomes the picture source. But it is *this* photo they
        // dropped, so the row opens on it rather than on the folder's first (#652).
        val photos = folder("photos", "a.jpg", "b.jpg", "c.png")

        drop(File(photos, "b.jpg"))

        val item = vm.scheduleItems.single() as ScheduleItem.PictureItem
        assertEquals(photos.absolutePath, item.folderPath)
        assertEquals("photos", item.folderName)
        assertEquals(3, item.imageCount, "every image in the folder is counted, not just the one")
        assertEquals(File(photos, "b.jpg").absolutePath, item.imagePath, "and it opens on the one dropped")
        assertEquals("b.jpg", item.displayText, "named for the picture, not the folder")
    }

    @Test
    fun `dropping a folder adds it with a count of the images inside`() {
        val photos = folder("slides", "one.jpg", "two.jpeg", "notes.txt")

        drop(photos)

        val item = vm.scheduleItems.single() as ScheduleItem.PictureItem
        assertEquals(2, item.imageCount, "the text file is not an image")
    }

    @Test
    fun `a folder with no images is ignored rather than added empty`() {
        drop(folder("documents", "readme.txt", "notes.md"))

        assertTrue(
            vm.scheduleItems.isEmpty(),
            "an empty picture item would present a blank screen: ${vm.scheduleItems}",
        )
    }

    @Test
    fun `a lottie file is added as a lower third`() {
        drop(file("welcome.json"))

        val item = vm.scheduleItems.single() as ScheduleItem.LowerThirdItem
        assertEquals("welcome", item.presetLabel)
    }

    @Test
    fun `a file of an unknown type is skipped, not added as something wrong`() {
        drop(file("accounts.xlsx"), file("archive.zip"))

        assertTrue(vm.scheduleItems.isEmpty(), "got ${vm.scheduleItems}")
    }

    @Test
    fun `dropping several files at once adds each of them, in the order dropped`() {
        val photos = folder("gallery", "x.jpg")

        drop(file("talk.pdf"), file("music.mp3"), photos)

        assertEquals(
            listOf("PresentationItem", "MediaItem", "PictureItem"),
            vm.scheduleItems.map { it::class.simpleName },
        )
    }

    @Test
    fun `the ones it understands are added even when the drop also holds junk`() {
        drop(file("notes.txt"), file("sermon.key"), file("unknown.xyz"))

        assertEquals(1, vm.scheduleItems.size, "got ${vm.scheduleItems}")
        assertTrue(vm.scheduleItems.single() is ScheduleItem.PresentationItem)
    }

    // ── What it could not use, and says so ──────────────────────────────────────────────────────
    //
    // Skipping used to be silent: no row, no message. A drop that added nothing looked exactly like
    // drag-and-drop being broken, which is how it came to be reported as missing. The names come
    // back so the panel can say which file it could not take — the tab draws them, and that one step
    // is all that is left untested here.

    @Test
    fun `a file it cannot use is named in what comes back`() {
        assertEquals(listOf("STT-blog-topics.md"), drop(file("STT-blog-topics.md")))
    }

    @Test
    fun `a folder with no images is named too, not just quietly dropped`() {
        // The other half of the silence: this folder is skipped by a different branch entirely.
        assertEquals(listOf("documents"), drop(folder("documents", "readme.txt")))
    }

    @Test
    fun `nothing is named when every file was added`() {
        val skipped = drop(file("talk.pdf"), file("music.mp3"), file("welcome.json"))

        assertTrue(skipped.isEmpty(), "a drop that worked has nothing to report: $skipped")
        assertEquals(3, vm.scheduleItems.size)
    }

    @Test
    fun `only the unusable ones are named when a drop holds both`() {
        val skipped = drop(file("sermon.key"), file("accounts.xlsx"), file("archive.zip"))

        assertEquals(listOf("accounts.xlsx", "archive.zip"), skipped)
        assertEquals(1, vm.scheduleItems.size, "the deck still went in: ${vm.scheduleItems}")
    }
}

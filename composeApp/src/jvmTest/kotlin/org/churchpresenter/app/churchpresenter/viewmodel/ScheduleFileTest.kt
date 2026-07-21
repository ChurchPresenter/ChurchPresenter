package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Saving and reopening a service. `.cps` files are AES-encrypted JSON, so a serialization or
 * encryption regression doesn't produce a visibly broken file — it produces one that simply
 * refuses to open, potentially on a Sunday morning with the previous week's plan inside.
 *
 * Both entry points go through the platform file dialog; MockK replaces `FileChooser.platformInstance`
 * with one that returns a temp path, so the real save/load pipeline runs without any UI.
 */
class ScheduleFileTest {

    private lateinit var home: File
    private var realHome: String? = null
    private lateinit var chooser: FileChooser
    private val created = mutableListOf<ScheduleViewModel>()

    @BeforeTest
    fun setUp() {
        // Pin the JVM-wide log path to the real test home before swapping user.home below.
        TestSingletons.latchToTestHome()

        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-schedule-file-test").toFile()
        System.setProperty("user.home", home.absolutePath)

        chooser = mockk()
        mockkObject(FileChooser.Companion)
        every { FileChooser.platformInstance } returns chooser
    }

    @AfterTest
    fun tearDown() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        unmockkObject(FileChooser.Companion)
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun vm(): ScheduleViewModel = ScheduleViewModel().also { created.add(it) }

    /** Points both the save and open dialogs at [file]. */
    private fun chooseFile(file: File) {
        coEvery { chooser.save(any(), any(), any(), any()) } returns file.toPath()
        coEvery { chooser.chooseSingle(any(), any(), any(), any()) } returns file.toPath()
    }

    private fun cancelDialogs() {
        coEvery { chooser.save(any(), any(), any(), any()) } returns null
        coEvery { chooser.chooseSingle(any(), any(), any(), any()) } returns null
    }

    private fun scheduleFile(name: String = "service.cps") = File(home, name)

    private val ScheduleViewModel.titles: List<String>
        get() = scheduleItems.map {
            when (it) {
                is ScheduleItem.SongItem -> it.title
                is ScheduleItem.LabelItem -> it.text
                is ScheduleItem.BibleVerseItem -> it.bookName
                else -> it.id
            }
        }

    // ── Round-trip ──────────────────────────────────────────────────────────────

    @Test
    fun `a service survives save and reopen`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val saver = vm()
        saver.addSong(1, "Amazing Grace", "Hymnal")
        saver.addBibleVerse("John", 3, 16, "For God so loved the world")
        saver.addLabel("Offering", "#FFFFFF", "#000000")
        saver.saveScheduleAs()

        assertTrue(file.exists(), "the schedule file should have been written")

        val loader = vm()
        loader.loadSchedule()

        assertEquals(3, loader.scheduleItems.size)
        assertEquals(listOf("Amazing Grace", "John", "Offering"), loader.titles)
    }

    @Test
    fun `item order is preserved exactly`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val saver = vm()
        listOf("First", "Second", "Third", "Fourth").forEachIndexed { i, t -> saver.addSong(i + 1, t, "Hymnal") }
        saver.moveItemToBottom(saver.scheduleItems.first().id) // deliberately not in add order
        val expected = saver.titles
        saver.saveScheduleAs()

        val loader = vm()
        loader.loadSchedule()
        assertEquals(expected, loader.titles, "a reordered service must reopen in the same order")
    }

    @Test
    fun `per-item notes survive the round-trip`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val saver = vm()
        saver.addSong(1, "Amazing Grace", "Hymnal")
        val id = saver.scheduleItems.single().id
        saver.setNote(id, "Key of G, no repeat")
        saver.saveScheduleAs()

        val loader = vm()
        loader.loadSchedule()
        assertEquals("Key of G, no repeat", loader.getNote(loader.scheduleItems.single().id))
    }

    @Test
    fun `item ids are stable across a round-trip so notes stay attached`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val saver = vm()
        saver.addSong(1, "Amazing Grace", "Hymnal")
        val originalId = saver.scheduleItems.single().id
        saver.saveScheduleAs()

        val loader = vm()
        loader.loadSchedule()
        assertEquals(originalId, loader.scheduleItems.single().id, "regenerated ids would orphan every note")
    }

    @Test
    fun `every item type survives the round-trip`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val saver = vm()
        saver.addSong(1, "Song", "Hymnal")
        saver.addBibleVerse("John", 3, 16, "text")
        saver.addLabel("Label", "#FFF", "#000")
        saver.addWebsite("https://example.org", "Site")
        saver.addPicture("/pics", "Pics", 3)
        saver.addPresentation("/deck.pptx", "deck.pptx", 10, "pptx")
        saver.addMedia("/clip.mp4", "Clip", "local")
        val expectedTypes = saver.scheduleItems.map { it::class.simpleName }
        saver.saveScheduleAs()

        val loader = vm()
        loader.loadSchedule()
        assertEquals(expectedTypes, loader.scheduleItems.map { it::class.simpleName })
    }

    @Test
    fun `non-ascii content survives the round-trip`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val saver = vm()
        saver.addSong(1, "Удивительная благодать", "Гимны")
        saver.saveScheduleAs()

        val loader = vm()
        loader.loadSchedule()
        assertEquals("Удивительная благодать", loader.titles.single())
    }

    // ── Encryption ──────────────────────────────────────────────────────────────

    @Test
    fun `the saved file is not readable plain text`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val saver = vm()
        saver.addSong(1, "Amazing Grace", "Hymnal")
        saver.saveScheduleAs()

        val raw = file.readText()
        assertFalse("Amazing Grace" in raw, "schedule contents should not be stored in the clear")
        assertFalse(raw.trimStart().startsWith("{"), "the payload should not be bare JSON")
    }

    // ── Loading edge cases ──────────────────────────────────────────────────────

    @Test
    fun `loading replaces the current service rather than appending`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val saver = vm()
        saver.addSong(1, "Saved Song", "Hymnal")
        saver.saveScheduleAs()

        val loader = vm()
        loader.addSong(9, "Pre-existing", "Hymnal")
        loader.loadSchedule()

        assertEquals(listOf("Saved Song"), loader.titles, "the previous service must be replaced")
    }

    @Test
    fun `loading clears the undo history`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val saver = vm()
        saver.addSong(1, "Saved", "Hymnal")
        saver.saveScheduleAs()

        val loader = vm()
        loader.addSong(9, "Something", "Hymnal")
        assertTrue(loader.canUndo)

        loader.loadSchedule()
        assertFalse(loader.canUndo, "undo must not reach back past an opened service")
        assertFalse(loader.canRedo)
    }

    @Test
    fun `cancelling the open dialog leaves the service untouched`() = runBlocking {
        val vm = vm()
        vm.addSong(1, "Keep Me", "Hymnal")
        cancelDialogs()

        vm.loadSchedule()
        assertEquals(listOf("Keep Me"), vm.titles)
    }

    @Test
    fun `cancelling the save dialog writes nothing`() = runBlocking {
        val vm = vm()
        vm.addSong(1, "Unsaved", "Hymnal")
        cancelDialogs()

        vm.saveScheduleAs()
        assertTrue(home.listFiles()?.none { it.name.endsWith(".cps") } ?: true)
    }

    @Test
    fun `a corrupt schedule file leaves the current service intact`() = runBlocking {
        val file = scheduleFile("broken.cps")
        file.writeText("this is not an encrypted schedule")
        chooseFile(file)

        val vm = vm()
        vm.addSong(1, "Still Here", "Hymnal")
        vm.loadSchedule()

        assertEquals(listOf("Still Here"), vm.titles, "a bad file must not wipe the loaded service")
    }

    @Test
    fun `a missing file is a no-op`() = runBlocking {
        chooseFile(File(home, "does-not-exist.cps"))
        val vm = vm()
        vm.addSong(1, "Still Here", "Hymnal")
        vm.loadSchedule()
        assertEquals(listOf("Still Here"), vm.titles)
    }

    @Test
    fun `an empty service round-trips to an empty service`() = runBlocking {
        val file = scheduleFile("empty.cps")
        chooseFile(file)

        vm().saveScheduleAs()
        val loader = vm()
        loader.addSong(1, "Will Be Replaced", "Hymnal")
        loader.loadSchedule()

        assertTrue(loader.scheduleItems.isEmpty())
    }

    // ── Save vs Save As ─────────────────────────────────────────────────────────

    @Test
    fun `save reuses the path chosen by the first save-as`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val vm = vm()
        vm.addSong(1, "First", "Hymnal")
        vm.saveScheduleAs()

        // A second save must not re-prompt: point the dialog somewhere else and check it is unused.
        val decoy = scheduleFile("decoy.cps")
        chooseFile(decoy)
        vm.addSong(2, "Second", "Hymnal")
        vm.saveSchedule()

        assertFalse(decoy.exists(), "save should write to the remembered path, not re-prompt")

        chooseFile(file)
        val loader = vm()
        loader.loadSchedule()
        assertEquals(listOf("First", "Second"), loader.titles)
    }

    @Test
    fun `save with no path yet falls back to save-as`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)

        val vm = vm()
        vm.addSong(1, "First", "Hymnal")
        vm.saveSchedule() // never saved before

        assertTrue(file.exists(), "the first save must prompt and write")
    }

    @Test
    fun `a follower instance refuses to open a schedule file`() = runBlocking {
        val file = scheduleFile()
        chooseFile(file)
        val saver = vm()
        saver.addSong(1, "Saved", "Hymnal")
        saver.saveScheduleAs()

        val follower = vm()
        follower.applyRemoteSchedule(emptyList())
        follower.loadSchedule()

        assertTrue(follower.scheduleItems.isEmpty(), "a follower's schedule is driven by its primary")
    }
}

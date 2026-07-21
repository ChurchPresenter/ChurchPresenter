package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
 * Crash recovery. The autosave is the only copy of a service the operator built but never saved —
 * if the "restore?" prompt doesn't appear, or appears with a stale file from last week, the plan is
 * gone minutes before the service starts.
 *
 * The 60-second autosave loop itself is not exercised here (waiting on it would assert on timing,
 * not behaviour). Instead each test plants a file at the autosave path — written by the real
 * save pipeline, so the same encryption and serialization the loop uses is under test — and then
 * builds a ViewModel against it.
 *
 * IMPORTANT: [ScheduleViewModel] resolves the autosave path from `user.home` **at construction**,
 * so every ViewModel here is built while `user.home` points at a throwaway directory. Otherwise
 * these tests would read and delete the developer's real autosave.
 */
class ScheduleAutoSaveTest {

    private lateinit var home: File
    private var realHome: String? = null
    private lateinit var chooser: FileChooser
    private val created = mutableListOf<ScheduleViewModel>()

    @BeforeTest
    fun setUp() {
        // Pin the JVM-wide log path to the real test home before swapping user.home below.
        TestSingletons.latchToTestHome()

        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-schedule-autosave-test").toFile()
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

    private val autoSaveFile: File get() = File(home, ".churchpresenter/autosave_schedule.tmp")

    /**
     * Writes an autosave in the real on-disk format by saving a throwaway schedule through
     * [ScheduleViewModel.saveScheduleAs] and copying the result into place. [build] populates the
     * schedule that ends up in the file.
     */
    private fun plantAutoSave(build: ScheduleViewModel.() -> Unit) = runBlocking {
        val staging = File(home, "staging.cps")
        coEvery { chooser.save(any(), any(), any(), any()) } returns staging.toPath()
        vm().apply(build).saveScheduleAs()
        autoSaveFile.parentFile.mkdirs()
        staging.copyTo(autoSaveFile, overwrite = true)
        staging.delete()
    }

    private val ScheduleViewModel.titles: List<String>
        get() = scheduleItems.map { (it as ScheduleItem.SongItem).title }

    // ── Availability ────────────────────────────────────────────────────────────

    @Test
    fun `no autosave means nothing to restore`() {
        val vm = vm()
        assertFalse(vm.autoSaveAvailable())
        assertEquals(0L, vm.autoSaveSavedAt())
        assertFalse(vm.restoreAutoSave())
    }

    @Test
    fun `a fresh autosave is offered and reports when it was written`() {
        plantAutoSave { addSong(1, "Amazing Grace", "Hymnal") }
        val vm = vm()
        assertTrue(vm.autoSaveAvailable())
        assertEquals(autoSaveFile.lastModified(), vm.autoSaveSavedAt())
    }

    @Test
    fun `an empty autosave file is not offered`() {
        autoSaveFile.parentFile.mkdirs()
        autoSaveFile.writeText("")
        assertFalse(vm().autoSaveAvailable(), "a zero-byte file would restore an empty service over the real one")
    }

    @Test
    fun `an autosave from an earlier service is not offered`() {
        plantAutoSave { addSong(1, "Last Week", "Hymnal") }
        val fiveHoursAgo = System.currentTimeMillis() - 5 * 60 * 60 * 1000L
        assertTrue(autoSaveFile.setLastModified(fiveHoursAgo))

        assertFalse(vm().autoSaveAvailable(), "offering a stale plan mid-setup is worse than offering nothing")
    }

    // ── Prompting ───────────────────────────────────────────────────────────────

    @Test
    fun `the restore prompt is offered once per session, not on every panel remount`() {
        plantAutoSave { addSong(1, "Amazing Grace", "Hymnal") }
        val vm = vm()
        assertTrue(vm.shouldPromptAutoRestore())
        assertFalse(vm.shouldPromptAutoRestore(), "collapsing and re-expanding the schedule must not re-ask")
        assertTrue(vm.autoSaveAvailable(), "declining the prompt must not discard the file")
    }

    @Test
    fun `no prompt when there is nothing to restore`() {
        assertFalse(vm().shouldPromptAutoRestore())
    }

    // ── Restoring ───────────────────────────────────────────────────────────────

    @Test
    fun `restoring brings back the items and their notes`() {
        var noteId = ""
        plantAutoSave {
            addSong(1, "Amazing Grace", "Hymnal")
            addSong(2, "How Great Thou Art", "Hymnal")
            noteId = scheduleItems.first().id
            setNote(noteId, "Key of G")
        }

        val vm = vm()
        assertTrue(vm.restoreAutoSave())
        assertEquals(listOf("Amazing Grace", "How Great Thou Art"), vm.titles)
        assertEquals(noteId, vm.scheduleItems.first().id, "regenerated ids would orphan the notes")
        assertEquals("Key of G", vm.getNote(noteId))
    }

    @Test
    fun `restoring replaces whatever the operator had started`() {
        plantAutoSave { addSong(1, "Recovered", "Hymnal") }
        val vm = vm()
        vm.addSong(9, "Typed Since The Crash", "Hymnal")

        assertTrue(vm.restoreAutoSave())
        assertEquals(listOf("Recovered"), vm.titles)
    }

    @Test
    fun `restoring clears the undo history`() {
        plantAutoSave { addSong(1, "Recovered", "Hymnal") }
        val vm = vm()
        vm.addSong(9, "Something", "Hymnal")
        assertTrue(vm.canUndo)

        vm.restoreAutoSave()

        assertFalse(vm.canUndo, "undo must not reach back past a recovery")
        assertFalse(vm.canRedo)
    }

    @Test
    fun `a restored autosave is consumed so it is never offered twice`() {
        plantAutoSave { addSong(1, "Recovered", "Hymnal") }
        val vm = vm()
        vm.restoreAutoSave()

        assertFalse(autoSaveFile.exists())
        assertFalse(vm.autoSaveAvailable())
    }

    @Test
    fun `a corrupt autosave fails without wiping the current service`() {
        autoSaveFile.parentFile.mkdirs()
        autoSaveFile.writeText("this is not an encrypted schedule")

        val vm = vm()
        vm.addSong(1, "Still Here", "Hymnal")

        assertFalse(vm.restoreAutoSave())
        assertEquals(listOf("Still Here"), vm.titles)
    }

    @Test
    fun `an autosave in the pre-v2 plain array format still restores`() {
        // Older builds wrote an unencrypted bare JSON array with no notes map. Those files can still
        // be sitting on disk after an in-place upgrade, so both fallbacks have to hold.
        val legacy = listOf<ScheduleItem>(
            ScheduleItem.SongItem(id = "legacy-1", songNumber = 1, title = "Old Format", songbook = "Hymnal")
        )
        autoSaveFile.parentFile.mkdirs()
        autoSaveFile.writeText(Json.encodeToString(ListSerializer(ScheduleItem.serializer()), legacy))

        val vm = vm()
        assertTrue(vm.restoreAutoSave())
        assertEquals(listOf("Old Format"), vm.titles)
        assertEquals("", vm.getNote("legacy-1"), "a format without notes restores with none, not a crash")
    }

    @Test
    fun `a follower ignores its autosave`() {
        plantAutoSave { addSong(1, "Recovered", "Hymnal") }
        val vm = vm()
        vm.applyRemoteSchedule(emptyList())

        assertFalse(vm.restoreAutoSave(), "a follower's schedule is driven by its primary")
        assertTrue(vm.scheduleItems.isEmpty())
        assertTrue(autoSaveFile.exists(), "the file must survive for when the link drops")
    }

    // ── Clearing ────────────────────────────────────────────────────────────────

    @Test
    fun `clearAutoSave removes the file and is safe to call twice`() {
        plantAutoSave { addSong(1, "Recovered", "Hymnal") }
        val vm = vm()

        vm.clearAutoSave()
        assertFalse(autoSaveFile.exists())
        vm.clearAutoSave()
        assertFalse(vm.autoSaveAvailable())
    }

    @Test
    fun `starting a new service discards the autosave`() {
        plantAutoSave { addSong(1, "Recovered", "Hymnal") }
        val vm = vm()
        vm.newSchedule()
        assertFalse(autoSaveFile.exists(), "an explicit fresh start must not leave a recovery prompt behind")
    }

    @Test
    fun `saving a service discards the autosave`() = runBlocking {
        plantAutoSave { addSong(1, "Recovered", "Hymnal") }
        val target = File(home, "service.cps")
        coEvery { chooser.save(any(), any(), any(), any()) } returns target.toPath()

        val vm = vm()
        vm.addSong(1, "Saved For Real", "Hymnal")
        vm.saveScheduleAs()

        assertFalse(autoSaveFile.exists(), "once a real file exists there is nothing left to recover")
    }
}

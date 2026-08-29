package org.churchpresenter.app.churchpresenter.viewmodel

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The small library-picker view models behind the Songs and Lower Third settings panes.
 * They share a shape — a directory, a listing, a selection, and a refresh counter the UI observes
 * to re-read the disk — so their contract is mostly about not stranding a stale selection when the
 * directory changes underneath it.
 *
 * The Bible pane had one of these too. It is gone: reading the folder in composition was what froze
 * the settings dialog on every open, so the tab now goes through `readBibleFolderListing` on
 * `Dispatchers.IO` and nothing was left for the view model to hold. Its listing cases live in
 * `BibleFolderListingTest`.
 */
class SongSettingsViewModelTest {

    private lateinit var dir: File
    private val vm = SongSettingsViewModel()

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-songsettings-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun file(name: String) = File(dir, name).also { it.writeText("x") }

    @Test
    fun `nothing is configured initially`() {
        assertEquals("", vm.storageDirectory)
        assertNull(vm.selectedFile)
        assertTrue(vm.filesInDirectory().isEmpty())
    }

    @Test
    fun `setting a directory lists its song files`() {
        file("b.sps"); file("a.sps"); file("notes.txt")
        vm.setDirectory(dir.path)
        assertEquals(listOf("a.sps", "b.sps"), vm.filesInDirectory())
    }

    @Test
    fun `changing the directory clears the selection`() {
        file("a.sps")
        vm.setDirectory(dir.path)
        vm.selectFile("a.sps")
        assertEquals("a.sps", vm.selectedFile)

        vm.setDirectory(dir.path + "/elsewhere")
        assertNull(vm.selectedFile, "a file from the old directory must not stay selected")
    }

    @Test
    fun `changing the directory and refreshing both bump the trigger`() {
        val start = vm.refreshTrigger
        vm.setDirectory(dir.path)
        assertEquals(start + 1, vm.refreshTrigger)
        vm.refresh()
        assertEquals(start + 2, vm.refreshTrigger, "the UI re-reads the disk off this counter")
    }

    @Test
    fun `selecting a file does not trigger a re-read`() {
        vm.setDirectory(dir.path)
        val trigger = vm.refreshTrigger
        vm.selectFile("anything.sps")
        assertEquals(trigger, vm.refreshTrigger, "picking a row is not a directory change")
    }
}

/**
 * Telling a Lottie animation from any other `.json` in the folder.
 *
 * What is left of `LowerThirdSettingsViewModelTest`. The view model drove a Lower Third settings
 * tab that duplicated the Lower Third content tab and has been removed; `isLottieFile` was never
 * part of it, and the content tab, the render cache, the ATEM bridge and the Server tab all ask
 * it which files in a folder are animations.
 */
class IsLottieFileTest {

    private lateinit var folder: File

    @BeforeTest
    fun createFolder() {
        folder = Files.createTempDirectory("cp-lowerthird-test").toFile()
    }

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    /** Minimal content that satisfies the Lottie sniff test. */
    private fun lottie(name: String) =
        File(folder, name).also { it.writeText("""{"v":"5.7.4","layers":[]}""") }

    private fun plainJson(name: String) =
        File(folder, name).also { it.writeText("""{"hello":"world"}""") }

    // ── isLottieFile ────────────────────────────────────────────────────────────

    @Test
    fun `a lottie file is recognised by its version and layers keys`() {
        assertTrue(isLottieFile(lottie("anim.json")))
    }

    @Test
    fun `ordinary json is not mistaken for a lottie`() {
        assertFalse(isLottieFile(plainJson("data.json")))
    }

    @Test
    fun `a missing or unreadable file is not a lottie`() {
        assertFalse(isLottieFile(File(folder, "nope.json")))
        assertFalse(isLottieFile(folder), "a directory is not a lottie")
    }

}


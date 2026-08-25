package org.churchpresenter.app.churchpresenter.viewmodel

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The small library-picker view model behind the Songs settings pane: a directory, a listing, a
 * selection, and a refresh counter the UI observes to re-read the disk. Its contract is mostly
 * about not stranding a stale selection when the directory changes underneath it.
 *
 * The Lower Third pane has one of the same shape. It moved out with its tab — see
 * `LowerThirdSettingsViewModelTest` in :lower-third-settings-tab.
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

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
 * The small library-picker view models behind the Songs, Bible and Lower Third settings panes.
 * They share a shape — a directory, a listing, a selection, and a refresh counter the UI observes
 * to re-read the disk — so their contract is mostly about not stranding a stale selection when the
 * directory changes underneath it.
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

class BibleSettingsViewModelTest {

    private lateinit var dir: File
    private val vm = BibleSettingsViewModel()

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-biblesettings-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    @Test
    fun `setting a directory lists only bible files`() {
        File(dir, "kjv.spb").writeText("x")
        File(dir, "synodal.spb").writeText("x")
        File(dir, "song.sps").writeText("x")
        vm.setDirectory(dir.path)
        assertEquals(listOf("kjv.spb", "synodal.spb"), vm.filesInDirectory())
    }

    @Test
    fun `display names are empty with no directory set`() {
        assertTrue(vm.fileDisplayNames(listOf("kjv.spb")).isEmpty())
    }

    @Test
    fun `an unreadable bible still gets a display-name entry`() {
        // The title is read from inside the file; a corrupt or non-SQLite file must not throw
        // and must not drop the row out of the picker.
        File(dir, "broken.spb").writeText("not a database")
        vm.setDirectory(dir.path)
        val names = vm.fileDisplayNames(listOf("broken.spb"))
        assertEquals(setOf("broken.spb"), names.keys)
    }

    @Test
    fun `changing the directory clears the selection and bumps the trigger`() {
        vm.setDirectory(dir.path)
        vm.selectFile("kjv.spb")
        val trigger = vm.refreshTrigger

        vm.setDirectory(dir.path + "/other")
        assertNull(vm.selectedFile)
        assertEquals(trigger + 1, vm.refreshTrigger)
    }
}

class LowerThirdSettingsViewModelTest {

    private lateinit var folder: File
    private val vm = LowerThirdSettingsViewModel()

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

    // ── Listing ─────────────────────────────────────────────────────────────────

    @Test
    fun `only lottie json files are listed, sorted`() {
        lottie("b.json"); lottie("a.json")
        plainJson("config.json")
        File(folder, "notes.txt").writeText("x")

        vm.setFolder(folder.path)
        assertEquals(listOf("a.json", "b.json"), vm.filesInDirectory())
    }

    @Test
    fun `no folder or a missing folder lists nothing`() {
        assertTrue(vm.filesInDirectory().isEmpty())
        vm.setFolder(File(folder, "does-not-exist").path)
        assertTrue(vm.filesInDirectory().isEmpty())
    }

    // ── Selection ───────────────────────────────────────────────────────────────

    @Test
    fun `the preview reads the selected file's contents`() {
        val file = lottie("anim.json")
        vm.setFolder(folder.path)
        vm.selectFile("anim.json")
        assertEquals(file.readText(), vm.previewJsonContent())
        assertEquals(file.absolutePath, vm.importSourcePath())
    }

    @Test
    fun `the preview is empty with nothing selected or nothing there`() {
        vm.setFolder(folder.path)
        assertEquals("", vm.previewJsonContent(), "no selection")
        assertEquals("", vm.importSourcePath())

        vm.selectFile("missing.json")
        assertEquals("", vm.previewJsonContent(), "a stale selection must not throw")
    }

    @Test
    fun `changing the folder clears the selection`() {
        lottie("anim.json")
        vm.setFolder(folder.path)
        vm.selectFile("anim.json")
        vm.setFolder(folder.path + "/sub")
        assertNull(vm.selectedFile)
    }

    // ── Import and remove ───────────────────────────────────────────────────────

    @Test
    fun `importing copies the file in and selects it`() {
        val source = Files.createTempDirectory("cp-lowerthird-src").toFile()
        try {
            val src = File(source, "imported.json").also { it.writeText("""{"v":"5.7.4","layers":[]}""") }
            vm.setFolder(folder.path)
            vm.importFile(src.path)

            assertTrue(File(folder, "imported.json").exists())
            assertEquals("imported.json", vm.selectedFile)
            assertTrue("imported.json" in vm.filesInDirectory())
        } finally {
            source.deleteRecursively()
        }
    }

    @Test
    fun `importing overwrites an existing file of the same name`() {
        val source = Files.createTempDirectory("cp-lowerthird-src2").toFile()
        try {
            lottie("anim.json")
            val src = File(source, "anim.json").also { it.writeText("""{"v":"9.9.9","layers":[]}""") }
            vm.setFolder(folder.path)
            vm.importFile(src.path)
            assertTrue("9.9.9" in File(folder, "anim.json").readText())
        } finally {
            source.deleteRecursively()
        }
    }

    @Test
    fun `importing with no folder configured does nothing`() {
        val source = Files.createTempDirectory("cp-lowerthird-src3").toFile()
        try {
            val src = File(source, "x.json").also { it.writeText("{}") }
            vm.importFile(src.path)
            assertNull(vm.selectedFile)
        } finally {
            source.deleteRecursively()
        }
    }

    @Test
    fun `importing bumps the refresh trigger`() {
        val source = Files.createTempDirectory("cp-lowerthird-src4").toFile()
        try {
            val src = File(source, "x.json").also { it.writeText("""{"v":"5","layers":[]}""") }
            vm.setFolder(folder.path)
            val trigger = vm.refreshTrigger
            vm.importFile(src.path)
            assertEquals(trigger + 1, vm.refreshTrigger)
        } finally {
            source.deleteRecursively()
        }
    }

    @Test
    fun `removing deletes the selected file and clears the selection`() {
        lottie("anim.json")
        vm.setFolder(folder.path)
        vm.selectFile("anim.json")

        vm.removeSelectedFile()
        assertFalse(File(folder, "anim.json").exists())
        assertNull(vm.selectedFile)
        assertTrue(vm.filesInDirectory().isEmpty())
    }

    @Test
    fun `removing with nothing selected is harmless`() {
        lottie("anim.json")
        vm.setFolder(folder.path)
        vm.removeSelectedFile()
        assertTrue(File(folder, "anim.json").exists(), "nothing was selected, so nothing should be deleted")
    }

    @Test
    fun `a generator save just triggers a re-read`() {
        vm.setFolder(folder.path)
        vm.selectFile("anim.json")
        val trigger = vm.refreshTrigger

        vm.onFileSavedFromGenerator()
        assertEquals(trigger + 1, vm.refreshTrigger)
        assertEquals("anim.json", vm.selectedFile, "the operator's selection should survive a re-read")
    }
}

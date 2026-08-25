package org.churchpresenter.lowerthird.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.companionserver.isLottieFile

/**
 * The library-picker view model behind the Lower Third settings pane: a directory, a listing, a
 * selection and a refresh counter the UI observes to re-read the disk. Its contract is mostly about
 * not stranding a stale selection when the directory changes underneath it.
 *
 * The Songs pane has a view model of the same shape; it stayed in :composeApp with its tab, and its
 * cases are in `SettingsViewModelsTest`.
 */
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

    @Test
    fun `a folder path that is really a file lists nothing`() {
        // A settings value left pointing at the animation itself rather than the folder holding it.
        vm.setFolder(lottie("anim.json").path)
        assertTrue(vm.filesInDirectory().isEmpty())
    }

    @Test
    fun `a selection with no folder configured resolves to nothing`() {
        // Selecting before a folder has been chosen leaves nothing to read or import from.
        vm.selectFile("anim.json")

        assertEquals("", vm.previewJsonContent())
        assertEquals("", vm.importSourcePath())
    }

    @Test
    fun `removing with no folder configured deletes nothing`() {
        val stray = lottie("anim.json")
        vm.selectFile("anim.json")

        vm.removeSelectedFile()

        assertTrue(stray.exists(), "with no folder there is no file to resolve, so nothing is deleted")
        assertEquals("anim.json", vm.selectedFile, "and the selection is left as it was")
    }
}

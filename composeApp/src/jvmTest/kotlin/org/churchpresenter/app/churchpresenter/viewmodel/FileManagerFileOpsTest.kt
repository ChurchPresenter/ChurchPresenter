package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Copying songs and Bibles into the library, and choosing the files to copy.
 *
 * [FileManagerTest] covers the directory scans; this covers the operations that actually write to
 * the user's disk, where a mistake costs the operator a file rather than a listing.
 *
 * There used to be a `deleteFile` section here, covering a canonical-path guard that refused a name
 * escaping the library (`../../something.sps`). Both it and the function were removed once a search
 * for its callers found none: it had never been wired to anything, so the untrusted name its guard
 * was written for could not arrive. **If a delete-file feature is ever built, that guard was
 * deliberately deleted rather than never written** — recover it from this file's history rather than
 * shipping the feature without one.
 */
class FileManagerFileOpsTest {

    private lateinit var root: File
    private lateinit var library: File
    private val fileManager = FileManager()

    /** Stands in for the platform file dialog; only the chooser tests give it answers. */
    private val chooser: FileChooser = mockk()

    @BeforeTest
    fun createDirs() {
        root = Files.createTempDirectory("cp-filemanager-ops-test").toFile()
        library = File(root, "library").also { it.mkdirs() }
        mockkObject(FileChooser.Companion)
        every { FileChooser.platformInstance } returns chooser
    }

    @AfterTest
    fun deleteDirs() {
        unmockkObject(FileChooser.Companion)
        root.deleteRecursively()
    }

    private fun sourceFile(name: String, content: String = "##Title: $name"): Path =
        File(root, name).also { it.writeText(content) }.toPath()

    private fun libraryFile(name: String, content: String = "existing"): File =
        File(library, name).also { it.writeText(content) }

    // ── Importing ───────────────────────────────────────────────────────────────

    @Test
    fun `importing copies the files in and reports no errors`() {
        val errors = fileManager.importFiles(listOf(sourceFile("a.sps"), sourceFile("b.sps")), library.path)

        assertTrue(errors.isEmpty(), "got $errors")
        assertEquals(listOf("a.sps", "b.sps"), library.list()?.sorted())
    }

    @Test
    fun `an imported file keeps its name and contents`() {
        fileManager.importFiles(listOf(sourceFile("song.sps", "##Title: Amazing Grace")), library.path)

        assertEquals("##Title: Amazing Grace", File(library, "song.sps").readText())
    }

    @Test
    fun `the original is left where it was`() {
        val source = sourceFile("song.sps")

        fileManager.importFiles(listOf(source), library.path)

        assertTrue(source.toFile().exists(), "importing copies; it must not move the operator's file")
    }

    @Test
    fun `importing over an existing file replaces it`() {
        libraryFile("song.sps", "old version")

        val errors = fileManager.importFiles(listOf(sourceFile("song.sps", "new version")), library.path)

        assertTrue(errors.isEmpty(), "got $errors")
        assertEquals("new version", File(library, "song.sps").readText(), "re-importing is how a song is updated")
    }

    @Test
    fun `importing nothing is not an error`() {
        assertTrue(fileManager.importFiles(emptyList(), library.path).isEmpty())
    }

    @Test
    fun `importing into a folder that is not there is refused up front`() {
        val missing = File(root, "not-created")

        val errors = fileManager.importFiles(listOf(sourceFile("a.sps")), missing.path)

        assertEquals(1, errors.size, "one clear message beats one per file")
        assertTrue(errors.single().contains(missing.path), "the message names the folder: ${errors.single()}")
        assertFalse(missing.exists(), "the folder must not be created behind the operator's back")
    }

    @Test
    fun `importing into a path that is a file is refused`() {
        val notADir = File(root, "notes.txt").also { it.writeText("x") }

        val errors = fileManager.importFiles(listOf(sourceFile("a.sps")), notADir.path)

        assertEquals(1, errors.size)
        assertEquals("x", notADir.readText(), "the target must not be overwritten")
    }

    @Test
    fun `one unreadable file does not stop the rest importing`() {
        val missing = File(root, "gone.sps").toPath() // never created

        val errors = fileManager.importFiles(listOf(sourceFile("a.sps"), missing, sourceFile("b.sps")), library.path)

        assertEquals(1, errors.size, "only the bad one is reported: $errors")
        assertTrue(errors.single().contains("gone.sps"), "the message names the file: ${errors.single()}")
        assertEquals(
            listOf("a.sps", "b.sps"),
            library.list()?.sorted(),
            "a half-failed import must still deliver what it could",
        )
    }

    // ── Choosing files ──────────────────────────────────────────────────────────

    @Test
    fun `choosing a directory returns its absolute path`() = runBlocking {
        coEvery { chooser.chooseSingle(any(), any(), any(), any()) } returns library.toPath()

        assertEquals(library.absolutePath, fileManager.chooseDirectory())
        coVerify { chooser.chooseSingle(any(), any(), any(), selectDirectory = true) }
    }

    @Test
    fun `cancelling the directory chooser returns nothing`() = runBlocking {
        coEvery { chooser.chooseSingle(any(), any(), any(), any()) } returns null

        assertNull(fileManager.chooseDirectory())
    }

    @Test
    fun `choosing song files asks for songs, not directories`() = runBlocking {
        val picked = listOf(sourceFile("a.sps"), sourceFile("b.sps"))
        coEvery { chooser.chooseMultiple(any(), any(), any(), any()) } returns picked

        assertEquals(picked, fileManager.chooseSongFiles())
        coVerify {
            chooser.chooseMultiple(
                any(),
                match { filters -> filters.single().extensions.contains("sps") },
                any(),
                selectDirectory = false,
            )
        }
    }

    @Test
    fun `cancelling the song chooser returns nothing`() = runBlocking {
        coEvery { chooser.chooseMultiple(any(), any(), any(), any()) } returns null

        assertNull(fileManager.chooseSongFiles())
    }

    @Test
    fun `choosing a bible asks for one bible file`() = runBlocking {
        val picked = sourceFile("kjv.spb")
        coEvery { chooser.chooseSingle(any(), any(), any(), any()) } returns picked

        assertEquals(picked, fileManager.chooseBibleFile())
        coVerify {
            chooser.chooseSingle(
                any(),
                match { filters -> filters.single().extensions.contains("spb") },
                any(),
                selectDirectory = false,
            )
        }
    }

    @Test
    fun `cancelling the bible chooser returns nothing`() = runBlocking {
        coEvery { chooser.chooseSingle(any(), any(), any(), any()) } returns null

        assertNull(fileManager.chooseBibleFile())
    }
}

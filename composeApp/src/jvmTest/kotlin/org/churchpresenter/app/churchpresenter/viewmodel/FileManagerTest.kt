package org.churchpresenter.app.churchpresenter.viewmodel

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [FileManager] discovers the user's song and Bible libraries by scanning directories. Everything
 * downstream — the songbook list, the Bible picker, the setup wizard's "found N songbooks" — is
 * built from these three functions, and they run against whatever a user's disk actually looks
 * like: nested folders, mixed file types, missing paths.
 *
 * Note the two different extensions in play: individual song FILES are `.sps`, while the songbook
 * FOLDER scan counts `.song` files.
 */
class FileManagerTest {

    private lateinit var root: File
    private val fileManager = FileManager()

    @BeforeTest
    fun createTree() {
        root = Files.createTempDirectory("cp-filemanager-test").toFile()
    }

    @AfterTest
    fun deleteTree() {
        root.deleteRecursively()
    }

    private fun file(relativePath: String): File =
        File(root, relativePath).also {
            it.parentFile.mkdirs()
            it.writeText("content")
        }

    // ── Song files (.sps) ───────────────────────────────────────────────────────

    @Test
    fun `song files are listed by name and sorted`() {
        file("charlie.sps")
        file("alpha.sps")
        file("bravo.sps")
        assertEquals(listOf("alpha.sps", "bravo.sps", "charlie.sps"), fileManager.getSongFilesInDirectory(root.path))
    }

    @Test
    fun `only sps files are listed`() {
        file("song.sps")
        file("notes.txt")
        file("bible.spb")
        file("other.song")
        assertEquals(listOf("song.sps"), fileManager.getSongFilesInDirectory(root.path))
    }

    @Test
    fun `the extension match is case-insensitive`() {
        file("upper.SPS")
        file("mixed.Sps")
        assertEquals(listOf("mixed.Sps", "upper.SPS"), fileManager.getSongFilesInDirectory(root.path))
    }

    @Test
    fun `the song scan does not descend into subdirectories`() {
        file("top.sps")
        file("nested/deep.sps")
        assertEquals(listOf("top.sps"), fileManager.getSongFilesInDirectory(root.path))
    }

    // ── Bible files (.spb) ──────────────────────────────────────────────────────

    @Test
    fun `bible files are listed by name and sorted`() {
        file("kjv.spb")
        file("synodal.spb")
        file("ignored.sps")
        assertEquals(listOf("kjv.spb", "synodal.spb"), fileManager.getBibleFilesInDirectory(root.path))
    }

    // ── Missing and invalid paths ───────────────────────────────────────────────

    @Test
    fun `an empty path yields nothing rather than scanning the working directory`() {
        assertTrue(fileManager.getSongFilesInDirectory("").isEmpty())
        assertTrue(fileManager.getBibleFilesInDirectory("").isEmpty())
        assertTrue(fileManager.getSongFoldersInDirectory("").isEmpty())
    }

    @Test
    fun `a missing directory yields nothing rather than throwing`() {
        // A saved library path whose drive is no longer mounted — common with network shares.
        val missing = File(root, "not/here").path
        assertTrue(fileManager.getSongFilesInDirectory(missing).isEmpty())
        assertTrue(fileManager.getBibleFilesInDirectory(missing).isEmpty())
        assertTrue(fileManager.getSongFoldersInDirectory(missing).isEmpty())
    }

    @Test
    fun `a path pointing at a file instead of a directory yields nothing`() {
        val notADir = file("some.sps").path
        assertTrue(fileManager.getSongFilesInDirectory(notADir).isEmpty())
        assertTrue(fileManager.getBibleFilesInDirectory(notADir).isEmpty())
        assertTrue(fileManager.getSongFoldersInDirectory(notADir).isEmpty())
    }

    @Test
    fun `an empty directory yields an empty list`() {
        assertTrue(fileManager.getSongFilesInDirectory(root.path).isEmpty())
        assertTrue(fileManager.getSongFoldersInDirectory(root.path).isEmpty())
    }

    // ── Songbook folders (.song counts) ─────────────────────────────────────────

    @Test
    fun `songs directly in the root are reported under the slash entry`() {
        file("a.song")
        file("b.song")
        assertEquals(listOf("/" to 2), fileManager.getSongFoldersInDirectory(root.path))
    }

    @Test
    fun `each subfolder containing songs is reported with its count`() {
        file("Hymnal/a.song")
        file("Hymnal/b.song")
        file("Worship/c.song")
        assertEquals(
            listOf("Hymnal" to 2, "Worship" to 1),
            fileManager.getSongFoldersInDirectory(root.path),
        )
    }

    @Test
    fun `nested songbooks are found and reported by relative path`() {
        file("Russian/Hymns/a.song")
        file("Russian/Modern/b.song")
        file("Russian/Modern/c.song")
        val folders = fileManager.getSongFoldersInDirectory(root.path)
        assertEquals(listOf("Russian/Hymns" to 1, "Russian/Modern" to 2), folders)
    }

    @Test
    fun `a folder with no songs is skipped but still traversed`() {
        // The intermediate folder itself holds no .song files, yet its child does.
        file("Empty/Inner/deep.song")
        assertEquals(listOf("Empty/Inner" to 1), fileManager.getSongFoldersInDirectory(root.path))
    }

    @Test
    fun `folders are reported in sorted order`() {
        file("Zulu/a.song")
        file("Alpha/a.song")
        file("Mike/a.song")
        assertEquals(
            listOf("Alpha", "Mike", "Zulu"),
            fileManager.getSongFoldersInDirectory(root.path).map { it.first },
        )
    }

    @Test
    fun `root songs and subfolders are reported together`() {
        file("loose.song")
        file("Hymnal/a.song")
        val folders = fileManager.getSongFoldersInDirectory(root.path)
        assertEquals(listOf("/" to 1, "Hymnal" to 1), folders)
    }

    @Test
    fun `only song files are counted, not other formats`() {
        file("Hymnal/a.song")
        file("Hymnal/b.sps")
        file("Hymnal/c.txt")
        assertEquals(listOf("Hymnal" to 1), fileManager.getSongFoldersInDirectory(root.path))
    }

    @Test
    fun `song counting is case-insensitive on the extension`() {
        file("Hymnal/a.SONG")
        file("Hymnal/b.Song")
        assertEquals(listOf("Hymnal" to 2), fileManager.getSongFoldersInDirectory(root.path))
    }
}

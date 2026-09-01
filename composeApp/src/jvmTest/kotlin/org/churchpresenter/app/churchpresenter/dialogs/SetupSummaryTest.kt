package org.churchpresenter.app.churchpresenter.dialogs

import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The figures the wizard's last step reports.
 *
 * These are the only claim the wizard makes about the setup it just walked someone through, so a
 * count that silently reads zero — or throws on a path that was mistyped — is worse than no step.
 */
class SetupSummaryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun dir(name: String): File = temp.newFolder(name)

    private fun File.song(name: String) = File(this, "$name.song").apply { writeText("x") }
    private fun File.bible(name: String) = File(this, "$name.spb").apply { writeText("x") }

    // ── Bible translations ──────────────────────────────────────────────────────

    @Test
    fun `installed translations are the spb files in the folder`() {
        val folder = dir("bibles")
        folder.bible("kjv")
        folder.bible("web")
        File(folder, "notes.txt").writeText("x")
        assertEquals(2, countBibleTranslations(folder.absolutePath))
    }

    @Test
    fun `the extension is matched whatever its case`() {
        val folder = dir("bibles-case")
        File(folder, "KJV.SPB").writeText("x")
        assertEquals(1, countBibleTranslations(folder.absolutePath))
    }

    @Test
    fun `a folder of translations does not count the subfolders it sits beside`() {
        val folder = dir("bibles-nested")
        folder.bible("kjv")
        File(folder, "archive").mkdirs()
        File(folder, "archive/old.spb").writeText("x")
        assertEquals(1, countBibleTranslations(folder.absolutePath), "only the top level is installed")
    }

    // ── Songs and song books ────────────────────────────────────────────────────

    @Test
    fun `each subfolder holding songs is one book`() {
        val folder = dir("songs")
        File(folder, "Hymnal").mkdirs().also { File(folder, "Hymnal/a.song").writeText("x") }
        File(folder, "Modern").mkdirs().also { File(folder, "Modern/b.song").writeText("x") }
        assertEquals(2, countSongBooks(folder.absolutePath))
        assertEquals(2, countSongs(folder.absolutePath))
    }

    @Test
    fun `a subfolder with no songs in it is not a book`() {
        val folder = dir("songs-empty-sub")
        File(folder, "Hymnal").mkdirs().also { File(folder, "Hymnal/a.song").writeText("x") }
        File(folder, "Artwork").mkdirs().also { File(folder, "Artwork/cover.png").writeText("x") }
        assertEquals(1, countSongBooks(folder.absolutePath), "a folder of images is not a song book")
    }

    @Test
    fun `a flat folder of songs counts as a single book`() {
        // A library is conventionally one folder per book, but pointing the app at a flat folder is
        // a perfectly ordinary thing to do, and reporting "0 books - 12 songs" for it reads as a
        // failure rather than as a description.
        val folder = dir("songs-flat")
        repeat(3) { folder.song("song$it") }
        assertEquals(1, countSongBooks(folder.absolutePath))
        assertEquals(3, countSongs(folder.absolutePath))
    }

    @Test
    fun `loose songs beside book folders add one more book, not one per song`() {
        val folder = dir("songs-mixed")
        File(folder, "Hymnal").mkdirs().also { File(folder, "Hymnal/a.song").writeText("x") }
        folder.song("loose1")
        folder.song("loose2")
        assertEquals(2, countSongBooks(folder.absolutePath), "the loose songs are one book between them")
        assertEquals(3, countSongs(folder.absolutePath))
    }

    @Test
    fun `songs are counted however deeply they are filed`() {
        val folder = dir("songs-deep")
        File(folder, "Book/Section").mkdirs()
        File(folder, "Book/Section/deep.song").writeText("x")
        assertEquals(1, countSongs(folder.absolutePath))
    }

    @Test
    fun `only the song extension is counted`() {
        val folder = dir("songs-other")
        folder.song("real")
        File(folder, "notes.txt").writeText("x")
        File(folder, "old.sng").writeText("x")
        assertEquals(1, countSongs(folder.absolutePath))
    }

    // ── The paths a first-run user actually arrives with ────────────────────────

    @Test
    fun `a folder never chosen counts as nothing rather than throwing`() {
        assertEquals(0, countBibleTranslations(""))
        assertEquals(0, countSongs(""))
        assertEquals(0, countSongBooks(""))
    }

    @Test
    fun `a path that does not exist counts as nothing rather than throwing`() {
        val missing = File(temp.root, "no-such-folder").absolutePath
        assertEquals(0, countBibleTranslations(missing))
        assertEquals(0, countSongs(missing))
        assertEquals(0, countSongBooks(missing))
    }

    @Test
    fun `a file where a folder was expected counts as nothing`() {
        val notADirectory = temp.newFile("bibles.spb").absolutePath
        assertEquals(0, countBibleTranslations(notADirectory))
        assertEquals(0, countSongs(notADirectory))
    }

    // ── The whole summary ───────────────────────────────────────────────────────

    @Test
    fun `the summary reports both folders together`() = runBlocking {
        val bibles = dir("s-bibles").also { it.bible("kjv"); it.bible("web"); it.bible("asv") }
        val songs = dir("s-songs")
        File(songs, "Hymnal").mkdirs().also { File(songs, "Hymnal/a.song").writeText("x") }
        File(songs, "Modern").mkdirs().also { File(songs, "Modern/b.song").writeText("x") }

        val summary = loadSetupSummary(bibles.absolutePath, songs.absolutePath)

        assertEquals(SetupSummary(bibleTranslations = 3, songBooks = 2, songs = 2), summary)
    }

    @Test
    fun `a summary of two unset folders is all zeroes`() = runBlocking {
        assertEquals(SetupSummary(0, 0, 0), loadSetupSummary("", ""))
    }
}

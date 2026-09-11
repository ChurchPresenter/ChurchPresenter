package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The songbook and song counts the usage ping carries — counts only, never a folder name, a path or
 * a title.
 *
 * It reads the real song folder, so it runs inside the ping coroutine rather than on the startup
 * path; what the tests pin here is what the numbers mean, and that a library it cannot read costs a
 * pair of zeroes rather than an exception on the way to opening a window.
 */
class LiveMapSongCountsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun settingsFor(dir: File) =
        AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath))

    private fun song(dir: File, name: String) {
        dir.mkdirs()
        File(dir, "$name.song").writeText("#Title: $name")
    }

    @Test
    fun `a library of one folder counts one songbook and its songs`() {
        val root = temp.newFolder("songs")
        val hymns = File(root, "Hymns")
        song(hymns, "Amazing Grace")
        song(hymns, "How Great Thou Art")

        assertEquals(1 to 2, LiveMapReporter.gatherSongCounts(settingsFor(root)))
    }

    @Test
    fun `songs sitting loose in the root count as a songbook of their own`() {
        val root = temp.newFolder("songs")
        song(root, "Loose One")
        song(root, "Loose Two")

        assertEquals(1 to 2, LiveMapReporter.gatherSongCounts(settingsFor(root)))
    }

    @Test
    fun `several songbooks are counted, and their songs added up`() {
        val root = temp.newFolder("songs")
        song(File(root, "Hymns"), "a")
        song(File(root, "Hymns"), "b")
        song(File(root, "Modern"), "c")
        song(File(root, "Christmas"), "d")

        assertEquals(3 to 4, LiveMapReporter.gatherSongCounts(settingsFor(root)))
    }

    @Test
    fun `a songbook nested inside another is counted on its own`() {
        val root = temp.newFolder("songs")
        song(File(root, "Hymns"), "a")
        song(File(root, "Hymns/Advent"), "b")

        assertEquals(2 to 2, LiveMapReporter.gatherSongCounts(settingsFor(root)))
    }

    @Test
    fun `a folder holding no songs is not a songbook`() {
        val root = temp.newFolder("songs")
        File(root, "Backgrounds").mkdirs()
        File(root, "Backgrounds/photo.jpg").writeText("not a song")
        song(File(root, "Hymns"), "a")

        assertEquals(1 to 1, LiveMapReporter.gatherSongCounts(settingsFor(root)))
    }

    @Test
    fun `files that are not songs are not counted`() {
        val root = temp.newFolder("songs")
        val hymns = File(root, "Hymns")
        song(hymns, "a")
        File(hymns, "notes.txt").writeText("x")
        File(hymns, "cover.png").writeText("x")

        assertEquals(1 to 1, LiveMapReporter.gatherSongCounts(settingsFor(root)))
    }

    @Test
    fun `an empty library counts nothing`() {
        assertEquals(0 to 0, LiveMapReporter.gatherSongCounts(settingsFor(temp.newFolder("songs"))))
    }

    @Test
    fun `a song folder that is not there counts nothing rather than failing`() {
        val missing = File(temp.root, "gone")
        assertEquals(0 to 0, LiveMapReporter.gatherSongCounts(settingsFor(missing)))
    }

    @Test
    fun `a song folder that is a file counts nothing`() {
        val notADir = temp.newFile("songs.txt")
        assertEquals(0 to 0, LiveMapReporter.gatherSongCounts(settingsFor(notADir)))
    }

    @Test
    fun `the counts reach the ping as songbooks and songs`() {
        val root = temp.newFolder("songs")
        song(File(root, "Hymns"), "a")
        song(File(root, "Hymns"), "b")

        val counts = LiveMapReporter.gatherSongCounts(settingsFor(root))
        val url = LiveMapReporter.buildPingUrl(
            "linux", "1.0", null, isDevBuild = false,
            setup = LiveMapReporter.setupFacts(AppSettings(), screenCount = 1, songCounts = counts),
        )
        assertEquals(true, "songbooks=1" in url, url)
        assertEquals(true, "songs=2" in url, url)
    }
}

package org.churchpresenter.songs

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A song folder that is not there when the app starts — made, unzipped or mounted later — is waited
 * for rather than given up on, which left the Songs tab empty until a restart. These are the
 * decisions the wait takes each time the folder above it changes; the wait itself is the platform's.
 */
class SongFolderAwaitTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-song-await").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `the nearest folder that is there is watched`() {
        assertEquals(dir.absoluteFile, nearestExistingFolder(File(dir, "Songs/Hymnal")))
        File(dir, "Songs").mkdirs()
        assertEquals(File(dir, "Songs").absoluteFile, nearestExistingFolder(File(dir, "Songs/Hymnal")))
    }

    @Test
    fun `nothing nearer yet keeps waiting`() {
        assertEquals(AwaitStep.KeepWaiting, awaitStep(File(dir, "Songs/Hymnal"), dir))
    }

    @Test
    fun `a folder on the way appearing moves the watch nearer`() {
        val songs = File(dir, "Songs").apply { mkdirs() }
        assertEquals(AwaitStep.WatchNearer(songs.absoluteFile), awaitStep(File(songs, "Hymnal"), dir))
    }

    @Test
    fun `the folder appearing loads it`() {
        val songs = File(dir, "Songs").apply { mkdirs() }
        assertEquals(AwaitStep.Appeared, awaitStep(songs, dir))
    }

    @Test
    fun `a file where the folder should be is still waited past`() {
        val blocker = File(dir, "Songs").apply { writeText("not a folder") }
        assertEquals(AwaitStep.KeepWaiting, awaitStep(blocker, dir))
    }
}

package org.churchpresenter.server

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.songs.SongItem
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app's song and Bible feeds: built on the server's publishing thread, not the caller's, and
 * applied in the order they were asked for. Every wait is on a latch or the publish's own job.
 */
class CompanionLibraryPublishTest {

    private val server = CompanionServer(shutdownGraceMs = 0)
    private val dirs = mutableListOf<File>()

    @AfterTest
    fun tearDown() {
        dirs.forEach { it.deleteRecursively() }
    }

    private fun song(number: String, title: String) = SongItem(number = number, title = title, songbook = "Hymnal")

    /** Holds [CompanionServer.publishing] busy until the returned latch is counted down. */
    private fun holdPublishing(): CountDownLatch {
        val release = CountDownLatch(1)
        val holding = CountDownLatch(1)
        server.scope.launch(server.publishing) {
            holding.countDown()
            release.await(WAIT_S, TimeUnit.SECONDS)
        }
        assertTrue(holding.await(WAIT_S, TimeUnit.SECONDS), "the publishing thread never started")
        return release
    }

    @Test
    fun `publishing songs returns before the catalog is built, and the catalog follows`() = runBlocking {
        val release = holdPublishing()

        val published = server.publishSongs(listOf(song("1", "Amazing Grace")))
        assertEquals(0, server._catalog.value.total, "nothing is built while the publishing thread is busy")

        release.countDown()
        withTimeout(WAIT_MS) { published.join() }
        assertEquals(1, server._catalog.value.total)
    }

    @Test
    fun `publishes are applied in the order they were asked for`() = runBlocking {
        val release = holdPublishing()
        server.publishSongs(listOf(song("1", "Old"), song("2", "Library")))
        val last = server.publishSongs(listOf(song("3", "New")))

        release.countDown()
        withTimeout(WAIT_MS) { last.join() }
        assertEquals(1, server._catalog.value.total)
        assertEquals("New", server._songs.single().title)
    }

    @Test
    fun `the Bible is published the same way, with its file`() = runBlocking {
        val dir = Files.createTempDirectory("cp-publish-bible").toFile().also { dirs += it }

        withTimeout(WAIT_MS) {
            server.publishBible(SpbFixture.loadedBible(dir), "KJV", filePath = "kjv.spb").join()
        }

        assertEquals("KJV", server._bibleCatalog.value?.translation)
        assertEquals("kjv.spb", server._bibleFilePath)
    }

    private companion object {
        const val WAIT_S = 5L
        const val WAIT_MS = WAIT_S * 1000
    }
}

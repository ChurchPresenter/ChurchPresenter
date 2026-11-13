package org.churchpresenter.songs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongFolderWatcherLifecycleTest {

    private lateinit var dir: File
    private val parent = Job()
    private val scope = CoroutineScope(parent)

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-watcher-coverage").toFile()
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun awaitChildren() = runBlocking {
        withTimeout(5_000) { parent.children.toList().forEach { it.join() } }
    }

    @Test
    fun `a folder that is not there yet is waited for`() {
        val watcher = SongFolderWatcher(scope) {}
        watcher.watchDirectory(File(dir, "missing"))
        assertEquals(1, parent.children.count(), "watching the folder above it until it appears")
        watcher.dispose()
        awaitChildren()
        assertTrue(parent.children.none { it.isActive })
    }

    @Test
    fun `a file is not watched as a folder`() {
        val file = File(dir, "a.song").apply { writeText("x") }
        SongFolderWatcher(scope) {}.watchDirectory(file)
        // The file/folder check runs in the watch's own IO job, which ends at once for a file.
        awaitChildren()
        assertTrue(parent.children.none { it.isActive })
    }

    @Test
    fun `disposing a watcher ends its watch`() {
        File(dir, "Hymnal").mkdirs()
        val watcher = SongFolderWatcher(scope) {}
        watcher.watchDirectory(dir)
        assertEquals(1, parent.children.count())
        watcher.dispose()
        awaitChildren()
        assertTrue(parent.children.none { it.isActive })
    }

    @Test
    fun `watching again ends the previous watch`() {
        val watcher = SongFolderWatcher(scope) {}
        watcher.watchDirectory(dir)
        val first = parent.children.single()
        watcher.watchDirectory(dir)
        runBlocking { withTimeout(5_000) { first.join() } }
        assertTrue(first.isCancelled)
        watcher.dispose()
        awaitChildren()
    }

    /** Watches [folder] until it is waiting, then stops it and checks the watch ended. */
    private fun watchThenStop(folder: File) {
        val waiting = CountDownLatch(1)
        val watcher = SongFolderWatcher(scope, onWatching = { waiting.countDown() }) {}
        watcher.watchDirectory(folder)
        assertTrue(waiting.await(5, TimeUnit.SECONDS), "the watch registered its folders and began waiting")
        watcher.dispose()
        awaitChildren()
        assertTrue(parent.children.none { it.isActive }, "closing the service ends a watch blocked waiting on it")
    }

    @Test
    fun `a watch waiting on a folder tree ends when stopped`() {
        File(dir, "Hymnal/Extra").mkdirs()
        watchThenStop(dir)
    }

    @Test
    fun `a watch waiting for a missing folder ends when stopped`() {
        watchThenStop(File(dir, "not-yet/songs"))
    }

    @Test
    fun `disposing a watcher that never watched is harmless`() {
        SongFolderWatcher(scope) {}.dispose()
        assertEquals(0, parent.children.count())
    }
}

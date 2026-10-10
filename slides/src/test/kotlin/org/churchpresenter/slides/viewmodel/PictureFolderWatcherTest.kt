package org.churchpresenter.slides.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.slides.solidImage
import org.churchpresenter.slides.tempDir
import java.awt.Color
import java.io.File
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PictureFolderWatcherTest {

    private val dir = tempDir("cp-folder-watcher")
    private val state = PicturesState(null)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private class Event(private val name: String, private val kind: WatchEvent.Kind<Path>) : WatchEvent<Path> {
        override fun kind(): WatchEvent.Kind<Path> = kind
        override fun count() = 1
        override fun context(): Path = Path.of(name)
    }

    private class Key(private val events: List<WatchEvent<*>>, private val valid: Boolean) : WatchKey {
        override fun isValid() = valid
        override fun pollEvents(): List<WatchEvent<*>> = events
        override fun reset() = valid
        override fun cancel() = Unit
        override fun watchable(): Watchable = error("not watched")
    }

    /** Hands out the keys a test queues, or throws [failure] once they run out. */
    private class Service(private val failure: Exception? = null) : WatchService {
        val keys = LinkedBlockingQueue<WatchKey>()
        var closed = false

        override fun take(): WatchKey = keys.poll() ?: throw checkNotNull(failure) { "no key queued" }
        override fun poll(): WatchKey? = keys.poll()
        override fun poll(timeout: Long, unit: TimeUnit): WatchKey? = keys.poll()
        override fun close() {
            closed = true
        }
    }

    private class Source(
        val service: Service = Service(),
        private val failures: Int = 0,
        private val openFailure: IOException? = null,
    ) : FolderWatchSource {
        var registrations = 0

        override fun open(): WatchService = openFailure?.let { throw it } ?: service

        override fun register(folder: File, service: WatchService) {
            registrations++
            if (registrations <= failures) throw IOException("entry vanished while registering")
        }
    }

    private fun watcher(source: Source) =
        PictureFolderWatcher(
            state,
            PictureThumbnails(state, Dispatchers.Unconfined),
            Dispatchers.Unconfined,
            scope,
            source,
        )

    private fun PictureFolderWatcher.watchToTheEnd(folder: File = dir) {
        start(folder)
        runBlocking { withTimeout(5_000) { watchJob!!.join() } }
    }

    @Test
    fun `pictures created and deleted in the folder are added and removed, and other files ignored`() {
        val source = Source()
        solidImage(dir, "a.png", Color.RED)
        File(dir, "notes.txt").writeText("x")
        source.service.keys += Key(
            listOf(
                Event("a.png", StandardWatchEventKinds.ENTRY_CREATE),
                Event("notes.txt", StandardWatchEventKinds.ENTRY_CREATE),
            ),
            valid = true,
        )
        source.service.keys += Key(listOf(Event("a.png", StandardWatchEventKinds.ENTRY_DELETE)), valid = false)
        val watcher = watcher(source)

        watcher.watchToTheEnd()

        assertTrue(state.images.isEmpty(), "added by the first key, removed by the second")
        assertTrue(source.service.closed, "a key that is no longer valid ends the watch")
    }

    @Test
    fun `a created picture stays listed while the watch goes on`() {
        val source = Source(Service(failure = ClosedWatchServiceException()))
        val a = solidImage(dir, "a.png", Color.RED)
        source.service.keys += Key(listOf(Event("a.png", StandardWatchEventKinds.ENTRY_CREATE)), valid = true)

        watcher(source).watchToTheEnd()

        assertEquals(listOf(a), state.images.toList())
    }

    @Test
    fun `a folder that keeps failing to register is given up on after three tries`() {
        val source = Source(failures = 5)
        watcher(source).watchToTheEnd()
        assertEquals(3, source.registrations)
        assertTrue(source.service.closed)
    }

    @Test
    fun `a folder that has gone is given up on at once`() {
        val source = Source(failures = 5)
        watcher(source).watchToTheEnd(File(dir, "gone"))
        assertEquals(1, source.registrations)
        assertTrue(source.service.closed)
    }

    @Test
    fun `a registration that fails once is retried and the watch goes on`() {
        val source = Source(failures = 1)
        source.service.keys += Key(emptyList(), valid = false)
        watcher(source).watchToTheEnd()
        assertEquals(2, source.registrations)
        assertTrue(source.service.closed)
    }

    @Test
    fun `an interrupted watch ends quietly`() {
        val source = Source(Service(failure = InterruptedException()))
        watcher(source).watchToTheEnd()
        assertEquals(1, source.registrations)
    }

    @Test
    fun `a watch service that cannot be opened ends the watch quietly`() {
        val source = Source(openFailure = IOException("no watch service"))
        watcher(source).watchToTheEnd()
        assertEquals(0, source.registrations)
    }

    @Test
    fun `starting again replaces the watch that was running`() {
        val first = Source(Service(failure = ClosedWatchServiceException()))
        val watcher = watcher(first)
        watcher.start(dir)
        val firstJob = watcher.watchJob!!
        watcher.watchToTheEnd()
        runBlocking { withTimeout(5_000) { firstJob.join() } }
        assertTrue(firstJob.isCompleted)
        assertTrue(watcher.watchJob !== firstJob)
    }
}

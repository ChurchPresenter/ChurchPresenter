package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongFolderWatcherEventTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var watcher: SongFolderWatcher
    private lateinit var watchService: WatchService

    @BeforeTest
    fun setUp() {
        dir = java.nio.file.Files.createTempDirectory("cp-song-watch").toFile()
        scope = CoroutineScope(Dispatchers.Unconfined)
        watcher = SongFolderWatcher(scope) {}
        watchService = FileSystems.getDefault().newWatchService()
    }

    @AfterTest
    fun tearDown() {
        watcher.dispose()
        scope.cancel()
        watchService.close()
        dir.deleteRecursively()
    }

    private class FakeKey(private val path: Path) : WatchKey {
        override fun isValid() = true
        override fun pollEvents(): MutableList<WatchEvent<*>> = mutableListOf()
        override fun reset() = true
        override fun cancel() = Unit
        override fun watchable(): Watchable = path
    }

    private class FakeEvent(
        private val name: String,
        private val eventKind: WatchEvent.Kind<Path>,
    ) : WatchEvent<Path> {
        override fun kind(): WatchEvent.Kind<Path> = eventKind
        override fun count() = 1
        override fun context(): Path = Path.of(name)
    }

    /** An OVERFLOW event as the JDK delivers it: no path, and a null context. */
    private class OverflowEvent : WatchEvent<Any> {
        override fun kind(): WatchEvent.Kind<Any> = StandardWatchEventKinds.OVERFLOW
        override fun count() = 1
        override fun context(): Any? = null
    }

    private fun relevant(
        name: String,
        kind: WatchEvent.Kind<Path> = StandardWatchEventKinds.ENTRY_MODIFY,
    ): Boolean = watcher.isRelevantEvent(FakeEvent(name, kind), FakeKey(dir.toPath()), watchService)

    @Test
    fun `a song file is relevant`() {
        File(dir, "hymn.song").writeText("[Verse 1]")

        assertTrue(relevant("hymn.song"))
    }

    @Test
    fun `a song file that has already been deleted is still relevant`() {
        assertTrue(relevant("gone.song", StandardWatchEventKinds.ENTRY_DELETE))
    }

    @Test
    fun `the extension is matched whatever case it was written in`() {
        assertTrue(relevant("HYMN.SONG"))
    }

    @Test
    fun `a file of another kind is ignored`() {
        File(dir, "notes.txt").writeText("not a song")

        assertFalse(relevant("notes.txt"))
    }

    @Test
    fun `a file with no extension at all is ignored`() {
        File(dir, "README").writeText("nothing")

        assertFalse(relevant("README"))
    }

    @Test
    fun `a new subfolder is relevant, because songs may appear inside it`() {
        File(dir, "Hymnal").mkdirs()

        assertTrue(relevant("Hymnal", StandardWatchEventKinds.ENTRY_CREATE))
    }

    @Test
    fun `an existing subfolder changing is relevant too`() {
        File(dir, "Hymnal").mkdirs()

        assertTrue(relevant("Hymnal", StandardWatchEventKinds.ENTRY_MODIFY))
    }

    @Test
    fun `a new subfolder starts being watched itself`() {
        val sub = File(dir, "Chorus Book").apply { mkdirs() }

        relevant("Chorus Book", StandardWatchEventKinds.ENTRY_CREATE)

        File(sub, "new.song").writeText("[Verse 1]")
        val key = watchService.poll(5, TimeUnit.SECONDS)
        assertTrue(key != null, "a song added to the new folder must reach the watcher")
        key?.reset()
    }

    @Test
    fun `a subfolder that vanished before the event was read falls back to its name`() {
        assertFalse(relevant("DeletedFolder", StandardWatchEventKinds.ENTRY_DELETE))
    }

    @Test
    fun `an overflow event is not relevant and its context is never read`() {
        assertFalse(watcher.isRelevantEvent(OverflowEvent(), FakeKey(dir.toPath()), watchService))
    }
}

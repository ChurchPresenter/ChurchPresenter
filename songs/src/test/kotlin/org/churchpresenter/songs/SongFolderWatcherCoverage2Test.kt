package org.churchpresenter.songs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongFolderWatcherCoverage2Test {

    private val dir: File = Files.createTempDirectory("cp-watch-events").toFile()
    private val service = FileSystems.getDefault().newWatchService()
    private val watcher = SongFolderWatcher(CoroutineScope(Dispatchers.Unconfined)) {}

    @AfterTest
    fun tearDown() {
        service.close()
        dir.deleteRecursively()
    }

    private class Event(private val kind: WatchEvent.Kind<Path>, private val context: Path?) : WatchEvent<Path> {
        override fun kind() = kind
        override fun count() = 1
        override fun context() = context
    }

    private class Key(private val watchable: Watchable) : WatchKey {
        override fun isValid() = true
        override fun pollEvents(): MutableList<WatchEvent<*>> = mutableListOf()
        override fun reset() = true
        override fun cancel() = Unit
        override fun watchable() = watchable
    }

    private val notAPath = object : Watchable {
        override fun register(
            watcher: WatchService,
            events: Array<out WatchEvent.Kind<*>>,
            vararg modifiers: WatchEvent.Modifier,
        ): WatchKey = throw UnsupportedOperationException()

        override fun register(watcher: WatchService, vararg events: WatchEvent.Kind<*>): WatchKey =
            throw UnsupportedOperationException()
    }

    private fun relevant(kind: WatchEvent.Kind<Path>, name: String?, watchable: Watchable = dir.toPath()) =
        watcher.isRelevantEvent(Event(kind, name?.let { Path.of(it) }), Key(watchable), service)

    @Test
    fun `an event with no file name is not relevant`() =
        assertFalse(relevant(StandardWatchEventKinds.ENTRY_MODIFY, null))

    @Test
    fun `a song file is relevant and any other file is not`() {
        assertTrue(relevant(StandardWatchEventKinds.ENTRY_MODIFY, "1 - Grace.SONG"))
        assertFalse(relevant(StandardWatchEventKinds.ENTRY_MODIFY, "notes.txt"))
    }

    @Test
    fun `a key on something that is not a path judges by the name alone`() {
        assertTrue(relevant(StandardWatchEventKinds.ENTRY_CREATE, "a.song", notAPath))
        assertFalse(relevant(StandardWatchEventKinds.ENTRY_CREATE, "folder", notAPath))
    }

    @Test
    fun `a new songbook folder is relevant and gets watched`() {
        File(dir, "Hymnal").mkdirs()
        assertTrue(relevant(StandardWatchEventKinds.ENTRY_CREATE, "Hymnal"))
    }

    @Test
    fun `a changed folder is relevant without being registered again`() {
        File(dir, "Hymnal").mkdirs()
        assertTrue(relevant(StandardWatchEventKinds.ENTRY_MODIFY, "Hymnal"))
    }
}

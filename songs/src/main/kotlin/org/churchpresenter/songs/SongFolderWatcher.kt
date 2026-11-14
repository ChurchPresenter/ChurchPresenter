package org.churchpresenter.songs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService

private const val DEBOUNCE_MS = 300L

class SongFolderWatcher(
    private val scope: CoroutineScope,
    /** Called on the watch's thread once its folders are registered and it starts waiting. */
    private val onWatching: () -> Unit = {},
    private val onSongsChanged: () -> Unit,
) {
    private var watchJob: Job? = null

    /** Held while [watchService] is taken or let go, so a stopped watch can never take it after a newer one. */
    private val serviceLock = Any()

    private var watchService: WatchService? = null

    /**
     * Watches [rootDir] and every folder under it, or waits for it if it is not there yet.
     *
     * Everything that touches the disk -- telling a file from a folder, making the watch service,
     * registering the folders -- happens in the watch's own IO job: this is called from the event
     * thread when the songs load, and on a slow or network drive those checks alone held it
     * (CHURCH-PRESENTER-DESKTOP-17F).
     */
    fun watchDirectory(rootDir: File) {
        stop()
        watchJob = scope.launch(Dispatchers.IO) {
            if (rootDir.isFile) return@launch
            if (rootDir.isDirectory) watchFolderTree(rootDir) else awaitFolder(rootDir)
        }
    }

    /**
     * Takes [service] as this watcher's, or closes it at once when the watch was stopped while it
     * was being made. Both this and [stop] hold [serviceLock], so one always sees the other: either
     * [stop] finds the service and closes it, or this finds the job cancelled. A stopped watch
     * finishing late must not take the field either -- it would put a newer watch's service out of
     * [stop]'s reach, and a service nobody closes leaves its thread blocked in `take()` for good.
     */
    private fun CoroutineScope.adopt(service: WatchService): Boolean {
        val taken = synchronized(serviceLock) {
            isActive.also { active -> if (active) watchService = service }
        }
        if (!taken) runCatching { service.close() }
        return taken
    }

    private suspend fun watchFolderTree(rootDir: File) = coroutineScope {
        val watchService = FileSystems.getDefault().newWatchService()
        if (!adopt(watchService)) return@coroutineScope
        try {
            // Recursively watch root and all subdirectories
            fun registerDir(dir: File) {
                try {
                    dir.toPath().register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY
                    )
                } catch (_: Exception) {
                }
                val subdirs = dir.listFiles { file -> file.isDirectory } ?: emptyArray()
                for (subdir in subdirs) {
                    registerDir(subdir)
                }
            }
            registerDir(rootDir)
            onWatching()

            while (isActive) {
                val key = watchService.take()
                var relevant = false

                for (event in key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue
                    if (isRelevantEvent(event, key, watchService)) relevant = true
                }

                if (relevant) {
                    // Debounce: wait a bit for batch operations to settle
                    delay(DEBOUNCE_MS)
                    withContext(Dispatchers.Main) {
                        onSongsChanged()
                    }
                }

                if (!key.reset()) break
            }
        } catch (_: ClosedWatchServiceException) {
            // Expected on dispose
        } catch (_: InterruptedException) {
            // Expected on cancel
        } finally {
            watchService.close()
        }
    }

    /**
     * True when this event should trigger a reload: a .song file changed, or a songbook folder
     * appeared — in which case the new folder is registered for watching too.
     */
    internal fun isRelevantEvent(
        event: WatchEvent<*>,
        key: WatchKey,
        watchService: WatchService,
    ): Boolean {
        // OVERFLOW carries no path and its context is null; the caller filters it, but nothing
        // here enforces that and a null dereference would take the watcher's whole coroutine down.
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) return false
        val fileName = event.context()?.toString() ?: return false
        val isSongFile = fileName.substringAfterLast('.', "").lowercase() == Constants.EXTENSION_SONG
        val file = (key.watchable() as? Path)?.resolve(fileName)?.toFile()
        if (file?.isDirectory != true) return isSongFile
        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            runCatching {
                file.toPath().register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                )
            }
        }
        return true
    }

    /**
     * Waits for a song folder that is not there yet — made, unzipped or mounted after the app
     * started — by watching the nearest folder above it that is, and loads the songs once it
     * appears. Giving up instead left the Songs tab empty until the app was restarted.
     */
    private suspend fun awaitFolder(rootDir: File) = coroutineScope {
        val start = nearestExistingFolder(rootDir) ?: return@coroutineScope
        val watchService = FileSystems.getDefault().newWatchService()
        if (!adopt(watchService)) return@coroutineScope
        try {
            var watching = start
            watching.toPath().register(watchService, StandardWatchEventKinds.ENTRY_CREATE)
            // Asked once before waiting: the folder may have appeared since the check above.
            var step = awaitStep(rootDir, watching)
            onWatching()
            while (isActive && step !is AwaitStep.Appeared) {
                if (step is AwaitStep.WatchNearer) {
                    watching = step.folder
                    watching.toPath().register(watchService, StandardWatchEventKinds.ENTRY_CREATE)
                } else {
                    val key = watchService.take()
                    key.pollEvents()
                    key.reset()
                }
                step = awaitStep(rootDir, watching)
            }
            if (step is AwaitStep.Appeared) {
                // Loading the songs watches the folder itself again, through watchDirectory.
                withContext(Dispatchers.Main) { onSongsChanged() }
            }
        } catch (_: ClosedWatchServiceException) {
            // Expected on dispose
        } catch (_: InterruptedException) {
            // Expected on cancel
        } finally {
            watchService.close()
        }
    }

    fun dispose() {
        stop()
    }

    /**
     * Closing a watch service waits for its polling thread to stop, which on Windows can take
     * hundreds of milliseconds (CHURCH-PRESENTER-DESKTOP-KJ), and this is called from the event
     * thread -- so the close happens on a thread of its own. The job is cancelled first, so
     * nothing reads the old service once it is let go.
     */
    private fun stop() {
        val old = synchronized(serviceLock) {
            watchJob?.cancel()
            watchJob = null
            watchService.also { watchService = null }
        }
        old?.let {
            Thread({ runCatching { it.close() } }, "song-watch-close").apply { isDaemon = true }.start()
        }
    }
}

/** What waiting for a missing song folder does next. */
internal sealed interface AwaitStep {
    /** The folder is there: load it. */
    data object Appeared : AwaitStep

    /** A folder on the way to it appeared: watch that one, which is nearer. */
    data class WatchNearer(val folder: File) : AwaitStep

    /** Nothing nearer yet: keep watching. */
    data object KeepWaiting : AwaitStep
}

/** What to do about [root] while [watching] the folder above it. */
internal fun awaitStep(root: File, watching: File): AwaitStep {
    if (root.isDirectory) return AwaitStep.Appeared
    val nearest = nearestExistingFolder(root)
    return if (nearest != null && nearest.absoluteFile != watching.absoluteFile) {
        AwaitStep.WatchNearer(nearest)
    } else {
        AwaitStep.KeepWaiting
    }
}

/** The nearest folder above [root] that exists, or null when none does. */
internal fun nearestExistingFolder(root: File): File? =
    generateSequence(root.absoluteFile.parentFile) { it.parentFile }.firstOrNull { it.isDirectory }

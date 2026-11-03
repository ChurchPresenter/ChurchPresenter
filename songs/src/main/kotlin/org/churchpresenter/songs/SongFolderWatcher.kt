package org.churchpresenter.songs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val onSongsChanged: () -> Unit
) {
    private var watchJob: Job? = null

    @Volatile private var watchService: WatchService? = null

    fun watchDirectory(rootDir: File) {
        stop()
        if (rootDir.isFile) return
        if (!rootDir.isDirectory) {
            awaitFolder(rootDir)
            return
        }

        val watchService = FileSystems.getDefault().newWatchService()
        this.watchService = watchService
        watchJob = scope.launch(Dispatchers.IO) {
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
    private fun awaitFolder(rootDir: File) {
        val start = nearestExistingFolder(rootDir) ?: return
        val watchService = FileSystems.getDefault().newWatchService()
        this.watchService = watchService
        watchJob = scope.launch(Dispatchers.IO) {
            try {
                var watching = start
                watching.toPath().register(watchService, StandardWatchEventKinds.ENTRY_CREATE)
                // Asked once before waiting: the folder may have appeared since the check above.
                var step = awaitStep(rootDir, watching)
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
    }

    fun dispose() {
        stop()
    }

    private fun stop() {
        watchJob?.cancel()
        watchJob = null
        watchService?.let { runCatching { it.close() } }
        watchService = null
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

package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds

class SongFolderWatcher(
    private val scope: CoroutineScope,
    private val onSongsChanged: () -> Unit
) {
    private var watchJob: Job? = null

    fun watchDirectory(rootDir: File) {
        watchJob?.cancel()
        if (!rootDir.exists() || !rootDir.isDirectory) return

        watchJob = scope.launch(Dispatchers.IO) {
            val watchService = FileSystems.getDefault().newWatchService()
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
                        val fileName = event.context().toString()

                        // Check if it's a .song file change
                        if (fileName.substringAfterLast('.', "").lowercase() == Constants.EXTENSION_SONG) {
                            relevant = true
                        }

                        // Check if it's a directory change (new songbook folder)
                        val watchedPath = (key.watchable() as? Path)?.resolve(fileName)
                        if (watchedPath != null) {
                            val file = watchedPath.toFile()
                            if (file.isDirectory) {
                                // Register the new subdirectory for watching
                                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                                    try {
                                        file.toPath().register(
                                            watchService,
                                            StandardWatchEventKinds.ENTRY_CREATE,
                                            StandardWatchEventKinds.ENTRY_DELETE,
                                            StandardWatchEventKinds.ENTRY_MODIFY
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                                relevant = true
                            }
                        }
                    }

                    if (relevant) {
                        // Debounce: wait a bit for batch operations to settle
                        delay(300)
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

    fun dispose() {
        watchJob?.cancel()
    }
}

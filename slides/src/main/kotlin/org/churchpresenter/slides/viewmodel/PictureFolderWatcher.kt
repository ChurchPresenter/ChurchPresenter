package org.churchpresenter.slides.viewmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent

private const val MAX_RESCAN_ATTEMPTS = 3

/**
 * How many times the folder watcher re-reads a newly created file before giving up on it.
 *
 * Three tries spanning ~240ms: enough to outlast a local copy finishing its write, short enough that
 * a genuinely corrupt file is reported almost immediately rather than sitting on "Loading…".
 */
private const val THUMBNAIL_RETRY_ATTEMPTS = 3

/** The picture file extensions a folder is loaded and watched for. */
internal val PICTURE_EXTENSIONS = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")

/** Keeps the image list in step with the selected folder as files arrive in it and leave it. */
internal class PictureFolderWatcher(
    private val state: PicturesState,
    private val thumbnails: PictureThumbnails,
    /** See `PicturesViewModel`'s parameter of the same name: where every snapshot write is made. */
    private val mainDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {
    private var watchJob: Job? = null

    /** Stops watching whatever folder is being watched. */
    fun cancel() {
        watchJob?.cancel()
        watchJob = null
    }

    fun start(folder: File) {
        watchJob?.cancel()
        watchJob = scope.launch {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                // On macOS the JDK uses PollingWatchService, which stats every existing entry at
                // registration time. A file deleted concurrently makes register() throw
                // NoSuchFileException, so retry a few times before giving up on watching.
                var registered = false
                var attempt = 0
                while (isActive && !registered) {
                    try {
                        folder.toPath().register(
                            watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE
                        )
                        registered = true
                    } catch (_: java.io.IOException) {
                        if (++attempt >= MAX_RESCAN_ATTEMPTS || !folder.isDirectory) {
                            watchService.close()
                            return@launch
                        }
                    }
                }
                // The watch job's own scope, so it can be put back inside the hop below.
                val watcher = this
                while (isActive) {
                    val key = watchService.take()
                    for (event in key.pollEvents()) {
                        val fileName = watchedImageName(event) ?: continue
                        // Every state this touches — the images, both thumbnail maps and the
                        // selected index — is snapshot state, and this coroutine runs on IO. A
                        // write from here races the thread advancing the global snapshot and
                        // throws `Reading a state that was created after the snapshot was taken`
                        // out of the watcher, where nothing catches it. It took CI red on
                        // `PicturesTabExtraTest`, blamed on whichever test was running when a
                        // previous folder's watcher woke up.
                        //
                        // Hopped here rather than inside `addWatchedImage`/`removeWatchedImage`
                        // for two reasons: one hop covers the list, both maps and the index
                        // together, and `PicturesViewModelWatchRaceTest` drives those two
                        // functions from raw threads on purpose — confining them would serialise
                        // its lanes and leave it passing while racing nothing.
                        //
                        // `with(watcher)` is load-bearing: without it the receiver inside
                        // `withContext` is the hop's own scope, and `addWatchedImage`'s
                        // `launch { decodeThumbnail(...) }` would decode images on the event
                        // queue. Putting the outer scope back keeps both the `isActive` gates and
                        // the decode's parentage exactly as they were.
                        withContext(mainDispatcher) {
                            with(watcher) { applyWatchEvent(event.kind(), File(folder, fileName)) }
                        }
                    }
                    if (!key.reset()) break
                }
                watchService.close()
            } catch (_: java.nio.file.ClosedWatchServiceException) {
                // Expected on dispose
            } catch (_: InterruptedException) {
                // Expected on cancel
            } catch (_: java.io.IOException) {
                // Folder became unavailable mid-watch (deleted/unmounted). Watching is best-effort.
            }
        }
    }

    /**
     * The image file name [event] refers to, or null when it is not an event to act on.
     *
     * OVERFLOW is filtered *before* [WatchEvent.context] is read. Its context is not a path and is
     * null in practice, so testing for OVERFLOW after dereferencing the context never runs — the
     * null dereference throws first, which is how this crashed in the field.
     */
    fun watchedImageName(event: WatchEvent<*>): String? {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) return null
        val fileName = event.context()?.toString() ?: return null
        return if (fileName.substringAfterLast('.', "").lowercase() in PICTURE_EXTENSIONS) fileName else null
    }

    /** Applies one watch event to the image list; true when the list actually changed. */
    fun CoroutineScope.applyWatchEvent(kind: WatchEvent.Kind<*>, file: File): Boolean = when (kind) {
        StandardWatchEventKinds.ENTRY_CREATE -> addWatchedImage(file)
        StandardWatchEventKinds.ENTRY_DELETE -> removeWatchedImage(file)
        else -> false
    }

    fun CoroutineScope.addWatchedImage(file: File): Boolean {
        // isActive gates the add: cancellation is cooperative, so a watcher cancelled by
        // clearImages() can still be mid-pollEvents() here — an add now would land in the images
        // after the reload and duplicate a path.
        if (!isActive || !file.exists() || !file.isFile) return false
        // Insert in sorted order, keep the selected image stable. The membership test belongs under
        // the same lock as the insert: apart, it is a check-then-act, and a duplicate path in
        // the images crashes the LazyVerticalGrid keyed by absolutePath. Null means "already there".
        val insertedAt: Int = synchronized(state.imagesLock) {
            val images = state.images
            if (images.any { it.absolutePath == file.absolutePath }) {
                null
            } else {
                val insertIndex = images.indexOfFirst { it.name > file.name }
                if (insertIndex >= 0) images.add(insertIndex, file) else images.add(file)
                insertIndex
            }
        } ?: return false
        if (insertedAt >= 0 && insertedAt <= state.selectedImageIndex.value) state.selectedImageIndex.value++
        // A file copied into a watched folder is seen the moment it is created, usually before it
        // is fully written, so the first decode of it legitimately fails.
        launch {
            thumbnails.reportThumbnailFailures(
                listOfNotNull(thumbnails.decodeThumbnail(file, attempts = THUMBNAIL_RETRY_ATTEMPTS))
            )
        }
        return true
    }

    fun CoroutineScope.removeWatchedImage(file: File): Boolean {
        // The same gate, and for the same reason, as addWatchedImage: a watcher cancelled by
        // clearImages() can still be working through a batch of events, and the list it is
        // removing from has already been emptied and repopulated for another folder.
        if (!isActive) return false
        // indexOf and removeAt have to be one step. Read apart, the index goes stale the moment
        // another writer shrinks the list, and removeAt then throws IndexOutOfBoundsException out
        // of the watcher coroutine, where nothing catches it.
        val idx = synchronized(state.imagesLock) {
            val at = state.images.indexOf(file)
            if (at >= 0) state.images.removeAt(at)
            at
        }
        if (idx < 0) return false
        state.thumbnails.remove(file)
        state.thumbnailFailures.remove(file)
        val selected = state.selectedImageIndex
        if (idx < selected.value) {
            selected.value--
        } else if (selected.value >= state.images.size && state.images.isNotEmpty()) {
            selected.value = state.images.size - 1
        }
        return true
    }
}

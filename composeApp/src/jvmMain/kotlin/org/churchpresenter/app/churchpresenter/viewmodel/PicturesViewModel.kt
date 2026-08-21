package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.PictureDecoder
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.WatchEvent
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private const val MAX_RESCAN_ATTEMPTS = 3

/** How long to wait before re-reading a file whose first decode failed. */
private const val THUMBNAIL_RETRY_MS = 120L

/**
 * How many times the folder watcher re-reads a newly created file before giving up on it.
 *
 * Three tries spanning ~240ms: enough to outlast a local copy finishing its write, short enough that
 * a genuinely corrupt file is reported almost immediately rather than sitting on "Loading…".
 */
private const val THUMBNAIL_RETRY_ATTEMPTS = 3

/**
 * How many times a decoded thumbnail is written into the state maps before giving up on it, and how
 * long between tries.
 *
 * The write races the thread advancing the global snapshot and can lose; the window is momentary, so
 * a handful of tries a few milliseconds apart clears it without costing anything measurable.
 */
/**
 * How many unreadable files one warning describes in full.
 *
 * A folder where everything failed is one problem, not a hundred, and the first handful of lines
 * already say which kind of file it is.
 */
private const val MAX_REPORTED_FAILURES = 20

private const val PUBLISH_ATTEMPTS = 4
private const val PUBLISH_RETRY_MS = 20L

class PicturesViewModel(
    appSettings: AppSettings? = null
) {
    private val defaultDirectory = appSettings?.pictureSettings?.storageDirectory ?: ""

    // State
    private val _selectedFolder = mutableStateOf<File?>(null)
    val selectedFolder: File? get() = _selectedFolder.value

    private val _images: SnapshotStateList<File> = mutableStateListOf()
    val images: List<File> get() = _images

    private val _thumbnails: SnapshotStateMap<File, ImageBitmap> = SnapshotStateMap()
    val thumbnails: Map<File, ImageBitmap> get() = _thumbnails

    /**
     * Files whose thumbnail could not be decoded, against the reason.
     *
     * The grid draws "Loading…" for any file with no entry in [thumbnails], so before this existed a
     * decode that threw was indistinguishable from one still running — and since the failure was
     * swallowed, the tile said "Loading…" for the rest of the session. A corrupt or truncated image
     * meant a permanent placeholder and no error anywhere.
     *
     * Every file therefore ends up in exactly one of [thumbnails] or here, which is also what lets a
     * test wait for a positive signal instead of for the absence of a label.
     */
    private val _thumbnailFailures: SnapshotStateMap<File, String> = SnapshotStateMap()
    val thumbnailFailures: Map<File, String> get() = _thumbnailFailures

    /**
     * Decodes [file] into [thumbnails], or records why it could not be in [thumbnailFailures].
     *
     * Returns a line describing the failure for [reportThumbnailFailures], or null when there is
     * nothing to report — the file decoded, or it holds no bytes at all. An empty file is a copy
     * that has not started, a cloud placeholder that has not been materialised, or a download in
     * flight; the tile still says so, but nothing about the app went wrong and reporting it buries
     * the files that genuinely could not be read.
     *
     * [attempts] exists for the folder watcher: a file being copied into a watched folder is
     * routinely seen the instant it is created and long before it is complete, so the first decode
     * of a half-written file legitimately fails and the same read succeeds moments later. The
     * initial folder load reads files that were already there and needs no retry.
     */
    internal suspend fun decodeThumbnail(file: File, attempts: Int = 1): String? {
        var lastError: Exception? = null
        repeat(attempts) { attempt ->
            try {
                publishThumbnail(file, loadImageBitmap(file))
                return null
            } catch (e: CancellationException) {
                // Disposing the view model cancels the decode. That is not a broken file, and
                // recording it would mark a working image failed and warn about it.
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < attempts - 1) delay(THUMBNAIL_RETRY_MS)
            }
        }
        val reason = lastError?.message ?: lastError?.toString() ?: "unknown"
        _thumbnailFailures[file] = reason
        if (file.length() == 0L) return null
        // The reason names the file — the tile and the local log want that, a report does not, so
        // the name comes out here rather than at the reporting end, where the exception's own
        // wording could put it back at any time.
        return "${reason.replace(file.name, "<file>")} (${PictureDecoder.diagnose(file)})"
    }

    /**
     * Reports the thumbnails that could not be decoded during one load, as a single warning.
     *
     * One event per file made every file name its own Sentry issue — a folder of unreadable
     * pictures arrived as a folder of unrelated-looking problems, each titled with a name belonging
     * to the person who reported it. The title is constant so the whole class of failure groups
     * into one issue, the count is a tag, and what actually distinguishes the files is in the
     * detail, capped at [MAX_REPORTED_FAILURES] because the first few are enough to tell what kind
     * of file it is and the rest are the same line again.
     */
    private fun reportThumbnailFailures(diagnostics: List<String>) {
        if (diagnostics.isEmpty()) return
        CrashReporter.reportWarning(
            "Pictures: thumbnails could not be decoded",
            tags = mapOf("subsystem" to "pictures", "failed.count" to diagnostics.size.toString()),
            extras = mapOf("files" to diagnostics.take(MAX_REPORTED_FAILURES).joinToString("\n"))
        )
    }

    /**
     * Writes a decoded thumbnail into the state maps, from the background thread that decoded it.
     *
     * Both maps are snapshot state, and a write from a thread other than the one advancing the
     * global snapshot can lose that race — the *write* throws `Reading a state that was created
     * after the snapshot was taken or in a snapshot that has not yet been applied`. The image is
     * fine; only the moment it was published in was wrong, so the write is simply made again.
     *
     * Writing inside `Snapshot.withMutableSnapshot` instead is not the fix — it moves the identical
     * failure onto the readers, where the grid throws it out of composition.
     *
     * Whatever the last attempt throws is left to [decodeThumbnail] to record, so a write that never
     * lands still resolves the file instead of leaving its tile on "Loading…" for ever.
     */
    private suspend fun publishThumbnail(file: File, bitmap: ImageBitmap) {
        repeat(PUBLISH_ATTEMPTS) { attempt ->
            try {
                _thumbnails[file] = bitmap
                _thumbnailFailures.remove(file)
                return
            } catch (e: IllegalStateException) {
                if (attempt == PUBLISH_ATTEMPTS - 1) throw e
                delay(PUBLISH_RETRY_MS)
            }
        }
    }

    private val _selectedImageIndex = mutableStateOf(0)
    var selectedImageIndex: Int
        get() = _selectedImageIndex.value
        set(value) { _selectedImageIndex.value = value }

    private val _isPlaying = mutableStateOf(false)
    var isPlaying: Boolean
        get() = _isPlaying.value
        set(value) { _isPlaying.value = value }

    private val _autoScrollInterval = mutableStateOf(appSettings?.pictureSettings?.autoScrollInterval ?: 5f)
    var autoScrollInterval: Float
        get() = _autoScrollInterval.value
        set(value) { _autoScrollInterval.value = value }

    private val _isLooping = mutableStateOf(appSettings?.pictureSettings?.isLooping ?: true)
    var isLooping: Boolean
        get() = _isLooping.value
        set(value) { _isLooping.value = value }

    private val _transitionDuration = mutableStateOf(appSettings?.pictureSettings?.transitionDuration ?: 500f)
    var transitionDuration: Float
        get() = _transitionDuration.value
        set(value) { _transitionDuration.value = value }

    private val _animationType = mutableStateOf(
        when (appSettings?.pictureSettings?.animationType) {
            Constants.ANIMATION_FADE -> AnimationType.FADE
            Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
            Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
            Constants.ANIMATION_NONE -> AnimationType.NONE
            else -> AnimationType.CROSSFADE
        }
    )
    var animationType: AnimationType
        get() = _animationType.value
        set(value) { _animationType.value = value }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watchJob: Job? = null

    /**
     * Guards every structural change to [_images], and any index read taken in order to make one.
     *
     * The list is a [androidx.compose.runtime.snapshots.SnapshotStateList], which makes a write
     * visible to composition but does not make one atomic against another thread. Writers arrive
     * from three directions: the caller's thread ([loadImagesFromFolder], [clearImages],
     * [moveImage]), the download coroutine in [loadPictureFromRemote], and the folder watcher.
     * More than one watcher can be live at a time — [selectFolder] cancels the previous watch job
     * and refills the list immediately, but cancellation is cooperative, so the outgoing watcher
     * can still be inside `pollEvents()` working through a batch of events against a list that has
     * already been emptied and repopulated underneath it.
     *
     * `indexOf` followed by `removeAt(index)` is the shape that fails: the index is read, the list
     * shrinks, and the removal throws `IndexOutOfBoundsException` from the folder watcher — where
     * nothing catches it. It took CI red intermittently (run 31793721082 on `main`, and again on
     * PR #298) as `index: 5, size: 5`, blamed on whichever screenshot test happened to be running
     * when a previous test's leaked watcher woke up.
     */
    private val imagesLock = Any()

    private val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")

    init {
        val savedFolder = appSettings?.pictureSettings?.storageDirectory.orEmpty()
        if (savedFolder.isNotEmpty()) {
            val folder = File(savedFolder)
            if (folder.exists() && folder.isDirectory) {
                selectFolder(folder)
            }
        }
    }

    // Business Logic Methods

    fun selectFolder(folder: File) {
        _selectedFolder.value = folder
        clearImages() // also cancels the previous folder's watcher
        loadImagesFromFolder(folder)
        startWatching(folder)
    }

    fun loadImagesFromFolder(folder: File) {
        if (!folder.exists() || !folder.isDirectory) {
            return
        }

        // Load images from folder
        val imageFiles = folder.listFiles { file ->
            file.isFile && file.extension.lowercase() in imageExtensions
        }?.sortedBy { it.name } ?: emptyList()

        // Add only files not already present so a re-entrant/repeated load stays idempotent — a
        // duplicate path in _images would crash the LazyVerticalGrid keyed by absolutePath.
        synchronized(imagesLock) {
            _images.addAll(imageFiles.filter { it !in _images })
        }

        // Load thumbnails in background
        scope.launch {
            reportThumbnailFailures(imageFiles.mapNotNull { file -> decodeThumbnail(file) })
        }
    }

    /**
     * Loads a picture folder from an Instance Link primary when [folderPath] doesn't resolve on this
     * machine (e.g. a mirrored schedule item whose folder lives on a network drive mounted
     * differently, or not mounted at all, here). Downloads each image's bytes via [fetchBytes] into
     * a cache dir keyed by [folderId] and populates [_images] with the cached files — same public
     * state contract as [selectFolder], so thumbnails, [syncWithPresenter], and navigation all work
     * unchanged afterward. [folderPath] is only used to build [_selectedFolder]'s display value.
     * [presenterManager] — when non-null, explicitly re-synced after every downloaded image (not
     * just once): images arrive one at a time here (unlike the synchronous local-folder path), and
     * PicturesTab's own reactive sync effect only restarts on selectedImageIndex/presentingMode
     * changes, so without this the presenter could be left showing nothing if presentingMode was
     * already PICTURES (e.g. a second remote item clicked while one was already live) and the
     * currently-selected index's bytes hadn't arrived yet when that effect last ran.
     */
    fun loadPictureFromRemote(
        folderId: String,
        folderPath: String,
        imageCount: Int,
        presenterManager: PresenterManager? = null,
        fetchBytes: suspend (index: Int) -> ByteArray?
    ) {
        clearImages()
        _selectedFolder.value = File(folderPath)
        val cacheDir = File(System.getProperty("user.home"), ".churchpresenter/instance-link/cache/picture-folders/$folderId")
        cacheDir.mkdirs()
        scope.launch {
            for (index in 0 until imageCount) {
                val cacheFile = File(cacheDir, "image_%04d.jpg".format(index))
                var cached = cacheFile.exists()
                if (!cached) {
                    val bytes = fetchBytes(index)
                    if (bytes != null) {
                        val tmp = File(cacheDir, "${cacheFile.name}.tmp")
                        tmp.writeBytes(bytes)
                        cached = tmp.renameTo(cacheFile)
                        if (!cached) tmp.delete()
                    }
                }
                if (cached) {
                    synchronized(imagesLock) { _images.add(cacheFile) }
                    reportThumbnailFailures(listOfNotNull(decodeThumbnail(cacheFile)))
                    presenterManager?.let { syncWithPresenter(it) }
                }
            }
        }
    }

    fun clearImages() {
        watchJob?.cancel()
        watchJob = null
        synchronized(imagesLock) { _images.clear() }
        _thumbnails.clear()
        _thumbnailFailures.clear()
        _selectedImageIndex.value = 0
        _isPlaying.value = false
    }

    /** [onInstanceLinkSendNext] — Instance Link Controller mode, non-null only when connected and
     *  controlling. Invoked unconditionally (even when this Controller's own [_images] is empty,
     *  which is the normal case — Controller mode doesn't mirror the primary's content) so next/prev
     *  still reaches the primary's own currently-live folder. See Constants.WS_CMD_NEXT_PICTURE. */
    fun nextImage(onInstanceLinkSendNext: (() -> Unit)? = null) {
        if (_images.isNotEmpty()) {
            if (_selectedImageIndex.value < _images.size - 1) {
                _selectedImageIndex.value = (_selectedImageIndex.value + 1)
            } else if (_isLooping.value) {
                _selectedImageIndex.value = 0
            } else {
                // Stop playing if at the end and not looping
                _isPlaying.value = false
            }
        }
        onInstanceLinkSendNext?.invoke()
    }

    fun previousImage(onInstanceLinkSendPrevious: (() -> Unit)? = null) {
        if (_images.isNotEmpty()) {
            _selectedImageIndex.value = if (_selectedImageIndex.value > 0) {
                _selectedImageIndex.value - 1
            } else {
                _images.size - 1
            }
        }
        onInstanceLinkSendPrevious?.invoke()
    }

    fun selectImage(index: Int) {
        if (index in _images.indices) {
            _selectedImageIndex.value = index
        }
    }

    private val _imageOrderVersion = mutableStateOf(0)
    val imageOrderVersion: Int get() = _imageOrderVersion.value

    fun moveImage(from: Int, to: Int) {
        if (from == to) return
        val currentFile = getCurrentImageFile()
        // Both indices are re-checked under the lock: a watcher can remove a file between the
        // caller reading these positions off the grid and the move landing.
        val moved = synchronized(imagesLock) {
            if (from !in _images.indices || to !in _images.indices) {
                false
            } else {
                _images.add(to, _images.removeAt(from))
                true
            }
        }
        if (!moved) return
        _imageOrderVersion.value++
        currentFile?.let { file ->
            val newIndex = synchronized(imagesLock) { _images.indexOf(file) }
            if (newIndex >= 0) _selectedImageIndex.value = newIndex
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun getCurrentImageFile(): File? = synchronized(imagesLock) {
        _images.getOrNull(_selectedImageIndex.value)
    }

    /**
     * Opens a native folder chooser dialog and loads images from the selected folder.
     */
    fun openFolderChooser(dialogTitle: String, onFolderSelected: ((String) -> Unit) = {}) {
        scope.launch {
            val dir = FileChooser.platformInstance.chooseSingle(
                path = Path(defaultDirectory),
                title = dialogTitle,
                selectDirectory = true,
                filters = emptyList()
            )
            if (dir != null) {
                selectFolder(dir.toFile())
                onFolderSelected(dir.absolutePathString())
            }
        }
    }

    /**
     * Presents the current image in the presenter window.
     *
     * [onInstanceLinkSendProject] — Instance Link Controller mode, non-null only when connected and
     * controlling. Always sends the whole folder via WS_CMD_PROJECT (never the narrower
     * WS_CMD_SELECT_PICTURE): unlike Bible/Songs, the primary only recognizes a `folderId` it
     * assigned itself when the folder was added to *its own* schedule — since `addPicture` there
     * generates a fresh id rather than preserving one a client sent, a Controller has no reliable way
     * to predict it, so every go-live goes through the schedule-add-and-present path instead.
     */
    fun goLive(presenterManager: PresenterManager, onInstanceLinkSendProject: ((ScheduleItem) -> Unit)? = null) {
        val currentImage = getCurrentImageFile() ?: return
        presenterManager.setSelectedImagePath(currentImage.absolutePath)
        val nextIndex = _selectedImageIndex.value + 1
        presenterManager.setNextImagePath(_images.getOrNull(nextIndex)?.absolutePath)
        presenterManager.setPresentingMode(Presenting.PICTURES)
        presenterManager.setShowPresenterWindow(true)
        onInstanceLinkSendProject?.let { send ->
            getScheduleData()?.let { (folderPath, folderName, imageCount) ->
                send(ScheduleItem.PictureItem(id = java.util.UUID.randomUUID().toString(), folderPath = folderPath, folderName = folderName, imageCount = imageCount))
            }
        }
    }

    /**
     * Returns folder data for adding to the schedule, or null if no folder is selected.
     * The caller is responsible for passing this to ScheduleViewModel.
     */
    fun getScheduleData(): Triple<String, String, Int>? {
        val folder = _selectedFolder.value ?: return null
        return Triple(folder.absolutePath, folder.name, _images.size)
    }

    /**
     * Syncs the currently selected image with the presenter if pictures are being presented.
     */
    fun syncWithPresenter(presenterManager: PresenterManager) {
        val anyScreenOnPictures = presenterManager.presentingMode.value == Presenting.PICTURES ||
            presenterManager.screenLocks.value.values.any { it == Presenting.PICTURES }
        if (anyScreenOnPictures && _images.isNotEmpty()) {
            val currentImage = getCurrentImageFile()
            if (currentImage != null) {
                presenterManager.setSelectedImagePath(currentImage.absolutePath)
                val nextIndex = _selectedImageIndex.value + 1
                presenterManager.setNextImagePath(_images.getOrNull(nextIndex)?.absolutePath)
            }
        }
    }

    private fun loadImageBitmap(file: File): ImageBitmap {
        val originalImage = PictureDecoder.decode(file)

        // Downscale to thumbnail size (400px max dimension) for grid display
        val maxThumbnailSize = 400
        val scale = maxThumbnailSize.toFloat() / maxOf(originalImage.width, originalImage.height)

        return if (scale < 1.0f) {
            val newWidth = (originalImage.width * scale).toInt()
            val newHeight = (originalImage.height * scale).toInt()

            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(newWidth, newHeight)
            val canvas = surface.canvas

            // High-quality downscale using Mitchell filter
            val srcRect = org.jetbrains.skia.Rect.makeWH(originalImage.width.toFloat(), originalImage.height.toFloat())
            val dstRect = org.jetbrains.skia.Rect.makeWH(newWidth.toFloat(), newHeight.toFloat())
            canvas.drawImageRect(
                originalImage,
                srcRect,
                dstRect,
                org.jetbrains.skia.SamplingMode.MITCHELL,
                org.jetbrains.skia.Paint(),
                true
            )

            surface.makeImageSnapshot().toComposeImageBitmap()
        } else {
            originalImage.toComposeImageBitmap()
        }
    }

    private fun startWatching(folder: File) {
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
                while (isActive) {
                    val key = watchService.take()
                    for (event in key.pollEvents()) {
                        val fileName = watchedImageName(event) ?: continue
                        applyWatchEvent(event.kind(), File(folder, fileName))
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
    internal fun watchedImageName(event: WatchEvent<*>): String? {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) return null
        val fileName = event.context()?.toString() ?: return null
        return if (fileName.substringAfterLast('.', "").lowercase() in imageExtensions) fileName else null
    }

    /** Applies one watch event to the image list; true when the list actually changed. */
    internal fun CoroutineScope.applyWatchEvent(kind: WatchEvent.Kind<*>, file: File): Boolean = when (kind) {
        StandardWatchEventKinds.ENTRY_CREATE -> addWatchedImage(file)
        StandardWatchEventKinds.ENTRY_DELETE -> removeWatchedImage(file)
        else -> false
    }

    internal fun CoroutineScope.addWatchedImage(file: File): Boolean {
        // isActive gates the add: cancellation is cooperative, so a watcher cancelled by
        // clearImages() can still be mid-pollEvents() here — an add now would land in _images
        // after the reload and duplicate a path.
        if (!isActive || !file.exists() || !file.isFile) return false
        // Insert in sorted order, keep the selected image stable. The membership test belongs under
        // the same lock as the insert: apart, it is a check-then-act, and a duplicate path in
        // _images crashes the LazyVerticalGrid keyed by absolutePath. Null means "already there".
        val insertedAt: Int = synchronized(imagesLock) {
            if (file in _images) {
                null
            } else {
                val insertIndex = _images.indexOfFirst { it.name > file.name }
                if (insertIndex >= 0) _images.add(insertIndex, file) else _images.add(file)
                insertIndex
            }
        } ?: return false
        if (insertedAt >= 0 && insertedAt <= _selectedImageIndex.value) _selectedImageIndex.value++
        // A file copied into a watched folder is seen the moment it is created, usually before it
        // is fully written, so the first decode of it legitimately fails.
        launch {
            reportThumbnailFailures(
                listOfNotNull(decodeThumbnail(file, attempts = THUMBNAIL_RETRY_ATTEMPTS))
            )
        }
        return true
    }

    internal fun CoroutineScope.removeWatchedImage(file: File): Boolean {
        // The same gate, and for the same reason, as addWatchedImage: a watcher cancelled by
        // clearImages() can still be working through a batch of events, and the list it is
        // removing from has already been emptied and repopulated for another folder.
        if (!isActive) return false
        // indexOf and removeAt have to be one step. Read apart, the index goes stale the moment
        // another writer shrinks the list, and removeAt then throws IndexOutOfBoundsException out
        // of the watcher coroutine, where nothing catches it.
        val idx = synchronized(imagesLock) {
            val at = _images.indexOf(file)
            if (at >= 0) _images.removeAt(at)
            at
        }
        if (idx < 0) return false
        _thumbnails.remove(file)
        _thumbnailFailures.remove(file)
        if (idx < _selectedImageIndex.value) {
            _selectedImageIndex.value--
        } else if (_selectedImageIndex.value >= _images.size && _images.isNotEmpty()) {
            _selectedImageIndex.value = _images.size - 1
        }
        return true
    }

    fun dispose() {
        watchJob?.cancel()
        scope.cancel()
    }
}


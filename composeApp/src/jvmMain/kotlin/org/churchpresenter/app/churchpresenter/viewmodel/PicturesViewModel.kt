package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.HeicDecoder
import org.jetbrains.skia.Image
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

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
        _images.addAll(imageFiles.filter { it !in _images })

        // Load thumbnails in background
        scope.launch {
            imageFiles.forEach { file ->
                try {
                    val bitmap = loadImageBitmap(file)
                    _thumbnails[file] = bitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
                if (!cacheFile.exists()) {
                    val bytes = fetchBytes(index) ?: continue
                    val tmp = File(cacheDir, "${cacheFile.name}.tmp")
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(cacheFile)) { tmp.delete(); continue }
                }
                _images.add(cacheFile)
                try {
                    _thumbnails[cacheFile] = loadImageBitmap(cacheFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                presenterManager?.let { syncWithPresenter(it) }
            }
        }
    }

    fun clearImages() {
        watchJob?.cancel()
        watchJob = null
        _images.clear()
        _thumbnails.clear()
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
        if (from !in _images.indices || to !in _images.indices) return
        val currentFile = getCurrentImageFile()
        _images.add(to, _images.removeAt(from))
        _imageOrderVersion.value++
        currentFile?.let { file ->
            val newIndex = _images.indexOf(file)
            if (newIndex >= 0) _selectedImageIndex.value = newIndex
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun getCurrentImageFile(): File? {
        return if (_selectedImageIndex.value in _images.indices) {
            _images[_selectedImageIndex.value]
        } else {
            null
        }
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
        val bytes = file.readBytes()

        // Try to decode directly with Skia (handles jpg, png, webp, gif, bmp…)
        val originalImage = try {
            Image.makeFromEncoded(bytes)
        } catch (e: Exception) {
            // Skia can't decode HEIC/HEIF natively — convert via platform tool first
            if (file.extension.lowercase() in listOf("heic", "heif")) {
                val jpegBytes = HeicDecoder.toJpegBytes(file)
                    ?: throw Exception("Failed to convert HEIC file ${file.name} — sips/ImageIO returned no data")
                Image.makeFromEncoded(jpegBytes)
            } else {
                throw Exception("Failed to decode image ${file.name}: ${e.message}", e)
            }
        }

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
                    } catch (e: java.io.IOException) {
                        if (++attempt >= 3 || !folder.isDirectory) {
                            watchService.close()
                            return@launch
                        }
                    }
                }
                while (isActive) {
                    val key = watchService.take()
                    var changed = false
                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue
                        val fileName = event.context().toString()
                        val ext = fileName.substringAfterLast('.', "").lowercase()
                        if (ext !in imageExtensions) continue

                        val file = File(folder, fileName)
                        when (kind) {
                            StandardWatchEventKinds.ENTRY_CREATE -> {
                                // isActive gates the add: cancellation is cooperative, so a watcher
                                // cancelled by clearImages() can still be mid-pollEvents() here — an
                                // add now would land in _images after the reload and duplicate a path.
                                if (isActive && file.exists() && file.isFile && file !in _images) {
                                    // Insert in sorted order, keep selected image stable
                                    val insertIndex = _images.indexOfFirst { it.name > file.name }
                                    if (insertIndex >= 0) {
                                        _images.add(insertIndex, file)
                                        if (insertIndex <= _selectedImageIndex.value) {
                                            _selectedImageIndex.value++
                                        }
                                    } else {
                                        _images.add(file)
                                    }
                                    // Load thumbnail
                                    launch {
                                        try {
                                            _thumbnails[file] = loadImageBitmap(file)
                                        } catch (_: Exception) {}
                                    }
                                    changed = true
                                }
                            }
                            StandardWatchEventKinds.ENTRY_DELETE -> {
                                val idx = _images.indexOf(file)
                                if (idx >= 0) {
                                    _images.removeAt(idx)
                                    _thumbnails.remove(file)
                                    if (idx < _selectedImageIndex.value) {
                                        _selectedImageIndex.value--
                                    } else if (_selectedImageIndex.value >= _images.size && _images.isNotEmpty()) {
                                        _selectedImageIndex.value = _images.size - 1
                                    }
                                    changed = true
                                }
                            }
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

    fun dispose() {
        watchJob?.cancel()
        scope.cancel()
    }
}


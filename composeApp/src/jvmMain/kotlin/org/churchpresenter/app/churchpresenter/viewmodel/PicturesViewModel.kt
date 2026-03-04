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
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.createFileChooser
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

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
        clearImages()
        loadImagesFromFolder(folder)
        startWatching(folder)
    }

    fun loadImagesFromFolder(folder: File) {
        if (!folder.exists() || !folder.isDirectory) {
            println("PicturesViewModel: Folder not found or not a directory: ${folder.absolutePath}")
            return
        }

        // Load images from folder
        val imageFiles = folder.listFiles { file ->
            file.isFile && file.extension.lowercase() in imageExtensions
        }?.sortedBy { it.name } ?: emptyList()

        println("PicturesViewModel: Found ${imageFiles.size} images in folder")
        _images.addAll(imageFiles)

        // Load thumbnails in background
        scope.launch {
            imageFiles.forEach { file ->
                try {
                    val bitmap = loadImageBitmap(file)
                    _thumbnails[file] = bitmap
                    println("Successfully loaded: ${file.name}")
                } catch (e: Exception) {
                    println("Failed to load ${file.name}: ${e.message}")
                    e.printStackTrace()
                }
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

    fun nextImage() {
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
    }

    fun previousImage() {
        if (_images.isNotEmpty()) {
            _selectedImageIndex.value = if (_selectedImageIndex.value > 0) {
                _selectedImageIndex.value - 1
            } else {
                _images.size - 1
            }
        }
    }

    fun selectImage(index: Int) {
        if (index in _images.indices) {
            _selectedImageIndex.value = index
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
    fun openFolderChooser(dialogTitle: String, onFolderSelected: ((String) -> Unit)? = null) {
        SwingUtilities.invokeLater {
            val chooser = createFileChooser {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                this.dialogTitle = dialogTitle
                if (defaultDirectory.isNotEmpty()) {
                    currentDirectory = File(defaultDirectory)
                }
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                selectFolder(chooser.selectedFile)
                onFolderSelected?.invoke(chooser.selectedFile.absolutePath)
            }
        }
    }

    /**
     * Presents the current image in the presenter window.
     */
    fun goLive(presenterManager: PresenterManager) {
        val currentImage = getCurrentImageFile() ?: return
        presenterManager.setSelectedImagePath(currentImage.absolutePath)
        presenterManager.setPresentingMode(Presenting.PICTURES)
        presenterManager.setShowPresenterWindow(true)
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
        if (presenterManager.presentingMode.value == Presenting.PICTURES && _images.isNotEmpty()) {
            val currentImage = getCurrentImageFile()
            if (currentImage != null) {
                presenterManager.setSelectedImagePath(currentImage.absolutePath)
            }
        }
    }

    private fun loadImageBitmap(file: File): ImageBitmap {
        println("Loading image: ${file.name}, extension: ${file.extension}, size: ${file.length()} bytes")

        val bytes = file.readBytes()
        println("Read ${bytes.size} bytes from ${file.name}")

        // Try to decode with Skia first
        val originalImage = try {
            Image.makeFromEncoded(bytes)
        } catch (e: Exception) {
            println("Skia failed to decode ${file.name}: ${e.message}")

            // If it's a HEIC file, try converting with ImageIO
            if (file.extension.lowercase() in listOf("heic", "heif")) {
                println("Attempting HEIC conversion using ImageIO for ${file.name}")
                try {
                    // Use ImageIO to read HEIC and convert to JPEG bytes
                    val bufferedImage = ImageIO.read(file)
                    if (bufferedImage != null) {
                        println("ImageIO successfully read HEIC: ${file.name}")

                        // Convert BufferedImage to JPEG bytes
                        val outputStream = ByteArrayOutputStream()
                        ImageIO.write(bufferedImage, "jpg", outputStream)
                        val jpegBytes = outputStream.toByteArray()

                        println("Converted HEIC to JPEG: ${jpegBytes.size} bytes")

                        // Try decoding the JPEG with Skia
                        Image.makeFromEncoded(jpegBytes)
                    } else {
                        println("ImageIO returned null for ${file.name}")
                        throw Exception("ImageIO could not read HEIC file")
                    }
                } catch (heicError: Exception) {
                    println("HEIC conversion also failed for ${file.name}: ${heicError.message}")
                    throw Exception("Failed to decode image ${file.name}: Cannot decode HEIC format. Original error: ${e.message}, Conversion error: ${heicError.message}", e)
                }
            } else {
                throw Exception("Failed to decode image ${file.name}: ${e.message}", e)
            }
        }

        println("Decoded ${file.name}: ${originalImage.width}x${originalImage.height}")

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
                folder.toPath().register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE
                )
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
                                if (file.exists() && file.isFile && file !in _images) {
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
            }
        }
    }

    fun dispose() {
        watchJob?.cancel()
        scope.cancel()
    }
}


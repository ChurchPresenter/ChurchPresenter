package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class PicturesViewModel {
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

    private val _autoScrollInterval = mutableStateOf(5f)
    var autoScrollInterval: Float
        get() = _autoScrollInterval.value
        set(value) { _autoScrollInterval.value = value }

    private val _isLooping = mutableStateOf(true)
    var isLooping: Boolean
        get() = _isLooping.value
        set(value) { _isLooping.value = value }

    private val _transitionDuration = mutableStateOf(500f) // milliseconds
    var transitionDuration: Float
        get() = _transitionDuration.value
        set(value) { _transitionDuration.value = value }

    private val _animationType = mutableStateOf(AnimationType.CROSSFADE)
    var animationType: AnimationType
        get() = _animationType.value
        set(value) { _animationType.value = value }

    private val scope = CoroutineScope(Dispatchers.IO)

    // Business Logic Methods

    fun selectFolder(folder: File) {
        _selectedFolder.value = folder
        clearImages()
        loadImagesFromFolder(folder)
    }

    fun loadImagesFromFolder(folder: File) {
        if (!folder.exists() || !folder.isDirectory) {
            println("PicturesViewModel: Folder not found or not a directory: ${folder.absolutePath}")
            return
        }

        // Load images from folder
        val imageFiles = folder.listFiles { file ->
            file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
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

        // Downscale to thumbnail size (300px max dimension) for better performance
        val maxThumbnailSize = 300
        val scale = maxThumbnailSize.toFloat() / maxOf(originalImage.width, originalImage.height)

        return if (scale < 1.0f) {
            println("Downscaling ${file.name} with scale factor: $scale")
            // Image is larger than thumbnail size, downscale it
            val newWidth = (originalImage.width * scale).toInt()
            val newHeight = (originalImage.height * scale).toInt()

            // Create a scaled bitmap
            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(newWidth, newHeight)
            val canvas = surface.canvas

            // Scale and draw the image
            canvas.scale(scale, scale)
            canvas.drawImage(originalImage, 0f, 0f)

            // Get the resulting bitmap
            surface.makeImageSnapshot().toComposeImageBitmap()
        } else {
            println("Using original size for ${file.name}")
            // Image is already small enough, use as-is
            originalImage.toComposeImageBitmap()
        }
    }
}


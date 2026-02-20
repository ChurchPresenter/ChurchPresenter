package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.AppSettings
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class PresentationViewModel(appSettings: AppSettings? = null) {
    private val _presentations = mutableStateListOf<File>()
    val presentations: List<File> = _presentations

    private val _selectedPresentation = mutableStateOf<File?>(null)
    val selectedPresentation: File?
        get() = _selectedPresentation.value

    private val _slides = mutableStateListOf<ImageBitmap>()
    val slides: SnapshotStateList<ImageBitmap> = _slides

    private val _selectedSlideIndex = mutableStateOf(0)
    val selectedSlideIndex: Int
        get() = _selectedSlideIndex.value

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: Boolean
        get() = _isPlaying.value

    private val _autoScrollInterval = mutableStateOf(appSettings?.pictureSettings?.autoScrollInterval ?: 5f)
    var autoScrollInterval: Float
        get() = _autoScrollInterval.value
        set(value) { _autoScrollInterval.value = value }

    private val _isLooping = mutableStateOf(appSettings?.pictureSettings?.isLooping ?: true)
    var isLooping: Boolean
        get() = _isLooping.value
        set(value) { _isLooping.value = value }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var activeLoadJob: Job? = null  // Track current load job to cancel on switch

    fun loadPresentationByPath(filePath: String) {
        val file = File(filePath)
        if (file.exists() && isValidPresentationFile(file)) {
            val existingFile = _presentations.find { it.absolutePath == file.absolutePath }
            if (existingFile == null) {
                _presentations.add(file)
            }
            selectPresentation(file)
        }
    }

    fun addPresentation(file: File) {
        if (file.exists() && isValidPresentationFile(file)) {
            val existingFile = _presentations.find { it.absolutePath == file.absolutePath }
            if (existingFile == null) {
                _presentations.add(file)
            }
            selectPresentation(file)
        }
    }

    fun removePresentation(file: File) {
        _presentations.removeAll { it.absolutePath == file.absolutePath }
        if (_selectedPresentation.value?.absolutePath == file.absolutePath) {
            _selectedPresentation.value = _presentations.firstOrNull()
            _selectedPresentation.value?.let { selectPresentation(it) } ?: run {
                _slides.clear()
                _selectedSlideIndex.value = 0
            }
        }
    }

    fun selectPresentation(file: File) {
        val existingFile = _presentations.find { it.absolutePath == file.absolutePath }
        if (existingFile != null) {
            _selectedPresentation.value = existingFile
            _selectedSlideIndex.value = 0
            activeLoadJob?.cancel()
            loadSlides(existingFile)
        }
    }

    fun nextSlide() {
        if (_selectedSlideIndex.value < _slides.size - 1) {
            _selectedSlideIndex.value++
        } else if (_isLooping.value && _slides.isNotEmpty()) {
            // Loop back to first slide if looping is enabled
            _selectedSlideIndex.value = 0
        }
    }

    fun previousSlide() {
        if (_selectedSlideIndex.value > 0) {
            _selectedSlideIndex.value--
        } else if (_isLooping.value && _slides.isNotEmpty()) {
            // Loop to last slide if looping is enabled
            _selectedSlideIndex.value = _slides.size - 1
        }
    }

    fun selectSlide(index: Int) {
        if (index in _slides.indices) {
            _selectedSlideIndex.value = index
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun clearPresentations() {
        _presentations.clear()
        _selectedPresentation.value = null
        _slides.clear()
        _selectedSlideIndex.value = 0
    }

    private fun isValidPresentationFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("ppt", "pptx", "key", "pdf")
    }

    private fun loadSlides(file: File) {
        activeLoadJob = scope.launch {
            try {
                withContext(Dispatchers.Main) { _slides.clear() }
                when (file.extension.lowercase()) {
                    "pdf" -> loadPdfSlides(file)
                    "pptx", "ppt" -> loadPowerPointSlides(file)
                    "key" -> loadKeynoteSlides(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadPdfSlides(file: File) {
        try {
            val pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument")
            val pdfRendererClass = Class.forName("org.apache.pdfbox.rendering.PDFRenderer")
            val loadMethod = pdDocumentClass.getMethod("load", File::class.java)
            val document = loadMethod.invoke(null, file)
            val numberOfPages = pdDocumentClass.getMethod("getNumberOfPages").invoke(document) as Int
            val renderer = pdfRendererClass.getConstructor(pdDocumentClass).newInstance(document)
            val renderMethod = pdfRendererClass.getMethod("renderImageWithDPI", Int::class.java, Float::class.java)
            for (page in 0 until numberOfPages) {
                val image = renderMethod.invoke(renderer, page, 150f) as BufferedImage
                val imageBitmap = bufferedImageToImageBitmap(image)
                withContext(Dispatchers.Main) { _slides.add(imageBitmap) }
            }
            pdDocumentClass.getMethod("close").invoke(document)
        } catch (e: ClassNotFoundException) {
            // PDFBox not found
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadPowerPointSlides(file: File) {
        try {
            val extension = file.extension.lowercase()
            if (extension == "pptx") {
                try {
                    val xmlSlideShowClass = Class.forName("org.apache.poi.xslf.usermodel.XMLSlideShow")
                    val fileInputStream = java.io.FileInputStream(file)
                    val ppt = xmlSlideShowClass.getConstructor(java.io.InputStream::class.java).newInstance(fileInputStream)
                    val slides = xmlSlideShowClass.getMethod("getSlides").invoke(ppt) as List<*>
                    val pageSize = xmlSlideShowClass.getMethod("getPageSize").invoke(ppt) as java.awt.Dimension
                    slides.forEach { slide ->
                        try {
                            val img = BufferedImage(pageSize.width, pageSize.height, BufferedImage.TYPE_INT_RGB)
                            val graphics = img.createGraphics()
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON)
                            graphics.paint = java.awt.Color.WHITE
                            graphics.fillRect(0, 0, pageSize.width, pageSize.height)
                            slide!!::class.java.getMethod("draw", java.awt.Graphics2D::class.java).invoke(slide, graphics)
                            graphics.dispose()
                            val imageBitmap = bufferedImageToImageBitmap(img)
                            withContext(Dispatchers.Main) { _slides.add(imageBitmap) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    xmlSlideShowClass.getMethod("close").invoke(ppt)
                    fileInputStream.close()
                } catch (e: ClassNotFoundException) {
                    // Apache POI not found
                }
            } else if (extension == "ppt") {
                try {
                    val hslfSlideShowClass = Class.forName("org.apache.poi.hslf.usermodel.HSLFSlideShow")
                    val fileInputStream = java.io.FileInputStream(file)
                    val ppt = hslfSlideShowClass.getConstructor(java.io.InputStream::class.java).newInstance(fileInputStream)
                    val slides = hslfSlideShowClass.getMethod("getSlides").invoke(ppt) as List<*>
                    val pageSize = hslfSlideShowClass.getMethod("getPageSize").invoke(ppt) as java.awt.Dimension
                    slides.forEach { slide ->
                        try {
                            val img = BufferedImage(pageSize.width, pageSize.height, BufferedImage.TYPE_INT_RGB)
                            val graphics = img.createGraphics()
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
                            graphics.paint = java.awt.Color.WHITE
                            graphics.fillRect(0, 0, pageSize.width, pageSize.height)
                            slide!!::class.java.getMethod("draw", java.awt.Graphics2D::class.java).invoke(slide, graphics)
                            graphics.dispose()
                            val imageBitmap = bufferedImageToImageBitmap(img)
                            withContext(Dispatchers.Main) { _slides.add(imageBitmap) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    hslfSlideShowClass.getMethod("close").invoke(ppt)
                    fileInputStream.close()
                } catch (e: ClassNotFoundException) {
                    // Apache POI not found
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private suspend fun loadKeynoteSlides(file: File) {
        loadKeynoteViaUnzip(file)
    }

    private suspend fun loadKeynoteViaUnzip(file: File) {
        try {
            val keynoteDir: File
            var tempDir: File? = null

            if (file.isDirectory) {
                keynoteDir = file
            } else if (file.name.endsWith(".key")) {
                tempDir = File(System.getProperty("java.io.tmpdir"), "keynote_extract_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                try {
                    java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(file))).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val outFile = File(tempDir, entry.name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                java.io.BufferedOutputStream(java.io.FileOutputStream(outFile)).use { out ->
                                    zip.copyTo(out)
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                    keynoteDir = tempDir
                } catch (e: Exception) {
                    tempDir.deleteRecursively()
                    return
                }
            } else {
                return
            }

            try {
                val dataDir = File(keynoteDir, "Data")
                if (!dataDir.exists()) return

                val allImageFiles = dataDir.listFiles()?.filter { f ->
                    f.isFile && f.extension.lowercase() in listOf("jpg", "jpeg", "png", "tiff", "tif")
                } ?: emptyList()

                val stFiles = allImageFiles.filter { it.name.lowercase().startsWith("st-") }
                val mtFullSizeFiles = allImageFiles.filter { f ->
                    val name = f.name.lowercase()
                    name.startsWith("mt-") && !name.contains("-small-")
                }

                val orderedSlides = sortKeynoteSlidesByIndex(keynoteDir, stFiles + mtFullSizeFiles)

                orderedSlides.forEach { slideFile ->
                    try {
                        val bufferedImage = ImageIO.read(slideFile)
                        if (bufferedImage != null) {
                            val imageBitmap = bufferedImageToImageBitmap(bufferedImage)
                            withContext(Dispatchers.Main) { _slides.add(imageBitmap) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } finally {
                tempDir?.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sortKeynoteSlidesByIndex(keynoteDir: File, slideFiles: List<File>): List<File> {
        if (slideFiles.isEmpty()) return slideFiles

        val fileById = slideFiles.associateBy { file ->
            file.nameWithoutExtension.split("-").lastOrNull()?.toLongOrNull()
        }.filterKeys { it != null }

        val ids = fileById.keys.filterNotNull().toSet()

        val presentationIwa = File(keynoteDir, "Index/Presentation.iwa")
        if (presentationIwa.exists()) {
            try {
                val bytes = presentationIwa.readBytes()
                data class VarintMatch(val value: Long, val offset: Int)
                val varintMatches = mutableListOf<VarintMatch>()
                var i = 0
                while (i < bytes.size) {
                    var value = 0L
                    var shift = 0
                    val startOffset = i
                    while (i < bytes.size) {
                        val b = bytes[i].toInt() and 0xFF
                        i++
                        value = value or ((b and 0x7F).toLong() shl shift)
                        if (b and 0x80 == 0) break
                        shift += 7
                        if (shift >= 63) break
                    }
                    if (value in ids) varintMatches.add(VarintMatch(value, startOffset))
                }
                if (varintMatches.isNotEmpty()) {
                    val ordered = varintMatches
                        .groupBy { it.value }
                        .mapValues { (_, matches) -> matches.minBy { it.offset } }
                        .values
                        .sortedBy { it.offset }
                        .mapNotNull { fileById[it.value] }
                        .distinct()
                    val missing = slideFiles.filter { it !in ordered }
                    return ordered + missing
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val indexFile = File(keynoteDir, "index.apxl")
        if (indexFile.exists()) {
            try {
                val content = indexFile.readText()
                val orderedIds = Regex("""<key:slide[^>]*sf:id="(\d+)"""")
                    .findAll(content).map { it.groupValues[1] }.toList()
                if (orderedIds.isNotEmpty()) {
                    val ordered = orderedIds.mapNotNull { fileById[it.toLongOrNull()] }
                    return ordered + slideFiles.filter { it !in ordered }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return slideFiles.sortedBy { it.lastModified() }
    }

    private fun bufferedImageToImageBitmap(bufferedImage: BufferedImage): ImageBitmap {
        val byteArray = java.io.ByteArrayOutputStream().use { baos ->
            ImageIO.write(bufferedImage, "PNG", baos)
            baos.toByteArray()
        }
        return org.jetbrains.skia.Image.makeFromEncoded(byteArray).toComposeImageBitmap()
    }
}

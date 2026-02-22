package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
            // Step 1: Read the zip entry order BEFORE extracting — this is the slide order
            val slideIwaOrder: List<Long> = if (!file.isDirectory) {
                readSlideOrderFromZip(file)
            } else {
                emptyList()
            }

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

                // st- files are exactly one-per-slide thumbnails generated by Keynote
                val stFiles = allImageFiles.filter { it.name.lowercase().startsWith("st-") }

                val orderedSlides = sortByZipOrder(stFiles, slideIwaOrder)

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

    /**
     * Reads the zip entries to get Slide-*.iwa order. The order entries appear in the zip
     * IS the presentation order — Keynote writes them sequentially slide 1, 2, 3...
     * Returns list of slide numeric IDs in presentation order.
     */
    private fun readSlideOrderFromZip(file: File): List<Long> {
        val ordered = mutableListOf<Long>()
        try {
            java.util.zip.ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { name ->
                        val base = name.substringAfterLast("/")
                        base.startsWith("Slide-") && base.endsWith(".iwa") && base != "Slide.iwa"
                    }
                    .forEach { name ->
                        val base = name.substringAfterLast("/")
                        // "Slide-2652855-2.iwa" -> "2652855-2" -> split[0] -> "2652855"
                        val idStr = base.removePrefix("Slide-").removeSuffix(".iwa").split("-")[0]
                        idStr.toLongOrNull()?.let { ordered.add(it) }
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ordered
    }

    /**
     * Sort st- thumbnail files by matching each to its Slide-*.iwa positionally.
     *
     * Key insight: Keynote assigns both SLIDEID (in Slide-NNNN.iwa filename) and
     * STID (trailing number in st-UUID-STID.jpg) sequentially at slide creation time.
     * So sorting Slide-*.iwa by SLIDEID and st- files by STID produces matching orders —
     * the Nth slide IWA corresponds to the Nth st- file. No binary parsing needed.
     *
     * The zip entry order gives us the PRESENTATION order of Slide-*.iwa files.
     */
    private fun sortByZipOrder(stFiles: List<File>, slideIwaOrder: List<Long>): List<File> {
        if (stFiles.isEmpty()) return stFiles

        // Sort st- files by their trailing STID number (creation order)
        val stFilesSortedByStId = stFiles.sortedBy { file ->
            file.nameWithoutExtension.split("-").lastOrNull()?.toLongOrNull() ?: Long.MAX_VALUE
        }

        // If we have zip entry order for Slide-*.iwa files, use it directly
        if (slideIwaOrder.isNotEmpty()) {
            // Sort the same st- files by matching: slideIwaOrder[i] pairs with stFilesSortedByStId[i]
            // slideIwaOrder is already in presentation order from zip entries
            // stFilesSortedByStId is in creation order (same as slideIwaOrder sorted numerically)
            val slideIwaOrderSorted = slideIwaOrder.sorted()

            // Build mapping: slideId rank (0,1,2...) in numeric order -> st- file
            val rankToStFile = stFilesSortedByStId.mapIndexed { rank, stFile -> rank to stFile }.toMap()

            // For each slideId in presentation order, find its rank in numeric order
            val result = slideIwaOrder.mapIndexed { _, slideId ->
                val rank = slideIwaOrderSorted.indexOf(slideId)
                rankToStFile[rank]
            }.filterNotNull().distinct()

            val missing = stFiles.filter { it !in result }
            return result + missing
        }

        // Fallback: return st- files sorted by STID (creation order ≈ presentation order)
        return stFilesSortedByStId
    }

    private fun bufferedImageToImageBitmap(bufferedImage: BufferedImage): ImageBitmap {
        val byteArray = java.io.ByteArrayOutputStream().use { baos ->
            ImageIO.write(bufferedImage, "PNG", baos)
            baos.toByteArray()
        }
        return org.jetbrains.skia.Image.makeFromEncoded(byteArray).toComposeImageBitmap()
    }

    fun dispose() {
        activeLoadJob?.cancel()
        scope.cancel()
    }
}

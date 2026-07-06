package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.PresentationLoadError
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.InvocationTargetException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO

class PresentationViewModel(private val appSettings: AppSettings? = null) {

    companion object {
        private val APP_SLIDES_DIR = File(System.getProperty("user.home"), ".churchpresenter/slides")

        /**
         * Called on startup: deletes any slide cache folders whose source file is not in
         * [keepPaths] (the union of recent and pinned presentation paths).
         */
        fun cleanupOrphanedCaches(keepPaths: Collection<String>) {
            if (!APP_SLIDES_DIR.exists()) return
            val keepIds = keepPaths.map { presentationIdFor(it) }.toSet()
            APP_SLIDES_DIR.listFiles()?.forEach { dir ->
                if (dir.isDirectory && dir.name !in keepIds) dir.deleteRecursively()
            }
        }

        private fun presentationIdFor(absolutePath: String): String =
            absolutePath.hashCode().toUInt().toString(16)
    }

    private val _presentations = mutableStateListOf<File>()
    val presentations: List<File> = _presentations

    private val _selectedPresentation = mutableStateOf<File?>(null)
    val selectedPresentation: File?
        get() = _selectedPresentation.value

    private val _slideFiles = mutableStateListOf<File>()
    val slideFiles: SnapshotStateList<File> = _slideFiles

    private val _totalSlides = mutableStateOf(0)
    val totalSlides: Int get() = _totalSlides.value

    /** Incremented each time slides finish loading (fresh render or cache hit). Use as LaunchedEffect key. */
    private val _loadGeneration = mutableStateOf(0)
    val loadGeneration: Int get() = _loadGeneration.value

    private val _slideNotes = mutableStateListOf<String>()
    val slideNotes: List<String> get() = _slideNotes

    private val _selectedSlideIndex = mutableStateOf(0)
    val selectedSlideIndex: Int
        get() = _selectedSlideIndex.value

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: Boolean
        get() = _isPlaying.value

    private val _isLoading = mutableStateOf(false)
    val isLoading: Boolean
        get() = _isLoading.value

    private val _loadError = mutableStateOf<PresentationLoadError?>(null)
    val loadError: PresentationLoadError?
        get() = _loadError.value

    private val _autoScrollInterval = mutableStateOf(appSettings?.presentationSettings?.autoScrollInterval ?: 5f)
    var autoScrollInterval: Float
        get() = _autoScrollInterval.value
        set(value) { _autoScrollInterval.value = value }

    private val _isLooping = mutableStateOf(appSettings?.presentationSettings?.isLooping ?: true)
    var isLooping: Boolean
        get() = _isLooping.value
        set(value) { _isLooping.value = value }

    private val _transitionDuration = mutableStateOf(appSettings?.presentationSettings?.transitionDuration ?: 500f)
    var transitionDuration: Float
        get() = _transitionDuration.value
        set(value) { _transitionDuration.value = value }

    private val _animationType = mutableStateOf(
        when (appSettings?.presentationSettings?.animationType) {
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
    private var activeLoadJob: Job? = null

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private fun presentationId(file: File): String =
        presentationIdFor(file.absolutePath)

    private fun slidesCacheDir(file: File): File =
        File(APP_SLIDES_DIR, presentationId(file))

    /** Returns true if the cache dir exists, has slides, and its mtime matches the source file. */
    private fun isCacheValid(file: File): Boolean {
        val dir = slidesCacheDir(file)
        if (!dir.exists()) return false
        val mtime = File(dir, "mtime.txt").takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
        return mtime == file.lastModified() &&
            dir.listFiles()?.any { it.name.startsWith("slide_") && it.extension == "jpg" } == true
    }

    private fun loadSlidesFromCache(file: File): List<File>? {
        if (!isCacheValid(file)) return null
        val dir = slidesCacheDir(file)
        return dir.listFiles()
            ?.filter { it.name.startsWith("slide_") && it.extension == "jpg" }
            ?.sortedBy { it.name }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun clearCurrentSlideState() {
        _slideFiles.clear()
        _slideNotes.clear()
    }

    private fun renderWidth(): Int =
        appSettings?.projectionSettings?.getAssignment(0)?.targetBoundsW?.takeIf { it > 0 } ?: 1920

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadPresentationByPath(filePath: String) {
        val file = File(filePath)
        if (file.exists() && isValidPresentationFile(file)) {
            val existingFile = _presentations.find { it.absolutePath == file.absolutePath }
            if (existingFile == null) _presentations.add(file)
            selectPresentation(file)
        }
    }

    fun addPresentation(file: File) {
        if (file.exists() && isValidPresentationFile(file)) {
            val existingFile = _presentations.find { it.absolutePath == file.absolutePath }
            if (existingFile == null) _presentations.add(file)
            selectPresentation(file)
        }
    }

    /**
     * Removes a presentation from the open list.
     * [isInRecentsOrPinned] — when false the disk cache for that file is also deleted.
     */
    fun removePresentation(file: File, isInRecentsOrPinned: Boolean = true) {
        _presentations.removeAll { it.absolutePath == file.absolutePath }
        if (!isInRecentsOrPinned) slidesCacheDir(file).deleteRecursively()
        if (_selectedPresentation.value?.absolutePath == file.absolutePath) {
            _selectedPresentation.value = _presentations.firstOrNull()
            _selectedPresentation.value?.let { selectPresentation(it) } ?: run {
                clearCurrentSlideState()
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
            activeLoadJob = scope.launch { loadOrCacheSlides(existingFile) }
        }
    }

    fun nextSlide() {
        if (_selectedSlideIndex.value < _slideFiles.size - 1) {
            _selectedSlideIndex.value++
        } else if (_isLooping.value && _slideFiles.isNotEmpty()) {
            _selectedSlideIndex.value = 0
        } else {
            _isPlaying.value = false
        }
    }

    fun previousSlide() {
        if (_selectedSlideIndex.value > 0) {
            _selectedSlideIndex.value--
        } else if (_isLooping.value && _slideFiles.isNotEmpty()) {
            _selectedSlideIndex.value = _slideFiles.size - 1
        }
    }

    fun selectSlide(index: Int) {
        if (index in _slideFiles.indices) {
            _selectedSlideIndex.value = index
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun clearPresentations() {
        activeLoadJob?.cancel()
        _presentations.clear()
        _selectedPresentation.value = null
        clearCurrentSlideState()
        _totalSlides.value = 0
        _selectedSlideIndex.value = 0
        _isLoading.value = false
    }

    fun dispose() {
        activeLoadJob?.cancel()
        scope.cancel()
    }

    // ── Load / cache orchestration ────────────────────────────────────────────

    private suspend fun loadOrCacheSlides(file: File) {
        withContext(Dispatchers.Main) { _loadError.value = null }
        val cached = loadSlidesFromCache(file)
        if (cached != null) {
            val dir = slidesCacheDir(file)
            val notes = cached.indices.map { i ->
                File(dir, "note_%04d.txt".format(i)).takeIf { it.exists() }?.readText() ?: ""
            }
            withContext(Dispatchers.Main) {
                clearCurrentSlideState()
                _totalSlides.value = cached.size
                _slideFiles.addAll(cached)
                _slideNotes.addAll(notes)
                _loadGeneration.value++
            }
        } else {
            renderSlides(file)
        }
    }

    private suspend fun renderSlides(file: File) = CrashReporter.trace(
        operation = "presentation.render",
        name = "Render ${file.extension.lowercase()} slides"
    ) {
        val cacheDir = slidesCacheDir(file).also { it.deleteRecursively(); it.mkdirs() }
        var success = false
        try {
            withContext(Dispatchers.Main) {
                clearCurrentSlideState()
                _totalSlides.value = 0
                _isLoading.value = true
            }
            when (file.extension.lowercase()) {
                "pdf" -> {
                    val failure = loadPdfSlides(file, cacheDir)
                    if (failure != null) withContext(Dispatchers.Main) { _loadError.value = failure }
                }
                "pptx", "ppt" -> loadPowerPointSlides(file, cacheDir)
                "key" -> loadKeynoteSlides(file, cacheDir)
            }
            if (_slideFiles.isNotEmpty()) {
                File(cacheDir, "mtime.txt").writeText(file.lastModified().toString())
                withContext(Dispatchers.Main) { _loadGeneration.value++ }
                success = true
            } else {
                if (_loadError.value == null) withContext(Dispatchers.Main) { _loadError.value = PresentationLoadError.RENDER_FAILED }
                CrashReporter.reportWarning(
                    "Presentation: No slides extracted from ${file.extension.lowercase()} file",
                    tags = mapOf(
                        "subsystem" to "presentation",
                        "file.type" to file.extension.lowercase(),
                        "failure.reason" to (_loadError.value?.name?.lowercase() ?: "unknown")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CrashReporter.reportWarning(
                "Presentation: Failed to render ${file.extension.lowercase()} slides",
                throwable = e,
                tags = mapOf("subsystem" to "presentation", "file.type" to file.extension.lowercase())
            )
        } finally {
            if (!success) cacheDir.deleteRecursively()
            withContext(Dispatchers.Main) { _isLoading.value = false }
        }
    }

    // ── Format-specific renderers ─────────────────────────────────────────────

    private suspend fun loadPdfSlides(file: File, cacheDir: File, notesPerSlide: List<String> = emptyList()): PresentationLoadError? {
        val pdDocumentClass = try {
            Class.forName("org.apache.pdfbox.pdmodel.PDDocument")
        } catch (e: ClassNotFoundException) {
            return PresentationLoadError.LIBRARY_MISSING
        }
        var document: Any? = null
        return try {
            val pdfRendererClass = Class.forName("org.apache.pdfbox.rendering.PDFRenderer")
            val loadMethod = pdDocumentClass.getMethod("load", File::class.java)
            document = loadMethod.invoke(null, file)
            val numberOfPages = pdDocumentClass.getMethod("getNumberOfPages").invoke(document) as Int
            if (numberOfPages == 0) return PresentationLoadError.EMPTY_DOCUMENT
            withContext(Dispatchers.Main) { _totalSlides.value = numberOfPages }
            val renderer = pdfRendererClass.getConstructor(pdDocumentClass).newInstance(document)
            val renderMethod = pdfRendererClass.getMethod("renderImageWithDPI", Int::class.java, Float::class.java)
            val targetDpi = 150f
            for (page in 0 until numberOfPages) {
                try {
                    val image = renderMethod.invoke(renderer, page, targetDpi) as BufferedImage
                    val slideFile = File(cacheDir, "slide_%04d.jpg".format(page))
                    ImageIO.write(image, "jpg", slideFile)
                    val notes = notesPerSlide.getOrElse(page) { "" }
                    if (notes.isNotBlank()) File(cacheDir, "note_%04d.txt".format(page)).writeText(notes)
                    withContext(Dispatchers.Main) {
                        _slideFiles.add(slideFile)
                        _slideNotes.add(notes)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            null
        } catch (e: InvocationTargetException) {
            val cause = e.targetException
            (cause ?: e).printStackTrace()
            if (cause is InvalidPasswordException) PresentationLoadError.PASSWORD_PROTECTED else PresentationLoadError.RENDER_FAILED
        } catch (e: Exception) {
            e.printStackTrace()
            PresentationLoadError.RENDER_FAILED
        } finally {
            try { document?.let { pdDocumentClass.getMethod("close").invoke(it) } } catch (_: Exception) {}
        }
    }

    private suspend fun loadPowerPointSlides(file: File, cacheDir: File) {
        try {
            val extension = file.extension.lowercase()
            val displayWidth = renderWidth()
            if (extension == "pptx") {
                try {
                    val xmlSlideShowClass = Class.forName("org.apache.poi.xslf.usermodel.XMLSlideShow")
                    java.io.FileInputStream(file).use { fileInputStream ->
                    val ppt = xmlSlideShowClass.getConstructor(java.io.InputStream::class.java).newInstance(fileInputStream)
                    val slides = xmlSlideShowClass.getMethod("getSlides").invoke(ppt) as List<*>
                    val pageSize = xmlSlideShowClass.getMethod("getPageSize").invoke(ppt) as java.awt.Dimension
                    withContext(Dispatchers.Main) { _totalSlides.value = slides.size }
                    val renderScale = displayWidth.toDouble() / pageSize.width
                    slides.forEachIndexed { slideIndex, slide ->
                        val s = slide ?: return@forEachIndexed
                        try {
                            val imgW = (pageSize.width * renderScale).toInt()
                            val imgH = (pageSize.height * renderScale).toInt()
                            val img = BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB)
                            val graphics = img.createGraphics()
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                            graphics.scale(renderScale, renderScale)
                            graphics.paint = java.awt.Color.WHITE
                            graphics.fillRect(0, 0, pageSize.width, pageSize.height)
                            s::class.java.getMethod("draw", java.awt.Graphics2D::class.java).invoke(s, graphics)
                            graphics.dispose()
                            val notes = extractPoiSlideNotes(s)
                            val slideFile = File(cacheDir, "slide_%04d.jpg".format(slideIndex))
                            ImageIO.write(img, "jpg", slideFile)
                            if (notes.isNotBlank()) File(cacheDir, "note_%04d.txt".format(slideIndex)).writeText(notes)
                            withContext(Dispatchers.Main) {
                                _slideFiles.add(slideFile)
                                _slideNotes.add(notes)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    xmlSlideShowClass.getMethod("close").invoke(ppt)
                    }
                } catch (e: ClassNotFoundException) {
                    // Apache POI not found
                }
            } else if (extension == "ppt") {
                try {
                    val hslfSlideShowClass = Class.forName("org.apache.poi.hslf.usermodel.HSLFSlideShow")
                    java.io.FileInputStream(file).use { fileInputStream ->
                    val ppt = hslfSlideShowClass.getConstructor(java.io.InputStream::class.java).newInstance(fileInputStream)
                    val slides = hslfSlideShowClass.getMethod("getSlides").invoke(ppt) as List<*>
                    val pageSize = hslfSlideShowClass.getMethod("getPageSize").invoke(ppt) as java.awt.Dimension
                    withContext(Dispatchers.Main) { _totalSlides.value = slides.size }
                    val renderScale = displayWidth.toDouble() / pageSize.width
                    slides.forEachIndexed { slideIndex, slide ->
                        val s = slide ?: return@forEachIndexed
                        try {
                            val imgW = (pageSize.width * renderScale).toInt()
                            val imgH = (pageSize.height * renderScale).toInt()
                            val img = BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB)
                            val graphics = img.createGraphics()
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                            graphics.scale(renderScale, renderScale)
                            graphics.paint = java.awt.Color.WHITE
                            graphics.fillRect(0, 0, pageSize.width, pageSize.height)
                            s::class.java.getMethod("draw", java.awt.Graphics2D::class.java).invoke(s, graphics)
                            graphics.dispose()
                            val notes = extractPoiSlideNotes(s)
                            val slideFile = File(cacheDir, "slide_%04d.jpg".format(slideIndex))
                            ImageIO.write(img, "jpg", slideFile)
                            if (notes.isNotBlank()) File(cacheDir, "note_%04d.txt".format(slideIndex)).writeText(notes)
                            withContext(Dispatchers.Main) {
                                _slideFiles.add(slideFile)
                                _slideNotes.add(notes)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    hslfSlideShowClass.getMethod("close").invoke(ppt)
                    }
                } catch (e: ClassNotFoundException) {
                    // Apache POI not found
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadKeynoteSlides(file: File, cacheDir: File) {
        val nativePdf = tryExportKeynoteViaApp(file)
        if (nativePdf != null) {
            try {
                val n = extractKeynoteNotes(file)
                loadPdfSlides(nativePdf, cacheDir, notesPerSlide = n)
            } finally {
                nativePdf.delete()
            }
            if (_slideFiles.isNotEmpty()) return
        }

        val quickLookPdf = extractKeynoteQuickLookPdf(file)
        if (quickLookPdf != null) {
            try {
                val n = extractKeynoteNotes(file)
                loadPdfSlides(quickLookPdf, cacheDir, notesPerSlide = n)
            } finally {
                quickLookPdf.delete()
            }
            if (_slideFiles.isNotEmpty()) return
        }

        val pdfFile = tryExportKeynoteToPdf(file)
        if (pdfFile != null) {
            try {
                val n = extractKeynoteNotes(file)
                loadPdfSlides(pdfFile, cacheDir, notesPerSlide = n)
            } finally {
                pdfFile.delete()
            }
            if (_slideFiles.isNotEmpty()) return
        }

        System.err.println("[Keynote] Falling back to embedded thumbnails for ${file.name}")
        loadKeynoteViaUnzip(file, cacheDir)
    }

    private suspend fun tryExportKeynoteViaApp(file: File): File? = withContext(Dispatchers.IO) {
        if (!System.getProperty("os.name", "").lowercase().contains("mac")) return@withContext null
        try {
            val dest = File(System.getProperty("java.io.tmpdir"), "keynote_export_${System.currentTimeMillis()}.pdf")
            val script = """
                tell application "Keynote"
                    set wasRunning to running
                    set theDoc to open POSIX file "${file.absolutePath}"
                    export theDoc to POSIX file "${dest.absolutePath}" as PDF with properties {PDF image quality:Best}
                    close theDoc saving no
                    if not wasRunning then quit
                end tell
            """.trimIndent()
            val process = ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exited = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                System.err.println("[Keynote] osascript timed out")
                dest.delete()
                return@withContext null
            }
            if (dest.exists() && dest.length() > 0) return@withContext dest
            System.err.println("[Keynote] osascript finished but no PDF produced. Output: $output")
            dest.delete()
            null
        } catch (e: Exception) {
            System.err.println("[Keynote] tryExportKeynoteViaApp failed: ${e.message}")
            null
        }
    }

    private suspend fun extractKeynoteQuickLookPdf(file: File): File? = withContext(Dispatchers.IO) {
        if (!file.isFile || !file.name.endsWith(".key", ignoreCase = true)) return@withContext null
        try {
            val dest = File(System.getProperty("java.io.tmpdir"), "keynote_ql_preview_${System.currentTimeMillis()}.pdf")
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name.equals("QuickLook/Preview.pdf", ignoreCase = true)) {
                        BufferedOutputStream(FileOutputStream(dest)).use { out -> zip.copyTo(out) }
                        zip.closeEntry()
                        if (dest.length() > 0) return@withContext dest
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            dest.delete()
            null
        } catch (e: Exception) {
            System.err.println("[Keynote] Failed to extract QuickLook/Preview.pdf: ${e.message}")
            null
        }
    }

    private suspend fun extractKeynoteNotes(file: File): List<String> = withContext(Dispatchers.IO) {
        if (!file.isFile || !file.name.endsWith(".key", ignoreCase = true)) return@withContext emptyList()
        try {
            val apxlNotes = mutableListOf<String>()
            val iwaNotesMap = mutableMapOf<Long, String>()
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val base = name.substringAfterLast("/")
                    when {
                        base.equals("index.apxl", ignoreCase = true) && apxlNotes.isEmpty() -> {
                            val bytes = zip.readBytes()
                            apxlNotes.addAll(parseApxlNotes(String(bytes, Charsets.UTF_8)))
                        }
                        base.startsWith("Slide") && base.endsWith(".iwa") -> {
                            val bytes = zip.readBytes()
                            val noteText = scanIwaForNoteText(bytes)
                            val sortKey = if (base == "Slide.iwa") -1L
                            else base.removePrefix("Slide-").removeSuffix(".iwa").split("-")[0].toLongOrNull() ?: Long.MAX_VALUE
                            iwaNotesMap[sortKey] = noteText
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (apxlNotes.isNotEmpty()) return@withContext apxlNotes
            if (iwaNotesMap.isNotEmpty()) return@withContext iwaNotesMap.entries.sortedBy { it.key }.map { it.value }
        } catch (e: Exception) {
            System.err.println("[Keynote] extractKeynoteNotes failed: ${e.message}")
        }
        emptyList()
    }

    private fun parseApxlNotes(xml: String): List<String> {
        val result = mutableListOf<String>()
        try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
            val slides = doc.getElementsByTagNameNS("*", "slide")
            for (i in 0 until slides.length) {
                val slide = slides.item(i)
                val notesSb = StringBuilder()
                val children = slide.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.localName == "notes") extractTextFromNode(child, notesSb)
                }
                result.add(notesSb.toString().trim())
            }
        } catch (e: Exception) {
            System.err.println("[Keynote] parseApxlNotes failed: ${e.message}")
        }
        return result
    }

    private fun extractTextFromNode(node: org.w3c.dom.Node, sb: StringBuilder) {
        if (node.nodeType == org.w3c.dom.Node.TEXT_NODE) {
            val text = node.nodeValue?.trim()
            if (!text.isNullOrBlank()) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(text)
            }
        }
        val children = node.childNodes
        for (i in 0 until children.length) extractTextFromNode(children.item(i), sb)
    }

    private fun scanIwaForNoteText(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size - 3) {
            if ((bytes[i].toInt() and 0xFF) == 0xB2 && (bytes[i + 1].toInt() and 0xFF) == 0x38) {
                var length = 0
                var shift = 0
                var j = i + 2
                while (j < bytes.size) {
                    val b = bytes[j].toInt() and 0xFF
                    length = length or ((b and 0x7F) shl shift)
                    j++
                    if (b and 0x80 == 0) break
                    shift += 7
                }
                if (length in 1..4096 && j + length <= bytes.size) {
                    try {
                        val s = String(bytes, j, length, Charsets.UTF_8)
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(s)
                    } catch (_: Exception) {}
                }
            }
            i++
        }
        return sb.toString().trim()
    }

    private suspend fun tryExportKeynoteToPdf(file: File): File? = withContext(Dispatchers.IO) {
        try {
            val outDir = File(System.getProperty("java.io.tmpdir"), "keynote_ql_${System.currentTimeMillis()}")
            outDir.mkdirs()
            val process = ProcessBuilder("qlmanage", "-p", file.absolutePath, "-o", outDir.absolutePath)
                .redirectErrorStream(true).start()
            val exited = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            if (!exited) { process.destroyForcibly(); outDir.deleteRecursively(); return@withContext null }
            val pdfOut = outDir.listFiles()?.firstOrNull { it.extension.equals("pdf", ignoreCase = true) }
            if (pdfOut != null && pdfOut.length() > 0) {
                val dest = File(System.getProperty("java.io.tmpdir"), "keynote_preview_${System.currentTimeMillis()}.pdf")
                pdfOut.copyTo(dest, overwrite = true)
                outDir.deleteRecursively()
                dest
            } else {
                outDir.deleteRecursively()
                null
            }
        } catch (e: Exception) {
            System.err.println("[Keynote] qlmanage failed: ${e.message}")
            null
        }
    }

    private suspend fun loadKeynoteViaUnzip(file: File, cacheDir: File) {
        try {
            val slideIwaOrder: List<Long> = if (!file.isDirectory) readSlideOrderFromZip(file) else emptyList()
            val keynoteDir: File
            var tempDir: File? = null

            if (file.isDirectory) {
                keynoteDir = file
            } else if (file.name.endsWith(".key")) {
                tempDir = File(System.getProperty("java.io.tmpdir"), "keynote_extract_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                try {
                    ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                        val tempCanonical = tempDir.canonicalPath
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val outFile = File(tempDir, entry.name)
                            if (!outFile.canonicalPath.startsWith(tempCanonical + File.separator) &&
                                outFile.canonicalPath != tempCanonical) {
                                zip.closeEntry(); entry = zip.nextEntry; continue
                            }
                            if (entry.isDirectory) outFile.mkdirs()
                            else {
                                outFile.parentFile?.mkdirs()
                                BufferedOutputStream(FileOutputStream(outFile)).use { out -> zip.copyTo(out) }
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
            } else return

            try {
                val dataDir = File(keynoteDir, "Data")
                if (!dataDir.exists()) return
                val allImageFiles = dataDir.listFiles()?.filter { f ->
                    f.isFile && f.extension.lowercase() in listOf("jpg", "jpeg", "png", "tiff", "tif")
                } ?: emptyList()
                val stFiles = allImageFiles.filter { it.name.lowercase().startsWith("st-") }
                val orderedSlides = sortByZipOrder(stFiles, slideIwaOrder)
                val keynoteNotes = extractKeynoteNotes(file)
                withContext(Dispatchers.Main) { _totalSlides.value = orderedSlides.size }

                orderedSlides.forEachIndexed { index, slideFile ->
                    try {
                        // st- files are already JPEGs — copy directly without decode/re-encode
                        val destFile = File(cacheDir, "slide_%04d.jpg".format(index))
                        slideFile.copyTo(destFile, overwrite = true)
                        val notes = keynoteNotes.getOrElse(index) { "" }
                        if (notes.isNotBlank()) File(cacheDir, "note_%04d.txt".format(index)).writeText(notes)
                        withContext(Dispatchers.Main) {
                            _slideFiles.add(destFile)
                            _slideNotes.add(notes)
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

    private fun readSlideOrderFromZip(file: File): List<Long> {
        val ordered = mutableListOf<Long>()
        try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { name ->
                        val base = name.substringAfterLast("/")
                        base.startsWith("Slide-") && base.endsWith(".iwa") && base != "Slide.iwa"
                    }
                    .forEach { name ->
                        val base = name.substringAfterLast("/")
                        val idStr = base.removePrefix("Slide-").removeSuffix(".iwa").split("-")[0]
                        idStr.toLongOrNull()?.let { ordered.add(it) }
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ordered
    }

    private fun sortByZipOrder(stFiles: List<File>, slideIwaOrder: List<Long>): List<File> {
        if (stFiles.isEmpty()) return stFiles
        val stFilesSortedByStId = stFiles.sortedBy { file ->
            file.nameWithoutExtension.split("-").lastOrNull()?.toLongOrNull() ?: Long.MAX_VALUE
        }
        if (slideIwaOrder.isNotEmpty()) {
            val slideIwaOrderSorted = slideIwaOrder.sorted()
            val rankToStFile = stFilesSortedByStId.mapIndexed { rank, stFile -> rank to stFile }.toMap()
            val result = slideIwaOrder.mapIndexed { _, slideId ->
                val rank = slideIwaOrderSorted.indexOf(slideId)
                rankToStFile[rank]
            }.filterNotNull().distinct()
            val missing = stFiles.filter { it !in result }
            return result + missing
        }
        return stFilesSortedByStId
    }

    private fun extractPoiSlideNotes(slide: Any): String {
        return try {
            val notesSlide = slide::class.java.getMethod("getNotes").invoke(slide) ?: return ""
            val shapes = notesSlide::class.java.getMethod("getShapes").invoke(notesSlide) as? List<*> ?: return ""
            val sb = StringBuilder()
            for (shape in shapes) {
                if (shape == null) continue
                try {
                    val text = shape::class.java.getMethod("getText").invoke(shape) as? String
                    if (!text.isNullOrBlank()) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(text.trim())
                    }
                } catch (_: NoSuchMethodException) {
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun isValidPresentationFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("ppt", "pptx", "key", "pdf")
    }
}

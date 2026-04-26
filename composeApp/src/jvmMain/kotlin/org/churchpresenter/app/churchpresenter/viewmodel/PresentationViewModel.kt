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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO

class PresentationViewModel(appSettings: AppSettings? = null) {
    private val _presentations = mutableStateListOf<File>()
    val presentations: List<File> = _presentations

    private val _selectedPresentation = mutableStateOf<File?>(null)
    val selectedPresentation: File?
        get() = _selectedPresentation.value

    private val _slides = mutableStateListOf<ImageBitmap>()
    val slides: SnapshotStateList<ImageBitmap> = _slides

    /** Raw BufferedImages kept in parallel with [slides] for server-side JPEG encoding. */
    private val _bufferedSlides = mutableListOf<BufferedImage>()
    val bufferedSlides: List<BufferedImage> get() = _bufferedSlides.toList()

    /** Presenter notes extracted from each slide (parallel to [slides]). Empty string if no notes. */
    private val _slideNotes = mutableStateListOf<String>()
    val slideNotes: List<String> get() = _slideNotes

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
        _bufferedSlides.clear()
        _slideNotes.clear()
        _selectedSlideIndex.value = 0
    }

    private fun isValidPresentationFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("ppt", "pptx", "key", "pdf")
    }

    private fun loadSlides(file: File) {
        activeLoadJob = scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    _slides.clear()
                    _bufferedSlides.clear()
                    _slideNotes.clear()
                }
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

    private suspend fun loadPdfSlides(file: File, notesPerSlide: List<String> = emptyList()) {
        try {
            val pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument")
            val pdfRendererClass = Class.forName("org.apache.pdfbox.rendering.PDFRenderer")
            val loadMethod = pdDocumentClass.getMethod("load", File::class.java)
            val document = loadMethod.invoke(null, file)
            val numberOfPages = pdDocumentClass.getMethod("getNumberOfPages").invoke(document) as Int
            val renderer = pdfRendererClass.getConstructor(pdDocumentClass).newInstance(document)
            val renderMethod = pdfRendererClass.getMethod("renderImageWithDPI", Int::class.java, Float::class.java)
            // 288 DPI → ~4× the standard 72 DPI PDF unit → 3840px wide for a typical 13.3" wide slide.
            // Use 300 DPI for a round number that is also standard print quality.
            val targetDpi = 300f
            for (page in 0 until numberOfPages) {
                val image = renderMethod.invoke(renderer, page, targetDpi) as BufferedImage
                println("[PDF] page $page rendered at ${image.width}×${image.height}px")
                val imageBitmap = bufferedImageToImageBitmap(image)
                val notes = notesPerSlide.getOrElse(page) { "" }
                withContext(Dispatchers.Main) {
                    _bufferedSlides.add(image)
                    _slides.add(imageBitmap)
                    _slideNotes.add(notes)
                }
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
                    java.io.FileInputStream(file).use { fileInputStream ->
                    val ppt = xmlSlideShowClass.getConstructor(java.io.InputStream::class.java).newInstance(fileInputStream)
                    val slides = xmlSlideShowClass.getMethod("getSlides").invoke(ppt) as List<*>
                    val pageSize = xmlSlideShowClass.getMethod("getPageSize").invoke(ppt) as java.awt.Dimension
                    // Scale to a fixed 3840-wide target so the bitmap is always 4K-ready.
                    // This covers Retina/HiDPI screens (logical 1920×1080 = 3840×2160 physical).
                    val renderScale = 3840.0 / pageSize.width
                    println("[Slides] PPTX pageSize=${pageSize.width}×${pageSize.height}  renderScale=$renderScale  → ${(pageSize.width*renderScale).toInt()}×${(pageSize.height*renderScale).toInt()}")
                    slides.forEach { slide ->
                        val s = slide ?: return@forEach
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
                            val imageBitmap = bufferedImageToImageBitmap(img)
                            // Save first PPTX slide to disk so we can verify render quality
                            if (_slides.isEmpty()) {
                                try {
                                    val debugFile = File(System.getProperty("java.io.tmpdir"), "churchpresenter_slide_debug.png")
                                    ImageIO.write(img, "PNG", debugFile)
                                    println("[Slides] Debug slide saved: ${debugFile.absolutePath}  (${img.width}×${img.height}px)")
                                } catch (ex: Exception) {
                                    println("[Slides] Debug save failed: ${ex.message}")
                                }
                            }
                            // Extract presenter notes for this slide
                            val notes = extractXslfSlideNotes(s)
                            withContext(Dispatchers.Main) {
                                _bufferedSlides.add(img)
                                _slides.add(imageBitmap)
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
                    // Scale to a fixed 3840-wide target so the bitmap is always 4K-ready.
                    val renderScale = 3840.0 / pageSize.width
                    slides.forEach { slide ->
                        val s = slide ?: return@forEach
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
                            val imageBitmap = bufferedImageToImageBitmap(img)
                            // Extract presenter notes for this slide
                            val notes = extractHslfSlideNotes(s)
                            withContext(Dispatchers.Main) {
                                _bufferedSlides.add(img)
                                _slides.add(imageBitmap)
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


    private suspend fun loadKeynoteSlides(file: File) {

        // Step 1: Use the Keynote app via osascript to export a full-quality PDF.
        // This is the highest-quality path on macOS and preserves all slide fidelity.
        val nativePdf = tryExportKeynoteViaApp(file)
        if (nativePdf != null) {
            try {
                val n = extractKeynoteNotes(file)
                loadPdfSlides(nativePdf, notesPerSlide = n)
            } finally {
                nativePdf.delete()
            }
            if (_slides.isNotEmpty()) return
        }

        // Step 2: Extract QuickLook/Preview.pdf from the .key zip (medium quality, no external tools).
        val quickLookPdf = extractKeynoteQuickLookPdf(file)
        if (quickLookPdf != null) {
            try {
                val n = extractKeynoteNotes(file)
                loadPdfSlides(quickLookPdf, notesPerSlide = n)
            } finally {
                quickLookPdf.delete()
            }
            if (_slides.isNotEmpty()) return
        }

        // Step 3: Try qlmanage PDF export (macOS only, may not be available)
        val pdfFile = tryExportKeynoteToPdf(file)
        if (pdfFile != null) {
            try {
                val n = extractKeynoteNotes(file)
                loadPdfSlides(pdfFile, notesPerSlide = n)
            } finally {
                pdfFile.delete()
            }
            if (_slides.isNotEmpty()) return
        }

        // Step 4: Fallback: extract embedded thumbnails (low quality)
        println("[Keynote] Falling back to embedded thumbnails for ${file.name}")
        loadKeynoteViaUnzip(file)
    }

    /**
     * Exports a Keynote file to a high-quality PDF using the macOS Keynote app via osascript.
     * This produces the best possible slide quality — pixel-perfect, full resolution.
     * Returns the PDF file on success, null if Keynote is not installed or export fails.
     */
    private suspend fun tryExportKeynoteViaApp(file: File): File? = withContext(Dispatchers.IO) {
        // Only available on macOS
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
                println("[Keynote] osascript timed out")
                dest.delete()
                return@withContext null
            }
            if (dest.exists() && dest.length() > 0) {
                println("[Keynote] Keynote app exported PDF: ${dest.absolutePath} (${dest.length()} bytes)")
                return@withContext dest
            }
            println("[Keynote] osascript finished but no PDF produced. Output: $output")
            dest.delete()
            null
        } catch (e: Exception) {
            println("[Keynote] tryExportKeynoteViaApp failed: ${e.message}")
            null
        }
    }
    /**
     * Extracts the QuickLook/Preview.pdf embedded inside a .key (zip) file.
     * Keynote always writes a full-quality PDF preview of all slides here.
     * Returns the temporary PDF file on success, null if not found or not a zip.
     */
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
                        if (dest.length() > 0) {
                            println("[Keynote] Extracted QuickLook/Preview.pdf: ${dest.absolutePath} (${dest.length()} bytes)")
                            return@withContext dest
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            dest.delete()
            null
        } catch (e: Exception) {
            println("[Keynote] Failed to extract QuickLook/Preview.pdf: ${e.message}")
            null
        }
    }

    /**
     * Extracts speaker notes from a Keynote .key file.
     * Tries multiple strategies:
     * 1. Parse index.apxl XML (older Keynote format)
     * 2. Scan Slide-*.iwa binary files for plain-text note strings (newer format)
     * Returns a list of note strings, one per slide (empty string if no notes).
     */
    private suspend fun extractKeynoteNotes(file: File): List<String> = withContext(Dispatchers.IO) {
        if (!file.isFile || !file.name.endsWith(".key", ignoreCase = true)) return@withContext emptyList()
        try {
            // Map: slideIndex (0-based) -> notes text
            val apxlNotes = mutableListOf<String>()
            val iwaNotesMap = mutableMapOf<Long, String>() // sortKey -> notes text

            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val base = name.substringAfterLast("/")
                    when {
                        // Strategy 1: index.apxl (older Keynote XML format)
                        base.equals("index.apxl", ignoreCase = true) && apxlNotes.isEmpty() -> {
                            val bytes = zip.readBytes()
                            val xml = String(bytes, Charsets.UTF_8)
                            apxlNotes.addAll(parseApxlNotes(xml))
                        }
                        // Strategy 2: Slide-*.iwa binary protobuf — scan for note text.
                        // Include Slide.iwa (slide 1) AND Slide-XXXX-2.iwa (slides 2+).
                        base.startsWith("Slide") && base.endsWith(".iwa") -> {
                            val bytes = zip.readBytes()
                            val noteText = scanIwaForNoteText(bytes)
                            // Use -1 as the sort key for "Slide.iwa" so it always comes first
                            val sortKey = if (base == "Slide.iwa") -1L
                            else base.removePrefix("Slide-").removeSuffix(".iwa").split("-")[0].toLongOrNull() ?: Long.MAX_VALUE
                            iwaNotesMap[sortKey] = noteText
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            if (apxlNotes.isNotEmpty()) {
                println("[Keynote] Extracted ${apxlNotes.size} notes from index.apxl")
                return@withContext apxlNotes
            }

            if (iwaNotesMap.isNotEmpty()) {
                // Sort by the numeric key (Slide.iwa=-1 first, then ascending slide ID)
                val sorted = iwaNotesMap.entries
                    .sortedBy { it.key }
                    .map { it.value }
                println("[Keynote] Extracted ${sorted.size} notes from .iwa files: ${sorted.map { it.take(30) }}")
                return@withContext sorted
            }
        } catch (e: Exception) {
            println("[Keynote] extractKeynoteNotes failed: ${e.message}")
        }
        emptyList()
    }

    /**
     * Parses presenter notes from an index.apxl XML (older Keynote format).
     * Notes are stored under <sl:notes> -> <sf:text-storage> -> <sf:p> elements.
     */
    private fun parseApxlNotes(xml: String): List<String> {
        val result = mutableListOf<String>()
        try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
            // Find all <sl:slide> elements in order
            val slides = doc.getElementsByTagNameNS("*", "slide")
            for (i in 0 until slides.length) {
                val slide = slides.item(i)
                val notesSb = StringBuilder()
                // Find <sl:notes> within this slide
                val children = slide.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.localName == "notes") {
                        extractTextFromNode(child, notesSb)
                    }
                }
                result.add(notesSb.toString().trim())
            }
        } catch (e: Exception) {
            println("[Keynote] parseApxlNotes failed: ${e.message}")
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
        for (i in 0 until children.length) {
            extractTextFromNode(children.item(i), sb)
        }
    }

    /**
     * Scans a Keynote .iwa protobuf binary for speaker note text.
     *
     * Keynote stores the notes plain-text string at protobuf field 902 (wire type 2 = length-delimited).
     * Field 902, wire type 2 encodes as a 2-byte varint tag: 0xB2 0x38.
     * This is reliable across Keynote versions tested.
     */
    private fun scanIwaForNoteText(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size - 3) {
            // Look for the 2-byte field tag: 0xB2 0x38 (field 902, wire type 2)
            if ((bytes[i].toInt() and 0xFF) == 0xB2 && (bytes[i + 1].toInt() and 0xFF) == 0x38) {
                // Read varint length starting at i+2
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

    /**
     * Uses macOS qlmanage to export a Keynote file to a full-quality PDF.
     * Returns the PDF file on success, null on failure.
     */
    private suspend fun tryExportKeynoteToPdf(file: File): File? = withContext(Dispatchers.IO) {
        try {
            val outDir = File(System.getProperty("java.io.tmpdir"), "keynote_ql_${System.currentTimeMillis()}")
            outDir.mkdirs()
            // qlmanage -p generates a Quick Look preview; for Keynote this is a multi-page PDF
            val process = ProcessBuilder("qlmanage", "-p", file.absolutePath, "-o", outDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            val exited = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            if (!exited) { process.destroyForcibly(); outDir.deleteRecursively(); return@withContext null }
            // qlmanage outputs a file named "<originalname>.pdf" inside outDir
            val pdfOut = outDir.listFiles()?.firstOrNull { it.extension.equals("pdf", ignoreCase = true) }
            if (pdfOut != null && pdfOut.length() > 0) {
                // Move to a stable temp path so outDir can be cleaned up
                val dest = File(System.getProperty("java.io.tmpdir"), "keynote_preview_${System.currentTimeMillis()}.pdf")
                pdfOut.copyTo(dest, overwrite = true)
                outDir.deleteRecursively()
                println("[Keynote] qlmanage PDF: ${dest.absolutePath}  (${dest.length()} bytes)")
                dest
            } else {
                outDir.deleteRecursively()
                null
            }
        } catch (e: Exception) {
            println("[Keynote] qlmanage failed: ${e.message}")
            null
        }
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
                    ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                        val tempCanonical = tempDir.canonicalPath
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val outFile = File(tempDir, entry.name)
                            if (!outFile.canonicalPath.startsWith(tempCanonical + File.separator) &&
                                outFile.canonicalPath != tempCanonical) {
                                zip.closeEntry()
                                entry = zip.nextEntry
                                continue
                            }
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                BufferedOutputStream(FileOutputStream(outFile)).use { out ->
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

                // Extract notes for fallback path
                val keynoteNotes = extractKeynoteNotes(file)

                orderedSlides.forEachIndexed { index, slideFile ->
                    try {
                        val bufferedImage = ImageIO.read(slideFile)
                        if (bufferedImage != null) {
                            val imageBitmap = bufferedImageToImageBitmap(bufferedImage)
                            val notes = keynoteNotes.getOrElse(index) { "" }
                            withContext(Dispatchers.Main) {
                                _bufferedSlides.add(bufferedImage)
                                _slides.add(imageBitmap)
                                _slideNotes.add(notes)
                            }
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
            ZipFile(file).use { zip ->
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

    /**
     * Extracts presenter notes text from an XSLFSlide (pptx) via reflection.
     * Uses duck-typing: tries getText() on every shape, skips those that don't have it.
     * Shape[0] in a notes slide is the slide thumbnail (no getText), shape[1+] are text bodies.
     */
    private fun extractXslfSlideNotes(slide: Any): String {
        return try {
            val notesSlide = slide::class.java.getMethod("getNotes").invoke(slide)
            if (notesSlide == null) {
                println("[Notes] getNotes() returned null for slide ${slide::class.java.simpleName}")
                return ""
            }
            val shapes = notesSlide::class.java.getMethod("getShapes").invoke(notesSlide) as? List<*>
            if (shapes == null) {
                println("[Notes] getShapes() returned null")
                return ""
            }
            println("[Notes] XSLF notes slide has ${shapes.size} shapes")
            val sb = StringBuilder()
            for ((i, shape) in shapes.withIndex()) {
                if (shape == null) continue
                try {
                    // Try calling getText() via reflection (duck-typing — works for any text shape subclass)
                    val getTextMethod = shape::class.java.getMethod("getText")
                    val text = getTextMethod.invoke(shape) as? String
                    println("[Notes] shape[$i] ${shape::class.java.simpleName} getText()='$text'")
                    if (!text.isNullOrBlank()) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(text.trim())
                    }
                } catch (_: NoSuchMethodException) {
                    println("[Notes] shape[$i] ${shape::class.java.simpleName} has no getText()")
                } catch (e: Exception) {
                    println("[Notes] shape[$i] getText() threw: ${e.message}")
                }
            }
            println("[Notes] Extracted notes: '$sb'")
            sb.toString()
        } catch (e: Exception) {
            println("[Notes] extractXslfSlideNotes failed: ${e.message}")
            ""
        }
    }

    /**
     * Extracts presenter notes text from an HSLFSlide (ppt) via reflection.
     * Uses duck-typing: tries getText() on every shape.
     */
    private fun extractHslfSlideNotes(slide: Any): String {
        return try {
            val notesSlide = slide::class.java.getMethod("getNotes").invoke(slide)
            if (notesSlide == null) {
                println("[Notes] HSLF getNotes() returned null")
                return ""
            }
            val shapes = notesSlide::class.java.getMethod("getShapes").invoke(notesSlide) as? List<*>
            if (shapes == null) {
                println("[Notes] HSLF getShapes() returned null")
                return ""
            }
            println("[Notes] HSLF notes slide has ${shapes.size} shapes")
            val sb = StringBuilder()
            for ((i, shape) in shapes.withIndex()) {
                if (shape == null) continue
                try {
                    val getTextMethod = shape::class.java.getMethod("getText")
                    val text = getTextMethod.invoke(shape) as? String
                    println("[Notes] shape[$i] ${shape::class.java.simpleName} getText()='$text'")
                    if (!text.isNullOrBlank()) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(text.trim())
                    }
                } catch (_: NoSuchMethodException) {
                    println("[Notes] shape[$i] ${shape::class.java.simpleName} has no getText()")
                } catch (e: Exception) {
                    println("[Notes] shape[$i] getText() threw: ${e.message}")
                }
            }
            println("[Notes] Extracted HSLF notes: '$sb'")
            sb.toString()
        } catch (e: Exception) {
            println("[Notes] extractHslfSlideNotes failed: ${e.message}")
            ""
        }
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

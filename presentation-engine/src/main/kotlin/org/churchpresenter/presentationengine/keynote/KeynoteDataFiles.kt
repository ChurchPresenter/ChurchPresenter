package org.churchpresenter.presentationengine.keynote

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * A deck's `Data/` files -- images decoded once and cached, movies handed out as real files --
 * from either bundle form. Owns the open zip and the temp files it extracts, released in [close].
 */
internal class KeynoteDataFiles(private val bundle: File) : AutoCloseable {

    private var zipFile: ZipFile? = null
    private val imageCache = HashMap<String, BufferedImage?>()
    private val extractedTempFiles = HashMap<String, File?>()

    /**
     * Resolves a Data/ file to a real filesystem [File] VLC can open directly. Directory-bundle
     * `.key` files already have one; zip-bundle files are extracted once to a temp file, cached
     * for the life of this object and deleted in [close] (not left to `deleteOnExit()`, which
     * would otherwise leak large `.mov` temp files for the whole app run).
     */
    fun extract(fileName: String): File? = extractedTempFiles.getOrPut(fileName) {
        if (bundle.isDirectory) {
            File(File(bundle, "Data"), fileName).takeIf { it.isFile }
        } else {
            try {
                extractFromZip(fileName)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun extractFromZip(fileName: String): File? {
        val zip = zip()
        val entry = zip.getEntry("Data/$fileName") ?: zip.getEntry(fileName) ?: return null
        val temp = File.createTempFile("kn_media_", "_${fileName.substringAfterLast('/')}")
        zip.getInputStream(entry).use { input -> temp.outputStream().use { input.copyTo(it) } }
        return temp
    }

    /** [fileName] decoded as an image, or null when it is missing or not one. */
    fun image(fileName: String): BufferedImage? = imageCache.getOrPut(fileName) {
        try {
            readBytes(fileName)?.let { ImageIO.read(ByteArrayInputStream(it)) }
        } catch (_: Exception) {
            null
        }
    }

    private fun readBytes(fileName: String): ByteArray? {
        if (bundle.isDirectory) return File(File(bundle, "Data"), fileName).takeIf { it.isFile }?.readBytes()
        val zip = zip()
        return (zip.getEntry("Data/$fileName") ?: zip.getEntry(fileName))?.let { zip.getInputStream(it).readBytes() }
    }

    private fun zip(): ZipFile = zipFile ?: ZipFile(bundle).also { zipFile = it }

    override fun close() {
        try {
            zipFile?.close()
        } catch (_: Exception) {
        }
        zipFile = null
        imageCache.clear()
        extractedTempFiles.values.forEach { it?.delete() }
        extractedTempFiles.clear()
    }
}

package org.churchpresenter.lottiegen.persistence

import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

object LogoStorage {

    private fun logosDir(): File {
        val dir = File(System.getProperty("user.home"), ".churchpresenter/churchpresenter-lottiegen/logos")
        dir.mkdirs()
        return dir
    }

    fun listLogos(): List<String> {
        val dir = logosDir()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "svg", "webp") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    fun getLogoFile(name: String): File = File(logosDir(), name)

    /**
     * Read a logo file and return its data as a base64 data URL, plus dimensions.
     */
    fun loadLogoData(file: File): LogoData? {
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "svg" -> "image/svg+xml"
                "webp" -> "image/webp"
                else -> "image/png"
            }
            val dataUrl = "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"

            // Read dimensions
            val img = ImageIO.read(file)
            val w = img?.width ?: 100
            val h = img?.height ?: 100

            LogoData(dataUrl, w, h)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copy a logo file into the logos directory.
     */
    fun importLogo(sourceFile: File): File? {
        return try {
            val dest = File(logosDir(), sourceFile.name)
            sourceFile.copyTo(dest, overwrite = true)
            dest
        } catch (e: Exception) {
            System.err.println("Failed to import logo: ${e.message}")
            null
        }
    }
}

data class LogoData(val dataUrl: String, val width: Int, val height: Int)

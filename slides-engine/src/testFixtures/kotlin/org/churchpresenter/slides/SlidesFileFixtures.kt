package org.churchpresenter.slides

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

// The file helpers both the presenters' suites here and :slides' own suites build pictures with.

/** A fresh temp directory for one test's files. */
fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

/** A small image filled with one [color], written as [name] in [dir]. */
fun solidImage(dir: File, name: String, color: Color, width: Int = 16, height: Int = 9): File {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    image.createGraphics().apply {
        this.color = color
        fillRect(0, 0, width, height)
        dispose()
    }
    return File(dir, name).also { ImageIO.write(image, name.substringAfterLast('.'), it) }
}

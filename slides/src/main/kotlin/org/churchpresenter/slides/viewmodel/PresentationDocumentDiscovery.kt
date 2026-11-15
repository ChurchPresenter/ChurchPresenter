package org.churchpresenter.slides.viewmodel

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.churchpresenter.presentationengine.PresentationLoader
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal object PresentationDocumentDiscovery {
    private val ignoredDirectories = setOf("node_modules", "dist", "build", ".git", "target", "\$recycle.bin")

    suspend fun discover(directories: List<File>): List<File> {
        val context = currentCoroutineContext()
        val found = linkedMapOf<Path, File>()
        directories.distinctBy { it.absoluteFile.normalize() }.filter { it.isDirectory }.forEach { root ->
            try {
                Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        context.ensureActive()
                        return if (dir.fileName?.toString()?.lowercase() in ignoredDirectories) {
                            FileVisitResult.SKIP_SUBTREE
                        } else {
                            FileVisitResult.CONTINUE
                        }
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        context.ensureActive()
                        val document = file.toFile()
                        if (attrs.isRegularFile && !document.name.startsWith("~\$") &&
                            document.extension.lowercase() in PresentationLoader.SUPPORTED_EXTENSIONS
                        ) {
                            found[file.toAbsolutePath().normalize()] = document
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                        FileVisitResult.CONTINUE
                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult =
                        FileVisitResult.CONTINUE
                })
            } catch (_: IOException) {
                // A removable or shared folder can disappear during discovery. Other roots still load.
            }
        }
        return found.values.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.absolutePath })
    }
}

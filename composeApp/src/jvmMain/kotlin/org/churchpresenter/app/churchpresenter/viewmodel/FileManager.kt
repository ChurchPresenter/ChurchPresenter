package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.bible.MAX_BIBLE_SCAN_DEPTH
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.BibleSettings
import java.io.File
import java.io.IOException
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Handles all file system operations for the application.
 * Separated from UI to follow clean architecture principles.
 */
class FileManager {

    /**
     * Get list of song files (*.sps) from a directory
     */
    fun getSongFilesInDirectory(directory: String): List<String> {
        if (directory.isEmpty()) return emptyList()

        val dir = File(directory)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles { file ->
            file.extension.lowercase() == Constants.EXTENSION_SPS
        }?.map { it.name }?.sorted() ?: emptyList()
    }

    /**
     * Get list of songbook folders (subdirectories containing .song files) from a directory
     */
    fun getSongFoldersInDirectory(directory: String): List<Pair<String, Int>> {
        if (directory.isEmpty()) return emptyList()

        val rootDir = File(directory)
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()

        val results = mutableListOf<Pair<String, Int>>()

        // Count .song files in the root directory itself
        val rootSongCount = rootDir.listFiles { file ->
            file.extension.lowercase() == Constants.EXTENSION_SONG
        }?.size ?: 0
        if (rootSongCount > 0) {
            results.add(Pair("/", rootSongCount))
        }

        collectSongFolders(rootDir, rootDir, results)
        return results.sortedBy { it.first }
    }

    private fun collectSongFolders(currentDir: File, rootDir: File, results: MutableList<Pair<String, Int>>) {
        val subdirs = currentDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        for (subdir in subdirs) {
            val songCount = subdir.listFiles { file ->
                file.extension.lowercase() == Constants.EXTENSION_SONG
            }?.size ?: 0
            if (songCount > 0) {
                val relativePath = subdir.toRelativeString(rootDir).replace('\\', '/')
                results.add(Pair(relativePath, songCount))
            }
            collectSongFolders(subdir, rootDir, results)
        }
    }

    /**
     * Get Bible files (*.spb) from a directory **and its subfolders**, as paths relative to
     * [directory] with `/` separators (e.g. `ENG/King James/kjv.spb`).
     *
     * Bible collections ship as nested folders — one directory per language, often one per
     * translation under that — so a flat listing finds nothing but whatever happens to sit at the
     * top level. Relative rather than bare names because a collection can easily carry two files of
     * the same name in different folders, and the returned string is the identity everything else
     * keys on ([BibleSettings.primaryBible],
     * the display-name map, the engine's file list).
     *
     * Callers resolve with `File(storageDirectory, value)`, which handles the embedded separators on
     * every platform — so previously stored bare filenames keep resolving unchanged.
     */
    fun getBibleFilesInDirectory(directory: String): List<String> {
        if (directory.isEmpty()) return emptyList()

        val dir = File(directory)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        // Bounded depth so a symlink cycle or a stray deep tree can't hang the settings dialog.
        return dir.walkTopDown().maxDepth(MAX_BIBLE_SCAN_DEPTH)
            .filter { it.isFile && it.extension.lowercase() == Constants.EXTENSION_SPB }
            .map { it.toRelativeString(dir).replace('\\', '/') }
            .sorted()
            .toList()
    }

    /**
     * Show directory chooser dialog and return selected directory path
     */
    suspend fun chooseDirectory(currentDirectory: String = ""): String? {
        return FileChooser.platformInstance.chooseSingle(
            path = Path(currentDirectory),
            filters = emptyList(),
            title = "",
            selectDirectory = true
        )?.absolutePathString()
    }

    /**
     * Show file chooser dialog for song files and return selected files
     */
    suspend fun chooseSongFiles(): List<Path>? {
        return FileChooser.platformInstance.chooseMultiple(
            path = null,
            filters = listOf(
                FileNameExtensionFilter(
                    "Song Files (*.${Constants.EXTENSION_SPS})", Constants.EXTENSION_SPS
                )
            ),
            title = "",
            selectDirectory = false
        )
    }

    /**
     * Show file chooser dialog for Bible files and return selected file
     */
    suspend fun chooseBibleFile(): Path? {
        return FileChooser.platformInstance.chooseSingle(
            path = null,
            filters = listOf(
                FileNameExtensionFilter(
                    "Bible Files (*.${Constants.EXTENSION_SPB})", Constants.EXTENSION_SPB
                )
            ),
            title = "",
            selectDirectory = false
        )
    }

    /**
     * Import (copy) files to target directory
     * Returns list of error messages if any
     */
    fun importFiles(sourceFiles: List<Path>, targetDirectory: String): List<String> {
        val errors = mutableListOf<String>()
        val targetDir = Path(targetDirectory)

        if (!targetDir.exists() || !targetDir.isDirectory()) {
            errors.add("Target directory does not exist: $targetDirectory")
            return errors
        }

        sourceFiles.forEach { sourceFile ->
            val targetFile = targetDir.resolve(sourceFile.name)
            try {
                sourceFile.copyTo(targetFile, overwrite = true)
            } catch (e: IOException) {
                errors.add("Error copying ${sourceFile.name}: ${e.message}")
            }
        }

        return errors
    }
}

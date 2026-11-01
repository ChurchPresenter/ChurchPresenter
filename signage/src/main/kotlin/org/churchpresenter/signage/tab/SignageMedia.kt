package org.churchpresenter.signage.tab

import org.churchpresenter.diagnostics.Log
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/**
 * The pictures and videos copied into the signage folder, so every computer sharing it plays the
 * same ones: a folder's under `Folders`, a single picture or video under `Files`. A playlist keeps
 * its copy's path relative to the signage folder, which each computer resolves against its own
 * mount of it. Blocking: run it on `Dispatchers.IO`.
 */
@OptIn(ExperimentalPathApi::class)
object SignageMedia {
    const val FOLDERS = "Folders"
    const val FILES = "Files"

    /**
     * Copies the pictures and videos directly in [source] into [signage], under a name no folder
     * there has yet; the copy's path relative to [signage], or null when it could not be made.
     */
    fun copyFolder(source: Path, signage: Path): String? {
        val target = freeName(signage.resolve(FOLDERS), source.name.ifEmpty { DEFAULT_FOLDER_NAME })
        return makeInPlace(target, signage) { part ->
            Files.createDirectories(part)
            SignageFolder.filesIn(source).forEach { Files.copy(it.path, part.resolve(it.path.name)) }
        }
    }

    /** Copies [source] into [signage] under a name no file there has yet; as [copyFolder] answers. */
    fun copyFile(source: Path, signage: Path): String? {
        val target = freeName(signage.resolve(FILES), source.name)
        return makeInPlace(target, signage) { part -> Files.copy(source, part) }
    }

    /** Where [path], as a playlist keeps it, is on this computer: under [signage] unless it is absolute. */
    fun resolve(signage: Path, path: String): Path? = try {
        Path(path).let { if (it.isAbsolute) it else signage.resolve(it) }
    } catch (_: InvalidPathException) {
        null
    }

    /**
     * [source] for a playlist: its path inside [signage] when it is already there, so it is played
     * where it is and every computer sharing the folder finds it; otherwise a copy made by [copy].
     * As [copyFolder] answers.
     */
    fun place(source: Path, signage: Path, copy: (Path, Path) -> String?): String? =
        within(signage, source) ?: copy(source, signage)

    /** [source]'s path relative to [signage] when it is inside it (or is it), by its real path; else null. */
    internal fun within(signage: Path, source: Path): String? {
        val home = realOf(signage)
        val real = realOf(source)
        if (!real.startsWith(home)) return null
        return home.relativize(real).invariantSeparatorsPathString.ifEmpty { "." }
    }

    /**
     * Whether [path] is a copy the tab made (under the signage folder's `Folders` or `Files`), rather
     * than a file or folder played where it is.
     */
    fun isCopy(path: String): Boolean = try {
        val parts = Path(path)
        !parts.isAbsolute && parts.nameCount == 2 && parts.getName(0).toString() in setOf(FOLDERS, FILES)
    } catch (_: InvalidPathException) {
        false
    }

    /**
     * Moves the copy at [path] under [signage] to the Trash, or deletes it where there is none
     * ([trash] null); false when something was left. A path that is no copy is never touched.
     */
    fun discard(
        signage: Path,
        path: String,
        trash: ((File) -> Boolean)? = SignageTrash.trashFor(SignageTrash.canTrash()),
    ): Boolean {
        if (!isCopy(path)) return false
        val target = resolve(signage, path)?.normalize() ?: return false
        // Only a copy inside the signage folder's own Folders or Files, whatever the path says.
        val home = target.parent
        if (home != signage.resolve(FOLDERS).normalize() && home != signage.resolve(FILES).normalize()) return false
        if (!target.exists()) return true
        return try {
            if (trash != null) {
                trash(target.toFile())
            } else {
                target.deleteRecursively()
                true
            }
        } catch (e: IOException) {
            Log.warn(TAG, "Could not remove $target: ${e.message}")
            false
        }
    }

    /** [name] in [dir], or `name 2`, `name 3`… when it is taken; a file keeps its extension. */
    internal fun freeName(dir: Path, name: String): Path {
        val plain = Path(name)
        val stem = if (plain.extension.isEmpty()) name else plain.nameWithoutExtension
        val dot = if (plain.extension.isEmpty()) "" else ".${plain.extension}"
        return generateSequence(1) { it + 1 }
            .map { n -> dir.resolve(if (n == 1) name else "$stem $n$dot") }
            .first { !it.exists() && !partOf(it).exists() }
    }

    /**
     * Makes [target] through [write] under a hidden name, then moves it into place, so another
     * computer reading the share never sees half a copy.
     */
    private fun makeInPlace(target: Path, signage: Path, write: (Path) -> Unit): String? {
        val part = partOf(target)
        return try {
            Files.createDirectories(target.parent)
            write(part)
            try {
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(part, target)
            }
            signage.relativize(target).invariantSeparatorsPathString
        } catch (e: IOException) {
            Log.warn(TAG, "Could not copy into $target: ${e.message}")
            if (part.isDirectory()) part.deleteRecursively() else Files.deleteIfExists(part)
            null
        }
    }

    private const val TAG = "Signage"
    private const val DEFAULT_FOLDER_NAME = "Folder"
}

/** This computer's Trash, where a removed copy goes when there is one. */
object SignageTrash {
    /** Whether this computer has a Trash to move a removed copy to; where not, it is deleted for good. */
    fun canTrash(): Boolean = trashAvailable(GraphicsEnvironment.isHeadless(), Desktop::isDesktopSupported) {
        Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
    }

    /** A Trash needs a display, a desktop AWT can reach, and one that offers moving to it. */
    internal fun trashAvailable(headless: Boolean, desktop: () -> Boolean, trash: () -> Boolean): Boolean =
        !headless && desktop() && trash()

    /** The Trash's move where [available]; null where there is none, so copies are deleted instead. */
    internal fun trashFor(available: Boolean): ((File) -> Boolean)? =
        if (available) Desktop.getDesktop()::moveToTrash else null
}

/**
 * [path] with links and `..` resolved: its nearest existing folder's real path, with the rest as it
 * is written.
 */
private fun realOf(path: Path): Path {
    val absolute = path.toAbsolutePath().normalize()
    val existing = generateSequence(absolute) { it.parent }.firstOrNull { it.exists() } ?: return absolute
    val real = try {
        existing.toRealPath()
    } catch (_: IOException) {
        return absolute
    }
    return real.resolve(existing.relativize(absolute))
}

/** Where a copy to [target] is made before it is moved into place. */
private fun partOf(target: Path): Path = target.resolveSibling(".${target.name}.copying")

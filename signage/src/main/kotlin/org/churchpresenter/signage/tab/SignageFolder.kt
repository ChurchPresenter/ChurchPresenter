package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.MediaKind
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** A picture or video in the signage folder, as it stood when the folder was read. */
data class FolderFile(val path: Path, val kind: MediaKind, val size: Long, val modifiedMs: Long)

/**
 * A playlist that follows a folder: everything in it, in file-name order. The signage folder's own
 * files are one; each folder directly inside it is another.
 */
data class FolderPlaylist(override val id: String, override val name: String, val files: List<FolderFile>) :
    PlaylistSource

/**
 * A playlist with each of its files' hashes, null where a file could not be read: a folder's files,
 * a file playlist's one file (none when it is missing), and nothing for a link.
 */
data class HashedPlaylist(val playlist: PlaylistSource, val hashes: Map<FolderFile, String?> = emptyMap())

/** [this] as the tab lists it. */
internal fun FolderPlaylist.summary() = PlaylistSummary(
    id = id,
    name = name,
    pictures = files.count { it.kind == MediaKind.IMAGE },
    videos = files.count { it.kind == MediaKind.VIDEO },
)

/** [this] playlist's file as it stands now; null when it is gone or cannot be read. Blocking. */
internal fun FilePlaylist.currentFile(): FolderFile? = try {
    val file = Path(path)
    if (!file.isRegularFile()) null else {
        FolderFile(file, kind, Files.size(file), Files.getLastModifiedTime(file).toMillis())
    }
} catch (_: IOException) {
    null
} catch (_: InvalidPathException) {
    null
}

/** A file chooser's filter for every picture and video a playlist can hold. */
// FileNameExtensionFilter takes its extensions only as varargs.
@Suppress("SpreadOperator")
internal fun picturesAndVideosFilter(description: String) =
    FileNameExtensionFilter(description, *(SignageFolder.IMAGES + SignageFolder.VIDEOS).toTypedArray())

/** [this] added playlist as the tab lists it; a file playlist whose file is gone is [PlaylistSummary.missing]. */
internal fun HashedPlaylist.addedSummary(): PlaylistSummary = when (val source = playlist) {
    is FilePlaylist -> PlaylistSummary(
        id = source.id,
        name = source.name,
        kind = if (source.kind == MediaKind.IMAGE) SourceKind.PICTURE else SourceKind.VIDEO,
        detail = Path(source.path).name,
        missing = hashes.values.none { it != null },
    )
    is LinkPlaylist -> PlaylistSummary(
        id = source.id,
        name = source.name,
        kind = if (source.kind == LinkKind.WEB) SourceKind.WEB else SourceKind.STREAM,
        detail = source.url,
    )
    is RotationPlaylist -> PlaylistSummary(
        id = source.id,
        name = source.name,
        kind = SourceKind.ROTATION,
        steps = source.steps.size,
        seconds = source.steps.sumOf { it.seconds ?: 0 },
    )
    is FolderPlaylist -> source.summary()
}

/** Reads the signage folder. Blocking: run it on `Dispatchers.IO`. */
object SignageFolder {
    internal val IMAGES = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    internal val VIDEOS = setOf("mp4", "m4v", "mov", "webm", "mkv")

    /** The id of the playlist made of the signage folder's own files. */
    const val ROOT_ID = "folder:"

    fun idOf(subfolder: String): String = "folder:$subfolder"

    /** The playlists in [root], the root's own first; folders with nothing playable are left out. */
    fun scan(root: Path, rootName: String): List<FolderPlaylist> {
        if (!root.isDirectory()) return emptyList()
        val own = FolderPlaylist(ROOT_ID, rootName, filesIn(root))
        val subfolders = list(root)
            .filter { it.isDirectory() && !it.isHidden() }
            .sortedBy { it.name.lowercase() }
            .map { FolderPlaylist(idOf(it.name), it.name, filesIn(it)) }
        return (listOf(own) + subfolders).filter { it.files.isNotEmpty() }
    }

    /** Whether this computer has a Trash to move a deleted playlist to; where not, it is deleted for good. */
    fun canTrash(): Boolean = trashAvailable(GraphicsEnvironment.isHeadless(), Desktop::isDesktopSupported) {
        Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
    }

    /** A Trash needs a display, a desktop AWT can reach, and one that offers moving to it. */
    internal fun trashAvailable(headless: Boolean, desktop: () -> Boolean, trash: () -> Boolean): Boolean =
        !headless && desktop() && trash()

    /**
     * Deletes playlist [id] from [root]: a folder inside it goes whole, the root's own playlist only
     * its pictures and videos. [trash] moves a file or folder to the Trash; where it cannot
     * ([canTrash] false), only the pictures and videos are deleted, for good. False when something
     * could not be removed. Blocking: run it on `Dispatchers.IO`.
     */
    fun delete(root: Path, id: String, trash: ((File) -> Boolean)? = defaultTrash()): Boolean {
        val target = if (id == ROOT_ID) root else root.resolve(id.removePrefix(ROOT_ID))
        // Only the signage folder or a folder directly inside it, whatever the id says.
        if (target != root && target.normalize().parent != root) return false
        if (trash != null && id != ROOT_ID) return trash(target.toFile())
        return filesIn(target).map { file ->
            try {
                if (trash != null) trash(file.path.toFile()) else Files.deleteIfExists(file.path)
            } catch (_: IOException) {
                false
            }
        }.all { it }
    }

    private fun defaultTrash(): ((File) -> Boolean)? = trashFor(canTrash())

    /** The Trash's move where [available]; null where there is none, so files are deleted instead. */
    internal fun trashFor(available: Boolean): ((File) -> Boolean)? =
        if (available) Desktop.getDesktop()::moveToTrash else null

    fun kindOf(path: Path): MediaKind? = when (path.extension.lowercase()) {
        in IMAGES -> MediaKind.IMAGE
        in VIDEOS -> MediaKind.VIDEO
        else -> null
    }

    private fun filesIn(dir: Path): List<FolderFile> = list(dir)
        .filter { it.isRegularFile() && !it.name.startsWith(".") }
        .sortedBy { it.name.lowercase() }
        .mapNotNull { path ->
            val kind = kindOf(path) ?: return@mapNotNull null
            try {
                FolderFile(path, kind, Files.size(path), Files.getLastModifiedTime(path).toMillis())
            } catch (_: IOException) {
                null
            }
        }

    private fun list(dir: Path): List<Path> = try {
        Files.list(dir).use { it.toList() }
    } catch (_: IOException) {
        emptyList()
    }
}

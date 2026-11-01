package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.MediaKind
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** A picture or video in a playlist's folder, as it stood when the folder was read. */
data class FolderFile(val path: Path, val kind: MediaKind, val size: Long, val modifiedMs: Long)

/**
 * A folder playlist as it was last read: everything in it, in file-name order, each picture up for
 * [pictureSeconds]; [missing] when the folder is gone.
 */
data class FolderPlaylist(
    override val id: String,
    override val name: String,
    val files: List<FolderFile>,
    val path: String,
    val missing: Boolean = false,
    val pictureSeconds: Int = PickedFolderPlaylist.DEFAULT_PICTURE_SECONDS,
) : PlaylistSource

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
    detail = path,
    missing = missing,
    seconds = pictureSeconds,
)

/**
 * [this] playlist's file as it stands now, a copy found under [signage]; null when it is gone or
 * cannot be read. Blocking.
 */
internal fun FilePlaylist.currentFile(signage: Path): FolderFile? = try {
    val file = SignageMedia.resolve(signage, path)
    if (file == null || !file.isRegularFile()) null else {
        FolderFile(file, kind, Files.size(file), Files.getLastModifiedTime(file).toMillis())
    }
} catch (_: IOException) {
    null
}

/** A file chooser's filter for every picture and video a playlist can hold. */
// FileNameExtensionFilter takes its extensions only as varargs.
@Suppress("SpreadOperator")
internal fun picturesAndVideosFilter(description: String) =
    FileNameExtensionFilter(description, *(SignageFolder.IMAGES + SignageFolder.VIDEOS).toTypedArray())

/** [folder] with each of its files' hashes, from [this] store's cache where they did not change. */
internal fun SignageStore.hashFolder(folder: FolderPlaylist): HashedPlaylist =
    HashedPlaylist(folder, folder.files.associateWith(::hashOf))

/** [this] playlist as the tab lists it; a file or folder that is gone is [PlaylistSummary.missing]. */
internal fun HashedPlaylist.addedSummary(): PlaylistSummary = when (val source = playlist) {
    is FilePlaylist -> PlaylistSummary(
        id = source.id,
        name = source.name,
        kind = if (source.kind == MediaKind.IMAGE) SourceKind.PICTURE else SourceKind.VIDEO,
        detail = source.path.substringAfterLast('/').substringAfterLast('\\'),
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
    // Listed as it stands until its first read.
    is PickedFolderPlaylist -> PlaylistSummary(
        source.id, source.name, detail = source.path, seconds = source.pictureSeconds,
    )
}

/** Reads the folders playlists are made from. Blocking: run it on `Dispatchers.IO`. */
object SignageFolder {
    internal val IMAGES = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    internal val VIDEOS = setOf("mp4", "m4v", "mov", "webm", "mkv")

    /**
     * [picked] as it stands now, a copy found under [signage]: its pictures and videos, or none and
     * missing when the folder is gone.
     */
    fun read(picked: PickedFolderPlaylist, signage: Path): FolderPlaylist {
        val dir = SignageMedia.resolve(signage, picked.path)?.takeIf { it.isDirectory() }
        return FolderPlaylist(
            picked.id, picked.name, dir?.let(::filesIn).orEmpty(), picked.path,
            missing = dir == null, pictureSeconds = picked.pictureSeconds,
        )
    }

    fun kindOf(path: Path): MediaKind? = when (path.extension.lowercase()) {
        in IMAGES -> MediaKind.IMAGE
        in VIDEOS -> MediaKind.VIDEO
        else -> null
    }

    /** The pictures and videos directly in [dir], in file-name order. */
    internal fun filesIn(dir: Path): List<FolderFile> = list(dir)
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

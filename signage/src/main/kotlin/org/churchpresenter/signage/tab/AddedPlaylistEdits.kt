package org.churchpresenter.signage.tab

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/**
 * The tab's edits to the playlists, each saved and published by [update]. A folder or a picture or
 * video is copied into the signage folder first, through [media], so every computer sharing it plays
 * the same files; removing the playlist removes that copy, never the original.
 */
class AddedPlaylistEdits internal constructor(
    private val update: (then: () -> Unit, change: (SignageSettings) -> SignageSettings) -> Unit,
    private val media: MediaCopies,
) {
    /** Adds [link] as a playlist. */
    fun addLink(link: NewLink) = update({}) { settings ->
        settings.copy(added = settings.added + LinkPlaylist.create(link))
    }

    /** Replaces link [id]'s name, address, kind, refresh, smoothness and touch use with [link]'s; it keeps its id. */
    fun updateLink(id: String, link: NewLink) = update({}) { settings ->
        settings.copy(
            added = settings.added.map { entry ->
                if (entry is LinkPlaylist && entry.id == id) {
                    entry.copy(
                        name = link.name.trim(),
                        url = link.url.trim(),
                        kind = link.kind,
                        refreshMinutes = link.refreshMinutes,
                        smooth = link.smooth,
                        interactive = link.interactive,
                    )
                } else {
                    entry
                }
            },
        )
    }

    /**
     * Adds [folder] as a playlist named after it: played where it is when it is inside the signage
     * folder already, so whoever drops pictures into it updates the screens; otherwise a copy of
     * its pictures and videos is made there.
     */
    fun addFolder(folder: Path) {
        // A drive's root has no name of its own.
        val name = folder.name.ifEmpty { folder.toString() }
        media.copy(name, { SignageMedia.place(folder, it, SignageMedia::copyFolder) }) { path ->
            update({}) { settings -> settings.copy(added = settings.added + PickedFolderPlaylist.create(name, path)) }
        }
    }

    /** Renames folder playlist [id] (its copy keeps its own name) and sets how long each picture stays up. */
    fun updateFolder(id: String, name: String, pictureSeconds: Int) = update({}) { settings ->
        val seconds = pictureSeconds.coerceIn(PickedFolderPlaylist.PICTURE_SECONDS)
        settings.copy(
            added = settings.added.map { entry ->
                if (entry is PickedFolderPlaylist && entry.id == id) {
                    entry.copy(name = name.trim(), pictureSeconds = seconds)
                } else {
                    entry
                }
            },
        )
    }

    /**
     * Adds [file] as a playlist of its own, played where it is when it is inside the signage folder
     * already and copied there otherwise; nothing when it is no picture or video.
     */
    fun addFile(file: Path) {
        val kind = SignageFolder.kindOf(file) ?: return
        media.copy(file.name, { SignageMedia.place(file, it, SignageMedia::copyFile) }) { path ->
            update({}) { settings ->
                settings.copy(added = settings.added + FilePlaylist.create(file.nameWithoutExtension, path, kind))
            }
        }
    }

    /**
     * Renames file playlist [id], and when [file] is given, plays a copy of it in place of the one it
     * had, which is removed; a file that is no picture or video is not taken.
     */
    fun updateFile(id: String, name: String, file: Path? = null) {
        val kind = file?.let(SignageFolder::kindOf)
        if (file == null || kind == null) {
            update({}) { settings -> settings.withFile(id) { it.copy(name = name.trim()) } }
            return
        }
        media.copy(file.name, { SignageMedia.place(file, it, SignageMedia::copyFile) }) { path ->
            var replaced: String? = null
            update({ replaced?.let(media::discard) }) { settings ->
                val changed = settings.withFile(id) { entry ->
                    replaced = entry.path.takeIf { it != path }
                    entry.copy(name = name.trim(), path = path, kind = kind)
                }
                replaced = replaced?.takeIf { changed.unused(it) }
                changed
            }
        }
    }

    /** Adds a rotation of [steps], other entries by id. */
    fun addRotation(name: String, steps: List<RotationStep>) = update({}) { settings ->
        settings.copy(added = settings.added + RotationPlaylist.create(name.trim(), steps))
    }

    /** Replaces rotation [id]'s name and steps. */
    fun updateRotation(id: String, name: String, steps: List<RotationStep>) = update({}) { settings ->
        settings.copy(
            added = settings.added.map { entry ->
                if (entry is RotationPlaylist && entry.id == id) {
                    entry.copy(name = name.trim(), steps = steps)
                } else {
                    entry
                }
            },
        )
    }

    /**
     * Removes playlist [id], and its copy in the signage folder (to the Trash where there is one); a
     * file or folder it played where it was is left alone. A screen that played it plays the first.
     */
    fun remove(id: String) {
        var gone: String? = null
        update({ gone?.let(media::discard) }) { settings ->
            val left = settings.withoutAdded(id)
            // A copy another playlist still plays stays.
            gone = settings.added.firstOrNull { it.id == id }?.copyPath()?.takeIf { left.unused(it) }
            left
        }
    }

    /** Where playlist [id]'s folder or file is on this computer; null for a link or a rotation. */
    fun locate(id: String): Path? = media.locate(id)
}

/**
 * Copies into the signage folder [signage] off the controller's thread, so a large one never holds
 * up the screens; each is listed by name in [state] while it runs.
 */
internal class MediaCopies(
    private val signage: Path,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SignageFolderState>,
) {
    /** Runs [work] (given the signage folder; the copy's path, or null) and hands a made copy to [then]. */
    fun copy(name: String, work: (Path) -> String?, then: (String) -> Unit): Job = scope.launch(Dispatchers.IO) {
        state.update { it.copy(copying = it.copying + name, copyFailed = null) }
        val path = work(signage)
        state.update { it.copy(copying = it.copying - name, copyFailed = name.takeIf { path == null }) }
        path?.let(then)
    }

    /** Removes the copy at [path], if it is one the tab made; a file or folder played where it is stays. */
    fun discard(path: String): Job = scope.launch(Dispatchers.IO) { SignageMedia.discard(signage, path) }

    fun locate(id: String): Path? =
        state.value.settings.added.firstOrNull { it.id == id }?.copyPath()?.let { SignageMedia.resolve(signage, it) }
}

/** The file or folder [this] playlist plays, as it keeps its path; null for a link or a rotation. */
internal fun AddedPlaylist.copyPath(): String? = when (this) {
    is FilePlaylist -> path
    is PickedFolderPlaylist -> path
    is LinkPlaylist, is RotationPlaylist -> null
}

/** Whether no playlist in [this] plays [path]. */
private fun SignageSettings.unused(path: String) = added.none { it.copyPath() == path }

/** [this] with file playlist [id] changed by [edit]. */
private fun SignageSettings.withFile(id: String, edit: (FilePlaylist) -> FilePlaylist) = copy(
    added = added.map { entry -> if (entry is FilePlaylist && entry.id == id) edit(entry) else entry },
)

package org.churchpresenter.signage.tab

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.churchpresenter.signage.core.Orientation
import kotlinx.serialization.SerializationException
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.signage.core.ScheduleBlock
import org.churchpresenter.signage.core.SignageJson
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** What the Signage tab keeps between runs, apart from the paired screens themselves. */
@Serializable
data class SignageSettings(
    /** What each screen plays, by screen id. */
    val screens: Map<String, ScreenPlan> = emptyMap(),
    /** The playlists added in the tab, in the order they were added. */
    val added: List<AddedPlaylist> = emptyList(),
) {
    fun plan(id: String): ScreenPlan = screens[id] ?: ScreenPlan()

    /**
     * Without [id] added, in no rotation, and with no screen still playing it: those play the first
     * playlist again.
     */
    fun withoutAdded(id: String): SignageSettings = copy(
        added = added.filterNot { it.id == id }.map { entry ->
            if (entry is RotationPlaylist) entry.copy(steps = entry.steps.filterNot { it.sourceId == id }) else entry
        },
        // Only what the screen plays goes back to the first playlist: its schedule stays.
        screens = screens.mapValues { (_, plan) -> if (plan.playlistId == id) plan.copy(playlistId = null) else plan },
    )
}

/**
 * What one screen plays: a playlist's id, [NOTHING], or null for the first playlist — and its weekly
 * [schedule], whose blocks play another playlist, turn the TV off or keep it on the service input at
 * their times. Outside every block the screen plays [playlistId].
 */
@Serializable
data class ScreenPlan(
    val playlistId: String? = null,
    val schedule: List<ScheduleBlock> = emptyList(),
    /** How the screen is mounted: everything it shows is turned to match. */
    val orientation: Orientation = Orientation.LANDSCAPE,
    /** What the screen's touch gesture asks for, as `KioskPin.hash` makes it; no PIN when null. */
    val kioskPinHash: String? = null,
    /** Whether holding the screen's corner switches to and from a touch page. */
    val kioskGesture: Boolean = true,
    /** The playlist the corner switches to; the first with a touch page when null. */
    val kioskGesturePlaylistId: String? = null,
) {
    companion object {
        /** The screen plays nothing and holds no content: it shows its idle card. */
        const val NOTHING = "nothing:"

        /** What a Plays choice named [name] stands for: a playlist's id, [NOTHING], or null if not listed. */
        fun forChoice(name: String, nothing: String, playlists: List<PlaylistSummary>): String? =
            if (name == nothing) NOTHING else playlists.firstOrNull { it.name == name }?.id
    }
}

/** A file's hash, and the size and time it had when it was hashed. */
@Serializable
data class HashedFile(val size: Long, val modifiedMs: Long, val sha256: String)

/**
 * The tab's files: its settings in [dir], which another computer may share and change, and in
 * [cacheDir], this computer's own, the hash of every file it has read, so a start-up only hashes
 * what changed while ChurchPresenter was off. Blocking: run it on `Dispatchers.IO`.
 */
class SignageStore(private val dir: Path, private val cacheDir: Path = dir) {
    private val settingsFile = dir.resolve("signage.json")
    private val hashesFile = cacheDir.resolve("hashes.json")
    private var hashes: MutableMap<String, HashedFile> = mutableMapOf()

    /**
     * The settings file as it was last read or written here, to tell another computer's write from
     * ours. Its content, not its time: a network share keeps times only to a second or two.
     */
    private var settingsSeen: String? = null

    fun loadSettings(): SignageSettings {
        settingsSeen = textOf(settingsFile)
        return read(settingsFile, SignageSettings.serializer()) ?: SignageSettings()
    }

    fun saveSettings(settings: SignageSettings) {
        write(settingsFile, SignageSettings.serializer(), settings)
        settingsSeen = textOf(settingsFile)
    }

    /** The settings again when another computer has changed them since they were read or written here. */
    fun settingsChangedElsewhere(): SignageSettings? =
        if (textOf(settingsFile) == settingsSeen) null else loadSettings()

    fun loadHashes() {
        hashes = read(hashesFile, HashesFile.serializer())?.files?.toMutableMap() ?: mutableMapOf()
    }

    /** [file]'s SHA-256, read from the cache while its size and time are unchanged. */
    fun hashOf(file: FolderFile): String? {
        val key = file.path.toAbsolutePath().toString()
        hashes[key]?.takeIf { it.size == file.size && it.modifiedMs == file.modifiedMs }?.let { return it.sha256 }
        val sha = try {
            hash(file.path)
        } catch (e: IOException) {
            Log.warn(TAG, "Could not read ${file.path}: ${e.message}")
            return null
        }
        hashes[key] = HashedFile(file.size, file.modifiedMs, sha)
        return sha
    }

    /**
     * An added playlist with its files' hashes: a picture or video is hashed again only when it
     * changed, and a missing one has none; a folder is read as it stands now.
     */
    fun hashAdded(entry: AddedPlaylist): HashedPlaylist = when (entry) {
        is FilePlaylist -> HashedPlaylist(entry, entry.currentFile(dir)?.let { mapOf(it to hashOf(it)) }.orEmpty())
        is PickedFolderPlaylist -> hashFolder(SignageFolder.read(entry, dir))
        is LinkPlaylist, is RotationPlaylist -> HashedPlaylist(entry)
    }

    /** Forgets every file not in [keep] and saves the rest. */
    fun saveHashes(keep: Set<Path>) {
        val wanted = keep.map { it.toAbsolutePath().toString() }.toSet()
        hashes.keys.retainAll(wanted)
        write(hashesFile, HashesFile.serializer(), HashesFile(hashes.toMap()))
    }

    @Serializable
    private data class HashesFile(val files: Map<String, HashedFile> = emptyMap())

    private fun hash(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun <T> read(file: Path, serializer: KSerializer<T>): T? = try {
        if (Files.exists(file)) SignageJson.decodeFromString(serializer, Files.readString(file)) else null
    } catch (e: IOException) {
        Log.warn(TAG, "Could not read $file: ${e.message}")
        null
    } catch (e: SerializationException) {
        Log.warn(TAG, "$file is damaged: ${e.message}")
        null
    }

    private fun <T> write(file: Path, serializer: KSerializer<T>, value: T) {
        try {
            Files.createDirectories(file.parent)
            val next = file.resolveSibling("${file.fileName}.next")
            Files.writeString(next, SignageJson.encodeToString(serializer, value))
            Files.move(next, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            Log.error(TAG, "Could not save $file: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "Signage"
        const val BUFFER_BYTES = 64 * 1024
    }
}

/** What [file] holds now; null when it is not there or cannot be read. */
private fun textOf(file: Path): String? = try {
    if (Files.exists(file)) Files.readString(file) else null
} catch (_: IOException) {
    null
}

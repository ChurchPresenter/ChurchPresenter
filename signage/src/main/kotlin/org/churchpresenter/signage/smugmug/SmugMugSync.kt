package org.churchpresenter.signage.smugmug

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.signage.tab.SmugMugPlaylist
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.random.Random

/**
 * How a slideshow's last pick went, kept beside its photos (`.smugmug.json`) so every computer sharing
 * the signage folder sees it: when the set was picked and how many photos it has, the error of the
 * last try when it failed, what the set was picked for ([settingsKey]), and each gallery's photos as
 * last listed, so a new pick lists again only the galleries that changed.
 */
@Serializable
data class SmugMugState(
    val pickedAtMs: Long? = null,
    val photos: Int = 0,
    val error: String? = null,
    val triedAtMs: Long? = null,
    val settingsKey: String = "",
    val albums: Map<String, CachedAlbum> = emptyMap(),
) {
    @Serializable
    data class CachedAlbum(val updated: String, val photos: List<String>)
}

/**
 * Fills each SmugMug slideshow's folder in the signage folder ([signageDir]) with a set of photos
 * picked at random, and picks again when it is due. A failed pick keeps the set it had: the screens go
 * on playing it. [apiKey] is read at each pick, so a key typed in later is used at once.
 */
class SmugMugSync(
    private val signageDir: Path,
    private val apiKey: () -> String,
    private val http: SmugMugHttp = JdkSmugMugHttp(),
    private val random: Random = Random.Default,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** What [playlist]'s last pick left; an empty state before its first. */
    fun state(playlist: SmugMugPlaylist): SmugMugState = read(dirOf(playlist))

    /**
     * Whether [playlist] should pick a new set now: its settings changed since its last try (at once,
     * even just after a failure), it never picked one, or its time is up; a failed try waits
     * [RETRY_MS] before the same one is tried again.
     */
    fun isDue(playlist: SmugMugPlaylist): Boolean {
        val state = state(playlist)
        val t = now()
        val picked = state.pickedAtMs
        val waiting = state.error != null && state.triedAtMs != null && t - state.triedAtMs < RETRY_MS
        return when {
            state.settingsKey != keyOf(playlist) -> true
            waiting -> false
            picked == null -> true
            else -> t - picked >= playlist.refreshHours * MS_PER_HOUR
        }
    }

    /**
     * Picks a new set for [playlist] and puts it in its folder; on failure keeps the set it had. How far
     * it has got goes to [onProgress] as it goes: the galleries listed, then the photos saved.
     */
    suspend fun pick(playlist: SmugMugPlaylist, onProgress: (SmugMugProgress) -> Unit = {}): SmugMugState {
        val dir = dirOf(playlist)
        val before = state(playlist)
        val after = try {
            val key = apiKey().trim()
            if (key.isEmpty()) throw IOException(NO_KEY)
            fill(playlist, dir, before, SmugMugClient(http, key), onProgress)
        } catch (e: IOException) {
            Log.warn(TAG, "SmugMug slideshow ${playlist.name} could not pick new photos: ${e.message}")
            // Remembered as tried with these settings, so it waits before trying them again.
            before.copy(error = e.message ?: e.javaClass.simpleName, triedAtMs = now(), settingsKey = keyOf(playlist))
        }
        write(dir, after)
        return after
    }

    /** Forgets [playlist]'s photos and state: it was removed. */
    @OptIn(ExperimentalPathApi::class)
    suspend fun forget(playlist: SmugMugPlaylist) = withContext(Dispatchers.IO) {
        val dir = dirOf(playlist)
        try {
            listOf(dir, staging(dir), old(dir)).filter { it.exists() }.forEach { it.deleteRecursively() }
        } catch (e: IOException) {
            Log.warn(TAG, "Could not delete a removed SmugMug slideshow's photos: ${e.message}")
        }
    }

    private suspend fun fill(
        playlist: SmugMugPlaylist,
        dir: Path,
        before: SmugMugState,
        client: SmugMugClient,
        onProgress: (SmugMugProgress) -> Unit,
    ): SmugMugState {
        val nickname = client.nickname(playlist.site)
        val albums = SmugMugPick.albumsToUse(
            client.albums(nickname),
            playlist.includeUnlisted,
            playlist.excludedAlbumKeys,
        )
        // Only galleries whose photos changed since the last pick are listed again, [LISTING] at a time.
        val gate = Semaphore(LISTING)
        val listedSoFar = AtomicInteger()
        onProgress(SmugMugProgress(SmugMugProgress.Stage.LISTING, 0, albums.size))
        val listed = coroutineScope {
            albums.map { album ->
                async {
                    val cached = before.albums[album.key]
                        ?.takeIf { it.updated == album.imagesLastUpdated && it.updated.isNotEmpty() }
                    val photos = cached ?: gate.withPermit {
                        SmugMugState.CachedAlbum(album.imagesLastUpdated, client.photoKeys(album))
                    }
                    onProgress(
                        SmugMugProgress(SmugMugProgress.Stage.LISTING, listedSoFar.incrementAndGet(), albums.size),
                    )
                    album.key to photos
                }
            }.awaitAll().toMap()
        }
        val picked = SmugMugPick.pick(listed.values.flatMap { it.photos }, playlist.photoCount, random)
        if (picked.isEmpty()) throw IOException(NO_PHOTOS)
        val saved = download(picked, dir, client, onProgress)
        if (saved == 0) throw IOException(NOTHING_SAVED)
        return SmugMugState(
            pickedAtMs = now(),
            photos = saved,
            settingsKey = keyOf(playlist),
            albums = listed,
        )
    }

    /**
     * Puts [keys] in [dir], in that order (their names start with their place), taking a photo the set
     * already had from there and downloading the rest, [PARALLEL] at a time. The new set is made beside
     * the folder and swapped in whole, so the folder never holds half of one. The number saved.
     */
    @OptIn(ExperimentalPathApi::class)
    private suspend fun download(
        keys: List<String>,
        dir: Path,
        client: SmugMugClient,
        onProgress: (SmugMugProgress) -> Unit,
    ): Int {
        val next = staging(dir)
        withContext(Dispatchers.IO) {
            if (next.exists()) next.deleteRecursively()
            Files.createDirectories(next)
        }
        val had = withContext(Dispatchers.IO) { photosIn(dir) }
        val gate = Semaphore(PARALLEL)
        val done = AtomicInteger()
        onProgress(SmugMugProgress(SmugMugProgress.Stage.DOWNLOADING, 0, keys.size))
        val saved = coroutineScope {
            keys.mapIndexed { index, key ->
                async {
                    gate.withPermit { savePhoto(key, index, had[key], next, client) }.also {
                        val saved = done.incrementAndGet()
                        onProgress(SmugMugProgress(SmugMugProgress.Stage.DOWNLOADING, saved, keys.size))
                    }
                }
            }.awaitAll().count { it }
        }
        withContext(Dispatchers.IO) {
            val previous = old(dir)
            if (previous.exists()) previous.deleteRecursively()
            if (dir.exists()) Files.move(dir, previous)
            Files.move(next, dir)
            if (previous.exists()) previous.deleteRecursively()
        }
        return saved
    }

    /** Photo [key] at place [index] in [into]: copied from [have] when the set had it, downloaded otherwise. */
    private suspend fun savePhoto(key: String, index: Int, have: Path?, into: Path, client: SmugMugClient): Boolean =
        try {
            val prefix = "%04d".format(index + 1)
            if (have != null) {
                withContext(Dispatchers.IO) {
                    val name = "$prefix-${have.name.substringAfter('-')}"
                    Files.copy(have, into.resolve(name), StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                val size = client.size(key) ?: return false
                http.download(size.url, into.resolve("$prefix-$key.${size.extension}"))
            }
            true
        } catch (e: IOException) {
            Log.warn(TAG, "A SmugMug photo could not be saved: ${e.message}")
            false
        }

    private fun dirOf(playlist: SmugMugPlaylist): Path = signageDir.resolve(playlist.path)

    companion object {
        /** Beside the photos, with a dot first, so the folder reader passes over it. */
        const val STATE_FILE = ".smugmug.json"

        /** What [SmugMugState.error] says when no API key is set, for the tab to recognise. */
        const val NO_KEY = "No SmugMug API key"
        const val NO_PHOTOS = "No public photos found"
        const val NOTHING_SAVED = "No photo could be downloaded"

        private const val PARALLEL = 4
        private const val LISTING = 6
        private const val MS_PER_HOUR = 3_600_000L

        /** How long a failed pick waits before it is tried again. */
        const val RETRY_MS = 15 * 60_000L

        /** What a set was picked for: a change to any of these picks again at once. */
        fun keyOf(playlist: SmugMugPlaylist): String = listOf(
            playlist.site.trim().lowercase(),
            playlist.photoCount,
            playlist.includeUnlisted,
            playlist.excludedAlbumKeys.sorted().joinToString(","),
        ).joinToString("|")
    }
}

/** The photos already in [dir], by their SmugMug key (the name between the place and the extension). */
private fun photosIn(dir: Path): Map<String, Path> = if (!dir.isDirectory()) emptyMap() else {
    dir.listDirectoryEntries()
        .filter { Files.isRegularFile(it) && !it.name.startsWith(".") }
        .associateBy { it.name.substringAfter('-').substringBeforeLast('.') }
}

/** Where a new set is made, beside the slideshow's folder. */
private fun staging(dir: Path): Path = dir.resolveSibling(".${dir.name}.new")

/** Where the set it replaces goes while the new one is moved in. */
private fun old(dir: Path): Path = dir.resolveSibling(".${dir.name}.old")

/** What a slideshow's last pick left in [dir]; an empty state when there is none it can read. */
private fun read(dir: Path): SmugMugState = try {
    val file = dir.resolve(SmugMugSync.STATE_FILE)
    if (file.exists()) JSON.decodeFromString(SmugMugState.serializer(), file.readText()) else SmugMugState()
} catch (e: IOException) {
    Log.warn(TAG, "Could not read a SmugMug slideshow's state: ${e.message}")
    SmugMugState()
} catch (_: SerializationException) {
    SmugMugState()
} catch (_: IllegalArgumentException) {
    SmugMugState()
}

/** Saves [state] beside the photos in [dir]. */
private suspend fun write(dir: Path, state: SmugMugState) = withContext(Dispatchers.IO) {
    try {
        Files.createDirectories(dir)
        dir.resolve(SmugMugSync.STATE_FILE).writeText(JSON.encodeToString(SmugMugState.serializer(), state))
    } catch (e: IOException) {
        Log.warn(TAG, "Could not save a SmugMug slideshow's state: ${e.message}")
    }
}

private const val TAG = "Signage"
private val JSON = Json { ignoreUnknownKeys = true }

/** How far a slideshow's pick has got: [done] of [total] galleries listed, or photos saved. */
data class SmugMugProgress(val stage: Stage, val done: Int, val total: Int) {
    enum class Stage { LISTING, DOWNLOADING }
}

package org.churchpresenter.signage.tab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.signage.smugmug.SmugMugClient
import org.churchpresenter.signage.smugmug.SmugMugHttp
import org.churchpresenter.signage.smugmug.SmugMugSite
import org.churchpresenter.signage.smugmug.SmugMugSync
import java.nio.file.Path

/**
 * The SmugMug slideshows' photos, kept in the signage folder ([storeDir]): each picked when it is due,
 * edited or asked to, one slideshow at a time, with how it went in [state]. A picked set reaches the
 * screens as a folder's change does, once the folder has stood still for a read. [apiKey] is read at
 * each pick; a new one tries a slideshow that failed for want of it at once.
 */
class SlideshowPicks internal constructor(
    storeDir: Path,
    private val apiKey: StateFlow<String>,
    private val http: SmugMugHttp,
    private val state: MutableStateFlow<SignageFolderState>,
    private val update: (then: () -> Unit, change: (SignageSettings) -> SignageSettings) -> Unit,
) {
    private val sync = SmugMugSync(storeDir, { apiKey.value }, http)

    /** Slideshows asked to pick new photos now, and a nudge to look at once. */
    private val asked = MutableStateFlow(emptySet<String>())
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /**
     * Saves [slideshow]: in place of the one with its id, or added when it is new, when its first
     * photos are picked straight away.
     */
    fun save(slideshow: SmugMugPlaylist) = update({}) { settings ->
        val known = settings.added.any { it.id == slideshow.id }
        val added = if (known) {
            settings.added.map { if (it.id == slideshow.id) slideshow else it }
        } else {
            settings.added + slideshow
        }
        settings.copy(added = added)
    }

    /** Has SmugMug slideshow [id] pick a new set of photos now, whenever its last was picked. */
    fun pickNewPhotos(id: String) {
        asked.update { it + id }
        wake()
    }

    /** Looks at every slideshow now rather than at the next check: a setting or the key changed. */
    fun wake() {
        wake.trySend(Unit)
    }

    /**
     * What [site]'s SmugMug account offers a slideshow, read with [key]: its nickname and its
     * galleries. Throws [java.io.IOException] when SmugMug cannot be reached or does not know the site.
     */
    suspend fun lookUp(site: String, key: String): SmugMugSite = withContext(Dispatchers.IO) {
        val client = SmugMugClient(http, key.trim())
        val nickname = client.nickname(site)
        SmugMugSite(nickname, client.albums(nickname))
    }

    /** Deletes a removed slideshow's photos. */
    internal suspend fun forget(slideshow: SmugMugPlaylist) = sync.forget(slideshow)

    /** Picks whatever is due, then waits [CHECK_MS] or a nudge, until cancelled. */
    internal suspend fun run() {
        var key = apiKey.value
        while (true) {
            val shows = state.value.settings.added.filterIsInstance<SmugMugPlaylist>().filter { it.site.isNotBlank() }
            val now = asked.getAndUpdate { emptySet() }
            val keyChanged = apiKey.value != key
            key = apiKey.value
            shows.forEach { show ->
                val failedForKey = keyChanged && sync.state(show).error != null
                if (show.id in now || failedForKey || sync.isDue(show)) pick(show)
            }
            state.update { current -> current.copy(slideshows = shows.associate { it.id to sync.state(it) }) }
            withTimeoutOrNull(CHECK_MS) { wake.receive() }
        }
    }

    /** Picks [show]'s photos now, said in [state] while it runs. */
    private suspend fun pick(show: SmugMugPlaylist) {
        state.update { it.copy(picking = it.picking + show.id) }
        sync.pick(show) { progress ->
            state.update { it.copy(pickProgress = it.pickProgress + (show.id to progress)) }
        }
        // Removed while it picked: what the pick just made goes too.
        if (state.value.settings.added.none { it.id == show.id }) sync.forget(show)
        state.update { it.copy(picking = it.picking - show.id, pickProgress = it.pickProgress - show.id) }
    }

    private companion object {
        const val CHECK_MS = 60_000L
    }
}

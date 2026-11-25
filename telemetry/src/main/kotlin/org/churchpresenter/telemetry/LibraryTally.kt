package org.churchpresenter.telemetry

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.InstanceLinkRole
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The song library and the Bibles as the Songs and Bible tabs loaded them, for the launch ping.
 *
 * What the settings and the song folder say at launch can fall short: a song folder on a share not
 * mounted yet, or an Instance Link follower whose songs and Bibles are the primary's. So the app
 * reports each list the tabs load here, and the ping waits for it. Where two counts disagree the
 * larger one wins.
 */
class LibraryTally {
    private val songs = MutableStateFlow<Pair<Int, Int>?>(null)
    private val bibles = MutableStateFlow(0)

    /** One list the Songs tab loaded, by each song's songbook. */
    fun songsLoaded(songbooks: List<String>) {
        val counts = songCountsOf(songbooks)
        songs.update { old -> mostSongs(old, counts) }
    }

    /** How many translations the Bible tab has loaded. */
    fun biblesLoaded(count: Int) {
        bibles.update { maxOf(it, count) }
    }

    /**
     * The most the tabs have loaded: waits up to [first] for the Songs tab's first list, then
     * [settle] more for one that arrives later. Songs are null when none loaded in time.
     */
    suspend fun await(first: Duration, settle: Duration = Duration.ZERO): Loaded {
        withTimeoutOrNull(first) { songs.first { it != null } }
        if (settle > Duration.ZERO) delay(settle)
        return Loaded(songs.value, bibles.value)
    }

    /** What the tabs had loaded when the ping asked. */
    data class Loaded(val songs: Pair<Int, Int>?, val bibles: Int)

    companion object {
        /** How long the ping waits for the Songs tab's first list. */
        val FIRST_LOAD_WAIT: Duration = 60.seconds

        /** How much longer a follower waits, for the primary's catalog and Bibles to arrive. */
        val FOLLOWER_SETTLE: Duration = 30.seconds

        /** The extra wait [settings] call for: only a follower that connects at launch gets one. */
        fun settleFor(settings: AppSettings): Duration = with(settings.instanceLink) {
            if (enabled && autoConnect && role == InstanceLinkRole.CONTROLLED) FOLLOWER_SETTLE else Duration.ZERO
        }
    }
}

/** Songbooks and songs in a list of songs given by songbook; a song with none is in the root one. */
fun songCountsOf(songbooks: List<String>): Pair<Int, Int> =
    songbooks.map { it.ifBlank { ROOT_SONGBOOK } }.toSet().size to songbooks.size

/** Whichever of two counts has more songs; [b] on a tie, [a] when [b] is missing. */
fun mostSongs(a: Pair<Int, Int>?, b: Pair<Int, Int>?): Pair<Int, Int>? = when {
    a == null -> b
    b == null -> a
    b.second >= a.second -> b
    else -> a
}

private const val ROOT_SONGBOOK = "/"

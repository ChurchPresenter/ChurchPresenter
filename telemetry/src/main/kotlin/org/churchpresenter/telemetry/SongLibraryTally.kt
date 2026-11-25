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
 * The song library as the Songs tab loaded it, for the launch ping: songbooks and songs.
 *
 * The folder scan at launch can count short — a song folder on a share not mounted yet, or an
 * Instance Link follower whose library is the primary's catalog — so the app reports each list the
 * tab loads here, and the ping waits for it. Where two counts disagree the one with the most songs
 * wins.
 */
class SongLibraryTally {
    private val most = MutableStateFlow<Pair<Int, Int>?>(null)

    /** One list the Songs tab loaded, by each song's songbook. */
    fun loaded(songbooks: List<String>) {
        val counts = songCountsOf(songbooks)
        most.update { old -> mostSongs(old, counts) }
    }

    /**
     * The most the tab has loaded: waits up to [first] for its first list, then [settle] more for
     * one that arrives later. Null when nothing loaded in time.
     */
    suspend fun await(first: Duration, settle: Duration = Duration.ZERO): Pair<Int, Int>? {
        withTimeoutOrNull(first) { most.first { it != null } } ?: return null
        if (settle > Duration.ZERO) delay(settle)
        return most.value
    }

    companion object {
        /** How long the ping waits for the Songs tab's first list. */
        val FIRST_LOAD_WAIT: Duration = 60.seconds

        /** How much longer a follower waits, for the primary's catalog to arrive. */
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

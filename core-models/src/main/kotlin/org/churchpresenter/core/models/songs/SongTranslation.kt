package org.churchpresenter.core.models.songs

import kotlinx.serialization.Serializable

/**
 * How many languages one song may carry, the primary included.
 *
 * Four rather than the Bible's six because a song slide holds whole lines rather than one verse:
 * four sets of wrapped lyric lines already fills a 1080p frame at a readable size, and the fifth
 * would only shrink the other four.
 */
const val MAX_SONG_TRANSLATIONS = 4

/** The most languages that can sit *beside* the primary — see [MAX_SONG_TRANSLATIONS]. */
const val MAX_SONG_EXTRA_TRANSLATIONS = MAX_SONG_TRANSLATIONS - 1

/**
 * One language of a song other than its primary.
 *
 * The primary stays [SongItem.title]/[SongItem.lyrics] rather than becoming entry zero of a list:
 * it is the song's identity, and the filename, the library grid, sorting, search and the CCLI
 * report all read it directly. These are the languages sung *alongside* it.
 *
 * [label] is what the operator called this language — "Ukrainian", "Spanish". Blank is allowed and
 * common (a two-language song written before labels existed has none), in which case the UI falls
 * back to a positional name.
 */
@Serializable
data class SongTranslation(
    val label: String = "",
    val title: String = "",
    val lyrics: List<String> = emptyList(),
) {
    /** Whether this language carries anything at all; a blank one is dropped rather than written. */
    val isEmpty: Boolean get() = title.isBlank() && lyrics.all { it.isBlank() }
}

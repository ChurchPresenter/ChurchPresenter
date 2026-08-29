package org.churchpresenter.core.models.songs

import kotlinx.serialization.Serializable

@Serializable
data class SongItem(
    val number: String,
    val title: String,
    val songbook: String = "",
    val tune: String = "",
    val author: String = "",
    val composer: String = "",
    val lyrics: List<String> = emptyList(),
    val secondaryTitle: String = "",
    val secondaryLyrics: List<String> = emptyList(),
    val sourceFile: String = "",
    val ccliNumber: String = "",
    /** This song's own full-screen background, or an inheriting one when it has none. */
    val background: SongBackground = SongBackground(),
    /** This song's own lower-third background, or an inheriting one when it has none. */
    val lowerThirdBackground: SongBackground = SongBackground()
) {
    /** Stable unique ID across songbooks: "songbook::number" or "songbook::title" when no number */
    val songId: String get() = if (number.isNotBlank()) "$songbook::$number" else "$songbook::$title"
}

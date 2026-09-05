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
    /**
     * The languages sung beside the primary, in order — up to [MAX_SONG_EXTRA_TRANSLATIONS].
     *
     * Source of truth. [secondaryTitle]/[secondaryLyrics] are retained only so an older build can
     * still read a song cache written by this one, and mirror entry zero — see [withTranslations]
     * and [migrateTranslations], which is the same arrangement `BibleSettings` uses for its
     * retained `primaryBible`/`secondaryBible` pair.
     */
    val translations: List<SongTranslation> = emptyList(),
    val sourceFile: String = "",
    val ccliNumber: String = "",
    /** This song's own full-screen background, or an inheriting one when it has none. */
    val background: SongBackground = SongBackground(),
    /** This song's own lower-third background, or an inheriting one when it has none. */
    val lowerThirdBackground: SongBackground = SongBackground()
) {
    /** Stable unique ID across songbooks: "songbook::number" or "songbook::title" when no number */
    val songId: String get() = if (number.isNotBlank()) "$songbook::$number" else "$songbook::$title"

    /**
     * Every language this song carries, the primary first, so a caller that treats them alike can
     * index straight into it. Position `n` here is what an output's `songTranslations` names.
     */
    fun translationList(): List<SongTranslation> =
        listOf(SongTranslation(title = title, lyrics = lyrics)) + extraTranslations()

    /**
     * [translations], falling back to the retained legacy pair for a song read from a cache written
     * before the list existed, so it still presents bilingually rather than losing its second half.
     */
    fun extraTranslations(): List<SongTranslation> = translations.ifEmpty { legacyTranslations() }

    /** How many languages this song actually has, the primary counted. */
    val translationCount: Int get() = 1 + extraTranslations().size

    /**
     * This song with extra language [index] rebuilt by [transform], the list grown to reach it.
     *
     * The way an editor changes one language without having to restate the others, and the only
     * path that keeps the retained legacy pair in step — assigning `secondaryTitle` with a plain
     * `copy` writes the mirror and leaves [translations], which is what is actually read, holding
     * the old value.
     */
    fun withTranslation(index: Int, transform: (SongTranslation) -> SongTranslation): SongItem {
        if (index !in 0 until MAX_SONG_EXTRA_TRANSLATIONS) return this
        val current = extraTranslations()
        val grown = List(maxOf(current.size, index + 1)) { current.getOrElse(it) { SongTranslation() } }
        return withTranslations(grown.mapIndexed { i, t -> if (i == index) transform(t) else t })
    }

    /** Fills [translations] from the legacy pair when it is empty; idempotent. */
    fun migrateTranslations(): SongItem =
        if (translations.isNotEmpty()) this else copy(translations = legacyTranslations())

    private fun legacyTranslations(): List<SongTranslation> =
        if (secondaryTitle.isBlank() && secondaryLyrics.isEmpty()) emptyList()
        else listOf(SongTranslation(title = secondaryTitle, lyrics = secondaryLyrics))

    /**
     * This song with [value] as its extra languages, the retained pair kept in step.
     *
     * Capped here rather than only where the editor adds one, so a hand-edited `.song` file or one
     * written by a rolled-forward build is bounded by the same rule the UI is.
     */
    fun withTranslations(value: List<SongTranslation>): SongItem {
        // Trailing blanks are dropped, interior ones kept. A gap is meaningful — a song written in
        // languages 1 and 4 must keep the fourth *at* position four, because that is the position
        // an output's `songTranslations` names. Dropping empties outright would slide it to two and
        // silently repoint every screen configured to show it.
        val cleaned = value.dropLastWhile { it.isEmpty }.take(MAX_SONG_EXTRA_TRANSLATIONS)
        return copy(
            translations = cleaned,
            secondaryTitle = cleaned.getOrNull(0)?.title.orEmpty(),
            secondaryLyrics = cleaned.getOrNull(0)?.lyrics ?: emptyList(),
        )
    }
}

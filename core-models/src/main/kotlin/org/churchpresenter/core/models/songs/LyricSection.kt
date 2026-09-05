package org.churchpresenter.core.models.songs

/**
 * One language's share of a single slide.
 *
 * [title] is that language's song title, carried per slide for the same reason the section's own is:
 * the presenter is handed a section and never the [SongItem] it came from.
 */
data class SectionTranslation(
    val title: String = "",
    val lines: List<String> = emptyList(),
)

data class LyricSection(
    val header: String? = null,
    val labelName: String = "",
    val title: String = "",
    val songNumber: Int = 0,
    val type: String = "", // "verse", "chorus"
    val lines: List<String> = emptyList(),
    /**
     * The same slide in every language *other* than the primary, in the song's own order.
     *
     * A list rather than the single `secondaryLines` it replaced, because a song may be sung in up
     * to [MAX_SONG_TRANSLATIONS] languages at once. Position `n` here is language `n + 1` of
     * [SongItem.translationList], which is what an output's `songTranslations` indexes into — so a
     * language the song does not have is a gap in the list, never a shifted entry.
     */
    val translations: List<SectionTranslation> = emptyList(),
    val isLastSection: Boolean = false,
    /**
     * Which slide of its section this is, and how many slides that section was split into.
     *
     * A long verse or chorus is broken across slides with a manual break (`[---]`) written inside
     * it, which produces several [LyricSection]s that all carry the *same* [header] and [type] --
     * both halves of a chorus still read "Chorus". These two are what tells them apart: the operator
     * sees "Chorus 2/3", and rules that mean "the opening slide" (the song title on the first page,
     * say) can ask for [slideIndex] `0` rather than assuming a section is one slide.
     *
     * An unsplit section is slide 0 of 1, which is why the defaults are what they are.
     */
    val slideIndex: Int = 0,
    val slideCount: Int = 1,
    val bpm: Int = 0, // metronome tempo for this song (0 = off)
    val capo: Int = 0, // capo the chart is read with (0 = none)
    /**
     * The section's lines as written, chord markers still in, for the stage monitor to draw a chart
     * from. Held apart from [lines] rather than inside it so no presenter can show a chord by
     * accident: [lines] is what the audience reads and is always stripped.
     *
     * Empty when the song has no chords. Unlike [lines] it keeps chord-only lines — an intro has no
     * words to present but is exactly what the band needs.
     */
    val chordLines: List<String> = emptyList(),
    /**
     * The song's own full-screen background, carried here for the same reason [bpm] and [capo] are:
     * the presenter is handed a section, never the [SongItem] it came from. Inheriting by default,
     * in which case the global Background settings decide.
     */
    val background: SongBackground = SongBackground(),
    /** The same for the lower-third band. */
    val lowerThirdBackground: SongBackground = SongBackground(),
) {
    /**
     * The second language's title and lines.
     *
     * Kept as accessors over [translations] rather than stored fields so the many call sites that
     * only ever wanted "the other language" read unchanged, while the ones that build a section
     * say which language they mean.
     */
    val secondaryTitle: String get() = translations.firstOrNull()?.title.orEmpty()
    val secondaryLines: List<String> get() = translations.firstOrNull()?.lines ?: emptyList()

    /** This slide in every language, the primary first — the order `songTranslations` indexes. */
    fun allLanguageLines(): List<List<String>> = listOf(lines) + translations.map { it.lines }

    /** The title in every language, the primary first, aligned with [allLanguageLines]. */
    fun allLanguageTitles(): List<String> = listOf(title) + translations.map { it.title }
}

/**
 * [section] with [song]'s own backgrounds filled in, so the presenter can draw them.
 *
 * Only where the section has none of its own. A section can carry a background written into the
 * lyrics beside it, and that is the more specific of the two — it exists precisely to say "not the
 * one the rest of this song uses". This used to overwrite it, which made the section field pure
 * transport for the song's value and left a per-section background impossible to express.
 */
fun LyricSection.withBackgroundsOf(song: SongItem): LyricSection = copy(
    background = if (background.isCustom) background else song.background,
    lowerThirdBackground = if (lowerThirdBackground.isCustom) lowerThirdBackground else song.lowerThirdBackground,
)

/**
 * This section with language [index]'s lines replaced and its title left alone, the list grown to
 * reach it.
 *
 * The replacement for what used to be `copy(secondaryLines = …)`: a plain `copy` of the whole
 * [LyricSection.translations] list would drop every other language's title along with it.
 */
fun LyricSection.withTranslationLines(index: Int, lines: List<String>): LyricSection {
    if (index < 0) return this
    val grown = List(maxOf(translations.size, index + 1)) { translations.getOrElse(it) { SectionTranslation() } }
    return copy(translations = grown.mapIndexed { i, t -> if (i == index) t.copy(lines = lines) else t })
}

/** [withTranslationLines] for the second language, which is the one most callers mean. */
fun LyricSection.withSecondaryLines(lines: List<String>): LyricSection = withTranslationLines(0, lines)

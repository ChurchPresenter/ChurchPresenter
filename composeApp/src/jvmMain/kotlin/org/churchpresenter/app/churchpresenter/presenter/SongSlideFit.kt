package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection

/**
 * The part of a song the "each slide" auto-fit measures, lifted out of `SongPresenter.kt` for the
 * reason `SongSectionLabelParts.kt` gives: that file is at detekt's per-file function ceiling.
 */

/**
 * Which slide is on screen: the [section] pushed to the presenter, where it sits in the song, and
 * the line within it in line mode -- the same three values `SongPresenter` is called with.
 */
internal data class SlidePosition(
    val section: LyricSection,
    val displaySectionIndex: Int,
    val displayLineIndex: Int,
)

/**
 * What one slide's fit measures: the [sections] it has to hold, and whether it [isLast], which is
 * the only slide that also has to leave room for the end-of-song marker.
 */
internal data class SlideFit(val sections: List<LyricSection>, val isLast: Boolean)

/**
 * The entry of [sectionsForFit] that stands for the slide at [slide], for a fit that sizes each
 * slide on its own rather than the whole song at once.
 *
 * [sectionsForFit] is what the song-wide fit measures, and it is already built slide by slide: one
 * entry per section in verse mode (each joined to the next when look-ahead is on), and one per line,
 * paired with the line after it, in line mode with look-ahead. So picking the slide on screen out of
 * it measures exactly what the song-wide fit would have measured for that slide, and nothing else.
 *
 * Line mode without look-ahead is the exception: the song-wide fit measures whole sections there, so
 * the line on screen is cut out of the section instead, with each language's matching line.
 *
 * Falls back to the pushed section on its own when it cannot be found in [allLyricSections] -- the
 * whole-song slide, or a section pushed by itself -- which is still the slide being drawn.
 */
internal fun slideFitSections(
    sectionsForFit: List<LyricSection>,
    allLyricSections: List<LyricSection>,
    slide: SlidePosition,
    isLineMode: Boolean,
    lookAheadEnabled: Boolean,
): SlideFit {
    val section = slide.section
    val position = slide.displaySectionIndex
        .takeIf { allLyricSections.getOrNull(it)?.isSamePageAs(section) == true }
        ?: allLyricSections.indexOfFirst { it.isSamePageAs(section) }
    val line = slide.displayLineIndex.coerceAtLeast(0)
    // The entry of `sectionsForFit` this slide is, when that list is built one entry per slide.
    val entryIndex = when {
        !isLineMode -> position
        lookAheadEnabled && position >= 0 -> allLyricSections.take(position).sumOf { it.lines.size } + line
        else -> -1
    }
    val entry = sectionsForFit.getOrNull(entryIndex)
    return when {
        entry != null -> SlideFit(listOf(entry), isLast = entryIndex == sectionsForFit.lastIndex)
        !isLineMode -> SlideFit(listOf(section), isLast = false)
        else -> SlideFit(
            listOf(
                section.copy(
                    lines = listOfNotNull(section.lines.getOrNull(line)),
                    translations = section.translations.map {
                        it.copy(lines = listOfNotNull(it.lines.getOrNull(line)))
                    },
                ),
            ),
            isLast = position >= 0 && position == allLyricSections.lastIndex && line == section.lines.lastIndex,
        )
    }
}

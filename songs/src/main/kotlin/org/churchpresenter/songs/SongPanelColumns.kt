package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.LyricSection

/**
 * The language slots the lyrics panel gives a column, for a song cut into [sections]: the [shown]
 * ones (the operator's choice, in slot order) that carry words somewhere in the song. A language
 * the song names but never fills takes no room; one missing from a single section keeps its column
 * there, blank, so the others stay put from section to section. Never empty.
 */
internal fun panelColumnSlots(sections: List<LyricSection>, shown: List<Int>): List<Int> =
    shown.filter { slot -> sections.any { it.allLanguageLines().getOrNull(slot).orEmpty().isNotEmpty() } }
        .ifEmpty { shown.take(1).ifEmpty { listOf(0) } }

/**
 * The lines [section] shows in each of [columns]: blank for a language missing from it. When every
 * column would be blank, the section's own lines in one column instead, so no section is empty.
 */
internal fun panelColumnLines(section: LyricSection, columns: List<Int>): List<List<String>> {
    val all = section.allLanguageLines()
    val lines = columns.map { all.getOrNull(it).orEmpty() }
    return if (lines.all { it.isEmpty() }) listOf(section.lines) else lines
}

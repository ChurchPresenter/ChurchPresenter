package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.withBackgroundsOf
import org.churchpresenter.core.models.songs.withTextOverridesOf
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.presentableSongLines
import org.churchpresenter.songchords.SecondVoice

fun SongsViewModel.setLineIndex(index: Int) {
    selectedLineIndexState.value = index
}

fun SongsViewModel.getSelectedSong(): LyricSection? {
    val items = filteredSongItemsState.value
    val idx = selectedSongIndexState.value
    if (items.isEmpty() || idx < 0 || idx >= items.size) return null
    val song = items[idx]
    return LyricSection(
        title = song.title,
        songNumber = song.number.toIntOrNull() ?: 0,
        // The whole-song slide is the lyrics verbatim, headers and all — but a directive is
        // configuration rather than words, and putting one on screen is never right.
        lines = presentableSongLines(song.lyrics),
        translations = song.presentableTranslations(),
        type = Constants.SECTION_TYPE_SONG
    ).withBackgroundsOf(song).withTextOverridesOf(song)
}

fun SongsViewModel.getSelectedLyricSection(): LyricSection? {
    val sections = getLyricSections()
    if (selectedSectionIndexState.value < 0 || selectedSectionIndexState.value >= sections.size) {
        return getSelectedSong()
    }
    return sections[selectedSectionIndexState.value]
}

fun SongsViewModel.navigatePreviousSong(): Boolean {
    if (selectedSongIndexState.value > 0) {
        selectedSongIndexState.value--
        selectedSectionIndexState.value = -1
        return true
    }
    return false
}

fun SongsViewModel.navigateNextSong(): Boolean {
    if (selectedSongIndexState.value < filteredSongItemsState.value.size - 1) {
        selectedSongIndexState.value++
        selectedSectionIndexState.value = -1
        return true
    }
    return false
}

/**
 * Where the arrows stop among [sections]: a section with words that the song does not leave out.
 * A song that leaves out every one is walked as written, rather than not at all.
 */
private fun stopsIn(sections: List<LyricSection>): (LyricSection) -> Boolean {
    val singsAny = sections.any { it.lines.isNotEmpty() && !it.skipped }
    return { it.lines.isNotEmpty() && (!it.skipped || !singsAny) }
}

fun SongsViewModel.navigatePreviousSection(): Boolean {
    selectedLineIndexState.value = 0
    val sections = getLyricSections()
    val stopsAt = stopsIn(sections)
    // Start from the end of what actually exists: the selection can outlive the list it indexes
    // (see [clampSectionSelection]), and this walk only guards its lower bound.
    var prevIdx = (selectedSectionIndexState.value - 1).coerceAtMost(sections.lastIndex)
    while (prevIdx >= 0) {
        if (stopsAt(sections[prevIdx])) {
            selectedSectionIndexState.value = prevIdx
            return true
        }
        prevIdx--
    }
    return false
}

fun SongsViewModel.navigateNextSection(): Boolean {
    selectedLineIndexState.value = 0
    val sections = getLyricSections()
    val stopsAt = stopsIn(sections)
    var nextIdx = selectedSectionIndexState.value + 1
    while (nextIdx < sections.size) {
        if (stopsAt(sections[nextIdx])) {
            selectedSectionIndexState.value = nextIdx
            return true
        }
        nextIdx++
    }
    return false
}

fun SongsViewModel.navigateNextLine(): Boolean {
    val section = getSelectedLyricSection() ?: return false
    val displayLines = section.lines
    // A line and the voice-2 line answering it show together, so one press steps over both.
    val shown = SecondVoice.lineGroupAt(displayLines, selectedLineIndexState.value)
    if (shown.last < displayLines.size - 1) {
        selectedLineIndexState.value = shown.last + 1
        return true
    }
    // Move to next section with content lines (skip empty sections)
    val sections = getLyricSections()
    val stopsAt = stopsIn(sections)
    var nextIdx = selectedSectionIndexState.value + 1
    while (nextIdx < sections.size) {
        if (stopsAt(sections[nextIdx])) {
            selectedSectionIndexState.value = nextIdx
            selectedLineIndexState.value = 0
            return true
        }
        nextIdx++
    }
    return false
}

fun SongsViewModel.navigatePreviousLine(): Boolean {
    val lines = getSelectedLyricSection()?.lines.orEmpty()
    val shownStart = SecondVoice.lineGroupAt(lines, selectedLineIndexState.value).first
    if (shownStart > 0) {
        selectedLineIndexState.value = SecondVoice.lineGroupAt(lines, shownStart - 1).first
        return true
    }
    // Move to previous section with content lines (skip empty sections)
    val sections = getLyricSections()
    val stopsAt = stopsIn(sections)
    // Clamped for the same reason as [navigatePreviousSection] — a stale index would index past
    // the end on the very first step.
    var prevIdx = (selectedSectionIndexState.value - 1).coerceAtMost(sections.lastIndex)
    while (prevIdx >= 0) {
        if (stopsAt(sections[prevIdx])) {
            selectedSectionIndexState.value = prevIdx
            val prevLines = sections[prevIdx].lines
            selectedLineIndexState.value =
                SecondVoice.lineGroupAt(prevLines, prevLines.lastIndex).first.coerceAtLeast(0)
            return true
        }
        prevIdx--
    }
    return false
}

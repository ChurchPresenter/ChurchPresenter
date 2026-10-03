package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.withBackgroundsOf
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.songBackgroundDirectiveOf

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
        lines = song.lyrics.filterNot { songBackgroundDirectiveOf(it) != null },
        translations = song.presentableTranslations(),
        type = Constants.SECTION_TYPE_SONG
    ).withBackgroundsOf(song)
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

fun SongsViewModel.navigatePreviousSection(): Boolean {
    selectedLineIndexState.value = 0
    val sections = getLyricSections()
    // Start from the end of what actually exists: the selection can outlive the list it indexes
    // (see [clampSectionSelection]), and this walk only guards its lower bound.
    var prevIdx = (selectedSectionIndexState.value - 1).coerceAtMost(sections.lastIndex)
    while (prevIdx >= 0) {
        if (sections[prevIdx].lines.isNotEmpty()) {
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
    var nextIdx = selectedSectionIndexState.value + 1
    while (nextIdx < sections.size) {
        if (sections[nextIdx].lines.isNotEmpty()) {
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
    val currentLine = selectedLineIndexState.value
    if (currentLine < displayLines.size - 1) {
        selectedLineIndexState.value = currentLine + 1
        return true
    }
    // Move to next section with content lines (skip empty sections)
    val sections = getLyricSections()
    var nextIdx = selectedSectionIndexState.value + 1
    while (nextIdx < sections.size) {
        if (sections[nextIdx].lines.isNotEmpty()) {
            selectedSectionIndexState.value = nextIdx
            selectedLineIndexState.value = 0
            return true
        }
        nextIdx++
    }
    return false
}

fun SongsViewModel.navigatePreviousLine(): Boolean {
    val currentLine = selectedLineIndexState.value
    if (currentLine > 0) {
        selectedLineIndexState.value = currentLine - 1
        return true
    }
    // Move to previous section with content lines (skip empty sections)
    val sections = getLyricSections()
    // Clamped for the same reason as [navigatePreviousSection] — a stale index would index past
    // the end on the very first step.
    var prevIdx = (selectedSectionIndexState.value - 1).coerceAtMost(sections.lastIndex)
    while (prevIdx >= 0) {
        if (sections[prevIdx].lines.isNotEmpty()) {
            selectedSectionIndexState.value = prevIdx
            selectedLineIndexState.value = (sections[prevIdx].lines.size - 1).coerceAtLeast(0)
            return true
        }
        prevIdx--
    }
    return false
}

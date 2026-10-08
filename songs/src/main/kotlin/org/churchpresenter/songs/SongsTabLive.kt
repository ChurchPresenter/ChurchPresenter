package org.churchpresenter.songs

import org.churchpresenter.sharedui.models.Presenting

// Going live from a key, and the keyboard between the search box and what is live.

/**
 * Go Live from a key: the selected song, from its first section when none is chosen -- what a
 * double-click on its row does. Nothing when that song is already live, so a second press never
 * restarts it. False when there is no song to send.
 */
internal fun SongsTabController.goLiveSelected(): Boolean {
    val song = selectedSong ?: return false
    if (isPresenting && song.songId == live.songId) return true
    if (viewModel.selectedSectionIndex.value < 0 && !live.titleSlideSelected) {
        viewModel.selectSong(viewModel.selectedSongIndex.value)
    }
    sendToPresenter(goLive = true)
    onPresenting(Presenting.LYRICS)
    return true
}

/** Selects the live song again, on the section and line that are up. Changes nothing on screen. */
internal fun SongsTabController.backToLive() {
    val songId = live.songId ?: return
    if (selectedSong?.songId != songId) viewModel.selectSongById(songId)
    viewModel.selectSection(live.sectionIndex)
    viewModel.setLineIndex(live.lineIndex)
    browsePausedHint = false
}

/** Moves the keyboard into the search box with its query selected. */
internal fun SongsTabController.focusSearch() = searchFocus.focusAndSelectAll()

/** The search ⇄ live key: out of search back to what is live (or the list), or into search. */
internal fun SongsTabController.switchSearchLive() {
    if (searchFieldFocused) {
        if (isPresenting) backToLive()
        tabFocusRequester.requestFocus()
    } else {
        focusSearch()
    }
}

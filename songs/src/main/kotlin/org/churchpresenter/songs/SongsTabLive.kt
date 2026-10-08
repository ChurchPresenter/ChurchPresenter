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

/**
 * Selects the live song again, on the section and line that are up. Changes nothing on screen.
 * When the search had to be cleared to show that song, it is held for the way back into search.
 */
internal fun SongsTabController.backToLive() {
    val songId = live.songId ?: return
    if (selectedSong?.songId != songId) {
        val search = ParkedSearch(viewModel.searchQuery.value, viewModel.selectedSongbook.value)
        viewModel.selectSongById(songId)
        if (search != ParkedSearch(viewModel.searchQuery.value, viewModel.selectedSongbook.value)) {
            parkedSearch = search
        }
    }
    viewModel.selectSection(live.sectionIndex)
    viewModel.setLineIndex(live.lineIndex)
    browsePausedHint = false
}

/**
 * Moves the keyboard into the search box with its query selected, putting back the search that
 * going back to live cleared. Only the highlight moves; nothing reaches the output.
 */
internal fun SongsTabController.focusSearch() {
    parkedSearch?.let {
        parkedSearch = null
        viewModel.updateSelectedSongbook(it.songbook)
        viewModel.updateSearchQuery(it.query)
    }
    searchFocus.focusAndSelectAll()
}

/** The operator's own search: a search held for the way back is dropped. */
internal fun SongsTabController.searchFor(query: String) {
    parkedSearch = null
    viewModel.updateSearchQuery(query)
}

/** The operator's own songbook: a search held for the way back is dropped. */
internal fun SongsTabController.pickSongbook(songbook: String) {
    parkedSearch = null
    viewModel.updateSelectedSongbook(songbook)
}

/** The search ⇄ live key: out of search back to what is live (or the list), or into search. */
internal fun SongsTabController.switchSearchLive() {
    if (searchFieldFocused) {
        if (isPresenting) backToLive()
        tabFocusRequester.requestFocus()
    } else {
        focusSearch()
    }
}

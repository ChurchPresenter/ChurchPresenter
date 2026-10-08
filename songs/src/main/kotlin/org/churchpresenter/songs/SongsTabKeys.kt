package org.churchpresenter.songs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.churchpresenter.settings.operatorSongSettings
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.ShortcutMap

/**
 * The tab's keys. False for any key it leaves alone.
 *
 * The search ⇄ live key works from anywhere in the tab. While the caret is in the search box the
 * arrow keys browse the song list and Go Live sends the highlighted song -- nothing else there
 * touches the output. On the tab itself Go Live sends the selected song (only from the tab root:
 * another one-line field lets Enter through), and the step keys follow.
 *
 * In line mode left/right step through lines, the title slide included; otherwise they move between
 * songs, but only while nothing is live. Up/down step through sections, and past either end onto
 * the next or previous song when nothing is live. While a song is live and a different one is
 * selected, the keys that would push its sections are held back and say so.
 */
internal fun SongsTabController.handleKey(keyEvent: KeyEvent, shortcuts: ShortcutMap): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false
    if (shortcuts.matches(ShortcutAction.SWITCH_SEARCH_LIVE, keyEvent)) {
        switchSearchLive()
        return true
    }
    if (searchFieldFocused) return handleSearchKey(keyEvent, shortcuts)
    if (shortcuts.matches(ShortcutAction.GO_LIVE, keyEvent)) return tabRootFocused && goLiveSelected()
    val isLineMode = isSongLineMode(appSettings.operatorSongSettings())
    val previous = shortcuts.matches(ShortcutAction.SONGS_PREVIOUS, keyEvent)
    val next = shortcuts.matches(ShortcutAction.SONGS_NEXT, keyEvent)
    val previousSection = shortcuts.matches(ShortcutAction.SONGS_PREVIOUS_SECTION, keyEvent)
    val nextSection = shortcuts.matches(ShortcutAction.SONGS_NEXT_SECTION, keyEvent)
    val isStepKey = previous || next || previousSection || nextSection
    if (!isStepKey) return false
    // Section keys always push, and so do the line keys in line mode.
    val pushes = previousSection || nextSection || isLineMode
    when {
        pushes && browsingAwayFromLive -> browsePausedHint = true
        previous -> previousKey(isLineMode)
        next -> nextKey(isLineMode)
        previousSection -> previousSectionKey()
        else -> nextSectionKey()
    }
    return true
}

/** The caret is in the search box: up/down browse the list, Go Live sends the highlighted song. */
private fun SongsTabController.handleSearchKey(keyEvent: KeyEvent, shortcuts: ShortcutMap): Boolean =
    when {
        shortcuts.matches(ShortcutAction.GO_LIVE, keyEvent) -> {
            if (goLiveSelected()) tabFocusRequester.requestFocus()
            true
        }
        keyEvent.key == Key.DirectionUp -> { browseSongs(forward = false); true }
        keyEvent.key == Key.DirectionDown -> { browseSongs(forward = true); true }
        else -> false
    }

/** Moves the highlight through the list without sending anything, live or not. */
private fun SongsTabController.browseSongs(forward: Boolean) {
    live.titleSlideSelected = false
    if (forward) viewModel.navigateNextSong() else viewModel.navigatePreviousSong()
}

private fun SongsTabController.previousKey(isLineMode: Boolean) {
    if (isLineMode) {
        if (!live.titleSlideSelected && !viewModel.navigatePreviousLine()) backToTitleSlide()
        sendToPresenter(goLive = isPresenting)
    } else if (!isPresenting) {
        live.titleSlideSelected = false
        viewModel.navigatePreviousSong()
    }
}

private fun SongsTabController.nextKey(isLineMode: Boolean) {
    if (isLineMode) {
        if (!leaveTitleSlide()) viewModel.navigateNextLine()
        sendToPresenter(goLive = isPresenting)
    } else if (!isPresenting) {
        live.titleSlideSelected = false
        viewModel.navigateNextSong()
    }
}

private fun SongsTabController.previousSectionKey() {
    val atStart = live.titleSlideSelected ||
        (!viewModel.navigatePreviousSection() && !backToTitleSlide())
    if (atStart && !isPresenting) {
        live.titleSlideSelected = false
        viewModel.navigatePreviousSong()
    }
    sendToPresenter(goLive = isPresenting)
}

private fun SongsTabController.nextSectionKey() {
    if (!leaveTitleSlide() && !viewModel.navigateNextSection() && !isPresenting) {
        viewModel.navigateNextSong()
    }
    sendToPresenter(goLive = isPresenting)
}

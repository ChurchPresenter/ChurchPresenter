package org.churchpresenter.songs

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import org.churchpresenter.settings.operatorSongSettings
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.ShortcutMap

/**
 * The navigation keys, while the caret is not in the search field. False for any other key.
 *
 * In line mode left/right step through lines, the title slide included; otherwise they move between
 * songs, but only while nothing is live. Up/down step through sections, and past either end onto
 * the next or previous song when nothing is live.
 */
internal fun SongsTabController.handleKey(keyEvent: KeyEvent, shortcuts: ShortcutMap): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown || searchFieldFocused) return false
    val isLineMode = isSongLineMode(appSettings.operatorSongSettings())
    when {
        shortcuts.matches(ShortcutAction.SONGS_PREVIOUS, keyEvent) -> previousKey(isLineMode)
        shortcuts.matches(ShortcutAction.SONGS_NEXT, keyEvent) -> nextKey(isLineMode)
        shortcuts.matches(ShortcutAction.SONGS_PREVIOUS_SECTION, keyEvent) -> previousSectionKey()
        shortcuts.matches(ShortcutAction.SONGS_NEXT_SECTION, keyEvent) -> nextSectionKey()
        else -> return false
    }
    return true
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

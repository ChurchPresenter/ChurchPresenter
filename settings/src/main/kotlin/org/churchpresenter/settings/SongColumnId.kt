package org.churchpresenter.settings

/**
 * The song list's column ids. They are persisted — the saved column order, widths and
 * [AppSettings.songHiddenCols] are keyed by them — so a value here must never change.
 */
object SongColumnId {
    const val NUMBER = "number"
    const val TITLE = "title"
    const val SONGBOOK = "songbook"
    const val TUNE = "tune"
    const val PLAY_COUNT = "play_count"
    const val AUTHOR = "author"
    const val COMPOSER = "composer"
    const val ADD_TO_SCHEDULE = "add_to_schedule"
    const val FAVORITES = "favorites"
}

package org.churchpresenter.calendar.ui

import org.churchpresenter.core.models.songs.SongItem

/**
 * One song asked to be edited, and everything an editor needs to do it.
 *
 * The same shape as `:songlibrary`'s `SongEditorRequest`, deliberately: inside ChurchPresenter both
 * are filled by the app's own Edit Song dialog, so a song is edited in one place whether it was
 * reached from the Songs tab, the Song Library Manager, or a run of show being planned here.
 *
 * [songbooks] and [allSongs] are what the library already holds in memory, so an editor that
 * offers a list of books or checks for a clashing number does not re-scan the folder.
 */
data class SongEditRequest(
    val song: SongItem,
    val songbooks: List<String>,
    val allSongs: List<SongItem>,
    val onSave: (SongItem) -> Unit,
    val onDismiss: () -> Unit,
)

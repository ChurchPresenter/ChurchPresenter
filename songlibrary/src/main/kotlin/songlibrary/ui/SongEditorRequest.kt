package songlibrary.ui

import core.models.songs.SongItem

/**
 * One song asked to be edited, and everything an editor needs to do it.
 *
 * [allSongs] and [songbooks] are what the library holds right now, so a host that checks for a
 * clashing number or offers a list of books does not have to scan the folder a second time.
 */
data class SongEditorRequest(
    val song: SongItem,
    val songbooks: List<String>,
    val allSongs: List<SongItem>,
    val onSave: (SongItem) -> Unit,
    val onDismiss: () -> Unit,
)

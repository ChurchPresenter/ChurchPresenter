package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.churchpresenter.core.models.songs.SongItem

/**
 * Which of the tab's dialogs is being asked for, and about which song.
 *
 * Held together because all three regions of the tab raise them — the song list, the lyrics panel
 * and the dialogs themselves — so as long as they were loose `var`s no region could be lifted into
 * a file of its own without a fistful of write-back callbacks.
 */
internal class SongDialogRequests {
    var editing by mutableStateOf<SongItem?>(null)
        private set
    var deleting by mutableStateOf<SongItem?>(null)
        private set
    var creatingNew by mutableStateOf(false)
        private set

    fun edit(song: SongItem?) { editing = song }
    fun delete(song: SongItem?) { deleting = song }
    fun createNew() { creatingNew = true }

    fun closeEditor() { editing = null }
    fun closeDelete() { deleting = null }
    fun closeNew() { creatingNew = false }
}

/**
 * What is on the presenter right now, as the Songs tab tracks it.
 *
 * [songId] is the song's stable id rather than a list index, so it survives the filtered list being
 * rebuilt by a search — see AGENT.md's "Song Edit While Live" note.
 */
internal class SongLiveState {
    var songId by mutableStateOf<String?>(null)
    var sectionIndex by mutableStateOf(0)
    var lineIndex by mutableStateOf(0)

    /** Whether the title slide, rather than a lyric section, is the selection in the panel. */
    var titleSlideSelected by mutableStateOf(false)

    fun live(songId: String?, sectionIndex: Int, lineIndex: Int) {
        this.songId = songId
        this.sectionIndex = sectionIndex
        this.lineIndex = lineIndex
    }
}

@Composable
internal fun rememberSongDialogRequests(): SongDialogRequests = remember { SongDialogRequests() }

@Composable
internal fun rememberSongLiveState(): SongLiveState = remember { SongLiveState() }

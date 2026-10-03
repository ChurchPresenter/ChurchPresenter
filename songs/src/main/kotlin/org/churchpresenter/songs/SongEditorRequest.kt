package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning

/**
 * What the tab asks of the song editor: the song to edit, or a template for a new one, and where
 * its changes go. The app draws it as `EditSongDialog`.
 */
data class SongEditorRequest(
    val isVisible: Boolean,
    val song: SongItem?,
    val songbooks: List<String>,
    val existingSongs: List<SongItem>,
    val isNewSong: Boolean = false,
    val tuning: SongTuning = SongTuning(),
    val chordsVisible: Boolean = true,
    val typicalSeconds: Int? = null,
    val onChordsVisibleChange: (Boolean) -> Unit,
    val onApplyBackgroundToSongbook: ((songbook: String, background: SongBackground,
                                       lowerThirdBackground: SongBackground) -> Unit)? = null,
    val languageNames: List<String>,
    val onLanguageNamesChange: (List<String>) -> Unit,
    val onDismiss: () -> Unit,
    val onSave: (SongItem, SongTuning) -> Unit,
)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_element_author
import churchpresenter.composeapp.generated.resources.song_element_ccli
import churchpresenter.composeapp.generated.resources.song_element_composer
import churchpresenter.composeapp.generated.resources.song_element_look_ahead
import churchpresenter.composeapp.generated.resources.song_element_lyrics
import churchpresenter.composeapp.generated.resources.song_element_next_section
import churchpresenter.composeapp.generated.resources.song_element_number
import churchpresenter.composeapp.generated.resources.song_element_tempo
import churchpresenter.composeapp.generated.resources.song_element_title
import org.jetbrains.compose.resources.stringResource

/** What the element's tab reads, in the Song settings tab. */
@Composable
internal fun SongStyleElement.label(): String = stringResource(
    when (this) {
        SongStyleElement.NUMBER -> Res.string.song_element_number
        SongStyleElement.TITLE -> Res.string.song_element_title
        SongStyleElement.LYRICS -> Res.string.song_element_lyrics
        SongStyleElement.LOOK_AHEAD -> Res.string.song_element_look_ahead
        SongStyleElement.NEXT_SECTION -> Res.string.song_element_next_section
        SongStyleElement.AUTHOR -> Res.string.song_element_author
        SongStyleElement.COMPOSER -> Res.string.song_element_composer
        SongStyleElement.CCLI -> Res.string.song_element_ccli
        SongStyleElement.TEMPO -> Res.string.song_element_tempo
    },
)

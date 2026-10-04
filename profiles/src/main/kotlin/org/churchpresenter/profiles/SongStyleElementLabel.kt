package org.churchpresenter.profiles

import org.churchpresenter.presenter.SongStyleElement
import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.author
import org.churchpresenter.strings.generated.resources.song_element_ccli
import org.churchpresenter.strings.generated.resources.composer
import org.churchpresenter.strings.generated.resources.song_element_look_ahead
import org.churchpresenter.strings.generated.resources.song_element_lyrics
import org.churchpresenter.strings.generated.resources.shortcut_description_next_section
import org.churchpresenter.strings.generated.resources.song_element_number
import org.churchpresenter.strings.generated.resources.song_element_section_label
import org.churchpresenter.strings.generated.resources.song_element_tempo
import org.churchpresenter.strings.generated.resources.title
import org.jetbrains.compose.resources.stringResource

/** What the element's tab reads, in the Song settings tab. */
@Composable
internal fun SongStyleElement.label(): String = stringResource(
    when (this) {
        // Both read "Number": each is chosen from its own slide's chip strip, so the slide the
        // operator is looking at already says which number they are editing.
        SongStyleElement.NUMBER, SongStyleElement.TITLE_SLIDE_NUMBER -> Res.string.song_element_number
        SongStyleElement.TITLE -> Res.string.title
        SongStyleElement.LYRICS -> Res.string.song_element_lyrics
        SongStyleElement.LOOK_AHEAD -> Res.string.song_element_look_ahead
        SongStyleElement.NEXT_SECTION -> Res.string.shortcut_description_next_section
        SongStyleElement.SECTION_LABEL -> Res.string.song_element_section_label
        SongStyleElement.AUTHOR -> Res.string.author
        SongStyleElement.COMPOSER -> Res.string.composer
        SongStyleElement.CCLI -> Res.string.song_element_ccli
        SongStyleElement.TEMPO -> Res.string.song_element_tempo
    },
)

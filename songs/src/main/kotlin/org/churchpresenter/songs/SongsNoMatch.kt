package org.churchpresenter.songs

import org.churchpresenter.strings.generated.resources.songs_no_selection_body
import org.churchpresenter.strings.generated.resources.songs_no_selection_title
import org.churchpresenter.icons.generated.resources.ic_storage_songs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_search
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.composables.EmptyStateButton
import org.churchpresenter.sharedui.composables.EmptyStateContent
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.songs_no_match_body
import org.churchpresenter.strings.generated.resources.songs_no_match_body_lyrics
import org.churchpresenter.strings.generated.resources.songs_no_match_clear
import org.churchpresenter.strings.generated.resources.songs_no_match_title
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/** A search with no songs: what was searched for, a hint, and the way back to the whole list. */
@Composable
internal fun SongListScope.NoSongsMatch() {
    // Contains already reads the lyrics; the other matches read only numbers and titles.
    val body = if (filterType == Constants.CONTAINS) {
        Res.string.songs_no_match_body
    } else {
        Res.string.songs_no_match_body_lyrics
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyStateContent(
            icon = IconRes.drawable.ic_search,
            neutral = true,
            compact = true,
            title = stringResource(Res.string.songs_no_match_title, searchQuery.trim()),
            body = stringResource(body),
        ) {
            EmptyStateButton(stringResource(Res.string.songs_no_match_clear), IconRes.drawable.ic_close) {
                onSearchQueryChange("")
            }
        }
    }
}

/** The lyrics side with no song picked: say so, rather than that the song has no lyrics. */
@Composable
internal fun NoSongSelected() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyStateContent(
            icon = IconRes.drawable.ic_storage_songs,
            neutral = true,
            compact = true,
            title = stringResource(Res.string.songs_no_selection_title),
            body = stringResource(Res.string.songs_no_selection_body),
        )
    }
}

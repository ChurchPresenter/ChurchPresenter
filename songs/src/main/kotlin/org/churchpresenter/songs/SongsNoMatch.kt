package org.churchpresenter.songs

import org.churchpresenter.strings.generated.resources.songs_loaded_body
import org.churchpresenter.strings.generated.resources.songs_loaded_title
import org.churchpresenter.strings.generated.resources.songs_no_match_search_lyrics
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import org.churchpresenter.sharedui.composables.EmptyStateSecondaryButton
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

/**
 * A search with no songs: what was searched for, and the next step -- searching the lyrics when the
 * match type reads only numbers and titles, and clearing the search.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongListScope.NoSongsMatch() {
    // Contains already reads the lyrics; the other match types read only numbers and titles.
    val searchedLyrics = filterType == Constants.CONTAINS
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyStateContent(
            icon = IconRes.drawable.ic_search,
            neutral = true,
            compact = true,
            title = stringResource(Res.string.songs_no_match_title, searchQuery.trim()),
            body = stringResource(
                if (searchedLyrics) Res.string.songs_no_match_body else Res.string.songs_no_match_body_lyrics,
            ),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searchedLyrics) {
                    EmptyStateButton(stringResource(Res.string.songs_no_match_clear), IconRes.drawable.ic_close) {
                        onSearchQueryChange("")
                    }
                } else {
                    EmptyStateButton(
                        stringResource(Res.string.songs_no_match_search_lyrics),
                        IconRes.drawable.ic_search,
                    ) { onFilterTypeChange(Constants.CONTAINS) }
                    EmptyStateSecondaryButton(
                        stringResource(Res.string.songs_no_match_clear),
                        IconRes.drawable.ic_close,
                    ) { onSearchQueryChange("") }
                }
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

/** A song folder chosen from the empty library has loaded: how many songs, and what to do next. */
@Composable
internal fun SongsLoaded(count: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyStateContent(
            icon = IconRes.drawable.ic_storage_songs,
            neutral = false,
            success = true,
            title = pluralStringResource(Res.plurals.songs_loaded_title, count, count),
            body = stringResource(Res.string.songs_loaded_body),
        )
    }
}

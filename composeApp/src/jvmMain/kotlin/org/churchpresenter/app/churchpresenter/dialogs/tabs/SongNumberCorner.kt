package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bottom_left
import churchpresenter.composeapp.generated.resources.bottom_right
import churchpresenter.composeapp.generated.resources.song_number_corner_off
import churchpresenter.composeapp.generated.resources.top_left
import churchpresenter.composeapp.generated.resources.top_right
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * Which corner the song number is pinned to on [lowerThird]'s output, or [Constants.NONE] for none.
 *
 * A corner is an alternative to the row the number otherwise shares with the title, not an extra
 * setting layered on it: while one is chosen the presenter draws the number over the slide and the
 * position and ordering controls have nothing left to say about it.
 */
internal fun SongSettings.numberCorner(lowerThird: Boolean): String =
    if (lowerThird) songNumberLowerThirdCorner else songNumberCorner

/** The inverse of [numberCorner]. */
internal fun SongSettings.withNumberCorner(lowerThird: Boolean, value: String): SongSettings =
    if (lowerThird) copy(songNumberLowerThirdCorner = value) else copy(songNumberCorner = value)

/** Off, then the four corners — the dropdown's options, keyed by what is stored. */
@Composable
internal fun songNumberCornerOptions(): List<Pair<String, String>> = listOf(
    Constants.NONE to stringResource(Res.string.song_number_corner_off),
    Constants.TOP_LEFT to stringResource(Res.string.top_left),
    Constants.TOP_RIGHT to stringResource(Res.string.top_right),
    Constants.BOTTOM_LEFT to stringResource(Res.string.bottom_left),
    Constants.BOTTOM_RIGHT to stringResource(Res.string.bottom_right),
)

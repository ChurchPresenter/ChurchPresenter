package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The measured Duration column: read only, since the number comes from the services a song was
// sung in and there is nothing to correct by hand. Its own file so SongTable stays under the
// function-count limit.

@Composable
internal fun DurationCell(seconds: Int?) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.width(DURATION_WIDTH).height(LibraryMetrics.rowHeight).padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            seconds?.let(::durationText).orEmpty(),
            style = LibraryType.body,
            color = scheme.onSurface,
            maxLines = 1,
        )
    }
}

/** `4:32` -- minutes and seconds, the way a song's length is said. */
internal fun durationText(seconds: Int): String =
    "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60

/** The measured Duration column, and what it adds to the table's width when shown. */
internal val DURATION_WIDTH = 96.dp
internal fun durationColumnWidth(shown: Boolean): Dp = if (shown) DURATION_WIDTH + 1.dp else 0.dp

package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.titleSlideBackground
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.withTitleSlideBackground
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_title_slide_background
import org.churchpresenter.strings.generated.resources.profile_title_slide_background_sub
import org.churchpresenter.strings.generated.resources.title_slide_background_none
import org.churchpresenter.strings.generated.resources.title_slide_background_song
import org.jetbrains.compose.resources.stringResource

/**
 * What this output's title slide is drawn over (#1016): the song's background, as every other slide,
 * or none -- the number and credits alone, over whatever is behind the output.
 */
@Composable
internal fun TitleSlideBackgroundRow(
    song: SongSettings,
    lowerThird: Boolean,
    updateSong: ((SongSettings) -> SongSettings) -> Unit,
) {
    SettingsRow(
        Res.string.profile_title_slide_background,
        sub = stringResource(Res.string.profile_title_slide_background_sub),
        paths = listOf(titleSlideBackgroundPath(lowerThird)),
        modifier = Modifier.testTag(TITLE_SLIDE_BACKGROUND_TAG),
    ) {
        RowSegmented(
            options = listOf(
                RowOption(
                    Constants.TITLE_SLIDE_BACKGROUND_SONG,
                    stringResource(Res.string.title_slide_background_song),
                ),
                RowOption(
                    Constants.TITLE_SLIDE_BACKGROUND_NONE,
                    stringResource(Res.string.title_slide_background_none),
                ),
            ),
            selected = song.titleSlideBackground(lowerThird),
            onSelect = { v -> updateSong { it.withTitleSlideBackground(lowerThird, v) } },
        )
    }
}

/** Where the title slide's background choice is stored for this output. */
internal fun titleSlideBackgroundPath(lowerThird: Boolean): String =
    "songSettings.layoutExtras." + if (lowerThird) "titleSlideBackgroundLowerThird" else "titleSlideBackground"

internal const val TITLE_SLIDE_BACKGROUND_TAG = "song_titleSlideBackground"

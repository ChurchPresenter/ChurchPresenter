package org.churchpresenter.profiles

import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.presenter.SongStyleTarget
import androidx.compose.runtime.Composable
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile

/**
 * What the element shows and where the number goes: the song tab's own options for it, which have no
 * simpler row of their own -- the slide chunk, the languages on screen, when the number and title
 * appear, the number's corner, the title slide's own placements.
 */
@Composable
internal fun SongElementRow(
    draft: AppSettings,
    profile: OutputProfile,
    element: SongStyleElement,
    target: SongStyleTarget,
    titleSlideView: Boolean,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
) {
    SettingsWideRow {
        SongElementOptions(
            settings = draft,
            onSettingsChange = onSettingsChange,
            element = element,
            target = target,
            titleSlideView = titleSlideView,
            outputMode = profile.songMode,
            onOutputModeChange = { onProfileChange(profile.copy(songMode = it)) },
        )
    }
}

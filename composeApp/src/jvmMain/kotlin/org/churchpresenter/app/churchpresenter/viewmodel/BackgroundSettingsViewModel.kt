package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.BackgroundConfig

class BackgroundSettingsViewModel {

    // ── Actions ──────────────────────────────────────────────────────

    fun updateDefaultColor(
        color: String,
        onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
    ) {
        onSettingsChange { s ->
            s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundColor = color))
        }
    }

    fun updateBibleBackground(
        config: BackgroundConfig,
        onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
    ) {
        onSettingsChange { s ->
            s.copy(backgroundSettings = s.backgroundSettings.copy(bibleBackground = config))
        }
    }

    fun updateBibleLowerThirdBackground(
        config: BackgroundConfig,
        onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
    ) {
        onSettingsChange { s ->
            s.copy(backgroundSettings = s.backgroundSettings.copy(bibleLowerThirdBackground = config))
        }
    }

    fun updateSongBackground(
        config: BackgroundConfig,
        onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
    ) {
        onSettingsChange { s ->
            s.copy(backgroundSettings = s.backgroundSettings.copy(songBackground = config))
        }
    }

    fun updateSongLowerThirdBackground(
        config: BackgroundConfig,
        onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
    ) {
        onSettingsChange { s ->
            s.copy(backgroundSettings = s.backgroundSettings.copy(songLowerThirdBackground = config))
        }
    }
}

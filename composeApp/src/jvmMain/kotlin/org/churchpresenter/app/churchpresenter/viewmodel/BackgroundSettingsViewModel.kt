package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundScope
import org.churchpresenter.app.churchpresenter.dialogs.tabs.withConfigFor
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig

class BackgroundSettingsViewModel {

    // ── Actions ──────────────────────────────────────────────────────

    /**
     * Writes [config] to whichever surface is open.
     *
     * One method rather than one per surface: the Background tab edits all six through the same
     * editor now, and [withConfigFor] is what knows that two of them keep their settings in flat
     * fields instead of a [BackgroundConfig].
     */
    internal fun updateBackground(
        scope: BackgroundScope,
        config: BackgroundConfig,
        onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
    ) {
        onSettingsChange { s ->
            s.copy(backgroundSettings = s.backgroundSettings.withConfigFor(scope, config))
        }
    }
}

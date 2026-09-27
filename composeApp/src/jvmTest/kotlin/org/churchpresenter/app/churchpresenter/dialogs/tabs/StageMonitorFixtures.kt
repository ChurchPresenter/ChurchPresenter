package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.StageMonitorSettings

/** Settings whose stage-monitor section is [change] applied to the defaults. */
internal fun stageSettings(change: StageMonitorSettings.() -> StageMonitorSettings): AppSettings =
    AppSettings().let { it.copy(stageMonitorSettings = it.stageMonitorSettings.change()) }

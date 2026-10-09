package org.churchpresenter.app.churchpresenter.benchmark

import org.churchpresenter.app.churchpresenter.CURRENT_EULA_VERSION
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SettingsManager

/**
 * The settings a startup benchmark launch opens with, written into the `user.home` it runs under:
 * the licence accepted and the setup wizard done, so every launch reaches the main window the way
 * an operator's does, instead of measuring the first-run screens. `startupBenchmark` runs this once
 * before the launches.
 */
fun main() {
    SettingsManager().saveSettings(AppSettings(eulaAcceptedVersion = CURRENT_EULA_VERSION, setupWizardShown = true))
}

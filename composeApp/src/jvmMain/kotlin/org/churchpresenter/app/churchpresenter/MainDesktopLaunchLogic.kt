package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings

/*
 * The main screen's decisions at launch: whether to invite crash feedback, whether the Companion
 * Surface tab has anything to show, and the configuration tags crash reports are filtered by. Pure.
 */

internal fun showCrashFeedbackAtLaunch(didCrashLastRun: Boolean, settings: AppSettings): Boolean =
    didCrashLastRun && settings.analyticsReportingEnabled

internal fun hasCompanionTabConnections(connections: List<CompanionSatelliteSettings>): Boolean =
    connections.any { it.showInTab && it.host.isNotBlank() }

internal fun startupConfigTags(settings: AppSettings, vlcAvailable: Boolean, screenCount: Int): Map<String, String> =
    mapOf(
        "vlc.available" to vlcAvailable.toString(),
        "screen.count" to screenCount.toString(),
        "output.count" to settings.projectionSettings.screenAssignments.size.toString(),
        "atem.enabled" to settings.atemSettings.host.isNotBlank().toString(),
        "obs.enabled" to settings.obsSettings.enabled.toString(),
        "server.enabled" to settings.serverSettings.enabled.toString()
    )

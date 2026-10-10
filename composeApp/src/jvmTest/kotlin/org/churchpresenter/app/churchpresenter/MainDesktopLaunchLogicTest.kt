package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainDesktopLaunchLogicTest {

    @Test
    fun `crash feedback is invited only after a crash and with analytics on`() {
        val on = AppSettings(analyticsReportingEnabled = true)
        val off = AppSettings(analyticsReportingEnabled = false)
        assertTrue(showCrashFeedbackAtLaunch(didCrashLastRun = true, settings = on))
        assertFalse(showCrashFeedbackAtLaunch(didCrashLastRun = true, settings = off))
        assertFalse(showCrashFeedbackAtLaunch(didCrashLastRun = false, settings = on))
        assertFalse(showCrashFeedbackAtLaunch(didCrashLastRun = false, settings = off))
    }

    @Test
    fun `the Companion Surface tab needs a connection shown in it with a host`() {
        val shown = CompanionSatelliteSettings(showInTab = true, host = "10.0.0.5")
        assertTrue(hasCompanionTabConnections(listOf(shown)))
        assertTrue(hasCompanionTabConnections(listOf(shown.copy(showInTab = false), shown)))
        assertFalse(hasCompanionTabConnections(listOf(shown.copy(showInTab = false))))
        assertFalse(hasCompanionTabConnections(listOf(shown.copy(host = "  "))))
        assertFalse(hasCompanionTabConnections(emptyList()))
    }

    @Test
    fun `the startup tags describe the setup`() {
        val plain = AppSettings()
        val tags = startupConfigTags(plain, vlcAvailable = true, screenCount = 2)
        assertEquals("true", tags["vlc.available"])
        assertEquals("2", tags["screen.count"])
        assertEquals(plain.projectionSettings.screenAssignments.size.toString(), tags["output.count"])
        assertEquals("false", tags["atem.enabled"])
        assertEquals(plain.obsSettings.enabled.toString(), tags["obs.enabled"])
        assertEquals(plain.serverSettings.enabled.toString(), tags["server.enabled"])

        val withAtem = plain.copy(atemSettings = plain.atemSettings.copy(host = "192.168.1.240"))
        assertEquals("true", startupConfigTags(withAtem, vlcAvailable = false, screenCount = 0)["atem.enabled"])
    }
}

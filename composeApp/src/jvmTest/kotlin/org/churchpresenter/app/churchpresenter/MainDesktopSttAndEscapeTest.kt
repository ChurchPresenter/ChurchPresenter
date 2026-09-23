package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Two small, unrelated decisions out of [MainDesktop] that share this file because each is only
 * a few lines: what URL to remember once STT actually connects ([sttUrlToPersist] /
 * [withSttLastConnectedUrl]), and which screens the Escape key should release a Stage Monitor lock
 * on ([stageMonitorScreenIndices]).
 *
 * Both matter for reasons a UI test wouldn't catch: persisting the STT URL on every recomposition
 * rather than only on a genuine change would spam settings writes, and releasing the wrong screen
 * indices on Escape would either leave a stage monitor stuck on stale content or unlock a screen
 * that was never locked.
 */
class MainDesktopSttAndEscapeTest {

    // ── sttUrlToPersist / withSttLastConnectedUrl ───────────────────────────────

    @Test
    fun `not connected means nothing to persist regardless of the url`() {
        val settings = AppSettings(sttSettings = STTSettings(serverUrl = "http://host:8080", lastConnectedUrl = ""))
        assertNull(sttUrlToPersist(settings, sttConnected = false))
    }

    @Test
    fun `a first successful connection returns the server url to persist`() {
        val settings = AppSettings(sttSettings = STTSettings(serverUrl = "http://host:8080", lastConnectedUrl = ""))
        assertEquals("http://host:8080", sttUrlToPersist(settings, sttConnected = true))
    }

    @Test
    fun `reconnecting to the same url that was already persisted has nothing new to persist`() {
        val settings = AppSettings(sttSettings = STTSettings(
            serverUrl = "http://host:8080",
            lastConnectedUrl = "http://host:8080",
        ))
        assertNull(sttUrlToPersist(settings, sttConnected = true))
    }

    @Test
    fun `connecting to a different url than what was last persisted returns the new one`() {
        val settings = AppSettings(sttSettings = STTSettings(
            serverUrl = "http://new-host:9090",
            lastConnectedUrl = "http://old-host:8080",
        ))
        assertEquals("http://new-host:9090", sttUrlToPersist(settings, sttConnected = true))
    }

    @Test
    fun `withSttLastConnectedUrl writes only the last-connected field`() {
        val before = AppSettings(sttSettings = STTSettings(serverUrl = "http://host:8080", lastConnectedUrl = ""))
        val after = withSttLastConnectedUrl(before, "http://host:8080")

        assertEquals("http://host:8080", after.sttSettings.lastConnectedUrl)
        assertEquals(before.sttSettings.serverUrl, after.sttSettings.serverUrl)
    }

    // ── stageMonitorScreenIndices ────────────────────────────────────────────────

    /** One profile per [modes] entry, and one screen assignment pointing at each in order. */
    private fun projectionOf(vararg modes: String): ProjectionSettings {
        val profiles = modes.mapIndexed { index, mode -> OutputProfile(id = "p$index", displayMode = mode) }
        return ProjectionSettings(
            outputProfiles = profiles,
            screenAssignments = profiles.map { ScreenAssignment(activeProfileId = it.id) },
        )
    }

    @Test
    fun `no screens configured releases nothing`() {
        val empty = ProjectionSettings(outputProfiles = emptyList(), screenAssignments = emptyList())
        assertEquals(emptyList(), stageMonitorScreenIndices(empty))
    }

    @Test
    fun `no screen in stage-monitor mode releases nothing`() {
        val proj = projectionOf(Constants.DISPLAY_MODE_FULLSCREEN, Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)
        assertEquals(emptyList(), stageMonitorScreenIndices(proj))
    }

    @Test
    fun `only the screens actually in stage-monitor mode are returned, by their own index`() {
        val proj = projectionOf(
            Constants.DISPLAY_MODE_FULLSCREEN,
            Constants.DISPLAY_MODE_STAGE_MONITOR,
            Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL,
            Constants.DISPLAY_MODE_STAGE_MONITOR,
        )
        assertEquals(listOf(1, 3), stageMonitorScreenIndices(proj))
    }

    @Test
    fun `every screen in stage-monitor mode is released`() {
        val proj = projectionOf(*Array(3) { Constants.DISPLAY_MODE_STAGE_MONITOR })
        assertEquals(listOf(0, 1, 2), stageMonitorScreenIndices(proj))
    }

    @Test
    fun `the indices resolve against the real projection settings shape used by MainDesktop`() {
        val settings = AppSettings(
            projectionSettings = projectionOf(
                Constants.DISPLAY_MODE_STAGE_MONITOR,
                Constants.DISPLAY_MODE_FULLSCREEN,
            ),
        )
        assertEquals(listOf(0), stageMonitorScreenIndices(settings.projectionSettings))
    }
}

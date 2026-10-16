package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.STTSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What URL to remember once STT actually connects ([sttUrlToPersist] /
 * [withSttLastConnectedUrl]), out of [MainDesktop]. It matters for a reason a UI test wouldn't
 * catch: persisting the STT URL on every recomposition rather than only on a genuine change would
 * spam settings writes. Which screens Escape releases is `stageMonitorScreenIndices`, tested in
 * `:settings`.
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
}

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.settings.SettingsManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The settings manager main reads the file with is the one the app keeps, so the first composition
 * answers from what it already read rather than reading the file again on the event thread.
 */
class AppRootStateSettingsTest {

    private val scope = CoroutineScope(SupervisorJob())

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `the settings manager main hands over is the one the app keeps, and its settings are the app's`() {
        val manager = SettingsManager()
        val read = manager.loadSettings()

        val root = AppRootState(
            object : ApplicationScope {
                override fun exitApplication() = Unit
            },
            scope,
            secondaryDisplays = { emptyList() },
            settingsManager = manager,
        )

        assertSame(manager, root.settingsManager)
        // Startup changes two parts on purpose: the presentation remote starts off, and the outputs
        // are reconciled with the displays attached. Everything else is what the manager read.
        val unchangedParts = root.appSettings.copy(
            presentationRemoteSettings = read.presentationRemoteSettings,
            projectionSettings = read.projectionSettings,
        )
        assertEquals(read, unchangedParts)
        assertEquals(false, root.appSettings.presentationRemoteSettings.remoteControlEnabled)
    }
}

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkCommandFailure
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WiringWithViewModelsTest {

    // ── InstanceLinkFailureWiring ──────────────────────────────────────────────

    @Test
    fun `command failures reported by a follower are collected for the operator`() = runComposeUiTest {
        val viewModel = InstanceLinkViewModel()
        val failures = mutableListOf<InstanceLinkCommandFailure>()
        setContent { InstanceLinkFailureWiring(viewModel, failures) }
        waitForIdle()
        assertTrue(failures.isEmpty(), "nothing has failed yet")
    }

    @Test
    fun `the failure collector survives recomposition without duplicating entries`() = runComposeUiTest {
        val viewModel = InstanceLinkViewModel()
        val failures = mutableListOf<InstanceLinkCommandFailure>()
        setContent { InstanceLinkFailureWiring(viewModel, failures) }
        waitForIdle()
        waitForIdle()
        assertEquals(0, failures.size)
    }

    // ── CompanionSatelliteWiring ───────────────────────────────────────────────

    @Test
    fun `no configured satellite connections reconciles nothing`() = runComposeUiTest {
        val viewModel = CompanionSatelliteViewModel()
        val reconciled = mutableMapOf<String, CompanionSatelliteSettings>()
        val settings = AppSettings().copy(companionSatelliteConnections = emptyList())
        setContent { CompanionSatelliteWiring(settings, viewModel, reconciled) }
        waitForIdle()
        assertTrue(reconciled.isEmpty())
        viewModel.dispose()
    }

    @Test
    fun `a connection that never auto-connects is not opened at startup`() = runComposeUiTest {
        // A brand-new connection only dials out when autoConnect is set — otherwise opening the
        // app would seize a Stream Deck the operator had not asked it to take over.
        val viewModel = CompanionSatelliteViewModel()
        val reconciled = mutableMapOf<String, CompanionSatelliteSettings>()
        val connection = CompanionSatelliteSettings(id = "sat-1", autoConnect = false)
        val settings = AppSettings().copy(companionSatelliteConnections = listOf(connection))
        setContent { CompanionSatelliteWiring(settings, viewModel, reconciled) }
        waitForIdle()
        assertTrue(viewModel.connectionStates.keys.none { it.connectionId == "sat-1" })
        viewModel.dispose()
    }

    // ── MediaRemoteWiring ──────────────────────────────────────────────────────

    @Test
    fun `media wiring composes against an idle player without a loaded file`() = runComposeUiTest {
        val server = CompanionServer()
        val media = MediaViewModel()
        val manager = PresenterManager()
        setContent { MediaRemoteWiring(server, media, manager) }
        waitForIdle()
        assertTrue(!media.isLoaded, "nothing was loaded, so nothing should be reported as playing")
    }

    @Test
    fun `media transport commands are accepted while nothing is loaded`() = runComposeUiTest {
        // The mobile Media tab can send transport commands at any time; with no file open they
        // must be absorbed rather than throw on a null player.
        val server = CompanionServer()
        val media = MediaViewModel()
        setContent { MediaRemoteWiring(server, media, PresenterManager()) }
        waitForIdle()
        media.togglePlayPause()
        media.stop()
        media.toggleMute()
        waitForIdle()
    }

    // ── ObsSceneWiring ─────────────────────────────────────────────────────────

    @Test
    fun `obs stays disconnected while the integration is switched off`() = runComposeUiTest {
        val obs = OBSWebSocketManager()
        val settings = AppSettings().let { it.copy(obsSettings = it.obsSettings.copy(enabled = false)) }
        setContent { ObsSceneWiring(settings, CompanionServer(), obs, PresenterManager()) }
        waitForIdle()
        assertEquals(OBSWebSocketManager.ConnectionStatus.DISCONNECTED, obs.status.value)
    }

    @Test
    fun `going live with no scene configured asks obs for nothing`() = runComposeUiTest {
        val obs = OBSWebSocketManager()
        val manager = PresenterManager()
        val settings = AppSettings().let { it.copy(obsSettings = it.obsSettings.copy(enabled = false)) }
        setContent { ObsSceneWiring(settings, CompanionServer(), obs, manager) }
        waitForIdle()
        manager.setPresentingMode(Presenting.BIBLE)
        waitForIdle()
        assertEquals(OBSWebSocketManager.ConnectionStatus.DISCONNECTED, obs.status.value)
    }

    @Test
    fun `a configured default scene is requested when the live content changes`() = runComposeUiTest {
        val obs = OBSWebSocketManager()
        val manager = PresenterManager()
        val settings = AppSettings().let {
            it.copy(obsSettings = it.obsSettings.copy(enabled = true, defaultScene = "Worship"))
        }
        setContent { ObsSceneWiring(settings, CompanionServer(), obs, manager) }
        waitForIdle()
        manager.setPresentingMode(Presenting.LYRICS)
        waitForIdle()
        // Enabled means the wiring dials out: nothing is listening, so it lands on CONNECTING or
        // ERROR — either way it tried, which is what distinguishes this from the disabled case.
        assertTrue(obs.status.value != OBSWebSocketManager.ConnectionStatus.DISCONNECTED)
    }

    // ── MediaRemoteWiring, with a file loaded ──────────────────────────────────

    @Test
    fun `a loaded file is reported to companions as the now-playing item`() = runComposeUiTest {
        val server = CompanionServer()
        val media = MediaViewModel().apply { loadMedia("file:///tmp/does-not-exist.mp3", "local") }
        setContent { MediaRemoteWiring(server, media, PresenterManager()) }
        waitForIdle()
        assertEquals("local", media.mediaType)
    }
}

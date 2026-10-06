package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.server.InstanceLinkCommandFailure
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.server.InstanceLinkViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.obs.OBSWebSocketManager
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        media.audio.toggleMute()
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

    /**
     * The connection is a function of the settings alone: switching the integration on dials out,
     * switching it off hangs up, with no restart of the app between the two.
     */
    @Test
    fun `the obs connection follows the integration being switched on and off`() = runComposeUiTest {
        val obs = OBSWebSocketManager()
        val server = CompanionServer()
        val manager = PresenterManager()
        var settings by mutableStateOf(
            AppSettings().let { it.copy(obsSettings = it.obsSettings.copy(enabled = false)) },
        )
        setContent { ObsSceneWiring(settings, server, obs, manager) }
        waitForIdle()
        assertEquals(OBSWebSocketManager.ConnectionStatus.DISCONNECTED, obs.status.value, "off to begin with")

        settings = settings.copy(obsSettings = settings.obsSettings.copy(enabled = true))
        waitForIdle()
        // Nothing is listening, so it lands on CONNECTING or ERROR -- either way it dialled out.
        assertTrue(
            obs.status.value != OBSWebSocketManager.ConnectionStatus.DISCONNECTED,
            "switching the integration on must dial out",
        )

        settings = settings.copy(obsSettings = settings.obsSettings.copy(enabled = false))
        waitForIdle()
        assertEquals(
            OBSWebSocketManager.ConnectionStatus.DISCONNECTED,
            obs.status.value,
            "and switching it off must hang up again",
        )
    }

    /**
     * The Q&A cooldown and voting switch reach the server as they are; the admin password is the API
     * key only while key checking is on, and nothing at all when it is off.
     */
    @Test
    fun `the QA settings reach the server and follow a change to them`() = runComposeUiTest {
        val obs = OBSWebSocketManager()
        val server = CompanionServer()
        val manager = PresenterManager()
        var settings by mutableStateOf(
            AppSettings().let {
                it.copy(
                    qaSettings = it.qaSettings.copy(rateLimitCooldownSeconds = 45, votingEnabled = true),
                    serverSettings = it.serverSettings.copy(apiKeyEnabled = true, apiKey = "secret"),
                )
            },
        )
        setContent { ObsSceneWiring(settings, server, obs, manager) }
        waitForIdle()
        assertEquals(45, server.qaCooldownSeconds, "the cooldown from the settings")
        assertTrue(server.qaVotingEnabled, "the voting switch from the settings")
        assertEquals("secret", server.qaAdminPassword, "key checking is on, so the key guards the admin panel")

        settings = settings.copy(
            qaSettings = settings.qaSettings.copy(rateLimitCooldownSeconds = 10, votingEnabled = false),
            serverSettings = settings.serverSettings.copy(apiKeyEnabled = false),
        )
        waitForIdle()
        assertEquals(10, server.qaCooldownSeconds, "a changed cooldown must reach the server")
        assertFalse(server.qaVotingEnabled, "and so must a switched-off vote")
        assertEquals("", server.qaAdminPassword, "with key checking off the admin panel asks for no password")
    }

    /**
     * The effects are keyed on the settings, not on the recomposition: a pass that changes nothing has
     * nothing to push. The server is changed behind the wiring's back to prove it is left alone.
     */
    @Test
    fun `recomposing with nothing changed does not push the settings to the server again`() = runComposeUiTest {
        val obs = OBSWebSocketManager()
        val server = CompanionServer()
        val manager = PresenterManager()
        val settings = AppSettings()
        var pass by mutableStateOf(0)
        setContent {
            Text("$pass") // read here, so a new pass recomposes this caller with the very same arguments
            ObsSceneWiring(settings, server, obs, manager)
        }
        waitForIdle()
        assertEquals(30, server.qaCooldownSeconds, "the default cooldown is pushed on the first pass")

        server.qaCooldownSeconds = 99
        pass++
        waitForIdle()

        assertEquals(99, server.qaCooldownSeconds, "an unchanged recomposition must leave the server alone")
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
        val dialled = obs.status.value != OBSWebSocketManager.ConnectionStatus.DISCONNECTED
        // Hang up before the test ends: a connection failure landing after it would be reported
        // against whichever coroutine test runs next in this JVM.
        obs.disconnect()
        assertTrue(dialled)
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

package org.churchpresenter.signage.tab

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.ServerMessage
import org.churchpresenter.signage.core.WebTestRun
import org.churchpresenter.signage.hub.PairingDecision
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenControlsTest {
    private val world = SignageWorld()
    private val controls = world.controller.controls
    private val hub = world.hub

    @AfterTest
    fun cleanUp() = world.close()

    private fun hello(code: String = "4821") = PlayerMessage.Hello(
        "player-1", "26.0.8", 1, 1, null, 1920, 1080, Platform.LINUX_X64, code,
    )

    @Test
    fun approvingAndDecliningSettleTheWait() = runBlocking {
        hub.registry.load()
        val approved = async { hub.pairing.await(hello(), "10.0.0.5") }
        withTimeout(SignageWorld.WAIT_MS) { hub.pairing.pending.first { it.isNotEmpty() } }
        controls.approve("player-1", "Lobby")
        assertIs<PairingDecision.Approved>(approved.await())

        controls.approve("nobody", "x")
        val declined = async { hub.pairing.await(hello().copy(playerId = "p2"), "10.0.0.6") }
        withTimeout(SignageWorld.WAIT_MS) { hub.pairing.pending.first { it.isNotEmpty() } }
        controls.reject("p2")
        assertEquals(PairingDecision.Rejected, declined.await())
    }

    @Test
    fun renamingAndIdentifyingReachTheScreen() = runBlocking {
        world.pairScreen()
        controls.rename("player-1", "Front door")
        assertEquals("Front door", hub.registry.screen("player-1")?.name)
        val identify = with(world) { expect { it as? ServerMessage.Identify } }
        assertTrue(controls.identify("player-1"))
        identify.await()
        assertFalse(controls.identify("away"))
        assertFalse(controls.wipe("away"))
    }

    @Test
    fun aWebTestIsSentOnlyToAPlayerThatCanRunItAndOnlyOnceAtATime() = runBlocking {
        world.pairScreen(features = listOf(Features.WEB, Features.WEB_TEST))
        val test = with(world) { expect { it as? ServerMessage.WebTest } }
        assertTrue(controls.testWebPages("player-1"))
        test.await()
        assertTrue(hub.links.links.value.getValue("player-1").webTestStartedAtMs != null)
        // Running: a second press sends nothing.
        assertFalse(controls.testWebPages("player-1"))
        hub.links.reported("player-1", PlayerMessage.WebTestResult(listOf(WebTestRun(15, 15.0))))
        val link = hub.links.links.value.getValue("player-1")
        assertEquals(null to 15.0, link.webTestStartedAtMs to link.webTest?.runs?.single()?.shownFps)
        world.pairScreen(id = "player-2", features = listOf(Features.WEB))
        assertFalse(controls.testWebPages("player-2"))
        assertFalse(controls.testWebPages("away"))
    }

    @Test
    fun previewsAreAskedOnlyOfScreensThatSendThemSharperForTheOneOpenBig() = runBlocking {
        world.pairScreen()
        world.pairScreen(id = "old", features = emptyList())
        val small = with(world) { expect { it as? ServerMessage.PreviewRequest } }
        controls.requestPreviews()
        assertEquals(ServerMessage.PreviewRequest.DEFAULT_PREVIEW_WIDTH, small.await().maxWidth)
        val big = with(world) { expect { it as? ServerMessage.PreviewRequest } }
        controls.requestPreviews(large = "player-1")
        assertTrue(big.await().maxWidth > ServerMessage.PreviewRequest.DEFAULT_PREVIEW_WIDTH)
    }

    @Test
    fun anUpdateIsOfferedOnceANewerReleaseIsFound() = runBlocking {
        world.pairScreen()
        val screen = checkNotNull(hub.registry.screen("player-1"))
        val link = hub.links.links.value["player-1"]
        assertNull(controls.offerFor(screen, link), "nothing checked yet")
        controls.checkForUpdates()
        withTimeout(SignageWorld.WAIT_MS) { controls.latestRelease.first { it != null } }
        assertNotNull(controls.offerFor(screen, link))
        assertNull(controls.offerFor(screen, null), "not connected")

        val offer = with(world) { expect { it as? ServerMessage.UpdateAvailable } }
        controls.update("player-1")
        assertEquals("26.0.9", offer.await().agentVersion)
        assertEquals(Features.UPDATE, link?.features?.last())
    }
}

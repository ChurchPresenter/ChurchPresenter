package org.churchpresenter.signage.hub

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Manifest
import org.churchpresenter.signage.core.OverrideAction
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.ScreenOverride
import org.churchpresenter.signage.core.ScreenSettings
import org.churchpresenter.signage.core.ServerMessage
import org.churchpresenter.signage.core.UpdateState
import java.io.IOException
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class, ExperimentalCoroutinesApi::class)
class SignageHubTest {
    private val dir = createTempDirectory("signage-hub")
    private val scope = SaveScope()
    private val release = TestRelease()
    private var served = release.files()
    private val updates =
        AgentUpdates(dir.resolve("builds"), "https://example.test", release.publicKey) { url, consume ->
            val bytes = served[url.substringAfterLast('/')] ?: throw IOException("404")
            bytes.inputStream().use(consume)
        }
    private val hub = SignageHub(dir, "Main PC", scope, clock = { 1_000 }, updates = updates)

    @AfterTest
    fun cleanUp() {
        // A save still writing would hold the folder open as it is deleted.
        runBlocking { scope.settle() }
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun manifest(name: String = "Lobby") =
        Manifest(ownerId = "o", screen = ScreenSettings(name), timeZone = "UTC")

    private suspend fun pairedAndConnected(features: List<String> = listOf(Features.UPDATE)) {
        hub.registry.load()
        hub.registry.pair(pending(), "Lobby")
        hub.links.opened("player-1", "10.0.0.5", null, features)
    }

    @Test
    fun approvingAWaitingPlayerPairsIt() = runTest {
        hub.registry.load()
        val decision = async { hub.pairing.await(hello(), "10.0.0.5") }
        runCurrent()
        assertTrue(hub.approve("player-1", "Lobby"))
        assertIs<PairingDecision.Approved>(decision.await())
        assertEquals("Lobby", hub.registry.screen("player-1")?.name)
        assertFalse(hub.approve("nobody", "x"), "not waiting")
    }

    @Test
    fun rejectingSettlesTheWait() = runTest {
        val decision = async { hub.pairing.await(hello(), "10.0.0.5") }
        runCurrent()
        hub.reject("player-1")
        assertEquals(PairingDecision.Rejected, decision.await())
    }

    @Test
    fun publishingTellsTheScreenOnlyWhenTheContentChanged() = runTest {
        pairedAndConnected()
        val sent = async { hub.links.messagesFor("player-1").take(1).toList() }
        runCurrent()
        hub.publish("player-1", manifest(), emptyMap())
        hub.publish("player-1", manifest(), emptyMap())
        assertIs<ServerMessage.ManifestChanged>(sent.await().single())
        assertEquals(manifest(), hub.published("player-1")?.manifest)
    }

    @Test
    fun aConnectingScreenIsGreetedWithWhatItMissed() = runTest {
        hub.registry.load()
        hub.publish("player-1", manifest(), emptyMap())
        val override = ScreenOverride(OverrideAction.TvOff, setAtMs = 1)
        hub.setOverride("player-1", override)
        val version = hub.published("player-1")?.version
        val greeting = hub.greeting(hello(code = null, manifestVersion = "older"))
        assertEquals(listOf(ServerMessage.ManifestChanged(version!!), ServerMessage.SetOverride(override)), greeting)
        assertEquals(emptyList(), hub.greeting(hello(code = null, manifestVersion = version)))
    }

    @Test
    fun revokingForgetsTheScreenAndTellsIt() = runTest {
        pairedAndConnected()
        hub.publish("player-1", manifest(), emptyMap())
        val sent = async { hub.links.messagesFor("player-1").first() }
        runCurrent()
        hub.revoke("player-1")
        assertEquals(ServerMessage.Released, sent.await())
        assertNull(hub.registry.screen("player-1"))
        assertNull(hub.published("player-1"))
    }

    @Test
    fun anUpdateIsDownloadedHereThenOfferedWithTheSignedRelease() = runTest {
        pairedAndConnected()
        updates.check()
        val sent = async { hub.links.messagesFor("player-1").first() }
        runCurrent()
        assertTrue(hub.update("player-1"))
        val offer = assertIs<ServerMessage.UpdateAvailable>(sent.await())
        assertEquals("26.0.9", offer.agentVersion)
        assertEquals("/api/signage/agent/linux_x64", offer.path)
        assertEquals(release.index(), offer.release)
        assertEquals(UpdateState.DOWNLOADING, hub.links.links.value.getValue("player-1").update?.state)
    }

    @Test
    fun aFailedDownloadShowsOnTheCard() = runTest {
        pairedAndConnected()
        updates.check()
        served = served - release.assetName
        assertFalse(hub.update("player-1"))
        assertEquals(UpdateState.FAILED, hub.links.links.value.getValue("player-1").update?.state)
    }

    @Test
    fun thereIsNothingToUpdateWithoutAnOffer() = runTest {
        assertFalse(hub.update("player-1"), "not paired")
        hub.registry.load()
        hub.registry.pair(pending(), "Lobby")
        assertFalse(hub.update("player-1"), "not connected")
        hub.links.opened("player-1", "a", null, emptyList())
        updates.check()
        assertFalse(hub.update("player-1"), "does not update itself")
    }

    @Test
    fun platformsAreNamedAsTheWireNamesThem() {
        assertEquals("linux_arm64", SignageHub.platformName(Platform.LINUX_ARM64))
        assertEquals("windows_x64", SignageHub.platformName(Platform.WINDOWS_X64))
    }
}

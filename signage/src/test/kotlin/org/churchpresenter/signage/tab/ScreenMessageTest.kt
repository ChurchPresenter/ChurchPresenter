package org.churchpresenter.signage.tab

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.ScreenMessage
import org.churchpresenter.signage.hub.PairedScreen
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A message for the screens: kept with the tab's settings, and sent only where it can show. */
@OptIn(ExperimentalPathApi::class)
class ScreenMessageTest {
    private val lobby = PairedScreen("lobby", "Lobby", "h", Platform.LINUX_X64, "26.0.70", 1920, 1080, 0)
    private val kids = lobby.copy(id = "kids", name = "Kids wing")
    private val dir = createTempDirectory("signage-message")

    @AfterTest
    fun cleanUp() = dir.deleteRecursively()

    private fun sent(screen: PairedScreen, message: SignageMessage?, features: List<String>, nowMs: Long = 0) =
        buildManifest(
            screen = screen,
            ownerId = "owner",
            settings = SignageSettings(message = message),
            playlists = emptyList(),
            timeZone = "UTC",
            features = features,
            nowMs = nowMs,
        ).first.screen.message

    @Test
    fun `a message goes only to a player that can show it, on the screens it names, until it runs out`() {
        val all = SignageMessage(" Parking lot B is full ", fullScreen = true, untilMs = 1_000)
        assertEquals(ScreenMessage("Parking lot B is full", true, 1_000), sent(lobby, all, listOf(Features.MESSAGE)))
        assertNull(sent(lobby, all, emptyList()), "an older player is not sent one")
        assertNull(sent(lobby, all, listOf(Features.MESSAGE), nowMs = 1_000), "one that has run out is not sent")
        val lobbyOnly = all.copy(screenIds = listOf("lobby"))
        assertEquals("Parking lot B is full", sent(lobby, lobbyOnly, listOf(Features.MESSAGE))?.text)
        assertNull(sent(kids, lobbyOnly, listOf(Features.MESSAGE)))
        assertNull(sent(lobby, null, listOf(Features.MESSAGE)))
    }

    @Test
    fun `a band can keep touches from a touch page, and a full-screen one sends no setting of its own`() {
        val band = SignageMessage("Doors open", blockTouch = true)
        assertEquals(ScreenMessage("Doors open", blockTouch = true), sent(lobby, band, listOf(Features.MESSAGE)))
        val full = band.copy(fullScreen = true)
        val onWire = sent(lobby, full, listOf(Features.MESSAGE))
        assertEquals(ScreenMessage("Doors open", fullScreen = true), onWire)
        assertTrue(onWire!!.blocksTouch)
    }

    @Test
    fun `it shows while it has words and time`() {
        assertTrue(SignageMessage("Hi").showsAt(Long.MAX_VALUE))
        assertFalse(SignageMessage("  ").showsAt(0))
        assertFalse(SignageMessage("Hi", untilMs = 5).showsAt(5))
        assertTrue(SignageMessage("Hi", screenIds = listOf("a")).reaches("a"))
        assertFalse(SignageMessage("Hi", screenIds = listOf("a")).reaches("b"))
    }

    @Test
    fun `it is saved with the tab's settings`() {
        val store = SignageStore(dir)
        val message = SignageMessage("Doors open", untilMs = 9, screenIds = listOf("lobby"), blockTouch = true)
        val settings = SignageSettings(message = message)
        store.saveSettings(settings)
        assertEquals(settings, store.loadSettings())
    }

    @Test
    fun `setting one sends it to a screen that can show it, and clearing takes it away`() = runBlocking {
        SignageWorld(folders = emptyList()).use { world ->
            world.controller.start()
            withTimeout(SignageWorld.WAIT_MS) { world.controller.state.first { it.ready } }
            world.pairScreen(features = listOf(Features.MESSAGE), load = false)
            world.controller.setMessage(SignageMessage("Welcome"))
            suspend fun shown() = withTimeout(SignageWorld.WAIT_MS) {
                var manifest = world.hub.published("player-1")?.manifest
                while (manifest == null) {
                    yield()
                    manifest = world.hub.published("player-1")?.manifest
                }
                manifest.screen.message
            }
            withTimeout(SignageWorld.WAIT_MS) { while (shown()?.text != "Welcome") yield() }
            assertEquals(listOf(Features.MESSAGE), world.controller.features.of("player-1"))
            world.controller.setMessage(null)
            withTimeout(SignageWorld.WAIT_MS) { while (shown() != null) yield() }
            assertNull(world.controller.state.value.settings.message)
        }
    }
}

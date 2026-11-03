package org.churchpresenter.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.churchpresenter.signage.core.Nudge
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.SignageJson
import org.churchpresenter.signage.core.nudgeOwner
import org.churchpresenter.signage.hub.PendingScreen
import org.churchpresenter.signage.hub.SignageHub
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** When ChurchPresenter knocks on its away screens, where, and with what. */
class SignageNudgerTest {
    private val dir = Files.createTempDirectory("cp-signage-nudge")
    private val scope = CoroutineScope(Job())
    private val hub = SignageHub(dir, "Main PC", scope).also { runBlocking { it.registry.load() } }
    private var now = 1_000_000L
    private var on = true
    private val sent = mutableListOf<Pair<String, String>>()
    private val nudger = SignageNudger(
        wanted = { SignageNudger.Wanted(hub, PORT).takeIf { on } },
        send = { host, bytes -> sent += host to String(bytes) },
        clock = { now },
    )

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        dir.toFile().deleteRecursively()
    }

    private fun screen(id: String, address: String?) {
        hub.registry.pair(PendingScreen(id, "1234", Platform.LINUX_X64, "26.0.8", 1920, 1080, "10.0.0.1"), id)
        if (address != null) hub.registry.refreshFrom(hello(id), address)
    }

    @Test
    fun `an away screen is knocked on at its last address, with this computer's owner and port`() {
        screen("lobby", "192.168.2.221")
        assertEquals(1, nudger.tick())
        val (host, text) = sent.single()
        assertEquals("192.168.2.221", host)
        val nudge = SignageJson.decodeFromString(Nudge.serializer(), text)
        assertEquals(Nudge(nudgeOwner(hub.registry.ownerId), PORT), nudge)
    }

    @Test
    fun `a connected screen, or one never seen, is not knocked on`() {
        screen("lobby", "192.168.2.221")
        screen("hall", null)
        hub.links.opened("lobby", "192.168.2.221", null)
        assertEquals(0, nudger.tick())
        assertEquals(emptyList(), sent)
    }

    @Test
    fun `knocks come every ten seconds at first, then every minute`() {
        screen("lobby", "192.168.2.221")
        assertEquals(1, nudger.tick())
        now += 5_000
        assertEquals(0, nudger.tick())
        now += SignageNudger.FAST_EVERY_MS
        assertEquals(1, nudger.tick())
        now += SignageNudger.FAST_FOR_MS
        assertEquals(1, nudger.tick())
        now += SignageNudger.FAST_EVERY_MS
        assertEquals(0, nudger.tick())
        now += SignageNudger.SLOW_EVERY_MS
        assertEquals(1, nudger.tick())
    }

    @Test
    fun `turning signage off and on starts the quick knocks again`() {
        screen("lobby", "192.168.2.221")
        nudger.tick()
        now += SignageNudger.FAST_FOR_MS + 1
        on = false
        assertEquals(0, nudger.tick())
        on = true
        assertEquals(1, nudger.tick())
        now += SignageNudger.FAST_EVERY_MS
        assertEquals(1, nudger.tick())
    }

    @Test
    fun `a knock that cannot be sent is let go`() {
        screen("lobby", "192.168.2.221")
        val failing = SignageNudger(
            wanted = { SignageNudger.Wanted(hub, PORT) },
            send = { _, _ -> throw IOException("no route") },
            clock = { now },
        )
        assertEquals(1, failing.tick())
    }

    @Test
    fun `refresh starts and stops the knocking with signage`() {
        nudger.refresh()
        nudger.refresh()
        on = false
        nudger.refresh()
    }

    private fun hello(id: String) = PlayerMessage.Hello(
        playerId = id,
        agentVersion = "26.0.8",
        protocolMin = 1,
        protocolMax = 1,
        manifestVersion = null,
        screenWidth = 1920,
        screenHeight = 1080,
        platform = Platform.LINUX_X64,
    )

    private companion object {
        const val PORT = 8765
    }
}

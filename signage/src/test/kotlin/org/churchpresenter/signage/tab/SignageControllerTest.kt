package org.churchpresenter.signage.tab

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.signage.core.ServerMessage
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignageControllerTest {
    private val world = SignageWorld()
    private val controller = world.controller
    private val hub = world.hub

    @AfterTest
    fun cleanUp() = world.close()

    private suspend fun ready() = withTimeout(SignageWorld.WAIT_MS) { controller.state.first { it.ready } }

    private fun CoroutineScope.manifestChanged() = with(world) { expect { it as? ServerMessage.ManifestChanged } }

    @Test
    fun theFolderBecomesEveryScreensPlaylists() = runBlocking {
        world.file("welcome.jpg")
        world.file("Events/flyer.png")
        controller.start()
        controller.start()
        val state = ready()
        assertEquals(listOf("Signage", "Events"), state.playlists.map { it.name })
        val published = manifestChanged()
        world.pairScreen(load = false)
        published.await()
        val manifest = hub.published("player-1")?.manifest
        assertEquals(listOf("folder:", "folder:Events"), manifest?.playlists?.map { it.id })
        assertEquals("Lobby", manifest?.screen?.name)
    }

    @Test
    fun choosingAPlaylistRepublishesWithItPlaying() = runBlocking {
        world.file("welcome.jpg")
        world.file("Events/flyer.png")
        controller.start()
        ready()
        val first = manifestChanged()
        world.pairScreen(load = false)
        first.await()
        val second = manifestChanged()
        controller.play("player-1", "folder:Events")
        second.await()
        assertEquals("folder:Events", hub.published("player-1")?.manifest?.defaultPlaylistId)
        assertEquals("folder:Events", controller.state.value.settings.plan("player-1").playlistId)
    }

    @Test
    fun wipingPublishesNothingFirstThenTellsTheScreen() = runBlocking {
        world.file("welcome.jpg")
        controller.start()
        ready()
        val first = manifestChanged()
        world.pairScreen(load = false)
        first.await()
        val order = mutableListOf<ServerMessage>()
        val wiped = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(SignageWorld.WAIT_MS) {
                hub.links.messagesFor("player-1").first { order += it; it == ServerMessage.Wipe }
            }
        }
        controller.wipe("player-1")
        wiped.await()
        assertTrue(order.first() is ServerMessage.ManifestChanged, "empty manifest first: $order")
        assertEquals(emptyList(), hub.published("player-1")?.manifest?.playlists)
    }

    @Test
    fun picturesTimeAndFolderAreKept() = runBlocking {
        world.file("welcome.jpg")
        controller.start()
        ready()
        val first = manifestChanged()
        world.pairScreen(load = false)
        first.await()
        val second = manifestChanged()
        controller.setPictureSeconds(0)
        second.await()
        assertEquals(1, controller.state.value.settings.pictureSeconds, "at least a second")
        assertEquals(1_000L, hub.published("player-1")?.manifest?.playlists?.single()?.items?.single()?.durationMs)

        val other = world.root.resolve("Other")
        controller.setFolder(other)
        withTimeout(SignageWorld.WAIT_MS) { controller.state.first { it.folder == other } }
        assertEquals(other.toString(), controller.state.value.settings.folder)
    }

    @Test
    fun aFileAddedLaterIsPublishedOnceItStandsStill() = runBlocking {
        controller.start()
        ready()
        val paired = manifestChanged()
        world.pairScreen(load = false)
        paired.await()
        val published = manifestChanged()
        world.file("late.jpg")
        published.await()
        assertEquals(1, hub.published("player-1")?.manifest?.playlists?.single()?.items?.size)
    }

    @Test
    fun removingAScreenForgetsItsPlan() = runBlocking {
        world.file("welcome.jpg")
        controller.start()
        ready()
        world.pairScreen(load = false)
        controller.play("player-1", "folder:")
        withTimeout(SignageWorld.WAIT_MS) { controller.state.first { "player-1" in it.settings.screens } }
        val released = with(world) { expect { it as? ServerMessage.Released } }
        controller.remove("player-1")
        released.await()
        withTimeout(SignageWorld.WAIT_MS) { controller.state.first { "player-1" !in it.settings.screens } }
        assertNull(hub.registry.screen("player-1"))
    }

    @Test
    fun nothingIsPublishedBeforeTheScreensAreRead() = runBlocking {
        world.file("welcome.jpg")
        controller.setPictureSeconds(5)
        withTimeout(SignageWorld.WAIT_MS) { controller.state.first { it.settings.pictureSeconds == 5 } }
        assertNull(hub.published("player-1"))
    }

    @Test
    fun turningAnalyticsOffTellsEveryScreenToStopReporting() = runBlocking {
        world.file("welcome.jpg")
        controller.start()
        ready()
        val first = manifestChanged()
        world.pairScreen(load = false)
        first.await()
        assertEquals(true, hub.published("player-1")?.manifest?.diagnostics)
        val changed = manifestChanged()
        world.diagnostics.value = false
        changed.await()
        assertEquals(false, hub.published("player-1")?.manifest?.diagnostics)
    }

    @Test
    fun aSavedFolderIsUsedAndABlankOneMeansTheDefault() = runBlocking {
        controller.start()
        ready()
        controller.setFolder(Path(""))
        withTimeout(SignageWorld.WAIT_MS) { controller.state.first { it.settings.folder.isEmpty() } }
        assertEquals(world.folder, controller.state.value.folder)
    }
}

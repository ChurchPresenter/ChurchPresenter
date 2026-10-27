package org.churchpresenter.signage.tab

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.ServerMessage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Playlists added in the tab: listed, kept, sent to the screens that can play them, and removed. */
class AddedPlaylistsTest {
    private val world = SignageWorld()
    private val controller = world.controller
    private val hub = world.hub

    @AfterTest
    fun cleanUp() = world.close()

    private suspend fun listed(predicate: (List<PlaylistSummary>) -> Boolean) =
        withTimeout(SignageWorld.WAIT_MS) { controller.state.first { predicate(it.playlists) } }.playlists

    private fun CoroutineScope.manifestChanged() = with(world) { expect { it as? ServerMessage.ManifestChanged } }

    @Test
    fun aLinkAndAPictureAreListedAfterTheFoldersAndKept() = runBlocking {
        world.file("welcome.jpg")
        val picture = world.root.resolve("elsewhere/flyer.png").also {
            it.parent.toFile().mkdirs()
            it.toFile().writeText("png")
        }
        controller.start()
        // Spaces round the name and the address are trimmed.
        controller.added.addLink(NewLink("  Weather ", " $WEATHER ", LinkKind.WEB, 10, smooth = true))
        controller.added.addFile(picture)
        controller.added.addFile(world.root.resolve("notes.txt"))
        val playlists = listed { it.size == 3 }
        assertEquals(listOf("Signage", "Weather", "flyer"), playlists.map { it.name })
        assertEquals(listOf(SourceKind.FOLDER, SourceKind.WEB, SourceKind.PICTURE), playlists.map { it.kind })
        assertEquals(WEATHER, playlists[1].detail)
        val saved = SignageStore(world.root.resolve("store")).loadSettings().added
        assertEquals(LinkPlaylist(playlists[1].id, "Weather", WEATHER, LinkKind.WEB, 10, true), saved[0])
        assertEquals(FilePlaylist(playlists[2].id, "flyer", picture.toString(), MediaKind.IMAGE), saved[1])
    }

    @Test
    fun aLinkReachesAScreenOnceItsPlayerSaysItCanPlayIt() = runBlocking {
        world.file("welcome.jpg")
        world.startAndPairScreen()
        controller.added.addLink(NewLink("Live", "https://example.org/live.m3u8", LinkKind.STREAM))
        listed { it.size == 2 }
        // The paired screen's player lists no streams: it is sent the folder only.
        assertEquals(listOf("folder:"), hub.published("player-1")?.manifest?.playlists?.map { it.id })
        val published = manifestChanged()
        // The same screen reconnects with a player that plays streams.
        hub.links.opened("player-1", "10.0.0.5", null, listOf(Features.WIPE, Features.STREAM))
        published.await()
        assertEquals(2, hub.published("player-1")?.manifest?.playlists?.size)
        // Gone for a moment, it keeps its link: what it could do is remembered.
        hub.links.closed("player-1", hub.links.links.value.getValue("player-1").session)
        controller.setPictureSeconds(12)
        withTimeout(SignageWorld.WAIT_MS) {
            while (controller.state.value.settings.pictureSeconds != 12) yield()
        }
        assertEquals(2, hub.published("player-1")?.manifest?.playlists?.size)
    }

    @Test
    fun editingKeepsThePlaylistAndRemovingSendsItsScreensBackToTheFirst() = runBlocking {
        world.file("welcome.jpg")
        world.startAndPairScreen()
        controller.added.addLink(NewLink("Live", "rtsp://camera", LinkKind.STREAM))
        val id = listed { it.size == 2 }[1].id
        controller.play("player-1", id)
        controller.added.updateLink(id, NewLink("Camera", "rtsp://camera-2", LinkKind.STREAM))
        val edited = listed { list -> list.any { it.name == "Camera" } }[1]
        assertEquals(id to "rtsp://camera-2", edited.id to edited.detail)
        controller.added.remove(id)
        listed { it.size == 1 }
        assertEquals(ScreenPlan(), controller.state.value.settings.plan("player-1"))
    }

    private companion object {
        const val WEATHER = "https://weather.example.org"
    }
}

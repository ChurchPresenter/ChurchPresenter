package org.churchpresenter.signage.tab

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.smugmug.SmugMugSync
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A SmugMug slideshow in the tab: its photos picked into the signage folder and sent as a folder's
 * are, picked again on request, gone with the slideshow, and waiting for a key until there is one.
 */
class SmugMugSlideshowTest {
    private val world = SignageWorld(folders = emptyList())
    private val controller = world.controller
    private val show = SmugMugPlaylist.create("Photos")
        .copy(site = "photos.example.org", photoCount = 3, pictureSeconds = 5)

    @AfterTest
    fun cleanUp() = world.close()

    private suspend fun picked(test: (SignageFolderState) -> Boolean) =
        withTimeout(SignageWorld.WAIT_MS) { controller.state.first(test) }

    private fun SignageFolderState.photosOf(id: String) = playlists.firstOrNull { it.id == id }?.pictures ?: 0

    @Test
    fun `its photos reach the screen as a folder's do`() = runBlocking {
        world.startAndPairScreen()
        controller.slideshows.save(show)
        val state = picked { it.photosOf(show.id) == 3 }
        val row = state.playlists.single { it.id == show.id }
        assertEquals(SourceKind.SMUGMUG to "photos.example.org", row.kind to row.detail)
        assertEquals(3, state.slideshows.getValue(show.id).photos)
        controller.play("player-1", show.id)
        val manifest = withTimeout(SignageWorld.WAIT_MS) {
            var shown = world.hub.published("player-1")?.manifest
            while (shown?.defaultPlaylistId != show.id) {
                yield()
                shown = world.hub.published("player-1")?.manifest
            }
            shown
        }
        val items = manifest.playlists.single { it.id == show.id }.items
        assertEquals(List(3) { MediaKind.IMAGE to 5_000L }, items.map { it.kind to it.durationMs })
    }

    @Test
    fun `new photos come on request, and a removed slideshow's go`() = runBlocking {
        controller.start()
        controller.slideshows.save(show)
        val first = picked { it.photosOf(show.id) == 3 }.slideshows.getValue(show.id).pickedAtMs!!
        val asked = world.smugMug.asked.size
        controller.slideshows.pickNewPhotos(show.id)
        // Done once a newer pick is recorded and nothing is picking.
        picked { (it.slideshows[show.id]?.pickedAtMs ?: 0) > first && show.id !in it.picking }
        assertTrue(world.smugMug.asked.size > asked)
        val photos: Path = world.signage.resolve(show.path)
        assertTrue(photos.exists())
        controller.added.remove(show.id)
        withTimeout(SignageWorld.WAIT_MS) { while (photos.exists()) yield() }
        assertFalse(photos.exists())
        assertTrue(controller.state.value.settings.added.none { it.id == show.id })
    }

    @Test
    fun `a slideshow removed while it picks leaves no photos behind`() = runBlocking {
        val held = CompletableDeferred<Unit>()
        world.smugMug.hold = held
        controller.start()
        controller.slideshows.save(show)
        picked { show.id in it.picking }
        controller.added.remove(show.id)
        withTimeout(SignageWorld.WAIT_MS) {
            controller.state.first { state -> state.settings.added.none { it.id == show.id } }
        }
        held.complete(Unit)
        picked { show.id !in it.picking }
        assertFalse(world.signage.resolve(show.path).exists())
    }

    @Test
    fun `without a key it says so, and a key typed in is tried at once`() = runBlocking {
        world.smugMugKey.value = ""
        controller.start()
        controller.slideshows.save(show)
        val waiting = picked { it.slideshows[show.id]?.error != null }
        assertEquals(SmugMugSync.NO_KEY, waiting.slideshows.getValue(show.id).error)
        world.smugMugKey.value = "KEY"
        assertNull(picked { it.photosOf(show.id) == 3 }.slideshows.getValue(show.id).error)
    }

    @Test
    fun `an edit is saved under its id, and the site is looked up for the dialog`() = runBlocking {
        controller.start()
        controller.slideshows.save(show)
        controller.slideshows.save(show.copy(name = "Lobby photos", excludedAlbumKeys = setOf("a2")))
        val saved = withTimeout(SignageWorld.WAIT_MS) {
            controller.state.first { state -> state.settings.added.any { it.name == "Lobby photos" } }
        }.settings.added.single { it.id == show.id } as SmugMugPlaylist
        assertEquals(setOf("a2"), saved.excludedAlbumKeys)
        val site = controller.slideshows.lookUp("www.photos.example.org", "KEY")
        assertEquals("Grace" to listOf("a1", "a2"), site.nickname to site.albums.map { it.key })
    }

    @Test
    fun `it is saved with the tab, kept apart in the signage folder, and plays in a rotation`() {
        val store = SignageStore(world.signage)
        val rotation = RotationPlaylist("rotation:r", "Lobby", listOf(RotationStep(show.id, null)))
        store.saveSettings(SignageSettings(added = listOf(show, rotation)))
        assertEquals(listOf(show, rotation), store.loadSettings().added)
        assertEquals("SmugMug", Path(show.path).parent.name)
        assertEquals(null, show.copyPath())

        val photo = FolderFile(Path("/s/0001-p1.jpg"), MediaKind.IMAGE, 1, 1)
        val folder = FolderPlaylist(show.id, show.name, listOf(photo), show.path, pictureSeconds = 5)
        val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_X64, "26.0.9", 1920, 1080, 0)
        val manifest = buildManifest(
            screen = screen,
            ownerId = "owner",
            settings = SignageSettings(added = listOf(show, rotation)),
            playlists = listOf(HashedPlaylist(folder, mapOf(photo to "a")), HashedPlaylist(rotation)),
            timeZone = "UTC",
            features = listOf(Features.ROTATION),
        ).first
        assertEquals(listOf("a"), manifest.playlists.single { it.id == "rotation:r" }.items.map { it.sha256 })
    }
}

package org.churchpresenter.signage.tab

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.churchpresenter.signage.core.MediaKind
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Folders and single pictures or videos, copied into the signage folder when they are added: listed,
 * sent, renamed, replaced, removed with their copy, and reported when the copy fails.
 */
@OptIn(ExperimentalPathApi::class)
class PickedFoldersTest {
    private val world = SignageWorld(folders = emptyList())
    private val controller = world.controller
    private val hub = world.hub

    private val signage: Path = world.signage

    @AfterTest
    fun cleanUp() = world.close()

    private suspend fun listed(predicate: (List<PlaylistSummary>) -> Boolean) =
        withTimeout(SignageWorld.WAIT_MS) { controller.state.first { predicate(it.playlists) } }.playlists

    private fun saved() = SignageStore(signage).loadSettings().added

    /** Waits until the screen has been sent playlists of [sizes] items each. */
    private suspend fun awaitSent(vararg sizes: Int) = withTimeout(SignageWorld.WAIT_MS) {
        while (hub.published("player-1")?.manifest?.playlists?.map { it.items.size } != sizes.toList()) yield()
    }

    private suspend fun awaitGone(path: Path) = withTimeout(SignageWorld.WAIT_MS) { while (path.exists()) yield() }

    @Test
    fun aFolderIsCopiedIntoTheSignageFolderAndSent() = runBlocking {
        world.original("Events/flyer.png")
        world.original("Events/intro.mp4")
        world.original("Events/notes.txt")
        world.startAndPairScreen()
        controller.added.addFolder(world.root.resolve("pictures/Events"))
        val folder = listed { it.size == 1 }.single()
        assertEquals(PlaylistSummary(folder.id, "Events", 1, 1, detail = "Folders/Events", seconds = 10), folder)
        assertEquals(
            listOf("flyer.png", "intro.mp4"),
            signage.resolve("Folders/Events").listDirectoryEntries().map { it.name }.sorted(),
        )
        assertEquals(listOf(PickedFolderPlaylist(folder.id, "Events", "Folders/Events")), saved())
        assertEquals(emptyList(), controller.state.value.copying)
        awaitSent(2)
    }

    @Test
    fun aSecondFolderOfTheSameNameGetsItsOwnCopy() = runBlocking {
        world.original("Events/flyer.png")
        controller.start()
        controller.added.addFolder(world.root.resolve("pictures/Events"))
        listed { it.size == 1 }
        controller.added.addFolder(world.root.resolve("pictures/Events"))
        val both = listed { it.size == 2 }
        assertEquals(listOf("Folders/Events", "Folders/Events 2"), both.map { it.detail })
        assertEquals(listOf("Events", "Events"), both.map { it.name })
    }

    @Test
    fun aRenamedFolderKeepsItsCopy() = runBlocking {
        world.original("Events/flyer.png")
        controller.start()
        controller.added.addFolder(world.root.resolve("pictures/Events"))
        val id = listed { it.size == 1 }.single().id
        controller.added.updateFolder(id, " Lobby ", 15)
        val renamed = listed { list -> list.any { it.name == "Lobby" } }.single()
        assertEquals(Triple("Folders/Events", 1, 15), Triple(renamed.detail, renamed.pictures, renamed.seconds))
    }

    @Test
    fun aCopyThatIsGoneIsListedMissingAndNotSent() = runBlocking {
        world.original("Events/flyer.png")
        world.startAndPairScreen()
        controller.added.addFolder(world.root.resolve("pictures/Events"))
        listed { it.size == 1 }
        awaitSent(1)
        signage.resolve("Folders/Events").deleteRecursively()
        val gone = listed { list -> list.any { it.missing } }.single()
        assertEquals(PlaylistSummary(gone.id, "Events", detail = "Folders/Events", missing = true, seconds = 10), gone)
        awaitSent()
    }

    @Test
    fun aFileIsCopiedAndChangingItReplacesTheCopy() = runBlocking {
        val flyer = world.original("flyer.png")
        controller.start()
        controller.added.addFile(flyer)
        val id = listed { it.size == 1 }.single().id
        assertEquals(listOf(FilePlaylist(id, "flyer", "Files/flyer.png", MediaKind.IMAGE)), saved())
        controller.added.updateFile(id, " Intro ", world.original("intro.mp4"))
        listed { list -> list.any { it.name == "Intro" } }
        assertEquals(listOf(FilePlaylist(id, "Intro", "Files/intro.mp4", MediaKind.VIDEO)), saved())
        awaitGone(signage.resolve("Files/flyer.png"))
        assertTrue(flyer.exists(), "the original stays")
        // A name alone keeps the copy; a file that is no picture or video is not taken.
        controller.added.updateFile(id, "Welcome", null)
        listed { list -> list.any { it.name == "Welcome" } }
        controller.added.updateFile(id, "Notes", world.original("notes.txt"))
        listed { list -> list.any { it.name == "Notes" } }
        assertEquals(listOf(FilePlaylist(id, "Notes", "Files/intro.mp4", MediaKind.VIDEO)), saved())
    }

    @Test
    fun removingAPlaylistRemovesItsCopyButNotTheOriginal() = runBlocking {
        val original = world.original("Events/flyer.png")
        controller.start()
        controller.added.addFolder(world.root.resolve("pictures/Events"))
        val id = listed { it.size == 1 }.single().id
        assertEquals(signage.resolve("Folders/Events"), controller.added.locate(id))
        controller.added.remove(id)
        listed { it.isEmpty() }
        awaitGone(signage.resolve("Folders/Events"))
        assertTrue(original.exists())
        assertNull(controller.added.locate(id))
    }

    @Test
    fun aFolderAlreadyInTheSignageFolderIsPlayedWhereItIs() = runBlocking {
        val announcements = signage.resolve("Announcements")
        announcements.resolve("a.jpg").also { it.parent.toFile().mkdirs() }.toFile().writeText("jpg")
        world.startAndPairScreen()
        controller.added.addFolder(announcements)
        val linked = listed { it.size == 1 }.single()
        assertEquals("Announcements" to 1, linked.detail to linked.pictures)
        assertEquals(false, signage.resolve("Folders/Announcements").exists(), "nothing copied")
        awaitSent(1)
        // Whoever drops a picture into it updates the screens.
        announcements.resolve("b.jpg").toFile().writeText("jpg")
        assertEquals(2, listed { list -> list.single().pictures == 2 }.single().pictures)
        awaitSent(2)
        assertEquals(announcements, controller.added.locate(linked.id))
    }

    @Test
    fun aCopyTwoPlaylistsPlayStaysUntilTheLastIsRemoved() = runBlocking {
        world.original("Events/flyer.png")
        controller.start()
        controller.added.addFolder(world.root.resolve("pictures/Events"))
        val first = listed { it.size == 1 }.single().id
        // The copy picked again is played where it is: both play Folders/Events.
        controller.added.addFolder(signage.resolve("Folders/Events"))
        val second = listed { it.size == 2 }[1]
        assertEquals("Folders/Events", second.detail)
        controller.added.remove(first)
        listed { it.size == 1 }
        assertTrue(signage.resolve("Folders/Events/flyer.png").exists())
        controller.added.remove(second.id)
        listed { it.isEmpty() }
        awaitGone(signage.resolve("Folders/Events"))
    }

    @Test
    fun aCopyThatFailsIsReportedAndAddsNothing() = runBlocking {
        controller.start()
        controller.added.addFile(world.root.resolve("pictures/gone.jpg"))
        withTimeout(SignageWorld.WAIT_MS) { controller.state.first { it.copyFailed == "gone.jpg" } }
        assertEquals(emptyList(), controller.state.value.copying)
        assertEquals(emptyList(), saved())
    }

    @Test
    fun anEditBeforeTheFirstReadKeepsWhatWasSaved() = runBlocking {
        SignageWorld(folders = listOf("")).use { seeded ->
            // Not started: the edit is the first thing to touch the settings.
            seeded.controller.added.updateFolder("folder:", "Welcome", 5)
            withTimeout(SignageWorld.WAIT_MS) {
                seeded.controller.state.first { state -> state.settings.added.any { it.name == "Welcome" } }
            }
            val saved = SignageStore(seeded.root.resolve("store")).loadSettings()
            assertEquals(listOf("folder:"), saved.added.map { it.id })
        }
    }
}

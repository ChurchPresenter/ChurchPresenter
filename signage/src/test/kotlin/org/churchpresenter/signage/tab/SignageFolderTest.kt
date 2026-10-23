package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.MediaKind
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class SignageFolderTest {
    private val root = createTempDirectory("signage-folder")

    @AfterTest
    fun cleanUp() = root.deleteRecursively()

    private fun file(relative: String): Path = root.resolve(relative).also {
        it.parent.createDirectories()
        it.writeText(relative)
    }

    @Test
    fun theFolderAndEachFolderInsideItArePlaylists() {
        file("b.mp4")
        file("A.jpg")
        file("notes.txt")
        file(".hidden.jpg")
        file("Events/flyer.png")
        file("Empty/readme.md")
        file(".Hidden/secret.jpg")
        val playlists = SignageFolder.scan(root, "Signage")
        assertEquals(listOf(SignageFolder.ROOT_ID, "folder:Events"), playlists.map { it.id })
        assertEquals(listOf("A.jpg", "b.mp4"), playlists[0].files.map { it.path.fileName.toString() })
        assertEquals(listOf(MediaKind.IMAGE, MediaKind.VIDEO), playlists[0].files.map { it.kind })
        assertEquals(PlaylistSummary("folder:Events", "Events", 1, 0), playlists[1].summary())
        assertEquals(PlaylistSummary(SignageFolder.ROOT_ID, "Signage", 1, 1), playlists[0].summary())
    }

    @Test
    fun aMissingFolderHasNoPlaylists() {
        assertEquals(emptyList(), SignageFolder.scan(root.resolve("gone"), "x"))
        assertNull(SignageFolder.kindOf(Path.of("a.pdf")))
        assertEquals(MediaKind.VIDEO, SignageFolder.kindOf(Path.of("A.MOV")))
    }

    @Test
    fun aFolderPlaylistGoesToTheTrashWhole() {
        file("Events/flyer.png")
        val trashed = mutableListOf<File>()
        assertTrue(SignageFolder.delete(root, "folder:Events") { trashed += it; true })
        assertEquals(listOf(root.resolve("Events").toFile()), trashed)
    }

    @Test
    fun theFoldersOwnPlaylistSendsOnlyItsPicturesAndVideos() {
        file("a.jpg")
        file("notes.txt")
        file("Events/flyer.png")
        val trashed = mutableListOf<String>()
        assertTrue(SignageFolder.delete(root, SignageFolder.ROOT_ID) { trashed += it.name; true })
        assertEquals(listOf("a.jpg"), trashed)
    }

    @Test
    fun withNoTrashOnlyThePicturesAndVideosAreDeleted() {
        val picture = file("Events/flyer.png")
        val notes = file("Events/notes.txt")
        assertTrue(SignageFolder.delete(root, "folder:Events", trash = null))
        assertFalse(picture.exists())
        assertTrue(notes.exists(), "anything that is not a picture or video stays")
    }

    @Test
    fun aTrashThatRefusesIsReported() {
        file("a.jpg")
        assertFalse(SignageFolder.delete(root, SignageFolder.ROOT_ID) { false })
        assertFalse(SignageFolder.delete(root, "folder:Events") { false })
    }

    @Test
    fun aTrashNeedsADisplayADesktopAndTheAction() {
        assertTrue(SignageFolder.trashAvailable(headless = false, desktop = { true }) { true })
        assertFalse(SignageFolder.trashAvailable(headless = true, desktop = { error("not asked") }) { true })
        assertFalse(SignageFolder.trashAvailable(headless = false, desktop = { false }) { error("not asked") })
        assertFalse(SignageFolder.trashAvailable(headless = false, desktop = { true }) { false })
    }

    @Test
    fun withNoTrashThereIsNothingToMoveTo() {
        assertNull(SignageFolder.trashFor(available = false))
    }

    @Test
    fun anIdCannotReachOutsideTheSignageFolder() {
        val outside = root.resolveSibling("${root.fileName}-outside").createDirectories()
        try {
            var asked = false
            assertFalse(SignageFolder.delete(root, "folder:../${outside.fileName}") { asked = true; true })
            assertFalse(SignageFolder.delete(root, "folder:a/b") { asked = true; true })
            assertFalse(asked)
        } finally {
            outside.deleteRecursively()
        }
    }
}

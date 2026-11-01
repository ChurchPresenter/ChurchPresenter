package org.churchpresenter.signage.tab

import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Copying into the signage folder, finding a copy again, and removing only copies. */
@OptIn(ExperimentalPathApi::class)
class SignageMediaTest {
    private val root = createTempDirectory("signage-media")
    private val signage = root.resolve("signage")
    private val pictures = root.resolve("pictures")

    @AfterTest
    fun cleanUp() = root.deleteRecursively()

    private fun file(relative: String): Path = pictures.resolve(relative).also {
        it.parent.createDirectories()
        it.writeText(relative)
    }

    @Test
    fun aFolderIsCopiedWithOnlyItsOwnPicturesAndVideos() {
        file("Easter/a.jpg")
        file("Easter/b.mp4")
        file("Easter/notes.txt")
        file("Easter/Inner/c.jpg")
        assertEquals("Folders/Easter", SignageMedia.copyFolder(pictures.resolve("Easter"), signage))
        val copy = signage.resolve("Folders/Easter")
        assertEquals(listOf("a.jpg", "b.mp4"), copy.listDirectoryEntries().map { it.name }.sorted())
        assertEquals("Easter/a.jpg", copy.resolve("a.jpg").readText())
        // Nothing half-made is left beside it.
        assertEquals(listOf("Easter"), signage.resolve("Folders").listDirectoryEntries().map { it.name })
    }

    @Test
    fun aTakenNameGetsANumberAndAFileKeepsItsExtension() {
        val flyer = file("flyer.jpg")
        assertEquals("Files/flyer.jpg", SignageMedia.copyFile(flyer, signage))
        assertEquals("Files/flyer 2.jpg", SignageMedia.copyFile(flyer, signage))
        assertEquals(signage.resolve("Folders/Lent 2"), SignageMedia.freeName(signage.resolve("Folders"), "Lent 2"))
        signage.resolve("Folders/Lent").createDirectories()
        assertEquals(signage.resolve("Folders/Lent 2"), SignageMedia.freeName(signage.resolve("Folders"), "Lent"))
    }

    @Test
    fun aCopyThatFailsLeavesNothingBehind() {
        assertNull(SignageMedia.copyFile(pictures.resolve("gone.jpg"), signage))
        assertEquals(emptyList(), signage.resolve("Files").listDirectoryEntries())
    }

    @Test
    fun aCopyIsFoundUnderTheSignageFolderAndAnythingElseWhereItIs() {
        val elsewhere = pictures.resolve("Lobby").toString()
        assertEquals(signage.resolve("Folders/Lobby"), SignageMedia.resolve(signage, "Folders/Lobby"))
        assertEquals(Path.of(elsewhere), SignageMedia.resolve(signage, elsewhere))
        assertTrue(SignageMedia.isCopy("Folders/Lobby"))
        assertFalse(SignageMedia.isCopy(elsewhere))
        assertNull(SignageMedia.resolve(signage, "bad\u0000path"))
        assertFalse(SignageMedia.isCopy("bad\u0000path"))
    }

    @Test
    fun whatIsAlreadyInTheSignageFolderIsPlayedWhereItIs() {
        val announcements = signage.resolve("Announcements").createDirectories()
        assertEquals("Announcements", SignageMedia.within(signage, announcements))
        assertEquals("Announcements/Easter", SignageMedia.within(signage, announcements.resolve("Easter")))
        assertEquals(".", SignageMedia.within(signage, signage))
        assertNull(SignageMedia.within(signage, pictures))
        // Through a `..` that leads back in, it is still inside.
        assertEquals("Announcements", SignageMedia.within(signage, pictures.resolve("../signage/Announcements")))
        var copied = false
        val place = { source: Path -> SignageMedia.place(source, signage) { _, _ -> "copied".also { copied = true } } }
        assertEquals("Announcements", place(announcements))
        assertFalse(copied)
        assertEquals("copied", place(pictures))
    }

    @Test
    fun onlyWhatTheTabCopiedCountsAsACopy() {
        assertTrue(SignageMedia.isCopy("Folders/Easter"))
        assertTrue(SignageMedia.isCopy("Files/flyer.jpg"))
        assertFalse(SignageMedia.isCopy("Announcements"))
        assertFalse(SignageMedia.isCopy("Folders"))
        assertFalse(SignageMedia.isCopy("Folders/Easter/a.jpg"))
        assertFalse(SignageMedia.isCopy("."))
        signage.resolve("Announcements").createDirectories().resolve("a.jpg").writeText("jpg")
        assertFalse(SignageMedia.discard(signage, "Announcements") { error("not asked") })
        assertTrue(signage.resolve("Announcements/a.jpg").exists())
    }

    @Test
    fun aCopyGoesToTheTrashOrIsDeletedWhereThereIsNone() {
        SignageMedia.copyFolder(file("Easter/a.jpg").parent, signage)
        val trashed = mutableListOf<File>()
        assertTrue(SignageMedia.discard(signage, "Folders/Easter") { trashed += it; true })
        assertEquals(listOf(signage.resolve("Folders/Easter").toFile()), trashed)
        assertTrue(SignageMedia.discard(signage, "Folders/Easter", trash = null))
        assertFalse(signage.resolve("Folders/Easter").exists())
        // Already gone counts as removed; a Trash that refuses does not.
        assertTrue(SignageMedia.discard(signage, "Folders/Easter", trash = null))
        SignageMedia.copyFile(file("flyer.jpg"), signage)
        assertFalse(SignageMedia.discard(signage, "Files/flyer.jpg") { false })
    }

    @Test
    fun onlyACopyInsideTheSignageFolderIsEverRemoved() {
        val original = file("Easter/a.jpg")
        var asked = false
        val trash: (File) -> Boolean = { asked = true; true }
        assertFalse(SignageMedia.discard(signage, original.parent.toString(), trash))
        assertFalse(SignageMedia.discard(signage, "../pictures/Easter", trash))
        assertFalse(SignageMedia.discard(signage, "Folders/../../pictures", trash))
        assertFalse(SignageMedia.discard(signage, "Folders", trash))
        assertFalse(SignageMedia.discard(signage, "screens.json", trash))
        assertFalse(asked)
        assertTrue(original.exists())
    }

    @Test
    fun aTrashNeedsADisplayADesktopAndTheAction() {
        assertTrue(SignageTrash.trashAvailable(headless = false, desktop = { true }) { true })
        assertFalse(SignageTrash.trashAvailable(headless = true, desktop = { error("not asked") }) { true })
        assertFalse(SignageTrash.trashAvailable(headless = false, desktop = { false }) { error("not asked") })
        assertFalse(SignageTrash.trashAvailable(headless = false, desktop = { true }) { false })
        assertNull(SignageTrash.trashFor(available = false))
    }
}

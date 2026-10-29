package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.MediaKind
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun aFolderPlaysItsOwnPicturesAndVideosInNameOrder() {
        file("b.mp4")
        file("A.jpg")
        file("notes.txt")
        file(".hidden.jpg")
        file("Events/flyer.png")
        val read = SignageFolder.read(PickedFolderPlaylist("dir:1", "Lobby", root.toString()), root)
        assertEquals(listOf("A.jpg", "b.mp4"), read.files.map { it.path.fileName.toString() })
        assertEquals(listOf(MediaKind.IMAGE, MediaKind.VIDEO), read.files.map { it.kind })
        assertEquals(PlaylistSummary("dir:1", "Lobby", 1, 1, detail = root.toString(), seconds = 10), read.summary())
    }

    @Test
    fun aMissingFolderIsListedAsMissing() {
        val gone = root.resolve("gone").toString()
        val read = SignageFolder.read(PickedFolderPlaylist("dir:1", "Gone", gone), root)
        assertEquals(PlaylistSummary("dir:1", "Gone", detail = gone, missing = true, seconds = 10), read.summary())
        assertNull(SignageFolder.kindOf(Path.of("a.pdf")))
        assertEquals(MediaKind.VIDEO, SignageFolder.kindOf(Path.of("A.MOV")))
    }
}

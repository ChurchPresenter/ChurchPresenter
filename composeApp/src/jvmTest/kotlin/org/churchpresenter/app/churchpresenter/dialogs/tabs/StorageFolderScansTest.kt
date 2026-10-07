package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.profiles.FileManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The storage pane's scans follow the folder as it is changed with the pane open: picking another
 * folder rescans it, rather than leaving the first folder's verdict on screen, and clearing the
 * choice clears the verdict.
 */
@OptIn(ExperimentalTestApi::class)
class StorageFolderScansTest {

    private val root: File = Files.createTempDirectory("cp-storage-scans").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun folder(name: String, vararg files: String): String =
        File(root, name).apply {
            mkdirs()
            files.forEach { File(this, it).apply { parentFile.mkdirs() }.writeText("x") }
        }.absolutePath

    @Test
    fun `the bible list follows the folder it is pointed at`() = runComposeUiTest {
        val first = folder("bibles-a", "kjv.spb")
        val second = folder("bibles-b", "rst.spb", "nested/ukr.spb")
        var directory by mutableStateOf(first)
        var found: List<String>? = null
        setContent { found = rememberBibleFiles(FileManager(), directory).value }

        waitUntil("the first folder was scanned") { found == listOf("kjv.spb") }
        directory = second
        waitUntil("the second folder was scanned") { found == listOf("nested/ukr.spb", "rst.spb") }
        directory = ""
        waitUntil("the cleared choice lists nothing") { found == emptyList<String>() }
    }

    @Test
    fun `the song scan follows the folder it is pointed at`() = runComposeUiTest {
        val legacy = folder("songs-a", "old.sps")
        val books = folder("songs-b", "Hymnal/a.song", "Hymnal/b.song")
        var directory by mutableStateOf(legacy)
        var scan: SongScan? = null
        setContent { scan = rememberSongScan(FileManager(), directory) }

        waitUntil("the legacy folder was scanned") { scan?.scanning == false && scan?.unsupported == listOf("old.sps") }
        directory = books
        waitUntil("the songbook folder was scanned") { scan?.scanning == false && scan?.folders?.isNotEmpty() == true }
        assertEquals(emptyList(), scan?.unsupported, "the first folder's verdict is gone")
        assertEquals(listOf(2), scan?.folders?.map { it.second })
        directory = ""
        waitUntil("the cleared choice scans nothing") { scan?.scanning == false && scan?.folders?.isEmpty() == true }
    }

    @Test
    fun `a folder's status follows the folder it is pointed at`() = runComposeUiTest {
        val real = folder("status")
        var directory by mutableStateOf(real)
        var status: DirStatus? = null
        setContent { status = rememberDirStatus(directory) }

        waitUntil("the real folder was checked") { status == DirStatus.WRITABLE }
        directory = File(root, "never-created").absolutePath
        waitUntil("the missing folder was checked") { status == DirStatus.NOT_FOUND }
        directory = ""
        waitUntil("the cleared choice is not set") { status == DirStatus.NOT_SET }
    }
}

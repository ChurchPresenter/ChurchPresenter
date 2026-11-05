package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.MediaKind
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.io.path.readText
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class SignageStoreTest {
    private val dir = createTempDirectory("signage-store")

    @AfterTest
    fun cleanUp() = dir.deleteRecursively()

    private fun folderFile(name: String, text: String): FolderFile {
        val path = dir.resolve(name).also { it.writeText(text) }
        return FolderFile(path, MediaKind.IMAGE, Files.size(path), Files.getLastModifiedTime(path).toMillis())
    }

    @Test
    fun aPlaylistOfAKindThisVersionDoesNotKnowIsKeptAsideAndWrittenBack() {
        val file = dir.resolve("signage.json")
        file.writeText(
            """{"added":[""" +
                """{"type":"folder","id":"folder:","name":"Welcome","path":"Folders/Welcome"},""" +
                """{"type":"hologram","id":"hologram:1","name":"From a newer version","depth":3}""" +
                """],"screens":{"p":{"playlistId":"hologram:1"}}}""",
        )
        val store = SignageStore(dir)
        val read = store.loadSettings()
        assertEquals(listOf("folder:"), read.added.map { it.id })
        assertEquals("hologram:1", read.plan("p").playlistId, "the screen's choice is left as it was")
        val weather = LinkPlaylist.create(NewLink("Weather", "https://w.example.org", LinkKind.WEB))
        store.saveSettings(read.copy(added = read.added + weather))
        val saved = file.readText()
        assertTrue(""""type":"hologram"""" in saved && """"depth":3""" in saved, saved)
        assertEquals(2, SignageStore(dir).loadSettings().added.size)
    }

    @Test
    fun damagedSettingsReadAsNone() {
        dir.resolve("signage.json").writeText("{ not json")
        assertEquals(SignageSettings(), SignageStore(dir).loadSettings())
        dir.resolve("signage.json").writeText("""{"added":[],"screens":"not a map"}""")
        assertEquals(SignageSettings(), SignageStore(dir).loadSettings())
    }

    @Test
    fun settingsAreKeptBetweenRuns() {
        assertEquals(SignageSettings(), SignageStore(dir).loadSettings())
        val settings = SignageSettings(
            added = listOf(PickedFolderPlaylist("dir:1", "Easter", "Folders/Easter", pictureSeconds = 7)),
            screens = mapOf("p" to ScreenPlan(ScreenPlan.NOTHING)),
        )
        SignageStore(dir).saveSettings(settings)
        assertEquals(settings, SignageStore(dir).loadSettings())
        assertEquals(ScreenPlan(), settings.plan("other"))
    }

    @Test
    fun aDamagedSettingsFileGivesTheDefaults() {
        dir.resolve("signage.json").writeText("{ broken")
        assertEquals(SignageSettings(), SignageStore(dir).loadSettings())
    }

    @Test
    fun aHashIsKeptWhileTheFileIsUnchanged() {
        val store = SignageStore(dir)
        val file = folderFile("a.jpg", "one")
        val sha = store.hashOf(file)
        store.saveHashes(setOf(file.path))

        // Another run trusts the saved hash for the same size and time, without reading the file.
        val again = SignageStore(dir).also { it.loadHashes() }
        assertEquals(sha, again.hashOf(file))

        // The same size but a new time is read again.
        assertEquals(sha, again.hashOf(file.copy(modifiedMs = file.modifiedMs + 1)))

        val changed = folderFile("a.jpg", "two, longer")
        assertNotEquals(sha, again.hashOf(changed))
    }

    @Test
    fun aFileThatCannotBeReadHasNoHash() {
        val gone = FolderFile(dir.resolve("gone.jpg"), MediaKind.IMAGE, 1, 1)
        assertNull(SignageStore(dir).hashOf(gone))
    }
}

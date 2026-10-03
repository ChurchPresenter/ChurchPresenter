@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTranslation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongsLogicCoverage2Test {

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.clickRow(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
    }

    @Test
    fun `up from the title slide in verse mode moves to the previous song`() =
        songsTab(songSettings = verseMode().copy(titleSlideEnabled = true)) { vm, reports ->
            clickRow("Be Thou My Vision")
            val before = vm.selectedSongIndex.value
            onNodeWithText("SONG TITLE SLIDE").performClick()
            waitForIdle()

            press(Key.DirectionUp)

            assertNotEquals(Constants.SECTION_TYPE_TITLE_SLIDE, reports.selectedSection?.type)
            assertTrue(vm.selectedSongIndex.value != before || before == 0)
        }

    @Test
    fun `up from the first verse while presenting stays on the song`() =
        songsTab(songSettings = verseMode(), isPresenting = true) { vm, reports ->
            clickRow("Amazing Grace")
            onNodeWithText("a line of Amazing Grace").performClick()
            waitForIdle()
            val song = vm.selectedSongIndex.value

            press(Key.DirectionUp)

            assertEquals(song, vm.selectedSongIndex.value)
            assertEquals("[Verse 1]", reports.selectedSection?.header)
        }

    @Test
    fun `a header with no name has no chip name`() {
        val match = findSongMatch(SongItem(number = "1", title = "T", lyrics = listOf("[]", "holy holy")), "holy")
        assertNull(match?.sectionName)
    }

    @Test
    fun `a snippet starting mid-word is cut to the next word`() {
        val text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma"
        val at = text.indexOf("sigma")
        val snippet = snippetAround(text, at, 5)
        assertTrue(snippet.startsWith("…") && snippet.endsWith("sigma"))
    }

    @Test
    fun `songs with no files are told apart by id and title`() {
        val dir = Files.createTempDirectory("cp-logic2").toFile()
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = File(dir, "missing").absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        try {
            vm.setInstanceLinkSource(
                active = true,
                catalog = listOf(
                    SongItem(number = "1", title = "A", songbook = "B"),
                    SongItem(number = "1", title = "C", songbook = "B"),
                ),
                fetchLyrics = null,
            )
            assertTrue(vm.selectSongByDetails(1, "C", "B", "B::1"))
            assertEquals("C", vm.filteredSongItems.value[vm.selectedSongIndex.value].title)
        } finally {
            vm.dispose()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an update to a song that is not in the library changes nothing`() {
        val songs = Songs().apply { addSongs(listOf(SongItem(number = "1", title = "A", songbook = "X"))) }
        songs.updateSong(SongItem(number = "1", title = "A", songbook = "Y"), SongItem(number = "1", title = "Z"))
        assertEquals("A", songs.getSongs().single().title)
    }

    @Test
    fun `a translation with no lyrics keeps its place as an empty group`() {
        val dir = Files.createTempDirectory("cp-logic2b").toFile()
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        try {
            val song = SongItem(
                number = "1", title = "A", lyrics = listOf("[Verse 1]", "one"),
                translations = listOf(SongTranslation(title = "B", lyrics = emptyList())),
            )
            assertEquals(1, vm.getLyricSections(song).size)
        } finally {
            vm.dispose()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a library folder that does not exist still loads`() {
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = "/definitely/not/here")),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        try {
            assertTrue(!vm.isLoading.value)
        } finally {
            vm.dispose()
        }
    }

    @Test
    fun `a song file written with an empty cache still loads`() {
        val dir = Files.createTempDirectory("cp-logic2c").toFile()
        SongFileParser().writeSongFile(
            SongItem(number = "1", title = "A", songbook = "B", lyrics = listOf("[Verse 1]", "x")),
            File(File(dir, "B").apply { mkdirs() }, "1 - A.song").absolutePath,
        )
        SongFileParser.saveSongCache(dir.absolutePath, emptyList())
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        try {
            assertEquals(listOf("A"), vm.filteredSongItems.value.map { it.title })
        } finally {
            vm.dispose()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the whole-song slide leaves background directives out`() {
        val dir = Files.createTempDirectory("cp-logic2d").toFile()
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        try {
            vm.setInstanceLinkSource(
                active = true,
                catalog = listOf(SongItem(number = "1", title = "A", lyrics = listOf("[background: color]", "one"))),
                fetchLyrics = null,
            )
            vm.selectSong(0)
            assertEquals(listOf("one"), vm.getSelectedSong()?.lines)
        } finally {
            vm.dispose()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `there is no whole-song slide with nothing selected`() {
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = "")),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        try {
            vm.setInstanceLinkSource(active = true, catalog = emptyList(), fetchLyrics = null)
            assertNull(vm.getSelectedSong())
        } finally {
            vm.dispose()
        }
    }
}

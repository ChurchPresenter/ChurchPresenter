@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SongsTabLiveCoverageTest {

    private fun ComposeUiTest.clickRow(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.goLive() {
        onAllNodes(hasContentDescription("Go Live"))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private val titleSlideOn = SongSettings(titleSlideEnabled = true)

    @Test
    fun `going live with a new song asks the primary to load it`() {
        val projects = mutableListOf<ScheduleItem>()
        songsTab(onInstanceLinkSendProject = { projects += it }) { _, _ ->
            clickRow("Amazing Grace")
            goLive()

            val sent = projects.single() as ScheduleItem.SongItem
            assertEquals("Amazing Grace", sent.title)
            assertEquals(1, sent.songNumber)
            assertEquals("Hymnal", sent.songbook)
            assertEquals("Hymnal::1", sent.songId)
        }
    }

    @Test
    fun `going live again on the same song sends only the section`() {
        val projects = mutableListOf<ScheduleItem>()
        val sections = mutableListOf<Triple<String, Int, Int>>()
        songsTab(
            onInstanceLinkSendProject = { projects += it },
            onInstanceLinkSendSongSection = { number, section, line -> sections += Triple(number, section, line) },
        ) { _, _ ->
            clickRow("Amazing Grace")
            goLive()
            goLive()

            assertEquals(1, projects.size)
            assertEquals(listOf(Triple("1", 0, 0)), sections)
        }
    }

    @Test
    fun `a song going live is reported once, however often its sections change`() {
        val wentLive = mutableListOf<String>()
        songsTab(onSongWentLive = { wentLive += it.title }) { _, _ ->
            clickRow("Amazing Grace")
            goLive()
            goLive()
            clickRow("Be Thou My Vision")
            goLive()

            assertEquals(listOf("Amazing Grace", "Be Thou My Vision"), wentLive)
        }
    }

    @Test
    fun `selecting a song without going live reports nothing`() {
        val wentLive = mutableListOf<String>()
        songsTab(onSongWentLive = { wentLive += it.title }) { _, _ ->
            clickRow("Amazing Grace")
            clickRow("Be Thou My Vision")

            assertTrue(wentLive.isEmpty())
        }
    }

    @Test
    fun `play counts handed to the tab order the list by plays`() {
        val counts = SongPlayCounts { id -> if (id == "Hymnal::2") 5 else 0 }
        songsTab(playCounts = counts) { vm, _ ->
            runOnIdle {
                vm.updateSort(Constants.SORT_PLAY_COUNT)
                vm.updateSort(Constants.SORT_PLAY_COUNT)
            }
            waitForIdle()

            assertEquals("Be Thou My Vision", vm.filteredSongItems.value.first().title)
        }
    }

    @Test
    fun `lyrics fetched from a primary are sent out once they arrive`() {
        songsTab { vm, reports ->
            runOnIdle {
                vm.setInstanceLinkSource(
                    active = true,
                    catalog = listOf(SongItem(number = "7", title = "Remote Song", songbook = "Hymnal")),
                    fetchLyrics = { _, _ -> listOf("[Verse 1]", "remote line") },
                )
                vm.selectSong(0)
            }
            waitForIdle()

            assertTrue(
                reports.allSections.any { pushed -> pushed.any { "remote line" in it.lines } },
                reports.allSections.toString(),
            )
        }
    }

    @Test
    fun `a new storage folder reloads the library from it`() {
        val other = Files.createTempDirectory("cp-songs-tab-other").toFile()
        try {
            SongFileParser().writeSongFile(
                SongItem(number = "1", title = "Elsewhere", songbook = "Other", lyrics = listOf("[Verse 1]", "far")),
                File(File(other, "Other").apply { mkdirs() }, "1 - Elsewhere.song").absolutePath,
            )
            val override = mutableStateOf<AppSettings?>(null)
            songsTab(settingsOverride = override) { vm, _ ->
                override.value = AppSettings().withSongsEverywhere(SongSettings(storageDirectory = other.absolutePath))
                waitForIdle()

                assertEquals(listOf("Elsewhere"), vm.filteredSongItems.value.map { it.title })
            }
        } finally {
            other.deleteRecursively()
        }
    }

    @Test
    fun `in line mode left from the first line steps back onto the title slide`() =
        songsTab(songSettings = titleSlideOn) { _, reports ->
            clickRow("Amazing Grace")
            onNodeWithText("a line of Amazing Grace").performClick()
            waitForIdle()

            press(Key.DirectionLeft)

            assertEquals(Constants.SECTION_TYPE_TITLE_SLIDE, reports.selectedSection?.type)
            assertEquals(0, reports.sectionIndex)
        }

    @Test
    fun `in line mode right from the title slide steps onto the first verse`() =
        songsTab(songSettings = titleSlideOn) { _, reports ->
            clickRow("Amazing Grace")
            onNodeWithText("SONG TITLE SLIDE").performClick()
            waitForIdle()
            assertEquals(Constants.SECTION_TYPE_TITLE_SLIDE, reports.selectedSection?.type)

            press(Key.DirectionRight)

            assertEquals("[Verse 1]", reports.selectedSection?.header)
        }

    @Test
    fun `left on the title slide stays on it`() =
        songsTab(songSettings = titleSlideOn) { _, reports ->
            clickRow("Amazing Grace")
            onNodeWithText("SONG TITLE SLIDE").performClick()
            waitForIdle()

            press(Key.DirectionLeft)

            assertEquals(Constants.SECTION_TYPE_TITLE_SLIDE, reports.selectedSection?.type)
        }

    @Test
    fun `the title slide sent out is built by the app's builder`() {
        val built = mutableListOf<String>()
        songsTab(
            songSettings = titleSlideOn,
            titleSlideFor = { song, tuning, settings -> built += song.title; fakeTitleSlide(song, tuning, settings) },
        ) { _, reports ->
            clickRow("Amazing Grace")
            onNodeWithText("SONG TITLE SLIDE").performClick()
            waitForIdle()

            assertTrue("Amazing Grace" in built)
            val sent = assertNotNull(reports.selectedSection)
            assertEquals(listOf("1", "Amazing Grace"), sent.lines)
            assertEquals(Constants.SECTION_TYPE_TITLE_SLIDE, reports.allSections.last().first().type)
        }
    }
}

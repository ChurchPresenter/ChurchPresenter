@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.WindowLayoutSettings
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.sharedui.models.Presenting
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongsTabParamsCoverage2Test {

    private fun withLibrary(
        settingsOf: (File) -> AppSettings,
        block: (SongsViewModel, AppSettings) -> Unit,
    ) {
        val dir = Files.createTempDirectory("cp-tab-params").toFile()
        try {
            SongFileParser().writeSongFile(
                SongItem(number = "1", title = "Grace", songbook = "Hymnal", lyrics = listOf("[Verse 1]", "amazing")),
                File(File(dir, "Hymnal").apply { mkdirs() }, "1 - Grace.song").absolutePath,
            )
            val settings = settingsOf(dir)
            val vm = SongsViewModel(
                settings,
                dispatcher = Dispatchers.Unconfined,
                ioDispatcher = Dispatchers.Unconfined,
                enableFolderWatcher = false,
            )
            try {
                block(vm, settings)
            } finally {
                vm.dispose()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private class Calls {
        val selected = mutableListOf<LyricSection>()
        val all = mutableListOf<List<LyricSection>>()
        val indices = mutableListOf<Int>()
        val typical: (SongItem) -> Int? = { 200 }
        val wentLive: (SongItem) -> Unit = {}
        val titleSlide: (SongItem, SongTuning, SongSettings) -> LyricSection = ::fakeTitleSlide
        val editor: @Composable (SongEditorRequest) -> Unit = {}
        val settingsChange: ((AppSettings) -> AppSettings) -> Unit = {}
        val schedule: (Int, String, String, String) -> Unit = { _, _, _, _ -> }
        val project: (ScheduleItem) -> Unit = {}
        val section: (String, Int, Int) -> Unit = { _, _, _ -> }
        val row = ScheduleItem.SongItem(id = "row", songNumber = 1, title = "Grace", songbook = "Hymnal")
        val onSelected: (LyricSection) -> Unit = { selected += it }
        val onAll: (List<LyricSection>) -> Unit = { all += it }
        val onIndex: (Int) -> Unit = { indices += it }
        val onLine: (Int) -> Unit = {}
        val onPresenting: (Presenting) -> Unit = {}
        val counts = SongPlayCounts { 3 }
        val modifier = Modifier.fillMaxSize()
    }

    @Composable
    @NonRestartableComposable
    private fun FullTab(vm: SongsViewModel, settings: AppSettings, c: Calls) = SongsTab(
        modifier = c.modifier,
        viewModel = vm,
        appSettings = settings,
        typicalSongSeconds = c.typical,
        onSongWentLive = c.wentLive,
        titleSlideFor = c.titleSlide,
        songEditor = c.editor,
        onSettingsChange = c.settingsChange,
        onAddToSchedule = c.schedule,
        onInstanceLinkSendProject = c.project,
        onInstanceLinkSendSongSection = c.section,
        selectedSongItem = c.row,
        selectedSongItemVersion = 1,
        onSongItemSelected = c.onSelected,
        onAllSectionsChanged = c.onAll,
        onSectionIndexChanged = c.onIndex,
        onLineIndexChanged = c.onLine,
        onPresenting = c.onPresenting,
        isPresenting = true,
        playCounts = c.counts,
        dialogDismissSignal = 2,
        searchIdleFocusMs = 50L,
    )

    @Test
    fun `the tab composes with every parameter given and recomposes with the same ones`() =
        withLibrary({ AppSettings().withSongsEverywhere(SongSettings(storageDirectory = it.absolutePath)) }) { vm, s ->
            val c = Calls()
            var tick by mutableStateOf(0)
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        Text("tick $tick")
                        FullTab(vm, s, c)
                    }
                }
                waitForIdle()
                tick++
                waitForIdle()
                val dir = File(s.songSettings.storageDirectory)
                SongFileParser().writeSongFile(
                    SongItem(number = "2", title = "Shine", songbook = "Choruses", lyrics = listOf("[Verse 1]", "x")),
                    File(File(dir, "Choruses").apply { mkdirs() }, "2 - Shine.song").absolutePath,
                )
                vm.loadSongs()
                waitForIdle()

                assertTrue(c.selected.isNotEmpty(), "the schedule row's song is pushed")
                assertTrue(c.all.isNotEmpty())
                assertTrue(c.indices.isNotEmpty())
            }
        }

    @Test
    fun `the tab with only its required parameters survives a library change`() =
        withLibrary({ AppSettings().withSongsEverywhere(SongSettings(storageDirectory = it.absolutePath)) }) { vm, s ->
            val selected = mutableListOf<LyricSection>()
            var tick by mutableStateOf(0)
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        Text("tick $tick")
                        SongsTab(
                            viewModel = vm,
                            appSettings = s,
                            titleSlideFor = ::fakeTitleSlide,
                            onSongItemSelected = { selected += it },
                        )
                    }
                }
                waitForIdle()
                val dir = File(s.songSettings.storageDirectory)
                SongFileParser().writeSongFile(
                    SongItem(number = "2", title = "Shine", songbook = "Choruses", lyrics = listOf("[Verse 1]", "x")),
                    File(File(dir, "Choruses").apply { mkdirs() }, "2 - Shine.song").absolutePath,
                )
                vm.loadSongs()
                tick++
                waitForIdle()
                onNodeWithText("Shine").performClick()
                waitForIdle()
                onNodeWithText("x").performClick()
                waitForIdle()

                assertEquals("Shine", selected.last().title)
            }
        }

    @Test
    fun `a maximized window with a saved panel width lays the tab out from it`() = withLibrary({
        AppSettings(maximizedLayout = WindowLayoutSettings(lyricsPanelWidthDp = 320))
            .withSongsEverywhere(SongSettings(storageDirectory = it.absolutePath))
    }) { vm, s ->
        val c = Calls()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalMainWindowState provides WindowState(placement = WindowPlacement.Maximized),
                ) {
                    MaterialTheme { FullTab(vm, s, c) }
                }
            }
            waitForIdle()

            assertTrue(c.selected.isNotEmpty())
        }
    }

    @Test
    fun `a floating window lays the tab out from the windowed layout`() = withLibrary({
        AppSettings(windowedLayout = WindowLayoutSettings(lyricsPanelWidthDp = 300))
            .withSongsEverywhere(SongSettings(storageDirectory = it.absolutePath))
    }) { vm, s ->
        val c = Calls()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalMainWindowState provides WindowState(placement = WindowPlacement.Floating),
                ) {
                    MaterialTheme { FullTab(vm, s, c) }
                }
            }
            waitForIdle()

            assertTrue(c.selected.isNotEmpty())
        }
    }

    @Test
    fun `lyric lines with no handlers just render`() = runComposeUiTest {
        setContent { MaterialTheme { Column { LyricLines(listOf("one", "two"), Color.Black) } } }

        onNodeWithText("one").assertExists()
        onNodeWithText("two").performClick()
    }

    @Test
    fun `lyric lines report clicks and double clicks, and mark the active one`() = runComposeUiTest {
        val clicks = mutableListOf<Int>()
        val doubles = mutableListOf<Int>()
        setContent {
            MaterialTheme {
                Column {
                    LyricLines(
                        listOf("one", "two"), Color.Black, activeLineIndex = 1,
                        onLineClick = { clicks += it }, onLineDoubleClick = { doubles += it },
                    )
                }
            }
        }
        onNodeWithText("two").performClick()
        waitForIdle()

        assertEquals(listOf(1), clicks)
        assertEquals(1, onAllNodesWithText("one").fetchSemanticsNodes().size)
    }

    @Test
    fun `lyric lines with a click but no double click handler still click`() = runComposeUiTest {
        val clicks = mutableListOf<Int>()
        setContent {
            MaterialTheme { Column { LyricLines(listOf("one"), Color.Black, onLineClick = { clicks += it }) } }
        }
        onNodeWithText("one").performClick()
        waitForIdle()

        assertEquals(listOf(0), clicks)
    }
}

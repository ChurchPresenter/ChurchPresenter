@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlinx.coroutines.flow.MutableSharedFlow
import org.churchpresenter.app.churchpresenter.remote.RemoteSongSelection
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.statistics.StatisticsManager
import org.churchpresenter.schedule.SCHEDULE_ROW_CARD_TAG
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.LiveHistoryLogger
import org.churchpresenter.sharedui.utils.TrainingDataLogger
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Taking a song live from its schedule row, through the composed root and the real Songs tab.
 *
 * Three things ride on this path that nothing else checks. The song itself must go up: the handler
 * once put an empty placeholder slide on air ahead of it, a blank for a whole transition. The play
 * log must count the song once -- the schedule handler and the Songs tab push both used to count
 * it. And the on-screen history must
 * name the row, since the schedule knows the songbook the presenter never sees.
 *
 * The output callbacks drive a real [PresenterManager], as the app's own wiring does, and its live
 * state goes to [LiveHistoryLogger] the way `LiveStateBroadcastWiring` sends it. `user.home` is a
 * temp dir, so the play log starts empty.
 */
class ScheduleSongGoLiveTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun setUp() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-schedule-go-live").toFile()
        System.setProperty("user.home", home.absolutePath)
        TrainingDataLogger.sessionId = "schedule-go-live"
    }

    @AfterTest
    fun tearDown() {
        TrainingDataLogger.sessionId = null
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun library(): AppSettings {
        val songs = File(home, "songs")
        val book = File(songs, "Hymnal").apply { mkdirs() }
        SongFileParser().writeSongFile(
            SongItem(number = "1", title = "A Test Song", songbook = "Hymnal", lyrics = listOf("[Verse 1]", "a line")),
            File(book, "1 - A Test Song.song").absolutePath,
        )
        return AppSettings(
            songSettings = SongSettings(storageDirectory = songs.absolutePath),
            hiddenTabs = Tabs.entries.filter { it != Tabs.SONGS }.map { it.name }.toSet(),
        )
    }

    private fun historyLines(): List<JsonObject> =
        File(home, ".churchpresenter/bible-stt-logs/live-content-schedule-go-live.jsonl")
            .readLines().map { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun `a song taken live from the schedule is counted once and logged under its row`() = runComposeUiTest {
        val presenter = PresenterManager()
        presenter.onLiveStateChanged = { pm, _ -> LiveHistoryLogger.logLiveState(liveHistoryEntryOf(pm, null)) }
        val statistics = StatisticsManager()
        var actions = ScheduleActions()
        val pushed = mutableListOf<LyricSection>()
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = library(),
                    presenterManager = presenter,
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    statisticsManager = statistics,
                    live = LiveOutputCallbacks(
                        presenting = { presenter.setPresentingMode(it) },
                        onVerseSelected = { presenter.setSelectedVerses(it) },
                        onSongItemSelected = { pushed += it; presenter.setLyricSection(it) },
                        onSectionIndexChanged = { presenter.setSongDisplaySectionIndex(it) },
                        onLineIndexChanged = { presenter.setSongDisplayLineIndex(it) },
                    ),
                    publish = MainDesktopPublishers(onScheduleActionsReady = { actions = it }),
                )
            }
        }
        waitForIdle()
        actions.addSong(1, "A Test Song", "Hymnal", "Hymnal::1")
        waitForIdle()

        // The row's own Go Live, not the Songs tab's.
        onAllNodes(hasContentDescription("Go Live") and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))[0]
            .performClick()
        waitForIdle()

        assertEquals(
            listOf("A Test Song" to 1),
            statistics.getAllSongsInRange(0L, Long.MAX_VALUE).map { it.title to it.count },
            "one go-live, counted once",
        )
        val lyricLines = historyLines().filter { it["contentType"]?.jsonPrimitive?.content == "LYRICS" }
        assertEquals(
            setOf("Hymnal::1"),
            lyricLines.map { it["songId"]?.jsonPrimitive?.content }.toSet(),
            "every lyric line of the song names its row",
        )
        assertEquals("schedule", lyricLines.first()["source"]?.jsonPrimitive?.content)
        assertTrue(pushed.isNotEmpty(), "the song reached the output")
        assertTrue(pushed.none { it.lines.isEmpty() }, "and no empty placeholder slide went up ahead of it")
    }

    @Test
    fun `a song projected by a remote goes up whole and is logged under the remote`() = runComposeUiTest {
        val presenter = PresenterManager()
        presenter.onLiveStateChanged = { pm, _ -> LiveHistoryLogger.logLiveState(liveHistoryEntryOf(pm, null)) }
        val remoteSongs = MutableSharedFlow<RemoteSongSelection>(extraBufferCapacity = 1)
        val pushed = mutableListOf<LyricSection>()
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = library(),
                    presenterManager = presenter,
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    live = LiveOutputCallbacks(
                        presenting = { presenter.setPresentingMode(it) },
                        onVerseSelected = { presenter.setSelectedVerses(it) },
                        onSongItemSelected = { pushed += it; presenter.setLyricSection(it) },
                    ),
                    flows = RemoteControlFlows(remoteSelectSongFlow = remoteSongs),
                )
            }
        }
        waitForIdle()
        val row = ScheduleItem.SongItem(
            id = "r", songNumber = 1, title = "A Test Song", songbook = "Hymnal", songId = "Hymnal::1",
        )
        remoteSongs.tryEmit(RemoteSongSelection(row, goLive = true, source = "remote"))
        waitUntil(timeoutMillis = 5_000) { presenter.slideContent.value == Presenting.LYRICS }

        assertTrue(pushed.isNotEmpty() && pushed.none { it.lines.isEmpty() }, "the song itself, never a blank")
        val lyricLine = historyLines().last { it["contentType"]?.jsonPrimitive?.content == "LYRICS" }
        assertEquals("Hymnal::1", lyricLine["songId"]?.jsonPrimitive?.content)
        assertEquals("remote", lyricLine["source"]?.jsonPrimitive?.content)
    }
}

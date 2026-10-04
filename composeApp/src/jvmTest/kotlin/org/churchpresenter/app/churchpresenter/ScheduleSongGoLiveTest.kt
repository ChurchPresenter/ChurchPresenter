@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

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
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.schedule.SCHEDULE_ROW_CARD_TAG
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.LiveHistoryLogger
import org.churchpresenter.sharedui.utils.TrainingDataLogger
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Taking a song live from its schedule row, through the composed root and the real Songs tab.
 *
 * Two things ride on this path that nothing else checks. The play log must count the song once —
 * the schedule handler and the Songs tab push both used to count it — and the on-screen history must
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
                        onSongItemSelected = { presenter.setLyricSection(it) },
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
    }
}

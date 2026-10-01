@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.utils.LiveHistoryEntry
import org.churchpresenter.app.churchpresenter.utils.LiveHistoryLogger
import org.churchpresenter.app.churchpresenter.utils.TrainingDataLogger
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SongsTabGoLiveTelemetryTest {

    private var realHome: String? = null
    private var tempHome: File? = null

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome?.deleteRecursively()
        realHome = null
        tempHome = null
    }

    private fun isolateHome() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        Files.createTempDirectory("cp-songs-telemetry").toFile().also {
            tempHome = it
            System.setProperty("user.home", it.absolutePath)
        }
    }

    private fun ComposeUiTest.goLiveWith(title: String) {
        onNodeWithText(title).performClick()
        waitForIdle()
        onAllNodes(hasContentDescription("Go Live"))[0].performClick()
        waitForIdle()
    }

    private fun statisticsFor(manager: StatisticsManager) =
        manager.getAllSongsInRange(0L, Long.MAX_VALUE)

    @Test
    fun `taking a song live records it for the usage report`() {
        isolateHome()
        val statistics = StatisticsManager()

        songsTab(statistics = statistics) { _, _ ->
            goLiveWith("Amazing Grace")

            val logged = statisticsFor(statistics)
            assertEquals(listOf("Amazing Grace"), logged.map { it.title })
        }
    }

    @Test
    fun `the recorded song carries the details a licence report needs`() {
        isolateHome()
        val statistics = StatisticsManager()

        songsTab(statistics = statistics) { _, _ ->
            goLiveWith("Amazing Grace")

            val logged = statisticsFor(statistics).single()
            assertEquals(1, logged.songNumber)
            assertEquals("Hymnal", logged.songbook)
            assertEquals("John Newton", logged.author)
        }
    }

    @Test
    fun `moving between sections of the live song does not record it again`() {
        isolateHome()
        val statistics = StatisticsManager()

        songsTab(statistics = statistics) { vm, _ ->
            goLiveWith("Amazing Grace")
            assertEquals(1, statisticsFor(statistics).size)

            vm.navigateNextSection()
            waitForIdle()

            assertEquals(
                1,
                statisticsFor(statistics).size,
                "a service counts songs, not section changes",
            )
        }
    }

    @Test
    fun `a second song going live is recorded separately`() {
        isolateHome()
        val statistics = StatisticsManager()

        songsTab(statistics = statistics) { _, _ ->
            goLiveWith("Amazing Grace")
            goLiveWith("Be Thou My Vision")

            assertEquals(
                listOf("Amazing Grace", "Be Thou My Vision"),
                statisticsFor(statistics).map { it.title }.sorted(),
            )
        }
    }

    @Test
    fun `merely selecting a song records nothing`() {
        isolateHome()
        val statistics = StatisticsManager()

        songsTab(statistics = statistics) { _, _ ->
            onNodeWithText("Amazing Grace").performClick()
            waitForIdle()

            assertTrue(
                statisticsFor(statistics).isEmpty(),
                "browsing the library is not presenting",
            )
        }
    }

    @Test
    fun `going live without a statistics manager still works`() {
        songsTab { _, reports ->
            goLiveWith("Amazing Grace")

            assertTrue(reports.presenting.isNotEmpty(), "the song still reaches the output")
        }
    }

    @Test
    fun `a song previewed from the schedule is recorded when it then goes live`() {
        // The service flow: click the song's schedule row (which previews it), then Go Live. The
        // preview used to mark the song as the live one, so the Go Live looked like a section change
        // on a song already up and was never counted.
        isolateHome()
        val statistics = StatisticsManager()
        val selection = mutableStateOf<ScheduleItem.SongItem?>(null)

        songsTab(statistics = statistics, scheduleSelection = selection) { _, _ ->
            selection.value =
                ScheduleItem.SongItem(id = "row-1", songNumber = 1, title = "Amazing Grace", songbook = "Hymnal")
            waitForIdle()
            assertTrue(statisticsFor(statistics).isEmpty(), "a preview is not a go-live")

            onAllNodes(hasContentDescription("Go Live"))[0].performClick()
            waitForIdle()

            assertEquals(listOf("Amazing Grace" to 1), statisticsFor(statistics).map { it.title to it.count })
        }
    }

    @Test
    fun `the song taken live is the row its lyric lines are logged under`() {
        isolateHome()
        TrainingDataLogger.sessionId = "songs-tab-history"
        try {
            songsTab { _, reports ->
                goLiveWith("Be Thou My Vision")
                val section = assertNotNull(reports.selectedSection)

                // What the presenter would report for the section the tab just pushed.
                LiveHistoryLogger.logLiveState(
                    LiveHistoryEntry("LYRICS", songNumber = section.songNumber, songTitle = section.title),
                )

                val line = File(tempHome, ".churchpresenter/bible-stt-logs/live-content-songs-tab-history.jsonl")
                    .readLines().last().let { Json.parseToJsonElement(it).jsonObject }
                assertEquals("Hymnal::2", line["songId"]?.jsonPrimitive?.content)
                assertEquals("manual", line["source"]?.jsonPrimitive?.content)
            }
        } finally {
            TrainingDataLogger.sessionId = null
        }
    }
}

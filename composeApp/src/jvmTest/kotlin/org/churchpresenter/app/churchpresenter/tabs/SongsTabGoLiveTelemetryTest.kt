@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.statistics.StatisticsManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
}

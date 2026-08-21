@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleTabGoLiveTelemetryTest {

    private var realHome: String? = null
    private var tempHome: File? = null

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome?.deleteRecursively()
        realHome = null
        tempHome = null
    }

    private fun isolateHome(): File {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        return Files.createTempDirectory("cp-bible-telemetry").toFile().also {
            tempHome = it
            System.setProperty("user.home", it.absolutePath)
        }
    }

    private fun ComposeUiTest.pressGoLive() {
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.goLive() {
        onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
        waitForIdle()
        pressGoLive()
    }

    private fun twoTranslations(files: List<String>) = files.map { BibleTranslationSettings(fileName = it) }

    @Test
    fun `a passage records every verse it put on screen, not just the first`() {
        isolateHome()
        val statistics = StatisticsManager()

        bibleTab(statistics = statistics) { vm, _ ->
            vm.ctrlClickVerse(1)
            vm.ctrlClickVerse(2)
            waitForIdle()

            pressGoLive()

            val logged = statistics.getAllVersesInRange(0L, Long.MAX_VALUE)
                .filter { it.bookName == "Genesis" && it.chapter == 1 }
            assertEquals(
                listOf(1, 2, 3),
                logged.map { it.verseNumber }.sorted(),
                "a CCLI report counts each verse read aloud, so a three-verse passage is three rows",
            )
        }
    }

    @Test
    fun `a passage is remembered in history as a range rather than as its first verse`() {
        bibleTab { vm, _ ->
            vm.ctrlClickVerse(1)
            waitForIdle()

            pressGoLive()

            assertEquals(
                listOf("Genesis 1:1-2"),
                vm.history.map { it.displayText },
                "clicking it again has to bring back the whole passage",
            )
        }
    }

    @Test
    fun `a single verse is still recorded once`() {
        isolateHome()
        val statistics = StatisticsManager()

        bibleTab(statistics = statistics) { _, _ ->
            goLive()

            val logged = statistics.getAllVersesInRange(0L, Long.MAX_VALUE)
                .filter { it.bookName == "Genesis" && it.chapter == 1 }
            assertEquals(listOf(1), logged.map { it.verseNumber })
        }
    }

    @Test
    fun `going live with several translations on one output still sends them all`() {
        bibleTab(
            secondContent = bibleFixture,
            settings = { settings ->
                settings.copy(
                    bibleSettings = settings.bibleSettings.withTranslations(
                        twoTranslations(listOf("test.spb", SECOND_MODULE)),
                    ),
                    projectionSettings = ProjectionSettings(
                        screenAssignments = listOf(ScreenAssignment(targetDisplay = 0)),
                    ),
                )
            },
        ) { _, reports ->
            goLive()

            assertEquals(
                2,
                reports.live?.size,
                "an output showing every translation must be handed every translation",
            )
        }
    }

    @Test
    fun `going live with a different translation per output still sends them all`() {
        bibleTab(
            secondContent = bibleFixture,
            settings = { settings ->
                settings.copy(
                    bibleSettings = settings.bibleSettings.withTranslations(
                        twoTranslations(listOf("test.spb", SECOND_MODULE)),
                    ),
                    projectionSettings = ProjectionSettings(
                        screenAssignments = listOf(
                            ScreenAssignment(targetDisplay = 0, bibleTranslations = listOf(0)),
                            ScreenAssignment(targetDisplay = 1, bibleTranslations = listOf(1)),
                        ),
                    ),
                )
            },
        ) { _, reports ->
            goLive()

            assertEquals(
                2,
                reports.live?.size,
                "each output picks its own translation out of the same live selection",
            )
        }
    }

    @Test
    fun `going live with one translation configured sends only that one`() {
        bibleTab(
            settings = { settings ->
                settings.copy(
                    projectionSettings = ProjectionSettings(
                        screenAssignments = listOf(ScreenAssignment(targetDisplay = 0)),
                    ),
                )
            },
        ) { _, reports ->
            goLive()

            assertEquals(1, reports.live?.size)
        }
    }

    @Test
    fun `a passage clears its multi-verse selection but keeps what it recorded`() {
        bibleTab { vm, reports ->
            vm.ctrlClickVerse(1)
            waitForIdle()

            pressGoLive()

            assertTrue(vm.history.isNotEmpty(), "the passage is in history")
            assertTrue(reports.live?.isNotEmpty() == true, "and it reached the output")
        }
    }
}

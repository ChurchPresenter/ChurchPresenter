@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.signage.core.CecState
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.WebTestRun
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.ScreenLink
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** A screen card's web page test: offered only where it can run, running, and each kind of answer. */
class WebTestLineTest {
    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_ARM64, "26.0.31", 1920, 1080, 0)
    private val folder =
        SignageFolderState(listOf(PlaylistSummary("folder:", "Signage", 1, 0)), ready = true)
    private val link = ScreenLink(
        "10.0.0.5", 0, "v", session = 1,
        features = listOf(Features.WEB, Features.WEB_TEST),
        status = PlayerMessage.Status(showingPlaylistId = "folder:", cec = CecState.WORKING),
    )
    private val tests = mutableListOf<String>()

    private fun result(at15: Double, at30: Double, cpu: Double, temp: Double? = null) =
        PlayerMessage.WebTestResult(listOf(WebTestRun(15, at15, cpu, temp), WebTestRun(30, at30, cpu, temp)))

    @Test
    fun theTestIsOfferedRunsAndAnswers() = runComposeUiTest {
        var row by mutableStateOf(ScreenRowState(screen, link, folder))
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ScreenCard(row, ScreenActions({ _, _ -> }, {}, { _, _ -> }, {}, onTestWebPages = { tests += it }))
                }
            }
        }
        onNodeWithContentDescription("Run test").performClick()
        assertEquals(listOf("p"), tests)

        row = row.copy(link = link.copy(webTestStartedAtMs = 1))
        onNodeWithText("Testing web pages", substring = true).assertExists()
        onNodeWithContentDescription("Run test").assertIsNotEnabled()

        row = row.copy(link = link.copy(webTest = result(15.0, 29.6, 58.0)))
        onNodeWithText("this screen handles Smooth motion well", substring = true).assertExists()
        onNodeWithText("15 fps: 15.0 shown · 30 fps: 29.6 shown · processor 58% busy").assertExists()
        onNodeWithContentDescription("Run test").performClick()
        assertEquals(listOf("p", "p"), tests)

        row = row.copy(link = link.copy(webTest = result(14.9, 21.3, 96.0, temp = 82.5)))
        onNodeWithText("fine at normal motion", substring = true).assertExists()
        onNodeWithText("It ran hot (82 °C)", substring = true).assertExists()

        row = row.copy(link = link.copy(webTest = result(9.4, 9.8, 100.0)))
        onNodeWithText("too slow on this screen", substring = true).assertExists()

        row = row.copy(link = link.copy(webTest = PlayerMessage.WebTestResult(error = "Chromium did not start")))
        onNodeWithText("The web page test could not run: Chromium did not start").assertExists()
    }

    @Test
    fun aScreenThatCannotRunItOrIsAwayIsNotOfferedIt() = runComposeUiTest {
        var row by mutableStateOf(ScreenRowState(screen, link.copy(features = listOf(Features.WEB)), folder))
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ScreenCard(row, ScreenActions({ _, _ -> }, {}, { _, _ -> }, {}))
                }
            }
        }
        onNodeWithContentDescription("Run test").assertIsNotEnabled()
        row = row.copy(link = null)
        waitForIdle()
        onNodeWithContentDescription("Run test").assertIsNotEnabled()
    }
}

@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.screenshot.captureComponent
import org.churchpresenter.signage.core.CecState
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.WebTestRun
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.ScreenLink
import kotlin.io.path.Path
import kotlin.test.Test

/**
 * Playlists added by hand and the web page test, in both themes: the list with every kind, the Add
 * menu, the Link dialog for a web page and a stream, and a screen card through the test.
 */
class SignageLinksScreenshotTest {
    private val folder = SignageFolderState(
        folder = Path("/Users/church/Documents/ChurchPresenter/Signage"),
        playlists = listOf(
            PlaylistSummary("folder:", "Signage", 6, 1),
            PlaylistSummary("folder:Events", "Events", 4, 0),
            PlaylistSummary("file:1", "Welcome", kind = SourceKind.PICTURE, detail = "welcome-banner.png"),
            PlaylistSummary("file:2", "Lobby loop", kind = SourceKind.VIDEO, detail = "lobby-loop.mp4"),
            PlaylistSummary("link:1", "Weather", kind = SourceKind.WEB, detail = WEATHER),
            PlaylistSummary("link:2", "Live", kind = SourceKind.STREAM, detail = LIVE),
            PlaylistSummary("file:3", "Old flyer", kind = SourceKind.PICTURE, detail = "flyer.jpg", missing = true),
        ),
        ready = true,
    )
    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_ARM64, "26.0.31", 1920, 1080, 0)
    private val link = ScreenLink(
        "192.168.2.78", 0, "v", session = 1,
        features = listOf(Features.WIPE, Features.STREAM, Features.WEB, Features.WEB_TEST),
        status = PlayerMessage.Status(showingPlaylistId = "folder:", cec = CecState.WORKING),
    )

    @Composable
    private fun Panel() = Box(Modifier.size(PANEL_WIDTH.dp, PANEL_HEIGHT.dp)) {
        SignageFolderPanel(folder, FolderActions({}, {}, FakeDesktop().desktop), Modifier)
    }

    @Composable
    private fun Card(link: ScreenLink) = Box(Modifier.width(CARD_WIDTH.dp)) {
        ScreenCard(ScreenRowState(screen, link, folder), ScreenActions({ _, _ -> }, {}, { _, _ -> }, {}))
    }

    private fun result(at15: Double, at30: Double, cpu: Double, temp: Double?) = PlayerMessage.WebTestResult(
        listOf(WebTestRun(15, at15, cpu * HALF, temp), WebTestRun(30, at30, cpu, temp)),
    )

    @Test
    fun `every kind of playlist`() = captureComponent(SECTION, "playlists") { Panel() }

    @Test
    fun `the add menu`() = captureComponent(SECTION, "add_menu", rootIndex = 1, drive = {
        onNodeWithText("Add").performClick()
        waitForIdle()
    }) { Panel() }

    @Test
    fun `a web page link moving smoothly`() = captureComponent(SECTION, "link_web", rootIndex = 1, drive = {
        onAllNodesWithText("Address")[0].performTextInput(WEATHER)
        onNodeWithText("Smooth motion").performClick()
        waitForIdle()
    }) { LinkDialog(onAdd = {}, onDismiss = {}) }

    @Test
    fun `a stream link`() = captureComponent(SECTION, "link_stream", rootIndex = 1, drive = {
        onAllNodesWithText("Address")[0].performTextInput(LIVE)
        waitForIdle()
    }) { LinkDialog(onAdd = {}, onDismiss = {}) }

    @Test
    fun `editing a link`() = captureComponent(SECTION, "link_edit", rootIndex = 1) {
        LinkDialog(
            onAdd = {},
            onDismiss = {},
            initial = LinkPlaylist("link:1", "Countdown", "http://192.168.2.240/?font-size=300", LinkKind.WEB, 0, true),
        )
    }

    @Test
    fun `editing a picture`() = captureComponent(SECTION, "file_edit", rootIndex = 1) {
        FileDialog(
            FilePlaylist("file:1", "Welcome", "/Users/church/Pictures/welcome.png", MediaKind.IMAGE),
            FakeDesktop().desktop,
            { _, _ -> },
            {},
        )
    }

    @Test
    fun `a card offering the test`() = captureComponent(SECTION, "test_offered") { Card(link) }

    @Test
    fun `a card running the test`() = captureComponent(SECTION, "test_running", drive = {
        // The clock stopped, the spinner is caught on its first frame every time: a short arc.
        mainClock.autoAdvance = false
    }) { Card(link.copy(webTestStartedAtMs = 1)) }

    @Test
    fun `a card whose screen moves smoothly`() = captureComponent(SECTION, "test_smooth") {
        Card(link.copy(webTest = result(15.0, 29.6, 58.0, 54.0)))
    }

    @Test
    fun `a card whose screen manages normal motion and runs hot`() = captureComponent(SECTION, "test_normal_hot") {
        Card(link.copy(webTest = result(14.9, 21.3, 96.0, 82.5)))
    }

    @Test
    fun `a card whose screen is too slow`() = captureComponent(SECTION, "test_too_slow") {
        Card(link.copy(webTest = result(9.4, 9.8, 100.0, 71.0)))
    }

    private companion object {
        const val SECTION = "signage-links"
        const val WEATHER = "https://weather.example.org/city/springfield"
        const val LIVE = "https://www.youtube.com/@gracechurch/live"
        const val PANEL_WIDTH = 520
        const val PANEL_HEIGHT = 640
        const val CARD_WIDTH = 560
        const val HALF = 0.6
    }
}

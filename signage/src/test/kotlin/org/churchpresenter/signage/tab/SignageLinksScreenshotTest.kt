@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import org.churchpresenter.signage.smugmug.SmugMugProgress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.churchpresenter.signage.smugmug.SmugMugAlbum
import org.churchpresenter.signage.smugmug.SmugMugSite
import org.churchpresenter.signage.smugmug.SmugMugState
import org.churchpresenter.signage.smugmug.SmugMugSync
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Playlists added by hand and the web page test, in both themes: the list with every kind and a
 * copy still running, the Add menu, the Link, file and folder dialogs, and a screen card through the
 * test.
 */
class SignageLinksScreenshotTest {
    private val folder = SignageFolderState(
        playlists = listOf(
            PlaylistSummary("folder:", "Welcome", 6, 1, detail = "Folders/Welcome", seconds = 8),
            PlaylistSummary("folder:Events", "Events", 4, 0, detail = "Folders/Events", seconds = 15),
            PlaylistSummary("dir:1", "Announcements", 9, 0, detail = "Announcements", seconds = 12),
            PlaylistSummary("file:1", "Welcome", kind = SourceKind.PICTURE, detail = "welcome-banner.png"),
            PlaylistSummary("file:2", "Lobby loop", kind = SourceKind.VIDEO, detail = "lobby-loop.mp4"),
            PlaylistSummary("link:1", "Weather", kind = SourceKind.WEB, detail = WEATHER),
            PlaylistSummary("link:2", "Live", kind = SourceKind.STREAM, detail = LIVE),
            PlaylistSummary("file:3", "Old flyer", kind = SourceKind.PICTURE, detail = "flyer.jpg", missing = true),
        ),
        ready = true,
        copying = listOf("Easter"),
    )
    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_ARM64, "26.0.31", 1920, 1080, 0)
    private val link = ScreenLink(
        "192.168.2.78", 0, "v", session = 1,
        features = listOf(Features.WIPE, Features.STREAM, Features.WEB, Features.WEB_TEST),
        status = PlayerMessage.Status(showingPlaylistId = "folder:", cec = CecState.WORKING),
    )

    @Composable
    private fun Panel() = Box(Modifier.size(PANEL_WIDTH.dp, PANEL_HEIGHT.dp)) {
        SignageFolderPanel(folder, FolderActions(FakeDesktop().desktop), Modifier)
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

    /** A slideshow picked this afternoon, one picking now, one whose pick failed, and one with no key. */
    @Test
    fun `smugmug slideshows`() = captureComponent(SECTION, "smugmug_rows") {
        val rows = listOf("Lobby photos", "Kids wing", "Youth", "Office")
            .mapIndexed { i, name ->
                PlaylistSummary(
                    "smugmug:$i", name, pictures = 50, kind = SourceKind.SMUGMUG, detail = SMUGMUG_SITE, seconds = 8,
                )
            }
        val state = SignageFolderState(
            playlists = rows,
            ready = true,
            slideshows = mapOf(
                "smugmug:0" to SmugMugState(pickedAtMs = PICKED_AT, photos = 50),
                "smugmug:1" to SmugMugState(pickedAtMs = PICKED_AT, photos = 50),
                "smugmug:2" to SmugMugState(pickedAtMs = PICKED_AT, photos = 50, error = "SmugMug answered 503"),
                "smugmug:3" to SmugMugState(error = SmugMugSync.NO_KEY),
            ),
            picking = setOf("smugmug:1"),
            pickProgress = mapOf("smugmug:1" to SmugMugProgress(SmugMugProgress.Stage.DOWNLOADING, 23, 50)),
        )
        Box(Modifier.size(PANEL_WIDTH.dp, SLIDESHOW_PANEL_HEIGHT.dp)) {
            SignageFolderPanel(state, FolderActions(FakeDesktop().desktop, clock = PINNED_CLOCK), Modifier)
        }
    }

    /** Opened to edit: the site looked up straight away, one gallery left out. */
    @Test
    fun `a smugmug slideshow's galleries`() = captureComponent(SECTION, "smugmug_dialog", rootIndex = 1) {
        SmugMugDialog(
            initial = SmugMugPlaylist.create("Lobby photos").copy(site = SMUGMUG_SITE, excludedAlbumKeys = setOf("a3")),
            apiKey = "0123456789abcdef",
            onApiKey = {},
            lookUp = { _, _ ->
                SmugMugSite(
                    "Grace",
                    listOf(
                        SmugMugAlbum("a1", "Fall harvest", "Public", imageCount = 299),
                        SmugMugAlbum("a2", "Seminar 2026", "Public", imageCount = 202),
                        SmugMugAlbum("a3", "Site images", "Public", imageCount = 12),
                        SmugMugAlbum("a4", "Baptism", "Public", imageCount = 86),
                    ),
                )
            },
            onSave = {},
            onDismiss = {},
            openUrl = {},
        )
    }

    @Test
    fun `the add menu`() = captureComponent(SECTION, "add_menu", rootIndex = 1, drive = {
        onNodeWithText("Add a playlist").performClick()
        waitForIdle()
    }) {
        Box(Modifier.size(PANEL_WIDTH.dp, PANEL_HEIGHT.dp)) {
            PanelWithMenu(folder, FolderActions(FakeDesktop().desktop))
        }
    }

    @Test
    fun `a web page link moving smoothly`() = captureComponent(SECTION, "link_web", rootIndex = 1, drive = {
        field(LINK_ADDRESS_TAG).performTextInput(WEATHER)
        onNodeWithText("Smooth motion").performClick()
        waitForIdle()
    }) { LinkDialog(onAdd = {}, onDismiss = {}) }

    @Test
    fun `a touch page kept to its own sites`() = captureComponent(SECTION, "link_touch", rootIndex = 1) {
        LinkDialog(
            onAdd = {},
            onDismiss = {},
            initial = LinkPlaylist(
                "link:2", "Fall retreat sign-up", "https://www.churchcenter.com/registrations/events/123",
                LinkKind.WEB, interactive = InteractiveLink(120, listOf("pay.churchcenter.com")),
            ),
        )
    }

    /** A photo slideshow while nobody uses the screen, each page with its own reload time. */
    @Test
    fun `a touch page over a slideshow that reloads`() =
        captureComponent(SECTION, "link_touch_idle_page", rootIndex = 1) {
            LinkDialog(
                onAdd = {},
                onDismiss = {},
                initial = LinkPlaylist(
                    "link:3", "Photos", "https://photos.example.org", LinkKind.WEB, refreshMinutes = 60,
                    interactive = InteractiveLink(
                        90, listOf(InteractiveLink.ANY_HOST),
                        idleUrl = "https://photos.example.org/slideshow", idlePrompt = "Tap the screen to start",
                        idleRefreshMinutes = 30,
                    ),
                ),
            )
        }

    /** A playlist while nobody uses the screen: what to show first, then which playlist. */
    @Test
    fun `a touch page over a playlist`() =
        captureComponent(SECTION, "link_touch_idle_playlist", rootIndex = 1) {
            LinkDialog(
                onAdd = {},
                onDismiss = {},
                initial = LinkPlaylist(
                    "link:4", "Photos", "https://photos.example.org", LinkKind.WEB,
                    interactive = InteractiveLink(
                        90, listOf(InteractiveLink.ANY_HOST),
                        idlePlaylistId = "folder:Events", idlePrompt = "Tap the screen to start",
                    ),
                ),
                idlePlaylists = listOf(
                    PlaylistSummary("folder:Events", "Events", 4, 0, detail = "Folders/Events", seconds = 15),
                    PlaylistSummary("file:2", "Lobby loop", kind = SourceKind.VIDEO, detail = "lobby-loop.mp4"),
                ),
            )
        }

    /** Scrolled down to the code, which fills the end of the dialog. */
    @Test
    fun `a page restyled with the church's own code`() = captureComponent(SECTION, "link_code", rootIndex = 1, drive = {
        field(LINK_SCRIPT_TAG).performScrollTo()
        waitForIdle()
    }) {
        LinkDialog(
            onAdd = {},
            onDismiss = {},
            initial = LinkPlaylist(
                "link:3", "Events", "https://www.churchcenter.com/events", LinkKind.WEB,
                pageCode = PageCode(
                    "header, .site-nav, footer {\n  display: none !important;\n}",
                    "document.querySelectorAll('a[href=\"/\"]')\n  .forEach(a => a.href = location.href);",
                ),
            ),
        )
    }

    @Test
    fun `a stream link`() = captureComponent(SECTION, "link_stream", rootIndex = 1, drive = {
        field(LINK_ADDRESS_TAG).performTextInput(LIVE)
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
    fun `how signage works`() = captureComponent(SECTION, "help", rootIndex = 1) { SignageHelpDialog(onDismiss = {}) }

    @Test
    fun `editing a folder`() = captureComponent(SECTION, "folder_edit", rootIndex = 1) {
        FolderDialog(
            PickedFolderPlaylist("folder:Events", "Events", "Folders/Events"),
            Path("/Users/church/Documents/ChurchPresenter/signage/Folders/Events"),
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
        const val SMUGMUG_SITE = "https://photos.example.org"
        const val SLIDESHOW_PANEL_HEIGHT = 330

        /** 2:30 in the afternoon, the day the clock is pinned to. */
        val PINNED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-11-04T18:00:00Z"), ZoneOffset.UTC)
        val PICKED_AT = Instant.parse("2026-11-04T14:30:00Z").toEpochMilli()

        const val SECTION = "signage-links"
        const val WEATHER = "https://weather.example.org/city/springfield"
        const val LIVE = "https://www.youtube.com/@gracechurch/live"
        const val PANEL_WIDTH = 520
        const val PANEL_HEIGHT = 640
        const val CARD_WIDTH = 560
        const val HALF = 0.6
    }
}

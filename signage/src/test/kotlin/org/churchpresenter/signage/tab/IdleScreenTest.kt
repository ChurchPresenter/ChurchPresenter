@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlaylistItem
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a touch page's screen shows while nobody uses it: chosen in the link dialog, kept on the
 * link, and sent in the manifest only to a player that can show it.
 */
class IdleScreenTest {
    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        ChurchPresenterTheme(ThemeMode.LIGHT) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
    }

    private val playlists = listOf(
        PlaylistSummary("folder:Events", "Events", 4, 0),
        PlaylistSummary("file:loop", "Lobby loop", kind = SourceKind.VIDEO),
    )
    private val page = "https://signup.example.org"

    private fun touchPage(touch: InteractiveLink) =
        LinkPlaylist("link:s", "Sign-up", page, LinkKind.WEB, interactive = touch)

    /** The link dialog for [initial], offering [offered] as idle playlists, saving into [saved]. */
    private fun ComposeUiTest.dialog(
        saved: MutableList<NewLink> = mutableListOf(),
        initial: LinkPlaylist = touchPage(InteractiveLink()),
        offered: List<PlaylistSummary> = playlists,
    ) = show { LinkDialog(onAdd = { saved += it }, onDismiss = {}, initial = initial, idlePlaylists = offered) }

    /** Opens the dropdown showing [shown] and picks [option] from its menu. */
    private fun ComposeUiTest.pick(shown: String, option: String) {
        onNodeWithText(shown).performClick()
        waitForIdle()
        onNode(hasText(option) and hasClickAction()).performClick()
        waitForIdle()
    }

    // ── The dialog ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a playlist plays until a tap, with the default line over it`() = runComposeUiTest {
        val saved = mutableListOf<NewLink>()
        dialog(saved)
        pick("Show this page", "Play a playlist")
        pick("Events", "Lobby loop")
        onNodeWithText("Save").performClick()
        val touch = saved.single().interactive!!
        assertEquals("file:loop", touch.idlePlaylistId)
        assertNull(touch.idleUrl)
        assertEquals("Tap the screen to start", touch.idlePrompt)
    }

    @Test
    fun `another page needs a whole address, and an empty line writes nothing`() = runComposeUiTest {
        val saved = mutableListOf<NewLink>()
        dialog(saved)
        pick("Show this page", "Show another web page")
        onNodeWithText("Save").assertIsNotEnabled()
        field(LINK_IDLE_URL_TAG).performTextInput("slides.example.org")
        onNodeWithText("Save").assertIsEnabled()
        field(LINK_IDLE_PROMPT_TAG).performTextReplacement("")
        onNodeWithText("Save").performClick()
        val touch = saved.single().interactive!!
        assertEquals("https://www.slides.example.org", touch.idleUrl)
        assertNull(touch.idlePlaylistId)
        assertNull(touch.idlePrompt)
    }

    @Test
    fun `a saved idle page opens with its address and its own line`() = runComposeUiTest {
        val saved = mutableListOf<NewLink>()
        val initial = touchPage(
            InteractiveLink(idleUrl = "https://slides.example.org", idlePrompt = "Tap to sign up"),
        )
        dialog(saved, initial)
        onNodeWithText("Show another web page").assertExists()
        onNodeWithText("Save").performClick()
        assertEquals(initial.interactive, saved.single().interactive)
    }

    @Test
    fun `a playlist that has gone counts as none, and the page shows itself`() = runComposeUiTest {
        val saved = mutableListOf<NewLink>()
        val initial = touchPage(InteractiveLink(idlePlaylistId = "folder:Gone", idlePrompt = "Tap"))
        dialog(saved, initial)
        onNodeWithText("Show this page").assertExists()
        onNodeWithText("Save").performClick()
        assertEquals(InteractiveLink(), saved.single().interactive)
    }

    @Test
    fun `with nothing to play the dialog says what to add, and cannot save a playlist`() =
        runComposeUiTest {
        dialog(offered = emptyList())
        pick("Show this page", "Play a playlist")
        onNodeWithText("Add a folder, picture or video to the list first.").assertExists()
        onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `a page drawn on the TV keeps no idle screen`() = runComposeUiTest {
        val saved = mutableListOf<NewLink>()
        dialog(saved)
        pick("Show this page", "Play a playlist")
        onNodeWithText("People use it on the touchscreen").performClick()
        onNodeWithText("Save").performClick()
        assertNull(saved.single().interactive)
    }

    // ── Which playlists it offers ───────────────────────────────────────────────────────────────

    @Test
    fun `it offers neither rotations, nor touch pages, nor the link itself`() {
        val signup = touchPage(InteractiveLink())
        val weather = LinkPlaylist("link:w", "Weather", "https://weather.example.org", LinkKind.WEB)
        val state = SignageFolderState(
            playlists = playlists + listOf(
                PlaylistSummary("link:s", "Sign-up", kind = SourceKind.WEB),
                PlaylistSummary("link:w", "Weather", kind = SourceKind.WEB),
                PlaylistSummary("rotation:r", "Mornings", kind = SourceKind.ROTATION),
            ),
            settings = SignageSettings(added = listOf(signup, weather)),
            ready = true,
        )
        val offered = idlePlaylists(state, editing = null).map { it.id }
        assertEquals(listOf("folder:Events", "file:loop", "link:w"), offered)
        assertEquals(listOf("folder:Events", "file:loop"), idlePlaylists(state, editing = "link:w").map { it.id })
    }

    // ── The manifest ────────────────────────────────────────────────────────────────────────────

    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_X64, "26.0.56", 1920, 1080, 0)

    private fun item(touch: InteractiveLink, features: List<String>): PlaylistItem {
        val link = touchPage(touch)
        return buildManifest(
            screen = screen,
            ownerId = "owner",
            settings = SignageSettings(added = listOf(link)),
            playlists = listOf(HashedPlaylist(link)),
            timeZone = "UTC",
            features = features,
        ).first.playlists.single().items.single()
    }

    private val canShow = listOf(Features.WEB, Features.INTERACTIVE, Features.IDLE_SCREEN)

    @Test
    fun `a player that can show an idle screen is sent it, with its line`() {
        val sent = item(InteractiveLink(idlePlaylistId = "folder:Events", idlePrompt = "  Tap  "), canShow)
        assertEquals("folder:Events", sent.idlePlaylistId)
        assertNull(sent.idleUrl)
        assertEquals("Tap", sent.idlePrompt)
        val page = item(InteractiveLink(idleUrl = "https://slides.example.org", idlePrompt = " "), canShow)
        assertEquals("https://slides.example.org", page.idleUrl)
        assertNull(page.idlePrompt, "a blank line writes nothing")
    }

    @Test
    fun `an older player gets the page alone, and a line with no idle screen is not sent`() {
        val older = item(
            InteractiveLink(idlePlaylistId = "folder:Events", idlePrompt = "Tap"),
            listOf(Features.WEB, Features.INTERACTIVE),
        )
        assertNull(older.idlePlaylistId)
        assertNull(older.idlePrompt)
        assertNull(item(InteractiveLink(idlePrompt = "Tap"), canShow).idlePrompt)
    }
}

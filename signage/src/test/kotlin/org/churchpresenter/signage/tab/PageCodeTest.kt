@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
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
 * The church's own CSS and JavaScript for a web page: written in the link dialog, kept on the link,
 * and sent in the manifest only to a player that runs it.
 */
class PageCodeTest {
    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        ChurchPresenterTheme(ThemeMode.LIGHT) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
    }

    private val code = PageCode("footer { display: none }", "document.title = 'Events'")

    private fun link(pageCode: PageCode? = null, touch: InteractiveLink? = null) = LinkPlaylist(
        "link:e", "Events", "https://events.example.org", LinkKind.WEB, interactive = touch, pageCode = pageCode,
    )

    private fun ComposeUiTest.dialog(saved: MutableList<NewLink>, initial: LinkPlaylist) =
        show { LinkDialog(onAdd = { saved += it }, onDismiss = {}, initial = initial) }

    @Test
    fun `nothing written is no code`() {
        assertNull(PageCode.of(" ", "\n"))
        assertEquals(PageCode("a { }", ""), PageCode.of(" a { } ", " "))
    }

    // ── The dialog ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `code written behind the switch is saved, trimmed`() = runComposeUiTest {
        val saved = mutableListOf<NewLink>()
        dialog(saved, link())
        onNodeWithText("Change how the page looks").performClick()
        field(LINK_CSS_TAG).performTextInput("  footer { display: none }\n")
        field(LINK_SCRIPT_TAG).performTextInput("document.title = 'Events'")
        onNodeWithText("Save").performClick()
        assertEquals(code, saved.single().pageCode)
    }

    @Test
    fun `a saved link opens with its code, and the switch off drops it`() = runComposeUiTest {
        val saved = mutableListOf<NewLink>()
        dialog(saved, link(code))
        onNodeWithText("Save").performClick()
        assertEquals(code, saved.single().pageCode)
        onNodeWithText("Change how the page looks").performClick()
        onNodeWithText("Save").performClick()
        assertNull(saved.last().pageCode)
    }

    @Test
    fun `emptied fields keep no code, and a stream keeps none`() = runComposeUiTest {
        val saved = mutableListOf<NewLink>()
        dialog(saved, link(code))
        field(LINK_CSS_TAG).performTextReplacement("")
        field(LINK_SCRIPT_TAG).performTextReplacement(" ")
        onNodeWithText("Save").performClick()
        assertNull(saved.single().pageCode)
        onNodeWithText("Video or live stream").performClick()
        onNodeWithText("Save").performClick()
        assertNull(saved.last().pageCode)
    }

    @Test
    fun `a touch page with an idle page says the code is not added to it`() = runComposeUiTest {
        val saved = mutableListOf<NewLink>()
        dialog(saved, link(code, InteractiveLink(idleUrl = "https://slides.example.org")))
        onNodeWithText("Not added to the page shown while nobody is using the screen.", substring = true)
            .assertExists()
    }

    // ── The manifest ────────────────────────────────────────────────────────────────────────────

    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_X64, "26.0.60", 1920, 1080, 0)

    private fun item(link: LinkPlaylist, features: List<String>): PlaylistItem = buildManifest(
        screen = screen,
        ownerId = "owner",
        settings = SignageSettings(added = listOf(link)),
        playlists = listOf(HashedPlaylist(link)),
        timeZone = "UTC",
        features = features,
    ).first.playlists.single().items.single()

    @Test
    fun `a player that runs it is sent the code, on a drawn page and a touch page`() {
        val drawn = item(link(code), listOf(Features.WEB, Features.PAGE_CODE))
        assertEquals(code.css to code.script, drawn.pageCss to drawn.pageScript)
        val touch = item(
            link(PageCode(css = "nav { }"), InteractiveLink()),
            listOf(Features.WEB, Features.INTERACTIVE, Features.PAGE_CODE),
        )
        assertEquals("nav { }", touch.pageCss)
        assertNull(touch.pageScript, "an empty field is not sent")
    }

    @Test
    fun `an older player gets the page as its site made it`() {
        val older = item(link(code), listOf(Features.WEB))
        assertEquals("https://events.example.org", older.url)
        assertNull(older.pageCss)
        assertNull(older.pageScript)
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.WebBookmark
import org.churchpresenter.web.presenter.WebNavController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Web tab's handlers, driven on the scope directly: where a preview navigation, a title, a zoom
 * and the mobile toggle go — the output, the schedule, the tab's own browser or the live one.
 *
 * A `CefBrowser` cannot be built without a running Chromium, so the browsers here are relaxed mocks,
 * as in `WebNavControllerTest`; what the tab and the output were told is asserted on their own state.
 */
class WebTabScopeTest {

    private val output = FakeWebOutput()
    private val liveBrowser = mockk<CefBrowser>(relaxed = true)
    private val previewBrowser = mockk<CefBrowser>(relaxed = true)
    private val titles = mutableListOf<Pair<String, String>>()

    private fun scope(
        isLive: Boolean,
        interactive: Boolean = false,
        output: FakeWebOutput? = this.output,
        state: WebTabState = WebTabState(savedUrl = "", savedTitle = ""),
    ): WebTabScope {
        this.output.setLiveBrowser(liveBrowser)
        state.useInteractivePreview = interactive
        return WebTabScope(
            output = output,
            appSettings = AppSettings(),
            onSettingsChange = {},
            onAddToSchedule = null,
            onUpdateScheduleTitle = { url, title -> titles += url to title },
            state = state,
            isLive = isLive,
            navController = WebNavController().apply { browser = previewBrowser },
            previewAspectRatio = 16f / 9f,
            outputPicker = {},
        )
    }

    @Test
    fun `a blank saved address starts the bar on https`() {
        assertEquals("https://", WebTabState(savedUrl = "", savedTitle = "").urlInput)
        assertEquals("https://a.org", WebTabState(savedUrl = "https://a.org", savedTitle = "").urlInput)
    }

    @Test
    fun `a preview navigation while live moves the bar, the output and the live browser`() {
        val s = scope(isLive = true)
        s.onPreviewNavigated("https://b.org")

        assertEquals("https://b.org", s.urlInput)
        assertEquals("https://b.org", s.liveUrl)
        assertEquals("https://b.org", output.websiteUrl.value)
        verify { liveBrowser.loadURL("https://b.org") }
    }

    @Test
    fun `a preview navigation while not live leaves the live browser alone`() {
        scope(isLive = false).onPreviewNavigated("https://b.org")

        assertEquals("https://b.org", output.websiteUrl.value)
        verify(exactly = 0) { liveBrowser.loadURL(any()) }
    }

    @Test
    fun `a title reaches the output, and the schedule once a page is loaded`() {
        val s = scope(isLive = false)
        s.onTitleChanged("Before")
        assertEquals("Before", output.webPageTitle.value)
        assertEquals(emptyList(), titles, "nothing loaded yet, so no schedule item to retitle")

        s.liveUrl = "https://a.org"
        s.onTitleChanged("After")
        assertEquals("After", s.pageTitle)
        assertEquals(listOf("https://a.org" to "After"), titles)
    }

    @Test
    fun `zoom goes to the live browser while mirroring, and to the preview's otherwise`() {
        scope(isLive = true).applyZoom(1.0)
        verify { liveBrowser.setZoomLevel(1.0) }
        verify(exactly = 0) { previewBrowser.setZoomLevel(any()) }

        val interactive = scope(isLive = true, interactive = true)
        interactive.applyZoom(2.0)
        assertEquals(2.0, interactive.zoomLevel)
        verify { previewBrowser.setZoomLevel(2.0) }

        scope(isLive = false).applyZoom(3.0)
        verify { previewBrowser.setZoomLevel(3.0) }
    }

    @Test
    fun `the mobile toggle reloads the live browser only while live`() {
        val live = scope(isLive = true)
        live.onMobileToggle(true)
        assertTrue(live.isMobileView)
        verify(exactly = 1) { liveBrowser.reload() }

        scope(isLive = false).onMobileToggle(false)
        verify(exactly = 1) { liveBrowser.reload() }
        verify(exactly = 2) { previewBrowser.reload() }
    }

    @Test
    fun `a title for a loaded page with no schedule to retitle still reaches the output`() {
        val s = WebTabScope(
            output = output,
            appSettings = AppSettings(),
            onSettingsChange = {},
            onAddToSchedule = null,
            onUpdateScheduleTitle = null,
            state = WebTabState(savedUrl = "https://a.org", savedTitle = ""),
            isLive = false,
            navController = WebNavController(),
            previewAspectRatio = 1f,
            outputPicker = {},
        )
        s.onTitleChanged("A")

        assertEquals("A", output.webPageTitle.value)
    }

    @Test
    fun `with no output every handler still updates the tab`() {
        val s = scope(isLive = true, output = null)
        s.onPreviewNavigated("https://c.org")
        s.onTitleChanged("C")
        s.applyZoom(1.5)
        s.onMobileToggle(true)

        assertEquals("https://c.org", s.liveUrl)
        assertEquals("C", s.pageTitle)
        assertEquals(1.5, s.zoomLevel)
        assertTrue(s.isMobileView)
    }

    @Test
    fun `whether the address is bookmarked compares it normalised`() {
        val state = WebTabState(savedUrl = "", savedTitle = "")
        val s = WebTabScope(
            output = null,
            appSettings = AppSettings(webBookmarks = listOf(WebBookmark(url = "https://a.org", title = "A"))),
            onSettingsChange = {},
            onAddToSchedule = null,
            onUpdateScheduleTitle = null,
            state = state,
            isLive = false,
            navController = WebNavController(),
            previewAspectRatio = 1f,
            outputPicker = {},
        )
        s.urlInput = "a.org"
        assertTrue(s.isBookmarked)
        s.urlInput = "b.org"
        assertEquals(false, s.isBookmarked)
    }

    @Test
    fun `a live tab with no output behind it waits for a snapshot rather than failing`() = runComposeUiTest {
        val s = WebTabScope(
            output = null,
            appSettings = AppSettings(),
            onSettingsChange = {},
            onAddToSchedule = null,
            onUpdateScheduleTitle = null,
            state = WebTabState(savedUrl = "https://a.org", savedTitle = "A"),
            isLive = true,
            navController = WebNavController(),
            previewAspectRatio = 1f,
            outputPicker = {},
        )
        setContent {
            MaterialTheme {
                s.WebTabEffects(selectedWebsiteItem = null, selectedWebsiteItemVersion = 0)
                s.WebPreviewCard(Modifier)
            }
        }
        waitForIdle()

        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
        assertEquals("https://a.org", s.liveUrl, "nothing from an absent presenter overwrote the bar")
    }
}

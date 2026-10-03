package org.churchpresenter.web.tabs

import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.WebBookmark
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.web.presenter.WebNavController
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What each toolbar control does, on the tab's scope directly: which browser navigation steers, what
 * the address bar sends, what is bookmarked and scheduled under which title, and what type-to-page
 * types into the live page.
 *
 * A `CefBrowser` cannot be built without Chromium, so the browsers are relaxed mocks, as in
 * `WebNavControllerTest`; the tab's and the output's own state is asserted wherever there is any.
 */
class WebTabActionsTest {

    private val output = FakeWebOutput()
    private val liveBrowser = mockk<CefBrowser>(relaxed = true)
    private val previewBrowser = mockk<CefBrowser>(relaxed = true)
    private val scheduled = mutableListOf<Pair<String, String>>()
    private var settings = AppSettings()

    private fun scope(
        isLive: Boolean = false,
        interactive: Boolean = false,
        attachLive: Boolean = true,
        bookmarks: List<WebBookmark> = emptyList(),
    ): WebTabScope {
        output.setLiveBrowser(if (attachLive) liveBrowser else null)
        settings = settings.copy(webBookmarks = bookmarks)
        val state = WebTabState(savedUrl = "", savedTitle = "").apply { useInteractivePreview = interactive }
        return WebTabScope(
            output = output,
            appSettings = settings,
            onSettingsChange = { settings = it(settings) },
            onAddToSchedule = { url, title -> scheduled += url to title },
            onUpdateScheduleTitle = null,
            state = state,
            isLive = isLive,
            navController = WebNavController().apply { browser = previewBrowser },
            previewAspectRatio = 1f,
            outputPicker = {},
        )
    }

    // ── Navigation ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `while mirroring the live window, navigation steers its browser`() {
        val s = scope(isLive = true)
        s.goBack()
        s.goForward()
        s.refresh()

        verify { liveBrowser.goBack() }
        verify { liveBrowser.goForward() }
        verify { liveBrowser.reload() }
        verify(exactly = 0) { previewBrowser.goBack() }
    }

    @Test
    fun `otherwise navigation steers the preview's browser`() {
        val notMirroring = listOf(
            { scope(isLive = false) },
            { scope(isLive = true, interactive = true) },
            { scope(isLive = true, attachLive = false) },
        )
        for (make in notMirroring) {
            val s = make()
            s.goBack()
            s.goForward()
            s.refresh()
        }

        verify(exactly = 3) { previewBrowser.goBack() }
        verify(exactly = 3) { previewBrowser.goForward() }
        verify(exactly = 3) { previewBrowser.reload() }
        verify(exactly = 0) { liveBrowser.goBack() }
    }

    @Test
    fun `refresh with no preview browser yet does nothing`() {
        val s = scope().also { it.navController.browser = null }
        s.refresh()
    }

    @Test
    fun `clearing the cache empties it and leaves the directory`() {
        val dir = Files.createTempDirectory("webcache").toFile()
        File(dir, "Cache").mkdirs()
        File(dir, "Cache/data_0").writeText("x")

        clearWebCache(dir)

        assertTrue(dir.isDirectory)
        assertEquals(emptyList(), dir.list()?.toList())
        dir.deleteRecursively()

        clearWebCache(dir)
        assertTrue(dir.isDirectory, "a cache that was never made is made empty")
        dir.deleteRecursively()
        clearWebCache(null)
    }

    @Test
    fun `zoom steps half a level each way`() {
        val s = scope()
        s.stepZoom(zoomIn = true)
        assertEquals(ZOOM_STEP, s.zoomLevel)
        s.stepZoom(zoomIn = false)
        s.stepZoom(zoomIn = false)
        assertEquals(-ZOOM_STEP, s.zoomLevel)
    }

    @Test
    fun `the preview toggles between mirroring and browsing on its own`() {
        val s = scope()
        s.toggleInteractivePreview()
        assertTrue(s.useInteractivePreview)
        s.toggleInteractivePreview()
        assertFalse(s.useInteractivePreview)
    }

    // ── The address bar ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `submitting the address normalises it and sends it out, into the live window while live`() {
        val s = scope(isLive = true)
        s.urlInput = " example.com "
        s.submitUrl()

        assertEquals("https://example.com", s.liveUrl)
        assertEquals("https://example.com", output.websiteUrl.value)
        verify { liveBrowser.loadURL("https://example.com") }

        scope(isLive = false).apply { urlInput = "b.org" }.submitUrl()
        verify(exactly = 0) { liveBrowser.loadURL("https://b.org") }
    }

    @Test
    fun `only a real address counts as one`() {
        val s = scope()
        assertFalse(s.hasAddress, "the bar starts on the bare scheme")
        s.urlInput = ""
        assertFalse(s.hasAddress)
        s.urlInput = "a.org"
        assertTrue(s.hasAddress)
    }

    // ── Bookmarks, the schedule and Go Live ─────────────────────────────────────────────────────

    @Test
    fun `a bookmark is saved under the page title, or its address when there is none`() {
        val titled = scope().apply {
            urlInput = "a.org"
            pageTitle = "A Site"
        }
        titled.toggleBookmark()
        assertEquals(listOf(WebBookmark(url = "https://a.org", title = "A Site")), settings.webBookmarks)

        scope().apply { urlInput = "b.org" }.toggleBookmark()
        assertEquals(WebBookmark(url = "https://b.org", title = "https://b.org"), settings.webBookmarks.single())
    }

    @Test
    fun `toggling an address already bookmarked removes it, and only it`() {
        val other = WebBookmark(url = "https://b.org", title = "B")
        val s = scope(bookmarks = listOf(WebBookmark(url = "https://a.org", title = "A"), other))
        s.urlInput = "a.org"
        s.toggleBookmark()

        assertEquals(listOf(other), settings.webBookmarks)
    }

    @Test
    fun `scheduling carries the page title, or the address when there is none`() {
        scope().apply {
            urlInput = "a.org"
            pageTitle = "A Site"
        }.addToSchedule()
        scope().apply { urlInput = "b.org" }.addToSchedule()

        assertEquals(listOf("https://a.org" to "A Site", "https://b.org" to "https://b.org"), scheduled)
    }

    @Test
    fun `going live puts the normalised address on screen`() {
        val s = scope().apply { urlInput = "a.org" }
        s.goLive()

        assertEquals(Presenting.WEBSITE, output.presentingMode.value)
        assertEquals("https://a.org", output.websiteUrl.value)
        assertEquals("https://a.org", s.urlInput)
    }

    @Test
    fun `opening a bookmark loads it, into the live window too while live`() {
        val bookmark = WebBookmark(url = "https://a.org", title = "A")
        scope(isLive = false).openBookmark(bookmark)
        verify(exactly = 0) { liveBrowser.loadURL(any()) }

        val live = scope(isLive = true)
        live.openBookmark(bookmark)
        assertEquals("A", live.pageTitle)
        assertEquals("https://a.org", output.websiteUrl.value)
        verify { liveBrowser.loadURL("https://a.org") }
    }

    @Test
    fun `removing a bookmark keeps the rest`() {
        val a = WebBookmark(url = "https://a.org", title = "A")
        val b = WebBookmark(url = "https://b.org", title = "B")
        scope(bookmarks = listOf(a, b)).removeBookmark(a)

        assertEquals(listOf(b), settings.webBookmarks)
    }

    // ── Type to page ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `type-to-page types only the difference into the live page`() {
        val s = scope(isLive = true)
        s.typeToPage("abc")
        s.typeToPage("abX")

        assertEquals("abX", s.typeBuffer)
        verify(exactly = 1) { liveBrowser.executeJavaScript(WEB_JS_BACKSPACE, "", 0) }
        verify(exactly = 1) { liveBrowser.executeJavaScript(jsInsert('X'), "", 0) }
        verify(exactly = 4) { liveBrowser.executeJavaScript(match { it != WEB_JS_BACKSPACE }, "", 0) }
    }

    @Test
    fun `with no live page, type-to-page only keeps the text`() {
        val s = scope(attachLive = false)
        s.typeToPage("abc")
        s.submitTypeToPage()
        s.focusFirstInput()

        assertEquals("", s.typeBuffer)
    }

    @Test
    fun `submitting presses Enter in the live page and starts afresh, and focusing finds its first field`() {
        val s = scope(isLive = true)
        s.typeToPage("q")
        s.submitTypeToPage()
        s.focusFirstInput()

        assertEquals("", s.typeBuffer)
        verify { liveBrowser.executeJavaScript(WEB_JS_ENTER, "", 0) }
        verify { liveBrowser.executeJavaScript(WEB_JS_FOCUS_FIRST_INPUT, "", 0) }
    }

    // ── With nothing behind the tab ─────────────────────────────────────────────────────────────

    @Test
    fun `with no output and no schedule every action still updates the tab itself`() {
        for (live in listOf(false, true)) {
            val s = WebTabScope(
                output = null,
                appSettings = AppSettings(),
                onSettingsChange = { settings = it(settings) },
                onAddToSchedule = null,
                onUpdateScheduleTitle = null,
                state = WebTabState(savedUrl = "", savedTitle = ""),
                isLive = live,
                navController = WebNavController(),
                previewAspectRatio = 1f,
                outputPicker = {},
            )
            s.urlInput = "a.org"
            s.submitUrl()
            s.addToSchedule()
            s.goLive()
            s.openBookmark(WebBookmark(url = "https://b.org", title = "B"))
            s.typeToPage("x")
            s.submitTypeToPage()
            s.focusFirstInput()
            s.goBack()
            s.refresh()

            assertEquals("https://b.org", s.liveUrl)
            assertEquals("", s.typeBuffer)
        }
        assertEquals(emptyList(), scheduled, "nowhere to schedule to")
    }
}

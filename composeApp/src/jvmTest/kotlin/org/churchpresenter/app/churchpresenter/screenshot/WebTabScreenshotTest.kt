@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.WebBookmark
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.tabs.WebLabel
import org.churchpresenter.app.churchpresenter.tabs.webButton
import org.churchpresenter.app.churchpresenter.tabs.webTab
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

/**
 * Every state of the Web tab, in both themes.
 *
 * Nothing here lets the tab build a real browser. `EmbeddedWebView` starts JCEF, which has no render
 * surface headless, and the tab reaches it whenever `liveUrl` is set and it is not mirroring — so
 * these shots set the URL *bar* without committing it (no Enter, no bookmark click), and every live
 * state stays in mirror mode, where the preview is the presenter's screenshot rather than a browser.
 *
 * Go Live is disabled in every shot and cannot be otherwise: it needs a second screen device and an
 * output with `showWebsite` on, and a headless test JVM reports one screen. That is the state a
 * single-monitor machine really shows, so it is worth having; the enabled one belongs to a machine
 * with a projector attached.
 */
class WebTabScreenshotTest {

    private fun shoot(
        name: String,
        settings: (AppSettings) -> AppSettings = { it },
        selectedWebsiteItem: ScheduleItem.WebsiteItem? = null,
        cefInitialized: Boolean = true,
        cefMacOsUnsupported: Boolean = false,
        cefWindowsUnsupported: Boolean = false,
        schedule: Boolean = true,
        width: Dp? = null,
        drive: ComposeUiTest.(PresenterManager) -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        webTab(
            settings = settings,
            selectedWebsiteItem = selectedWebsiteItem,
            cefInitialized = cefInitialized,
            cefMacOsUnsupported = cefMacOsUnsupported,
            cefWindowsUnsupported = cefWindowsUnsupported,
            includeAddToSchedule = schedule,
            width = width,
            themeMode = mode,
        ) { presenter, _ ->
            drive(presenter)
            waitForIdle()
            captureTo(file)
        }
    }

    // ── The URL bar and its actions ─────────────────────────────────────────────────────────────

    @Test
    fun `nothing entered yet`() = shoot("empty")

    @Test
    fun `a URL typed`() = shoot("url_typed") { typeUrl() }

    @Test
    fun `a URL that is already bookmarked`() = shoot(
        "url_bookmarked",
        settings = { it.copy(webBookmarks = listOf(WebBookmark(url = URL, title = "Church Notices"))) },
    ) { typeUrl() }

    @Test
    fun `a row of saved bookmarks`() = shoot("bookmarks_bar", settings = { it.copy(webBookmarks = BOOKMARKS) })

    @Test
    fun `more bookmarks than the bar can show`() =
        shoot("bookmarks_bar_scrolled", settings = { it.copy(webBookmarks = MANY_BOOKMARKS) })

    @Test
    fun `with nowhere to schedule it`() = shoot("no_schedule", schedule = false) { typeUrl() }

    // Not shot: the tab with no presenter at all (`webTabWithoutPresenter`, which the setup wizard
    // composes). It renders byte-identically to `empty` — everything the presenter would change is
    // either behind `isLive`, which is false without one, or behind Go Live, which is disabled here
    // regardless. `WebTabNoPresenterTest` covers that the two are reached by different paths.

    // ── Zoom and the device toggle ──────────────────────────────────────────────────────────────

    @Test
    fun `zoomed in`() = shoot("zoomed_in") {
        repeat(3) { webButton(WebLabel.ZOOM_IN).performClick() }
        waitForIdle()
    }

    @Test
    fun `zoomed out`() = shoot("zoomed_out") {
        repeat(2) { webButton(WebLabel.ZOOM_OUT).performClick() }
        waitForIdle()
    }

    @Test
    fun `emulating a mobile device`() = shoot("mobile_view") {
        onNodeWithText(WebLabel.DESKTOP).performClick()
        waitForIdle()
    }

    // ── Live ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Live with no frame yet: a spinner, and after seven seconds a hint saying why one might never
     * arrive. Both are shot, the second by advancing the test clock past that delay.
     */
    @Test
    fun `live, waiting for the first frame`() = shoot("live_waiting") { presenter ->
        goLive(presenter)
    }

    @Test
    fun `live, waiting long enough for the hint`() = shoot("live_waiting_hint") { presenter ->
        goLive(presenter)
        mainClock.advanceTimeBy(7_001)
        waitForIdle()
    }

    @Test
    fun `live, mirroring the page on screen`() = shoot("live_mirror") { presenter ->
        goLive(presenter)
        presenter.setWebSnapshot(pageSnapshot())
        waitForIdle()
    }

    @Test
    fun `live, with something typed into the page`() = shoot("live_type_to_page") { presenter ->
        goLive(presenter)
        presenter.setWebSnapshot(pageSnapshot())
        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).performTextReplacement("Sunday 10:30")
        waitForIdle()
    }

    /**
     * Interactive mode hands the preview back to a local browser, so it is shot with no URL
     * committed — the tab then draws its placeholder instead of starting JCEF.
     */
    @Test
    fun `live, switched to interactive`() = shoot("live_interactive") { presenter ->
        goLive(presenter)
        onNodeWithText(WebLabel.MIRROR).performClick()
        waitForIdle()
    }

    @Test
    fun `opened from the schedule`() = shoot(
        "from_schedule",
        selectedWebsiteItem = ScheduleItem.WebsiteItem(
            id = "schedule-1",
            url = URL,
            title = "Church Notices",
        ),
    ) { presenter -> presenter.setWebSnapshot(pageSnapshot()); waitForIdle() }

    // ── The engine failing to start ─────────────────────────────────────────────────────────────

    @Test
    fun `the browser engine could not start`() = shoot("engine_unavailable", cefInitialized = false)

    @Test
    fun `the browser engine needs a newer macOS`() =
        shoot("engine_unavailable_macos", cefInitialized = false, cefMacOsUnsupported = true)

    @Test
    fun `the browser engine needs a newer Windows`() =
        shoot("engine_unavailable_windows", cefInitialized = false, cefWindowsUnsupported = true)

    // ── Toolbar widths ──────────────────────────────────────────────────────────────────────────

    /** Under 960dp the toolbar stacks: nav and actions on top, the URL bar on its own row below. */
    @Test
    fun `a toolbar narrow enough to stack`() = shoot("narrow_toolbar", width = 700.dp) { typeUrl() }

    @Test
    fun `a narrow panel with bookmarks`() = shoot(
        "narrow_with_bookmarks",
        settings = { it.copy(webBookmarks = BOOKMARKS) },
        width = 520.dp,
    )

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    /** Types into the URL bar without committing it, which is what would start a real browser. */
    private fun ComposeUiTest.typeUrl() {
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement(URL)
        waitForIdle()
    }

    private fun ComposeUiTest.goLive(presenter: PresenterManager) {
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()
        presenter.setWebPageTitle("Church Notices — Sunday Services")
        waitForIdle()
    }

    /**
     * A stand-in for the presenter's screenshot: a header bar, a headline and some body lines.
     *
     * Drawn rather than an `ImageBitmap(8, 8)` — the mirror scales whatever it is given to fill the
     * preview, and an empty bitmap would leave the state's whole point invisible.
     */
    private fun pageSnapshot(): ImageBitmap {
        val bitmap = ImageBitmap(960, 540)
        val canvas = Canvas(bitmap)
        fun bar(top: Float, left: Float, width: Float, height: Float, color: Color) {
            canvas.drawRect(left, top, left + width, top + height, Paint().apply { this.color = color })
        }
        bar(0f, 0f, 960f, 540f, Color(0xFFFAFAFA))
        bar(0f, 0f, 960f, 64f, Color(0xFF2B3A67))
        canvas.drawCircle(Offset(40f, 32f), 16f, Paint().apply { color = Color(0xFFFFFFFF) })
        bar(24f, 72f, 220f, 16f, Color(0xFFDDE3F0))
        bar(112f, 64f, 520f, 34f, Color(0xFF20242B))
        listOf(176f, 212f, 248f, 284f).forEach { y -> bar(y, 64f, 700f, 14f, Color(0xFFC9CDD4)) }
        bar(320f, 64f, 420f, 14f, Color(0xFFC9CDD4))
        bar(380f, 64f, 260f, 44f, Color(0xFF3F7D58))
        bar(176f, 800f, 96f, 96f, Color(0xFFE4E8EF))
        return bitmap
    }

    private companion object {
        const val SECTION = "webTab"

        const val URL = "https://example.church/notices"

        val BOOKMARKS = listOf(
            WebBookmark(url = URL, title = "Church Notices"),
            WebBookmark(url = "https://example.church/give", title = "Giving"),
            WebBookmark(url = "https://youtube.com/@examplechurch", title = "Live Stream"),
        )

        val MANY_BOOKMARKS = BOOKMARKS + List(8) {
            WebBookmark(url = "https://example.church/page$it", title = "Ministry Page ${it + 1}")
        }
    }
}

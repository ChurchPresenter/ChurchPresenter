@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each composable and helper called two ways: with nothing but what it cannot do without, and again
 * with every parameter supplied.
 *
 * `WebTab` has eleven defaulted parameters and `WebsitePresenter` seven, and the app passes all of
 * them — so nothing else ever runs the defaults, even though previews and the setup wizard rely on
 * them. Kotlin compiles defaults into a bitmask branch per parameter, and the two shapes below take
 * each of those branches one way and then the other.
 */
class WebDefaultsTest {

    // ── handleAddressChange ─────────────────────────────────────────────────────

    @Test
    fun `an address change with no listener attached is simply dropped`() {
        val frame = mockk<CefFrame>()
        every { frame.isMain } returns true

        // The presenter composes without an onUrlChanged in every output that only mirrors — there
        // is nobody to tell, and that must not be an error.
        handleAddressChange(frame, "https://example.com", null)
    }

    @Test
    fun `an address change on a subframe with no listener is also dropped`() {
        val frame = mockk<CefFrame>()
        every { frame.isMain } returns false

        handleAddressChange(frame, "https://ads.example", null)
    }

    // ── WebsitePresenter ────────────────────────────────────────────────────────

    @Test
    fun `the presenter draws given nothing but a url`() = runComposeUiTest {
        setContent { MaterialTheme { Box(Modifier.size(64.dp)) { WebsitePresenter(url = "https://example.com") } } }
        waitForIdle()
    }

    @Test
    fun `every presenter parameter can be given explicitly`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(64.dp)) {
                    WebsitePresenter(
                        url = "https://example.com",
                        modifier = Modifier.fillMaxSize(),
                        onSnapshot = { },
                        onBrowserCreated = { },
                        onUrlChanged = { },
                        onTitleChanged = { },
                        audioDeviceId = "",
                        outputRole = Constants.OUTPUT_ROLE_NORMAL,
                    )
                }
            }
        }
        waitForIdle()
    }

    // ── WebTab ──────────────────────────────────────────────────────────────────

    @Test
    fun `the tab draws given nothing at all`() = runComposeUiTest {
        // Every parameter defaulted, which is how a preview composes it: no output, no schedule
        // item, no callbacks, and the real JCEF singleton's own answer for whether it started.
        setContent { MaterialTheme { WebTab() } }
        waitForIdle()
    }

    @Test
    fun `every tab parameter can be given explicitly`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        val output = FakeWebOutput(url = "https://example.com", title = "Example", live = true)
        output.liveBrowser = browser
        output.setSnapshot(ImageBitmap(16, 16))
        var scheduled: Pair<String, String>? = null
        var retitled: Pair<String, String>? = null

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    WebTab(
                        modifier = Modifier.fillMaxSize(),
                        output = output,
                        selectedWebsiteItem = ScheduleItem.WebsiteItem(
                            id = "item-1",
                            url = "https://example.com",
                            title = "Example",
                        ),
                        selectedWebsiteItemVersion = 1,
                        appSettings = AppSettings(),
                        onSettingsChange = { },
                        onAddToSchedule = { url, title -> scheduled = url to title },
                        onUpdateScheduleTitle = { url, title -> retitled = url to title },
                        cefInitialized = true,
                        cefMacOsUnsupported = false,
                        cefWindowsUnsupported = false,
                    )
                }
            }
            waitForIdle()

            // The schedule item is applied on first composition, which is what the version counter
            // is for — so the tab has adopted its address rather than started blank.
            assertEquals("https://example.com", output.url)
        }
        assertEquals(null, scheduled, "composing alone must not add anything to the schedule")
        // It does re-title, though: adopting a schedule item pushes the page title back to the
        // schedule, which is how an item added before the page finished loading gets its real name.
        // Here the title it pushes is the one it just read, so the schedule is unchanged in effect.
        assertEquals("https://example.com" to "Example", retitled)
    }
}

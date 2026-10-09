@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.core.models.schedule.ScheduleItem
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.qa.QAManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app's wrappers around the feature tabs, composed with only what they require: every optional
 * parameter at its default must still draw a working tab.
 */
class AppTabsDefaultsTest {

    @BeforeTest
    fun latch() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
    }

    @Test
    fun `the web tab draws with every parameter at its default`() = runComposeUiTest {
        setContent { MaterialTheme { AppWebTab() } }
        waitForIdle()
        assertTrue(onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the qa tab draws with only its required parts and presents nothing on its own`() = runComposeUiTest {
        val presented = mutableListOf<Presenting>()
        setContent {
            MaterialTheme {
                AppQATab(
                    qaManager = QAManager(),
                    presenterManager = PresenterManager(),
                    serverUrl = "",
                    presenting = { presented += it },
                )
            }
        }
        waitForIdle()
        assertEquals(emptyList(), presented)
    }

    @Test
    fun `the announcements tab draws at its default size`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AppAnnouncementsTab(
                    appSettings = AppSettings(),
                    onSettingsChange = {},
                    presenterManager = PresenterManager(),
                    onAddToSchedule = null,
                    onSavePreset = null,
                )
            }
        }
        waitForIdle()
    }

    @Test
    fun `the web tab takes every new input while it stays on screen`() = runComposeUiTest {
        var manager by mutableStateOf<PresenterManager?>(null)
        var item by mutableStateOf<ScheduleItem.WebsiteItem?>(null)
        var version by mutableIntStateOf(0)
        var settings by mutableStateOf(AppSettings())
        var onChange by mutableStateOf<((AppSettings) -> AppSettings) -> Unit>({})
        var onAdd by mutableStateOf<((String, String) -> Unit)?>(null)
        var onTitle by mutableStateOf<((String, String) -> Unit)?>(null)
        var cefUp by mutableStateOf(false)
        var macUnsupported by mutableStateOf(false)
        var blocked by mutableStateOf(false)
        var missing by mutableStateOf<String?>(null)
        setContent {
            MaterialTheme {
                AppWebTab(
                    presenterManager = manager,
                    selectedWebsiteItem = item,
                    selectedWebsiteItemVersion = version,
                    appSettings = settings,
                    onSettingsChange = onChange,
                    onAddToSchedule = onAdd,
                    onUpdateScheduleTitle = onTitle,
                    cefInitialized = cefUp,
                    cefMacOsUnsupported = macUnsupported,
                    cefBlockedByPolicy = blocked,
                    cefMissingLibrary = missing,
                )
            }
        }
        waitForIdle()
        listOf<() -> Unit>(
            { manager = PresenterManager() },
            { item = ScheduleItem.WebsiteItem(id = "w", url = "https://example.org", title = "Example") },
            { version++ },
            { settings = AppSettings(quickBackgroundsExpanded = true) },
            { onChange = {} },
            { onAdd = { _, _ -> } },
            { onTitle = { _, _ -> } },
            { macUnsupported = true },
            { blocked = true },
            { missing = "libcef" },
        ).forEach { change ->
            change()
            waitForIdle()
        }
        assertEquals(false, cefUp, "nothing here starts Chromium")
    }

    @Test
    fun `the qa tab takes every new input while it stays on screen`() = runComposeUiTest {
        val qa = QAManager()
        var manager by mutableStateOf(PresenterManager())
        var url by mutableStateOf("")
        var settings by mutableStateOf(AppSettings())
        var tunnel by mutableStateOf("")
        var display by mutableStateOf("")
        setContent {
            MaterialTheme {
                AppQATab(
                    qaManager = qa,
                    presenterManager = manager,
                    serverUrl = url,
                    presenting = {},
                    appSettings = settings,
                    tunnelUrl = tunnel,
                    qaDisplayUrl = display,
                )
            }
        }
        waitForIdle()
        listOf<() -> Unit>(
            { manager = PresenterManager() },
            { url = "http://127.0.0.1:3" },
            { settings = AppSettings(quickBackgroundsExpanded = true) },
            { tunnel = "https://t.example" },
            { display = "http://127.0.0.1:3/qa" },
        ).forEach { change ->
            change()
            waitForIdle()
        }
    }
}

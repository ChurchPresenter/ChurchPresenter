@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

internal class WebReports {
    val scheduled = mutableListOf<Pair<String, String>>()
    val titleUpdates = mutableListOf<Pair<String, String>>()
    var settingsChanges = 0
    var settingsAfterChange: AppSettings? = null
}

@OptIn(ExperimentalTestApi::class)
internal fun webTab(
    presenterManager: PresenterManager = PresenterManager(),
    selectedWebsiteItem: ScheduleItem.WebsiteItem? = null,
    settings: (AppSettings) -> AppSettings = { it },
    cefInitialized: Boolean = true,
    cefMacOsUnsupported: Boolean = false,
    includeAddToSchedule: Boolean = true,
    /**
     * Constrains the tab's width.
     *
     * The toolbar lays itself out from `BoxWithConstraints`, sitting in one row above
     * navButtonsWidth(440dp) + minUrlWidth(200dp) + actionButtonsWidth(320dp) and stacking into two
     * rows below it. Left null everywhere else, which gives the tab the whole test window — the
     * single-row branch.
     */
    width: Dp? = null,
    themeMode: ThemeMode? = null,
    block: ComposeUiTest.(presenter: PresenterManager, reports: WebReports) -> Unit,
) {
    TestSingletons.latchToTestHome()
    val appSettings = settings(AppSettings())
    val reports = WebReports()
    runComposeUiTest {
        setContent {
            ThemedForTest(themeMode) {
                Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                    WebTab(
                        presenterManager = presenterManager,
                        selectedWebsiteItem = selectedWebsiteItem,
                        appSettings = appSettings,
                        onSettingsChange = { transform ->
                            reports.settingsChanges++
                            reports.settingsAfterChange = transform(reports.settingsAfterChange ?: appSettings)
                        },
                        onAddToSchedule =
                            if (includeAddToSchedule) { url, title -> reports.scheduled += url to title } else null,
                        onUpdateScheduleTitle = { url, title -> reports.titleUpdates += url to title },
                        cefInitialized = cefInitialized,
                        cefMacOsUnsupported = cefMacOsUnsupported,
                    )
                }
            }
        }
        block(presenterManager, reports)
    }
}

/**
 * Renders the tab with **no** [PresenterManager] at all — the parameter's own default.
 *
 * `WebTab` reaches the presenter through about forty `presenterManager?.` calls, and [webTab] always
 * supplies one, so every null side of those went untaken. This is not a synthetic case: the tab is
 * declared with `presenterManager: PresenterManager? = null` and previews and the setup wizard
 * compose it that way, so the whole toolbar has to stay usable with nothing behind it.
 */
@OptIn(ExperimentalTestApi::class)
internal fun webTabWithoutPresenter(
    settings: (AppSettings) -> AppSettings = { it },
    selectedWebsiteItem: ScheduleItem.WebsiteItem? = null,
    themeMode: ThemeMode? = null,
    block: ComposeUiTest.(reports: WebReports) -> Unit,
) {
    TestSingletons.latchToTestHome()
    val appSettings = settings(AppSettings())
    val reports = WebReports()
    runComposeUiTest {
        setContent {
            ThemedForTest(themeMode) {
                WebTab(
                    presenterManager = null,
                    selectedWebsiteItem = selectedWebsiteItem,
                    appSettings = appSettings,
                    onSettingsChange = { transform ->
                        reports.settingsChanges++
                        reports.settingsAfterChange = transform(reports.settingsAfterChange ?: appSettings)
                    },
                    onAddToSchedule = { url, title -> reports.scheduled += url to title },
                    onUpdateScheduleTitle = { url, title -> reports.titleUpdates += url to title },
                    cefInitialized = true,
                    cefMacOsUnsupported = false,
                )
            }
        }
        block(reports)
    }
}

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}

internal object WebLabel {
    const val BOOKMARK_ADD = "Add Bookmark"
    const val BOOKMARK_REMOVE = "Remove Bookmark"
    const val ADD_TO_SCHEDULE = "Add to Schedule"
    const val GO_LIVE = "Go Live"
    const val BACK = "Back"
    const val FORWARD = "Forward"
    const val REFRESH = "Refresh"
    const val CLEAR_CACHE = "Clear Cache"
    const val ZOOM_IN = "Zoom In"
    const val ZOOM_OUT = "Zoom Out"
    const val FOCUS_FIRST_INPUT = "Focus first input on page"
    const val MOBILE = "Mobile"
    const val DESKTOP = "Desktop"
    const val LIVE_BADGE = "LIVE"
    const val MIRROR = "Mirror"
    const val INTERACTIVE = "Interactive"
    const val URL_PLACEHOLDER_DEFAULT = "https://"
    const val PREVIEW_HINT = "Enter a URL above and tap Go Live"
    const val TYPE_TO_PAGE_PLACEHOLDER = "Click an input on the live page first"
    const val ENGINE_UNAVAILABLE_TITLE = "Web browser unavailable"
    const val ENGINE_UNAVAILABLE_BODY =
            "The browser engine could not start. Install the Microsoft Visual C++ Redistributable (x64) and restart " +
                "the app."
    const val ENGINE_UNAVAILABLE_MACOS_TITLE = "Web browser requires a newer macOS"
    const val ENGINE_UNAVAILABLE_MACOS_BODY =
            "ChurchPresenter's browser engine no longer supports this version of macOS. Update to macOS 12 " +
                "(Monterey) or later to use the Web tab and browser sources."
    const val SNAPSHOT_WAITING = "Waiting for snapshot..."
    const val SNAPSHOT_SCREEN_RECORDING_HINT =
        "If this persists, grant Screen Recording permission\n" +
            "in System Settings > Privacy & Security > Screen Recording"
}

internal fun zoomPercentText(level: Double): String = "${(Math.pow(1.2, level) * 100).toInt()}%"

internal fun ComposeUiTest.webButton(label: String): SemanticsNodeInteraction = onNodeWithContentDescription(label)

internal fun ComposeUiTest.hasWebButton(label: String): Boolean =
    onAllNodesWithContentDescription(label).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.composables.SCANNING_ROW_TAG
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.settings.TabLabelStyle
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialogContent
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AppPreviewSettingsScreenshotTest {

    /** The picker's "Recent" row is JVM-wide state — see [PinnedRecentColors]. */
    private val recents = PinnedRecentColors()

    @BeforeTest
    fun pinRecentColors() = recents.clear()

    @AfterTest
    fun unpinRecentColors() = recents.restore()

    private fun settingsTab(name: String, tab: Int, settings: AppSettings = library()) {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        val appSettings = settings
        THEMES.forEach { (suffix, mode) ->
            runSkikoComposeUiTest(size = Size(1400f, 900f), density = Density(1f)) {
                setContent {
                    OptionsDialogContent(
                        theme = mode,
                        settingsManager = SettingsManager(),
                        companionServer = CompanionServer(),
                        remoteClientManager = RemoteClientManager(),
                        onDismiss = {},
                        initialTab = tab,
                        initialSettings = appSettings,
                        detectScreens = { emptyList() },
                    )
                }
                waitForIdle()
                // Several tabs read something off disk in the background — the song folder on the
                // System tab, the Bible folder on the Bible tab, VLC's device list on Projection —
                // and show a ScanningRow until it lands. What replaces it is different text in a
                // block of a different height, so everything under it moves and a capture taken
                // mid-scan is a different picture every run. Waiting on the tag rather than on each
                // label covers all of them at once. The condition starts out true and flips when
                // the last scan ends, so this waits on the work finishing, not on a clock; on the
                // tabs that never scan there is nothing to wait for and it returns at once.
                waitUntil(timeoutMillis = RENDER_TIMEOUT_MS) {
                    onAllNodesWithTag(SCANNING_ROW_TAG)
                        .fetchSemanticsNodes(atLeastOneRootRequired = false)
                        .isEmpty()
                }
                captureTo(File("$SCREENSHOT_ROOT/previewApp/settings_${name}_$suffix.png"))
            }
        }
    }

    @Test
    fun appearance() = settingsTab("appearance", 0)

    @Test
    fun bible() = settingsTab("bible", 1)

    @Test
    fun background() = settingsTab("background", 2)

    @Test
    fun projection() = settingsTab("projection", 4)

    @Test
    fun server() = settingsTab("server", 5)

    @Test
    fun atem() = settingsTab("atem", 6)

    @Test
    fun `companion satellite`() = settingsTab("companion_satellite", 7)

    // The dialog's own tab row follows the General → Tab labels setting, so it is shot in the two
    // styles the System tab does not open with, and at the widest spacing, on the System tab where
    // the buttons that pick them are in the same picture.

    @Test
    fun `tabs as icons and text`() =
        settingsTab("tabs_icons_and_text", 0, library().copy(tabLabelStyle = TabLabelStyle.ICONS_AND_TEXT))

    @Test
    fun `tabs as icons`() = settingsTab("tabs_icons", 0, library().copy(tabLabelStyle = TabLabelStyle.ICONS))

    @Test
    fun `tabs with large spacing`() =
        settingsTab("tabs_spacing_large", 0, library().copy(tabLabelMargin = TabLabelMargin.LARGE))

}

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import org.churchpresenter.app.churchpresenter.composables.LocalFontPreviewFace
import org.churchpresenter.app.churchpresenter.dialogs.tabs.LocalDefaultCalendarFolder
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialogContent
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.churchpresenter.settings.OutputProfile
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick

class AppPreviewSettingsScreenshotTest {

    /** The picker's "Recent" row is JVM-wide state — see [PinnedRecentColors]. */
    private val recents = PinnedRecentColors()

    @BeforeTest
    fun pinRecentColors() = recents.clear()

    @AfterTest
    fun unpinRecentColors() = recents.restore()

    private fun settingsTab(
        name: String,
        tab: Int,
        settings: AppSettings = library(),
        drive: ComposeUiTest.() -> Unit = {},
    ) {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        val appSettings = settings
        THEMES.forEach { (suffix, mode) ->
            runSkikoComposeUiTest(size = Size(1400f, 900f), density = Density(1f)) {
                setContent {
                    // Every font name drawn in one face rather than its own. The dialog's font
                    // dropdowns otherwise render against the host's font book, which is what made
                    // `settings_bible` disagree from machine to machine. See `LocalFontPreviewFace`.
                    // And the calendar's default folder with it: the System tab prints it, and with
                    // none configured it is the machine's app data folder — which under test lands
                    // inside the working copy. See `LocalDefaultCalendarFolder` (issue #617).
                    CompositionLocalProvider(
                        LocalFontPreviewFace provides { FontFamily.Default },
                        LocalDefaultCalendarFolder provides PINNED_CALENDAR_FOLDER,
                    ) {
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
                drive()
                waitForIdle()
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
    fun profiles() = settingsTab("profiles", 3)

    @Test
    fun `profiles with several`() = settingsTab(
        "profiles_several",
        3,
        library().let { base ->
            val projection = base.projectionSettings
            base.copy(
                projectionSettings = projection.copy(
                    outputProfiles = projection.outputProfiles +
                        OutputProfile(id = "profile2", name = "Lower Third") +
                        OutputProfile(id = "profile3", name = "Stage Confidence"),
                ),
            )
        },
    )

    @Test
    fun `profiles songs section`() = settingsTab("profiles_songs", 3) {
        onAllNodesWithText("Songs").let { it[it.fetchSemanticsNodes().lastIndex] }.performClick()
    }

    @Test
    fun `profiles background section`() = settingsTab("profiles_background", 3) {
        onAllNodesWithText("Background").let { it[it.fetchSemanticsNodes().lastIndex] }.performClick()
    }

    @Test
    fun `profiles lower third mode`() = settingsTab("profiles_lower_third", 3) {
        onAllNodesWithText("Lower Third")[0].performClick()
    }

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


    private companion object {
        /**
         * Stands in for the machine's app data folder, which the System tab's Calendar row prints.
         *
         * The same literal `SystemSettingsTabScreenshotTest` uses, so the row reads alike in both
         * the tab's own images and the ones taken through the settings dialog.
         */
        const val PINNED_CALENDAR_FOLDER = "/Users/church/Library/Application Support/ChurchPresenter"
    }
}

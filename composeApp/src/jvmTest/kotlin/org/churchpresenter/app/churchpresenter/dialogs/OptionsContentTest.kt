@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.composables.TAB_STRIP_ARROW_BACK_TAG
import org.churchpresenter.app.churchpresenter.composables.TAB_STRIP_ARROW_FORWARD_TAG
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OptionsContentTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        // Pin the JVM-wide log path to the real test home before swapping user.home below: this test
        // builds a PresenterManager and a CompanionServer, whose Instance Link paths log, and
        // InstanceLinkLogger keeps whatever user.home pointed at the first time anything logged.
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-options-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    /** Narrow enough that the tab strip always overflows, whatever the tab count. */
    private val OVERFLOWING_STRIP_WIDTH = 500.dp

    private class Result {
        var dismissed = 0
        var saved: AppSettings? = null
    }

    private fun dialog(
        initialTab: Int = 0,
        obsManager: OBSWebSocketManager? = null,
        // Pinned only by the overflow-arrows test, so that it stays a test of the arrows rather
        // than of how many tabs happen to exist: removing a tab must not silently make it vacuous.
        width: Dp = Dp.Unspecified,
        block: ComposeUiTest.(Result) -> Unit,
    ) {
        val result = Result()
        runComposeUiTest {
            setContent {
              Box(modifier = if (width == Dp.Unspecified) Modifier else Modifier.width(width)) {
                OptionsDialogContent(
                    theme = ThemeMode.LIGHT,
                    settingsManager = SettingsManager(),
                    companionServer = CompanionServer(),
                    remoteClientManager = RemoteClientManager(),
                    presenterManager = PresenterManager(),
                    onDismiss = { result.dismissed++ },
                    onSave = { result.saved = it },
                    obsManager = obsManager,
                    initialTab = initialTab,
                    detectScreens = { emptyList() },
                )
              }
            }
            block(result)
        }
    }

    /**
     * The tab named [label], rather than any text on the tab it opens.
     *
     * The open tab's own content names the same things its tabs do — the System tab lists a Bible
     * folder, a Song folder — so a bare text match finds two nodes and fails on the ambiguity.
     * Only the tab itself is selectable.
     */
    private fun ComposeUiTest.tab(label: String) = onNode(hasText(label) and isSelectable())

    @Test
    fun `every settings tab is shown without an OBS connection`() = dialog {
        listOf(
            "System", "Bible", "Song", "Background", "Projection",
            "Server", "Stage Monitor", "ATEM", "Dictionary", "Companion Satellite",
        ).forEach { tab(it).assertExists() }
        onNodeWithText("OBS").assertDoesNotExist()
    }

    @Test
    fun `the System tab is selected by default`() = dialog {
        tab("System").assertIsSelected()
    }

    @Test
    fun `clicking a different tab switches the selection`() = dialog {
        tab("Bible").performClick()
        tab("Bible").assertIsSelected()
        tab("System").assertIsNotSelected()
    }

    @Test
    fun `initialTab opens directly on that tab`() = dialog(initialTab = 3) {
        tab("Background").assertIsSelected()
    }

    @Test
    fun `an out-of-range initialTab is coerced onto the last real tab`() = dialog(initialTab = 999) {
        tab("Companion Satellite").assertIsSelected()
    }

    @Test
    fun `Cancel dismisses without saving`() = dialog { result ->
        onNodeWithText("Cancel", substring = true).performClick()

        assertEquals(1, result.dismissed)
        assertNull(result.saved)
    }

    @Test
    fun `Apply saves without dismissing`() = dialog { result ->
        onNodeWithText("Apply").performClick()

        assertEquals(0, result.dismissed)
        assertEquals(ThemeMode.SYSTEM.name, result.saved?.theme)
        assertEquals(ThemeMode.SYSTEM.name, SettingsManager().loadSettings().theme)
    }

    @Test
    fun `OK saves and dismisses`() = dialog { result ->
        onNodeWithText("OK", substring = true).performClick()

        assertEquals(1, result.dismissed)
        assertEquals(ThemeMode.SYSTEM.name, result.saved?.theme)
        assertEquals(ThemeMode.SYSTEM.name, SettingsManager().loadSettings().theme)
    }

    @Test
    fun `every tab renders its own settings content when selected`() = dialog {
        listOf(
            "System", "Song", "Background", "Projection", "Server", "ATEM",
        ).forEach { label ->
            onNode(hasText(label) and hasClickAction()).performClick()
            onNode(hasText(label) and hasClickAction()).assertIsSelected()
        }
    }

    @Test
    fun `toggling analytics reporting on the System tab feeds back into saved settings`() = dialog { result ->
        // Ordinal 0 is Launch at Login, which registers a real OS autostart entry — never touch it.
        onAllNodes(isToggleable())[1].performScrollTo().performClick()
        onNodeWithText("Apply").performClick()

        assertEquals(!AppSettings().analyticsReportingEnabled, result.saved?.analyticsReportingEnabled)
    }

    @Test
    fun `toggling a checkbox on the Bible tab feeds back into saved settings`() = dialog(initialTab = 1) { result ->
        // The checkbox and its label are siblings, so the label is not clickable -- the toggleable
        // node is what has to be pressed.
        onAllNodes(isToggleable())[0].performScrollTo().performClick()
        onNodeWithText("Apply").performClick()

        assertEquals(
            !AppSettings().bibleSettings.multiTranslationDivider,
            result.saved?.bibleSettings?.multiTranslationDivider,
        )
    }

    @Test
    fun `toggling the title slide checkbox on the Song tab feeds back into saved settings`() =
        dialog(initialTab = 2) { result ->
        onNodeWithTag("song_titleSlideEnabled").performScrollTo().performClick()
        onNodeWithText("Apply").performClick()

        assertEquals(!AppSettings().songSettings.titleSlideEnabled, result.saved?.songSettings?.titleSlideEnabled)
    }

    @Test
    fun `changing the background type dropdown on the Background tab feeds back into saved settings`() =
        dialog(initialTab = 3) { result ->
        // The tab opens on the Default surface, whose type segments sit in the editor beside the
        // rail. "Image" names exactly one of them; the rail rows carry their own type as a meta
        // line, so the segment is the only *clickable* node reading it on its own.
        onNode(hasTextExactly("Image") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Apply").performClick()

        assertEquals(Constants.BACKGROUND_IMAGE, result.saved?.backgroundSettings?.defaultBackgroundType)
    }

    // The Lower Third tab's window-left field was exercised here, as this suite's proof that a tab
    // with only number fields feeds Apply. The insets went first -- a Lottie file is self-contained
    // -- and then the tab itself, which duplicated the Lower Third content tab. The Background tab
    // above and the Server tab below carry the same proof for their own control types.

    @Test
    fun `toggling API Key Protection on the Server tab feeds back into saved settings`() =
        dialog(initialTab = 5) { result ->
        // Ordinal 0 is Enable Server, which starts a real server on a real port — never touch it.
        onAllNodes(isToggleable())[1].performScrollTo().performClick()
        onNodeWithText("Apply").performClick()

        assertEquals(true, result.saved?.serverSettings?.apiKeyEnabled)
    }

    @Test
    fun `editing the host field on the ATEM tab feeds back into saved settings`() = dialog(initialTab = 7) { result ->
        onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("test-atem-host")
        onNodeWithText("Apply").performClick()

        assertEquals("test-atem-host", result.saved?.atemSettings?.host)
    }

    @Test
    fun `adding a Companion Satellite connection without OBS feeds back into saved settings`() =
        dialog(initialTab = 9) { result ->
        onNodeWithText("+ Add Connection").performScrollTo().performClick()
        onNodeWithText("Apply").performClick()

        assertEquals(2, result.saved?.companionSatelliteConnections?.size)
    }

    @Test
    fun `the OBS and Companion Satellite tabs feed control changes back into saved settings`() = dialog(
        initialTab = 9,
        obsManager = OBSWebSocketManager(),
    ) { result ->
        onAllNodes(isToggleable())[0].performScrollTo().performClick() // OBS tab: "Connect to OBS Studio"

        onNodeWithText("Companion Satellite").performClick()
        onNodeWithText("+ Add Connection").performScrollTo().performClick()

        onNodeWithText("Apply").performClick()

        assertEquals(true, result.saved?.obsSettings?.enabled)
        assertEquals(2, result.saved?.companionSatelliteConnections?.size)
    }

    @Test
    fun `the tab strip's overflow arrows appear with the overflow and scroll it`() =
        dialog(width = OVERFLOWING_STRIP_WIDTH) {
        // The tabs do not fit this width, which is the whole reason the arrows exist: there is
        // somewhere to go forward to, and nowhere to go back to until we have.
        onNodeWithTag(TAB_STRIP_ARROW_BACK_TAG).assertDoesNotExist()
        onNodeWithTag(TAB_STRIP_ARROW_FORWARD_TAG).assertExists()

        onNodeWithTag(TAB_STRIP_ARROW_FORWARD_TAG).performClick()
        // The strip moved: there is now a way back, which there was not a moment ago.
        waitUntilAtLeastOneExists(hasTestTag(TAB_STRIP_ARROW_BACK_TAG))

        // And back again returns the first tab to view.
        onNodeWithTag(TAB_STRIP_ARROW_BACK_TAG).performClick()
        waitUntilAtLeastOneExists(hasText("System") and hasClickAction())
        onNodeWithText("System").assertIsDisplayed()
    }

    @Test
    fun `an OBS connection adds an OBS tab ahead of Companion Satellite`() = dialog(
        obsManager = OBSWebSocketManager(),
    ) {
        onNodeWithText("OBS").assertExists()

        // The last two tabs sit past the right edge of the strip at this window width — which is
        // what the strip's own overflow arrows are for, so scroll them in the way a user would.
        onNodeWithText("OBS").performScrollTo().performClick()
        onNodeWithText("OBS").assertIsSelected()

        onNodeWithText("Companion Satellite").performScrollTo().performClick()
        onNodeWithText("Companion Satellite").assertIsSelected()
    }

    @Test
    fun `OptionsDialog renders nothing when not visible, using only its required parameters`() = runComposeUiTest {
        setContent {
            OptionsDialog(
                isVisible = false,
                theme = ThemeMode.LIGHT,
                settingsManager = SettingsManager(),
                companionServer = CompanionServer(),
                remoteClientManager = RemoteClientManager(),
                presenterManager = PresenterManager(),
                onDismiss = {},
            )
        }
        onNodeWithText("Apply").assertDoesNotExist()
    }

    @Test
    fun `OptionsDialog renders nothing when not visible, with every optional parameter supplied`() = runComposeUiTest {
        setContent {
            OptionsDialog(
                isVisible = false,
                theme = ThemeMode.LIGHT,
                settingsManager = SettingsManager(),
                companionServer = CompanionServer(),
                remoteClientManager = RemoteClientManager(),
                presenterManager = PresenterManager(),
                onDismiss = {},
                onSave = {},
                onIdentifyScreen = {},
                onIdentifyBrowserSource = {},
                scenes = emptyList(),
                obsManager = null,
                companionSatelliteViewModel = null,
                initialTab = 0,
                initialSettings = AppSettings(),
            )
        }
        onNodeWithText("Apply").assertDoesNotExist()
    }

    @Test
    fun `every optional callback can be supplied explicitly instead of defaulted`() = runComposeUiTest {
        setContent {
            OptionsDialogContent(
                theme = ThemeMode.LIGHT,
                settingsManager = SettingsManager(),
                companionServer = CompanionServer(),
                remoteClientManager = RemoteClientManager(),
                presenterManager = PresenterManager(),
                onDismiss = {},
                onSave = {},
                onIdentifyScreen = {},
                onIdentifyBrowserSource = {},
                scenes = emptyList(),
                obsManager = null,
                companionSatelliteViewModel = null,
                initialTab = 0,
                initialSettings = AppSettings(),
                detectScreens = { emptyList() },
            )
        }
        onNodeWithText("System").assertIsSelected()
    }
}

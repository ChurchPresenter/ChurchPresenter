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
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.ui.TAB_STRIP_ARROW_BACK_TAG
import org.churchpresenter.ui.TAB_STRIP_ARROW_FORWARD_TAG
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ContentLabel
import org.churchpresenter.app.churchpresenter.dialogs.tabs.MetronomeLabel
import org.churchpresenter.app.churchpresenter.dialogs.tabs.chooseRouting
import org.churchpresenter.companionserver.CompanionServer
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

    private class Result {
        var dismissed = 0
        var saved: AppSettings? = null
    }

    private fun dialog(
        initialTab: Int = 0,
        obsManager: OBSWebSocketManager? = null,
        block: ComposeUiTest.(Result) -> Unit,
    ) {
        val result = Result()
        runComposeUiTest {
            setContent {
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
            block(result)
        }
    }

    @Test
    fun `every settings tab is shown without an OBS connection`() = dialog {
        listOf(
            "System", "Bible", "Song", "Background", "Projection", "Lower Third",
            "Server", "Stage Monitor", "ATEM", "Dictionary", "Companion Satellite",
        ).forEach { onNodeWithText(it).assertExists() }
        onNodeWithText("OBS").assertDoesNotExist()
    }

    @Test
    fun `the System tab is selected by default`() = dialog {
        onNodeWithText("System").assertIsSelected()
    }

    @Test
    fun `clicking a different tab switches the selection`() = dialog {
        onNodeWithText("Bible").performClick()
        onNodeWithText("Bible").assertIsSelected()
        onNodeWithText("System").assertIsNotSelected()
    }

    @Test
    fun `initialTab opens directly on that tab`() = dialog(initialTab = 3) {
        onNodeWithText("Background").assertIsSelected()
    }

    @Test
    fun `an out-of-range initialTab is coerced onto the last real tab`() = dialog(initialTab = 999) {
        onNodeWithText("Companion Satellite").assertIsSelected()
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
            "System", "Song", "Background", "Projection", "Lower Third", "Server",
            "Stage Monitor", "ATEM", "Dictionary",
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
        // Both the full-screen and lower-third cards default to "Color" — the full-screen one composes first.
        onAllNodes(hasText("Color") and hasClickAction())[0].performScrollTo().performClick()
        waitForIdle()
        onNode(hasText("Image") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Apply").performClick()

        assertEquals(Constants.BACKGROUND_IMAGE, result.saved?.backgroundSettings?.defaultBackgroundType)
    }

    @Test
    fun `editing the window-left field on the Lower Third tab feeds back into saved settings`() =
        dialog(initialTab = 5) { result ->
        onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("77")
        onNodeWithText("Apply").performClick()

        assertEquals(77, result.saved?.streamingSettings?.windowLeft)
    }

    @Test
    fun `toggling API Key Protection on the Server tab feeds back into saved settings`() =
        dialog(initialTab = 6) { result ->
        // Ordinal 0 is Enable Server, which starts a real server on a real port — never touch it.
        onAllNodes(isToggleable())[1].performScrollTo().performClick()
        onNodeWithText("Apply").performClick()

        assertEquals(true, result.saved?.serverSettings?.apiKeyEnabled)
    }

    @Test
    fun `picking the metronome position on the Stage Monitor tab feeds back into saved settings`() =
        dialog(initialTab = 7) { result ->
        chooseRouting(ContentLabel.METRONOME, MetronomeLabel.CENTER)
        onNodeWithText("Apply").performClick()

        assertEquals(MetronomePosition.CENTER, result.saved?.stageMonitorSettings?.metronomePosition)
    }

    @Test
    fun `editing the host field on the ATEM tab feeds back into saved settings`() = dialog(initialTab = 8) { result ->
        onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("test-atem-host")
        onNodeWithText("Apply").performClick()

        assertEquals("test-atem-host", result.saved?.atemSettings?.host)
    }

    @Test
    fun `toggling Show Word on the Dictionary tab feeds back into saved settings`() = dialog(initialTab = 9) { result ->
        onAllNodes(isToggleable())[0].performScrollTo().performClick()
        onNodeWithText("Apply").performClick()

        assertEquals(!AppSettings().dictionarySettings.showWord, result.saved?.dictionarySettings?.showWord)
    }

    @Test
    fun `adding a Companion Satellite connection without OBS feeds back into saved settings`() =
        dialog(initialTab = 10) { result ->
        onNodeWithText("+ Add Connection").performScrollTo().performClick()
        onNodeWithText("Apply").performClick()

        assertEquals(2, result.saved?.companionSatelliteConnections?.size)
    }

    @Test
    fun `the OBS and Companion Satellite tabs feed control changes back into saved settings`() = dialog(
        initialTab = 10,
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
    fun `the tab strip's overflow arrows appear with the overflow and scroll it`() = dialog {
        // A dozen tabs do not fit this dialog's width, which is the whole reason the arrows exist:
        // there is somewhere to go forward to, and nowhere to go back to until we have.
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
                onOpenLottieGen = { _, _ -> },
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
                onOpenLottieGen = { _, _ -> },
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

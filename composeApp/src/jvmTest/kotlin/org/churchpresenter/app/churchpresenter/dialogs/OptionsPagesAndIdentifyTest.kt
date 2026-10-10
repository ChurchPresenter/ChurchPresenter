@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.obs.OBSWebSocketManager
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OptionsPagesAndIdentifyTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-options-pages").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun dialog(
        initialTab: Int,
        initialSettings: AppSettings = AppSettings(),
        obsManager: OBSWebSocketManager? = null,
        onIdentifyScreen: () -> Unit = {},
        onIdentifyBrowserSource: (Int) -> Unit = {},
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            OptionsDialogContent(
                theme = ThemeMode.LIGHT,
                settingsManager = SettingsManager(),
                companionServer = CompanionServer(shutdownGraceMs = 0),
                remoteClientManager = RemoteClientManager(),
                onDismiss = {},
                onIdentifyScreen = onIdentifyScreen,
                onIdentifyBrowserSource = onIdentifyBrowserSource,
                obsManager = obsManager,
                initialTab = initialTab,
                initialSettings = initialSettings,
                detectScreens = { emptyList() },
            )
        }
        block()
    }

    private fun ComposeUiTest.tab(label: String) = onNode(hasText(label) and isSelectable())

    @Test
    fun `each settings page maps to its own place in the tab row`() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5, 6, 7),
            SettingsPage.entries.map(::optionsTabIndexOf),
        )
    }

    @Test
    fun `a page's index opens the dialog on that page's tab`() {
        mapOf(
            SettingsPage.SYSTEM to "System",
            SettingsPage.BIBLE to "Bible",
            SettingsPage.PROFILES to "Profiles",
            SettingsPage.SERVER to "Server",
        ).forEach { (page, label) ->
            dialog(initialTab = optionsTabIndexOf(page)) { tab(label).assertIsSelected() }
        }
    }

    @Test
    fun `the integrations page opens on the OBS tab when OBS is connected`() =
        dialog(initialTab = optionsTabIndexOf(SettingsPage.INTEGRATIONS), obsManager = OBSWebSocketManager()) {
            tab("OBS").assertIsSelected()
        }

    @Test
    fun `the Projection page's identify buttons reach their callbacks`() {
        var screens = 0
        val browserSources = mutableListOf<Int>()
        val settings = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    browserSourceOutputs = listOf(ScreenAssignment(), ScreenAssignment()),
                ),
            )
        }
        dialog(
            initialTab = optionsTabIndexOf(SettingsPage.PROJECTION),
            initialSettings = settings,
            onIdentifyScreen = { screens++ },
            onIdentifyBrowserSource = { browserSources += it },
        ) {
            val identify = onAllNodes(hasText("Identify"))
            assertEquals(3, identify.fetchSemanticsNodes().size, "one for the screens and one per Browser Source")
            identify[0].performScrollTo().performClick()
            identify[2].performScrollTo().performClick()
            waitForIdle()
        }
        assertEquals(1, screens)
        assertEquals(listOf(1), browserSources)
    }
}

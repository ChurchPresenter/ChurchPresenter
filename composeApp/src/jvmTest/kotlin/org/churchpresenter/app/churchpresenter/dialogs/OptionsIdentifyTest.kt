@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.dialogs.DialogFrame
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.settings.TabLabelStyle
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OptionsIdentifyTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-options-identify").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val oneBrowserSource = AppSettings(
        tabLabelStyle = TabLabelStyle.ICONS_AND_TEXT,
        projectionSettings = ProjectionSettings(browserSourceOutputs = listOf(ScreenAssignment())),
    )

    private fun sized(block: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT), density = Density(1f)) { block() }

    private fun SkikoComposeUiTest.openTab(label: String) {
        onNode(hasText(label) and isSelectable()).performClick()
        waitForIdle()
    }

    private fun SkikoComposeUiTest.identifyButtons() = onAllNodes(hasTextExactly("Identify") and hasClickAction())

    private fun SkikoComposeUiTest.pressEveryIdentify(): Int {
        val count = identifyButtons().fetchSemanticsNodes().size
        repeat(count) { identifyButtons()[it].performScrollTo().performClick() }
        waitForIdle()
        return count
    }

    @Test
    fun `the Projection tab's Identify buttons reach the screen and the browser source callbacks`() {
        val screens = mutableListOf<Unit>()
        val browserSources = mutableListOf<Int>()
        sized {
            setContent {
                OptionsDialogContent(
                    theme = ThemeMode.LIGHT,
                    settingsManager = SettingsManager(),
                    companionServer = CompanionServer(shutdownGraceMs = 0),
                    remoteClientManager = RemoteClientManager(),
                    onDismiss = {},
                    onIdentifyScreen = { screens += Unit },
                    onIdentifyBrowserSource = { browserSources += it },
                    initialSettings = oneBrowserSource,
                    detectScreens = { emptyList() },
                )
            }
            openTab("Projection")
            assertEquals(2, pressEveryIdentify())
        }
        assertEquals(1, screens.size)
        assertEquals(listOf(0), browserSources)
    }

    @Test
    fun `left to its defaults the content identifies nothing and OK still saves`() = sized {
        setContent {
            OptionsDialogContent(
                theme = ThemeMode.LIGHT,
                settingsManager = SettingsManager(),
                companionServer = CompanionServer(shutdownGraceMs = 0),
                remoteClientManager = RemoteClientManager(),
                onDismiss = {},
                initialSettings = oneBrowserSource,
                detectScreens = { emptyList() },
            )
        }
        openTab("Projection")
        assertEquals(2, pressEveryIdentify())
        onNodeWithText("✓ OK").performClick()
        waitForIdle()
        assertEquals(TabLabelStyle.ICONS_AND_TEXT, SettingsManager().loadSettings().tabLabelStyle)
    }

    @Test
    fun `the dialog left to its defaults identifies from Profiles and saves on OK`() {
        var dismissed = 0
        val frame: DialogFrame = { _, content -> Box(Modifier.fillMaxSize()) { content() } }
        sized {
            setContent {
                OptionsDialog(
                    isVisible = true,
                    theme = ThemeMode.LIGHT,
                    settingsManager = SettingsManager(),
                    companionServer = CompanionServer(shutdownGraceMs = 0),
                    remoteClientManager = RemoteClientManager(),
                    onDismiss = { dismissed++ },
                    initialSettings = oneBrowserSource,
                    frame = frame,
                )
            }
            openTab("Profiles")
            onNodeWithTag("profile_nav_outputs").performClick()
            waitForIdle()
            assertEquals(1, pressEveryIdentify())

            onNodeWithText("✓ OK").performClick()
            waitForIdle()
        }
        assertEquals(1, dismissed)
        assertEquals(TabLabelStyle.ICONS_AND_TEXT, SettingsManager().loadSettings().tabLabelStyle)
    }

    private companion object {
        const val WIDTH = 1400f
        const val HEIGHT = 900f
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.signage.hub.SignageHub
import org.churchpresenter.signage.tab.SignageController
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class MainDesktopBareRootTest {

    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob())

    @BeforeTest
    fun setUp() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp").delete()
        dir = Files.createTempDirectory("cp-main-desktop-bare").toFile()
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
        File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp").delete()
    }

    private fun settings(only: Tabs? = null) = AppSettings(
        songSettings = SongSettings(storageDirectory = dir.absolutePath),
        hiddenTabs = if (only == null) emptySet() else Tabs.entries.filter { it != only }.map { it.name }.toSet(),
    )

    private fun screen(settings: AppSettings, block: ComposeUiTest.() -> Unit, content: @Composable () -> Unit) =
        runComposeUiTest(testTimeout = 60.seconds) {
            setContent {
                CompositionLocalProvider(LocalShortcuts provides ShortcutMap.from(settings.keyboardShortcutSettings)) {
                    MaterialTheme { content() }
                }
            }
            waitForIdle()
            block()
        }

    private fun live() = LiveOutputCallbacks(presenting = {}, onVerseSelected = {}, onSongItemSelected = {})

    private fun ComposeUiTest.press(key: Key, ctrl: Boolean = false) {
        onAllNodes(isRoot())[0].performKeyInput {
            if (ctrl) keyDown(Key.CtrlLeft)
            pressKey(key)
            if (ctrl) keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    @Test
    fun `the screen's own keys and buttons reach the handlers it is given`() {
        val settings = settings(Tabs.LOWER_THIRD).let {
            it.copy(streamingSettings = it.streamingSettings.copy(lowerThirdFolder = File(dir, "lt").absolutePath))
        }
        var settingsOpened = 0
        var backgroundsOpened = 0
        val generated = mutableListOf<String>()
        screen(settings, block = {
            onNodeWithContentDescription("Settings").performClick()
            onNodeWithContentDescription("Background").performClick()
            onNodeWithText("Generate").performClick()
            waitForIdle()
            assertEquals(1, settingsOpened)
            assertEquals(1, backgroundsOpened)
            assertEquals(listOf(File(dir, "lt").absolutePath), generated)
        }) {
            MainDesktop(
                appSettings = settings,
                presenterManager = PresenterManager(),
                live = live(),
                companionSatelliteViewModel = CompanionSatelliteViewModel(),
                onShowSettings = { settingsOpened++ },
                onShowBackgroundSettings = { backgroundsOpened++ },
                onOpenLottieGen = { folder, _ -> generated += folder },
            )
        }
    }

    @Test
    fun `a quick background and the developer unlock work with no handler given`() {
        val settings = settings().copy(quickBackgrounds = listOf(QuickBackground(id = "q1", label = "Blue")))
        val manager = PresenterManager()
        screen(settings, block = {
            press(Key.One, ctrl = true)
            repeat(7) { press(Key.D) }
            onNodeWithContentDescription("Settings").assertExists()
        }) {
            MainDesktop(
                appSettings = settings,
                presenterManager = manager,
                live = live(),
                companionSatelliteViewModel = CompanionSatelliteViewModel(),
            )
        }
    }

    @Test
    fun `the signage tab is drawn from its input, and its server button is wired`() {
        val folder = File(dir, "signage").apply { mkdirs() }.toPath()
        val hub = SignageHub(folder, "Test", scope)
        val controller = SignageController(hub, folder, scope)
        var turnedOn = 0
        val settings = settings(Tabs.SIGNAGE)
        screen(settings, block = {
            onNodeWithText("Turn on server").performClick()
            waitForIdle()
            assertEquals(1, turnedOn)
        }) {
            MainDesktop(
                appSettings = settings,
                presenterManager = PresenterManager(),
                live = live(),
                companionSatelliteViewModel = CompanionSatelliteViewModel(),
                signage = SignageTabInput(controller, serverRunning = false, onTurnOnServer = { turnedOn++ }),
            )
        }
    }
}

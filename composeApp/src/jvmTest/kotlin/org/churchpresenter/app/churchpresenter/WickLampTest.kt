@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.sharedui.models.Presenting
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The lamp as the app draws it, fed from the app's own state. */
class WickLampTest {

    // Launched work is never run: nothing here reaches the network.
    private val scope = CoroutineScope(SupervisorJob() + Executor { }.asCoroutineDispatcher())

    private val root = AppRootState(
        object : ApplicationScope {
            override fun exitApplication() = Unit
        },
        scope,
        secondaryDisplays = { emptyList() },
    )

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    /** Past the panel's open and close animations, with the clock held. */
    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS, ignoreFrameDuration = true)
        waitForIdle()
    }

    @Test
    fun `the lamp opens Wick, and putting it away from the panel hides it`() = runComposeUiTest {
        root.appSettings = root.appSettings.copy(helper = HelperSettings(tipsEnabled = false, startedByUser = true))
        // The lamp flickers for ever: with the clock held, idle means nothing left to compose.
        mainClock.autoAdvance = false
        setContent { MaterialTheme { root.WickLamp(Modifier, devices = emptyArray()) } }
        waitForIdle()
        onNodeWithTag("helper.lamp").performClick()
        settle()
        assertTrue(root.helperState.isOpen)
        onNodeWithTag("helper.input").performTextInput("hello")
        onNodeWithTag("helper.hide").performClick()
        settle()
        onNodeWithTag("helper.primary").performClick()
        settle()
        assertFalse(root.appSettings.helper.enabled)
        onNodeWithTag("helper.lamp").assertDoesNotExist()
    }

    @Test
    fun `a hidden lamp is not drawn, whatever the app is doing`() = runComposeUiTest {
        root.appSettings = root.appSettings.copy(helper = HelperSettings(enabled = false, startedByUser = true))
        root.helperSongCount = 0
        root.presenterManager.setPresentingMode(Presenting.LYRICS)
        mainClock.autoAdvance = false
        setContent { MaterialTheme { root.WickLamp(Modifier, devices = emptyArray()) } }
        waitForIdle()
        onNodeWithTag("helper.lamp").assertDoesNotExist()
    }

    @Test
    fun `the main window's host draws the lamp and asks for Wick's pack once`() = runComposeUiTest {
        root.appSettings = root.appSettings.copy(helper = HelperSettings(tipsEnabled = false, introSeen = true))
        var refreshed = 0
        mainClock.autoAdvance = false
        setContent { MaterialTheme { root.HelperHost(Modifier, refreshPack = { refreshed++ }) } }
        settle()
        onNodeWithTag("helper.lamp").assertExists()
        assertEquals(1, refreshed)
        root.helperState.session.otherLamps = 1
        settle()
        onNodeWithTag("helper.lamp").assertDoesNotExist()
    }

    @Test
    fun `the intro, once finished, is remembered and points at the lamp`() = runComposeUiTest {
        root.appSettings = root.appSettings.copy(helper = HelperSettings(tipsEnabled = false))
        root.helperState.replayIntro = true
        mainClock.autoAdvance = false
        setContent { MaterialTheme { root.HelperHost(Modifier, refreshPack = {}) } }
        settle()
        onNodeWithTag("helper.intro.skip").performClick()
        settle()
        assertTrue(root.appSettings.helper.introSeen)
        assertFalse(root.helperState.replayIntro)
        assertTrue(root.helperState.introPointer)
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}

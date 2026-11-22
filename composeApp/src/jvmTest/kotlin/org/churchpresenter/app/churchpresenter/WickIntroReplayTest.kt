@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.settings.SettingsManager
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WickIntroReplayTest {

    private val scope = CoroutineScope(SupervisorJob() + Executor { }.asCoroutineDispatcher())
    private val settingsDir: File = Files.createTempDirectory("wick-intro-replay").toFile()
    private val root = AppRootState(
        object : ApplicationScope {
            override fun exitApplication() = Unit
        },
        scope,
        secondaryDisplays = { emptyList() },
        SettingsManager(settingsDir),
    )

    @AfterTest
    fun tearDown() {
        scope.cancel()
        SettingsManager.flushAllSaves()
        settingsDir.deleteRecursively()
    }

    @Test
    fun `an intro replayed after it was seen points at the lamp and saves nothing`() = runComposeUiTest {
        root.appSettings = root.appSettings.copy(helper = HelperSettings(tipsEnabled = false, introSeen = true))
        val before = root.appSettings
        root.helperState.replayIntro = true
        mainClock.autoAdvance = false
        setContent { MaterialTheme { root.HelperHost(Modifier, refreshPack = {}) } }
        mainClock.advanceTimeBy(SETTLE_MS, ignoreFrameDuration = true)
        waitForIdle()

        onNodeWithTag("helper.intro.skip").performClick()
        mainClock.advanceTimeBy(SETTLE_MS, ignoreFrameDuration = true)
        waitForIdle()

        assertSame(before, root.appSettings)
        assertFalse(root.helperState.replayIntro)
        assertTrue(root.helperState.introPointer)
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}

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
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WickIntroReplayTest {

    private val scope = CoroutineScope(SupervisorJob() + Executor { }.asCoroutineDispatcher())

    private val root by lazy {
        TestSingletons.latchToTestHome()
        AppRootState(
            object : ApplicationScope {
                override fun exitApplication() = Unit
            },
            scope,
            secondaryDisplays = { emptyList() },
        )
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `an intro played again after it was seen leaves the settings as they were`() = runComposeUiTest {
        root.appSettings = root.appSettings.copy(helper = HelperSettings(tipsEnabled = false, introSeen = true))
        val before = root.appSettings
        root.helperState.replayIntro = true
        mainClock.autoAdvance = false
        setContent { MaterialTheme { root.HelperHost(Modifier, refreshPack = {}) } }
        mainClock.advanceTimeBy(1_000L, ignoreFrameDuration = true)
        waitForIdle()
        onNodeWithTag("helper.intro.skip").performClick()
        mainClock.advanceTimeBy(1_000L, ignoreFrameDuration = true)
        waitForIdle()
        assertSame(before, root.appSettings, "nothing new to remember, so nothing is saved")
        assertFalse(root.helperState.replayIntro)
        assertTrue(root.helperState.introPointer)
    }
}

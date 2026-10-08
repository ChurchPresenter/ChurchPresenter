@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.app.churchpresenter.remote.AppShowHost
import org.churchpresenter.app.churchpresenter.remote.ShowOutlets
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.clearFromOperator
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.ActionRunner

/** An operator's clear stops the action lists still waiting. */
class ShowControlEffectsTest {

    @Test
    fun `clearing the outputs cancels a list that is still waiting`() = runComposeUiTest {
        val manager = PresenterManager(showPresenterWindowInitially = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val runner = ActionRunner(AppShowHost(manager, { AppSettings() }, ShowOutlets()), scope)
        setContent { MaterialTheme { ShowControlEffects(manager, runner) } }
        waitForIdle()

        runner.run(listOf(Action.Wait(60.0)), "row")
        assertTrue(runner.isRunning("row"))
        manager.clearFromOperator()
        waitUntil(timeoutMillis = 5_000) { !runner.isRunning("row") }
        assertFalse(runner.isRunning("row"))
        scope.cancel()
    }
}

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Signage runs while its tab is shown: showing it makes the hub and controller once and hands the
 * tab its input, and hiding it switches signage off on the server and takes the input away.
 */
@OptIn(ExperimentalTestApi::class)
class SignageWiringTest {

    private val scope = CoroutineScope(SupervisorJob())
    private val root = AppRootState(
        object : ApplicationScope {
            override fun exitApplication() = Unit
        },
        scope,
        secondaryDisplays = { emptyList() },
    )

    @AfterTest
    fun cleanUp() {
        root.signageController?.stop()
        scope.cancel()
    }

    private fun showTab(shown: Boolean) {
        val hidden = root.appSettings.hiddenTabs - Tabs.SIGNAGE.name
        root.appSettings = root.appSettings.copy(hiddenTabs = if (shown) hidden else hidden + Tabs.SIGNAGE.name)
    }

    @Test
    fun `signage follows its tab being shown and hidden`() = runComposeUiTest {
        showTab(false)
        var input: SignageTabInput? = null
        setContent {
            root.SignageEffect()
            input = root.signageTabInput()
        }
        waitForIdle()
        assertNull(root.signageController, "a hidden tab starts nothing")
        assertNull(input)
        assertFalse(root.companionServer.signageEnabled)
        assertEquals(root.devMode, root.companionServer.devMode)

        showTab(true)
        waitUntil(timeoutMillis = WAIT_MS) { input != null }
        val controller = assertNotNull(root.signageController)
        assertTrue(root.companionServer.signageEnabled)
        assertNotNull(root.companionServer.signage, "the server shares the tab's hub")
        assertSame(controller, input?.controller)
        assertFalse(input?.serverRunning ?: true)

        showTab(false)
        waitUntil(timeoutMillis = WAIT_MS) { input == null }
        assertFalse(root.companionServer.signageEnabled)

        showTab(true)
        waitUntil(timeoutMillis = WAIT_MS) { input != null }
        assertSame(controller, root.signageController, "shown again, the same controller starts again")
    }

    @Test
    fun `the screens are told the analytics setting`() = runComposeUiTest {
        root.appSettings = root.appSettings.copy(analyticsReportingEnabled = false)
        setContent { root.SignageEffect() }
        waitForIdle()
        assertFalse(root.signageDiagnostics.value)
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class MainDesktopInputsSignageTest {

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

    @Test
    fun `the signage input left without a key keeps none and saves nothing`() = runComposeUiTest {
        root.appSettings = root.appSettings.copy(hiddenTabs = root.appSettings.hiddenTabs - Tabs.SIGNAGE.name)
        setContent { root.SignageEffect() }
        waitUntil(timeoutMillis = 5_000) { root.signageController != null }
        val controller = assertNotNull(root.signageController)
        var turnedOn = 0

        val input = SignageTabInput(controller, serverRunning = false, onTurnOnServer = { turnedOn++ })
        input.onTurnOnServer()
        input.onSmugMugApiKey("key")

        assertSame(controller, input.controller)
        assertEquals("", input.smugMugApiKey)
        assertEquals(1, turnedOn)
    }
}
